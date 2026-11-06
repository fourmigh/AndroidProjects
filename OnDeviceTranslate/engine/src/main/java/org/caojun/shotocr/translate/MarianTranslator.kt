package org.caojun.shotocr.translate

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.PriorityQueue
import kotlin.math.exp
import kotlin.math.ln

/**
 * 单个翻译方向的 Marian (OPUS-MT) 翻译器。
 *
 * 内含 encoder + decoder 两个 ONNX session（decoder 为 `decoder_model_merged` 变体）。
 * ONNX Runtime Android 不提供 BeamSearch contrib op，故自行实现 beam search。
 *
 * 解码策略：**不使用 KV cache**。merged decoder 的 `use_cache_branch=false` 分支经过实测可用，
 * 而 `true` 分支（复用 past）在较长序列上会触发 `encoder_attn` 的 Reshape 错误；
 * 分类名很短，直接每步喂入完整序列即可，实现也更简单可靠。
 *
 * 创建代价较高（需加载模型），应缓存复用；[close] 释放 session / tokenizer / mmap 缓冲。
 * 实例非线程安全，调用方需保证串行调用。
 */
internal class MarianTranslator(
    private val env: OrtEnvironment,
    private val encoderSession: OrtSession,
    private val decoderSession: OrtSession,
    private val tokenizer: TranslateTokenizer,
    private val modelBuffers: List<ByteBuffer>,
    configJson: String,
    genJson: String,
) : AutoCloseable {

    private val gen = parseGenConfig(genJson, configJson)
    private val encIo = parseEncoderIo(encoderSession)
    private val decIo = parseDecoderIo(decoderSession)
    private val hiddenSize = resolveHiddenSize()

    /** `use_cache_branch=false` 常量张量，复用；[close] 时释放。 */
    private val noCacheBranchTensor: OnnxTensor = boolTensor(false)

    /** 24 个零长度 past 输入张量（merged decoder 的必填 graph input），复用。 */
    private val emptyPastTensors: List<OnnxTensor> = decIo.pastInputNames.map { name ->
        val shape = inputShapeOf(name).also { it[0] = 1L; it[2] = 0L }
        OnnxTensor.createTensor(env, ByteBuffer.allocateDirect(0).asFloatBuffer(), shape)
    }

    private var closed = false

    /** 将 [text] 翻译为目标语言；空白输入直接返回空串。 */
    fun translate(text: String): String {
        check(!closed) { "translator closed" }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        // 单个英文单词直接输入时，opus-mt-en-zh 会退化重复（如 Learning -> 学习学习学习学习）。
        // 实测加冒号可诱导模型走「词条翻译」通路，且多词短语不受影响，故仅在必要时改写，
        // 并在输出端剥离该冒号（用户看不到这一层加工）。
        val needsHint = SINGLE_ENGLISH_WORD.matches(trimmed)
        val modelInput = if (needsHint) "$trimmed$HINT_SUFFIX" else trimmed

        val srcIds = tokenizer.encode(modelInput)
        if (srcIds.isEmpty()) return ""

        val srcLen = srcIds.size
        val hidden = runEncoder(srcIds)
        val decoded = decode(hidden, srcLen)
        return if (needsHint) decoded.stripHintSuffix() else decoded
    }

    // ---------------------------------------------------------------- encoder

    private fun runEncoder(inputIds: IntArray): FloatArray {
        val srcLen = inputIds.size
        val idsTensor = OnnxTensor.createTensor(env, longBuffer(inputIds), longArrayOf(1, srcLen.toLong()))
        val maskTensor = OnnxTensor.createTensor(
            env, longBuffer(IntArray(srcLen) { 1 }), longArrayOf(1, srcLen.toLong())
        )

        val inputs = HashMap<String, OnnxTensor>(2)
        inputs[encIo.inputIdsName] = idsTensor
        inputs[encIo.attentionMaskName] = maskTensor

        try {
            encoderSession.run(inputs).use { results ->
                return flattenTo1D(requireOutput(results, encIo.hiddenOutputName))
            }
        } finally {
            idsTensor.close()
            maskTensor.close()
        }
    }

    // ------------------------------------------------------------- beam search

    private class BeamState(val tokens: List<Long>, val score: Double)

    private data class Candidate(val beamIndex: Int, val tokenId: Int, val score: Double)

    private fun decode(hidden: FloatArray, srcLen: Int): String {
        val stepCap = minOf(gen.maxLength, DECODE_STEP_CAP)
        val startedAt = System.nanoTime()

        var active = listOf(BeamState(listOf(gen.decoderStartTokenId), 0.0))
        val finished = mutableListOf<BeamState>()
        var step = 0
        var converged = false

        if (VERBOSE) {
            Log.d(TAG, "decode start: srcLen=$srcLen stepCap=$stepCap beams=${gen.numBeams} vocab=${gen.vocabSize}")
        }

        while (step < stepCap && active.isNotEmpty()) {
            val stepStartedAt = System.nanoTime()

            val stepLogits = Array(active.size) { i -> runDecoderStep(active[i].tokens, hidden, srcLen) }

            if (VERBOSE && step == 0) {
                val first = stepLogits[0]
                var nanCount = 0
                for (v in first) if (v.isNaN()) nanCount++
                Log.d(TAG, "step0 logits: nan=$nanCount top3=${topKValues(first, 3)}")
            }

            val candidates = ArrayList<Candidate>(active.size * gen.numBeams * 2)
            for (i in active.indices) {
                val logits = stepLogits[i]
                applyBadWords(logits)
                val logProbs = logSoftmax(logits)
                for (token in topKIndices(logProbs, gen.numBeams * 2)) {
                    candidates.add(Candidate(i, token, active[i].score + logProbs[token].toDouble()))
                }
            }
            candidates.sortByDescending { it.score }

            val nextActive = mutableListOf<BeamState>()
            for (cand in candidates) {
                if (nextActive.size >= gen.numBeams && finished.size >= gen.numBeams) break
                val parent = active[cand.beamIndex]
                val tokens = parent.tokens + cand.tokenId.toLong()
                if (cand.tokenId.toLong() == gen.eosTokenId) {
                    finished.add(BeamState(tokens, cand.score))
                } else if (nextActive.size < gen.numBeams) {
                    nextActive.add(BeamState(tokens, cand.score))
                }
            }

            // 不裁剪 finished：数量天然有界，且 early-stop 会及时收敛
            step++
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            if (VERBOSE) {
                val stepMs = (System.nanoTime() - stepStartedAt) / 1_000_000
                Log.d(
                    TAG,
                    "step=$step active=${nextActive.size} finished=${finished.size} " +
                        "bestActive=${nextActive.maxOfOrNull { it.score }} stepMs=$stepMs totalMs=$elapsedMs"
                )
            }

            // EARLY STOP：已完成 numBeams 条，且最好的活跃 beam 也追不上最差的已完成 beam。
            // 统一使用原始累加分（与 transformers 的 BeamSearchScorer 一致），
            // 长度归一化只在最终挑选答案时应用。
            if (finished.size >= gen.numBeams) {
                val worstFinished = finished.minOf { it.score }
                val bestActive = nextActive.maxOfOrNull { it.score } ?: Double.NEGATIVE_INFINITY
                if (worstFinished >= bestActive) {
                    converged = true
                    if (VERBOSE) Log.d(TAG, "converged at step=$step")
                    break
                }
            }

            // 墙钟兜底（native 调用很短，这里能可靠生效）
            if (elapsedMs > DECODE_TIMEOUT_MS) {
                throw TranslateException("decode timeout after ${elapsedMs}ms (step=$step, srcLen=$srcLen)")
            }

            active = nextActive
        }

        if (finished.isEmpty()) {
            // 没有任何候选吐出 EOS（罕见）：只能退回最佳活跃 beam + 强制结束符
            val fallback = active.maxByOrNull { it.score }
            if (fallback == null) {
                if (VERBOSE) Log.d(TAG, "decode produced nothing (steps=$step)")
                return ""
            }
            finished.add(BeamState(fallback.tokens + gen.forcedEosTokenId, fallback.score))
            if (VERBOSE) {
                Log.d(TAG, "no EOS candidate, fallback to active beam (steps=$step, tokens=${fallback.tokens.size})")
            }
        }

        val best = finished.maxByOrNull { it.score / it.tokens.size } ?: return ""
        val ids = best.tokens
            .drop(1)                                        // 去掉 decoder_start_token_id
            .filter { it != gen.eosTokenId && it != gen.padTokenId }
            .map { it.toInt() }
            .toIntArray()

        if (VERBOSE) {
            val totalMs = (System.nanoTime() - startedAt) / 1_000_000
            Log.d(
                TAG,
                "decode done: steps=$step converged=$converged tokens=${ids.size} totalMs=$totalMs ids=${ids.joinToString(",")}"
            )
        }
        return tokenizer.decode(ids)
    }

    /** 诊断用：返回分值最高的 k 个 (token, value)。 */
    private fun topKValues(values: FloatArray, k: Int): String =
        topKIndices(values, k).joinToString(", ") { "($it, ${values[it]})" }

    /** 跑一步 decoder（无 cache），返回末位 logits（长度 = vocab）。 */
    private fun runDecoderStep(seq: List<Long>, hidden: FloatArray, srcLen: Int): FloatArray {
        val ids = OnnxTensor.createTensor(
            env, longBuffer(seq.toLongArray()), longArrayOf(1, seq.size.toLong())
        )
        val hiddenTensor = OnnxTensor.createTensor(
            env, floatBuffer(hidden), longArrayOf(1, srcLen.toLong(), hiddenSize.toLong())
        )
        val maskTensor = OnnxTensor.createTensor(
            env, longBuffer(LongArray(srcLen) { 1L }), longArrayOf(1, srcLen.toLong())
        )

        val inputs = HashMap<String, OnnxTensor>(4 + emptyPastTensors.size)
        inputs[decIo.inputIdsName] = ids
        inputs[decIo.encoderHiddenName] = hiddenTensor
        inputs[decIo.encoderMaskName] = maskTensor
        inputs[decIo.useCacheBranchName] = noCacheBranchTensor
        for (i in decIo.pastInputNames.indices) {
            inputs[decIo.pastInputNames[i]] = emptyPastTensors[i]
        }

        try {
            decoderSession.run(inputs).use { results ->
                val logits = flattenTo1D(requireOutput(results, decIo.logitsName))
                val vocab = gen.vocabSize
                val seqLen = logits.size / vocab
                return logits.copyOfRange((seqLen - 1) * vocab, seqLen * vocab)
            }
        } finally {
            ids.close()
            hiddenTensor.close()
            maskTensor.close()
        }
    }

    // ------------------------------------------------------------------ 数值工具

    private fun applyBadWords(logits: FloatArray) {
        for (bad in gen.badWordsIds) {
            if (bad in logits.indices) logits[bad] = Float.NEGATIVE_INFINITY
        }
    }

    /** `log_softmax`；配合 [applyBadWords] 的 -inf 等价于 `renormalize_logits`。 */
    private fun logSoftmax(logits: FloatArray): FloatArray {
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        if (max.isInfinite()) return logits

        var sum = 0.0
        for (v in logits) sum += exp(v - max)
        val logSum = ln(sum) + max

        return FloatArray(logits.size) { i -> (logits[i] - logSum).toFloat() }
    }

    private fun topKIndices(values: FloatArray, k: Int): List<Int> {
        val size = minOf(k, values.size)
        val queue = PriorityQueue<Pair<Int, Float>>(size + 1, compareBy { it.second })
        for (i in values.indices) {
            val v = values[i]
            if (queue.size < size) {
                queue.add(i to v)
            } else if (v > (queue.peek()?.second ?: Float.NEGATIVE_INFINITY)) {
                queue.poll()
                queue.add(i to v)
            }
        }
        return queue.map { it.first }
    }

    // ------------------------------------------------------------------ IO 解析

    private class EncoderIO(
        val inputIdsName: String,
        val attentionMaskName: String,
        val hiddenOutputName: String,
    )

    private class DecoderIO(
        val inputIdsName: String,
        val encoderHiddenName: String,
        val encoderMaskName: String,
        val useCacheBranchName: String,
        val pastInputNames: List<String>,
        val logitsName: String,
    )

    private fun parseEncoderIo(session: OrtSession): EncoderIO {
        val inputNames = session.inputNames.toList()
        return EncoderIO(
            inputIdsName = inputNames.first { it.contains("input_ids") },
            attentionMaskName = inputNames.first { it.contains("attention_mask") },
            hiddenOutputName = session.outputNames.first(),
        )
    }

    private fun parseDecoderIo(session: OrtSession): DecoderIO {
        val inputNames = session.inputNames.toList()
        val outputNames = session.outputNames.toList()

        val useCache = inputNames.firstOrNull { it.contains("use_cache_branch") }
            ?: error("decoder has no use_cache_branch input: $inputNames")

        val pastNames = inputNames
            .filter { it.contains("past_key_values") }
            .sortedWith(compareBy({ indexOfNumber(it) }, { if (it.endsWith(".value")) 1 else 0 }, { if (it.contains("encoder")) 1 else 0 }))

        require(pastNames.isNotEmpty()) { "decoder has no past inputs: $inputNames" }

        return DecoderIO(
            inputIdsName = inputNames.first { it.contains("input_ids") },
            encoderHiddenName = inputNames.first { it.contains("encoder_hidden") },
            encoderMaskName = inputNames.first { it.contains("encoder_attention_mask") },
            useCacheBranchName = useCache,
            pastInputNames = pastNames,
            logitsName = outputNames.first { it.contains("logits") },
        )
    }

    private fun indexOfNumber(name: String): Int =
        Regex("""(\d+)""").find(name)?.groupValues?.get(1)?.toInt() ?: 0

    private fun tensorInfoOf(name: String): TensorInfo =
        decoderSession.inputInfo[name]?.info as? TensorInfo
            ?: error("no tensor info for input: $name")

    private fun inputShapeOf(name: String): LongArray = tensorInfoOf(name).shape.clone()

    private fun resolveHiddenSize(): Int =
        tensorInfoOf(decIo.encoderHiddenName).shape.lastOrNull()?.takeIf { it > 0 }?.toInt() ?: 512

    // ------------------------------------------------------------------ helpers

    private fun requireOutput(results: OrtSession.Result, name: String): Any =
        results.get(name).orElse(null)?.value ?: error("missing output: $name")

    private fun flattenTo1D(value: Any): FloatArray {
        val leaves = ArrayList<FloatArray>(8)
        collectLeaves(value, leaves)
        var total = 0
        for (leaf in leaves) total += leaf.size
        val out = FloatArray(total)
        var offset = 0
        for (leaf in leaves) {
            System.arraycopy(leaf, 0, out, offset, leaf.size)
            offset += leaf.size
        }
        return out
    }

    private fun collectLeaves(value: Any?, out: MutableList<FloatArray>) {
        when (value) {
            is FloatArray -> out.add(value)
            is Array<*> -> for (element in value) collectLeaves(element, out)
            else -> error("unexpected tensor element type: ${value?.javaClass?.name}")
        }
    }

    private fun boolTensor(value: Boolean): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(1)
        buffer.put(if (value) 1 else 0)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, longArrayOf(1), OnnxJavaType.BOOL)
    }

    private fun longBuffer(values: IntArray): LongBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 8)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()
        for (v in values) buffer.put(v.toLong())
        buffer.rewind()
        return buffer
    }

    private fun longBuffer(values: LongArray): LongBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 8)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()
        buffer.put(values)
        buffer.rewind()
        return buffer
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(values)
        buffer.rewind()
        return buffer
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { noCacheBranchTensor.close() }
        for (t in emptyPastTensors) runCatching { t.close() }
        runCatching { encoderSession.close() }
        runCatching { decoderSession.close() }
        runCatching { tokenizer.close() }
    }

    companion object {

        private const val TAG = "TranslateEngine"
        private const val DECODE_STEP_CAP = 24
        private const val DECODE_TIMEOUT_MS = 15_000L
        private const val VERBOSE = false

        /** 仅用于诱导模型、随后会被剥离的输入后缀。 */
        private const val HINT_SUFFIX = ":"

        /** 单个英文单词（可含连字符/撇号），例如 `Learning`、`In-store`、`O'Brien`。 */
        private val SINGLE_ENGLISH_WORD = Regex("""[A-Za-z][A-Za-z'\-]*""")

        /** 剥离模型跟随 [HINT_SUFFIX] 回带的尾部冒号（含全角）。 */
        private fun String.stripHintSuffix(): String {
            val t = trim()
            return when {
                t.endsWith("：") -> t.dropLast(1).trim()
                t.endsWith(":") -> t.dropLast(1).trim()
                else -> t
            }
        }

        internal class GenConfig(
            val badWordsIds: Set<Int>,
            val decoderStartTokenId: Long,
            val eosTokenId: Long,
            val padTokenId: Long,
            val forcedEosTokenId: Long,
            val maxLength: Int,
            val numBeams: Int,
            val vocabSize: Int,
        )

        /** 解析 `generation_config.json`；`vocab_size` 取自模型 `config.json`。 */
        internal fun parseGenConfig(genJson: String, configJson: String): GenConfig {
            val gen = JSONObject(genJson)
            val config = JSONObject(configJson)

            val badWords = mutableSetOf<Int>()
            if (gen.has("bad_words_ids")) {
                val arr = gen.getJSONArray("bad_words_ids")
                for (i in 0 until arr.length()) {
                    val inner = arr.getJSONArray(i)
                    for (j in 0 until inner.length()) badWords.add(inner.getInt(j))
                }
            }

            val eos = gen.optLong("eos_token_id", config.optLong("eos_token_id", 0L))

            return GenConfig(
                badWordsIds = badWords,
                decoderStartTokenId = gen.optLong(
                    "decoder_start_token_id", config.optLong("decoder_start_token_id", 65000L)
                ),
                eosTokenId = eos,
                padTokenId = gen.optLong("pad_token_id", config.optLong("pad_token_id", 65000L)),
                forcedEosTokenId = gen.optLong("forced_eos_token_id", eos),
                maxLength = gen.optInt("max_length", 512),
                numBeams = gen.optInt("num_beams", 6),
                vocabSize = config.optInt("vocab_size", 65001),
            )
        }
    }
}
