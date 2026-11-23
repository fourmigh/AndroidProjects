package org.caojun.ai.translate.hub.llama

import android.util.Log
import org.caojun.ai.translate.hub.core.CatalogModel
import org.caojun.ai.translate.hub.core.HubTranslateEngine
import org.caojun.ai.translate.hub.core.PromptBuilder
import java.io.File

/**
 * 基于 llama.cpp + GGUF 的翻译引擎。
 *
 * 同一时刻只加载一个模型；切换模型时会先卸载旧的。
 */
class LlamaTranslateEngine : HubTranslateEngine {

    override var loadedModelId: String? = null
        private set

    override var lastError: String? = null
        private set

    private var loadedArch: String = ""

    override val engineVersion: Int
        get() = if (LlamaBridge.isAvailable) runCatching { LlamaBridge.nativeEngineVersion() }.getOrDefault(1) else 0

    private fun threadCount(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

    override fun ensureLoaded(model: CatalogModel, modelFile: File): Boolean {
        if (!LlamaBridge.isAvailable) {
            lastError = "native library libhubllama.so is not available (only arm64-v8a)"
            Log.e(TAG, lastError!!)
            return false
        }
        if (loadedModelId == model.id) return true

        unload()
        lastError = null

        if (!modelFile.exists()) {
            lastError = "model file missing: ${modelFile.absolutePath}"
            Log.e(TAG, lastError!!)
            return false
        }

        // nativeLoad 返回 null 表示成功，否则是失败原因（含 llama.cpp 日志）
        val error = runCatching {
            LlamaBridge.nativeLoad(modelFile.absolutePath, CONTEXT_SIZE, threadCount())
        }.getOrElse { t ->
            t.message ?: t.javaClass.simpleName
        }

        if (error != null) {
            lastError = error
            Log.e(TAG, "load failed: $error")
            return false
        }

        val arch = runCatching { LlamaBridge.nativeArchitecture() }.getOrDefault("")
        val allowed = model.engineReq.llamaArch
        if (allowed.isNotEmpty() && arch.isNotBlank() && arch !in allowed) {
            // 架构白名单会随模型迭代过期；能不能跑交给 llama.cpp 决定，这里只留痕。
            Log.w(TAG, "architecture not in allow-list: model=$arch allowed=$allowed (continuing)")
        }

        loadedArch = arch
        loadedModelId = model.id

        // warmup：首次 decode 要分配计算图与内存池（2–4 秒），提前做掉，
        // 避免用户第一次点「翻译」时等待过久。
        val warmupStart = System.currentTimeMillis()
        runCatching {
            LlamaBridge.nativeTranslate(
                prompt = "Hello",
                maxTokens = 1,
                temp = 0f,
                topK = 1,
                topP = 1f,
                repPenalty = 1f,
                stopCsv = "",
            )
        }
        Log.i(
            TAG,
            "loaded ${model.id} arch=$arch threads=${threadCount()} " +
                "warmup=${System.currentTimeMillis() - warmupStart}ms",
        )
        return true
    }

    override fun translate(model: CatalogModel, text: String, from: String, to: String): String {
        if (loadedModelId != model.id) {
            throw IllegalStateException("model ${model.id} is not loaded")
        }

        val selfContained = PromptBuilder.buildSelfContained(model, text, from, to)
        val prompts = buildList {
            if (selfContained != null) {
                add("pfml3" to selfContained)
                // 专有格式失败时，用最简单的指令再试一次
                add("plain" to PromptBuilder.buildPlainBootstrap(text, to))
            } else {
                val userContent = PromptBuilder.build(model, text, from, to)
                val chat = runCatching {
                    LlamaBridge.nativeApplyChat(userContent, true)
                }.getOrNull()?.toString(Charsets.UTF_8).orEmpty()
                add("chat" to chat.ifBlank { PromptBuilder.chatMl(userContent) })
            }
        }

        val sampling = model.sampling
        val maxTokens = estimateMaxTokens(text)
        var lastDiagnosis = ""

        for ((kind, prompt) in prompts) {
            val tokenInfo = inspectPrompt(prompt)

            val raw = runCatching {
                LlamaBridge.nativeTranslate(
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temp = sampling.temp,
                    topK = sampling.topK,
                    topP = sampling.topP,
                    repPenalty = sampling.repPenalty,
                    stopCsv = model.stopTokens.joinToString("\n"),
                ).toString(Charsets.UTF_8)
            }.getOrElse {
                Log.e(TAG, "translate failed", it)
                throw it
            }

            val nativeError = runCatching { LlamaBridge.nativeLastError() }.getOrDefault("")
            val processed = postprocess(model, text, raw)

            if (processed.isNotBlank()) {
                val stats = runCatching { LlamaBridge.nativeLastStats() }.getOrDefault("")
                Log.i(TAG, "ok via '$kind' ($stats)")
                if (kind != prompts.first().first) {
                    Log.w(TAG, "fallback prompt '$kind' produced the result")
                }
                return processed
            }

            lastDiagnosis += "$kind: $tokenInfo native=${nativeError.ifBlank { "-" }} rawLen=${raw.length}; "
            Log.w(TAG, "empty result -> $kind: $tokenInfo native=$nativeError rawLen=${raw.length}")
        }

        throw IllegalStateException("模型没有给出有效结果（$lastDiagnosis）")
    }

    /** 调试：分词并还原，判断分词器是否把 prompt 切坏了。 */
    private fun inspectPrompt(prompt: String): String {
        val tokens = runCatching { LlamaBridge.nativeTokenize(prompt, true) }.getOrDefault(IntArray(0))
        if (tokens.isEmpty()) return "tokens=0"
        val back = runCatching {
            LlamaBridge.nativeDetokenize(tokens).toString(Charsets.UTF_8)
        }.getOrDefault("")
        val same = back.trim() == prompt.trim()
        if (!same) {
            Log.w(TAG, "round-trip mismatch. in=${prompt.take(120)} out=${back.take(120)}")
        }
        return "tokens=${tokens.size} roundTrip=$same first=${tokens.first()}"
    }

    override fun unload() {
        if (!LlamaBridge.isAvailable) return
        runCatching { LlamaBridge.nativeUnload() }
        loadedModelId = null
        loadedArch = ""
    }
    // ---------------------------------------------------------------- 后处理

    private fun estimateMaxTokens(text: String): Int =
        (text.length * 2 + 16).coerceIn(32, 192)

    private fun postprocess(model: CatalogModel, source: String, raw: String): String {
        var t = raw.trim()

        // 去掉模型可能回带的特殊标记（<|plamo:...|>、<|im_end|> 等）
        t = t.replace(Regex("<\\|[A-Za-z0-9_:.\\-]+\\|>"), "").trim()

        for (op in model.postprocess) {
            t = when (op) {
                "stripFence" -> stripFence(t)
                "stripQuotes" -> stripQuotes(t)
                "stripPrefix" -> stripPrefix(t)
                else -> t
            }
        }

        // 模型把原文照抄回来时视为失败，交给上层回退
        if (t.equals(source.trim(), ignoreCase = true)) return ""
        return t.trim()
    }

    private fun stripFence(text: String): String {
        var t = text
        if (t.startsWith("```")) {
            t = t.removePrefix("```")
            t = t.substringAfter('\n', t)
        }
        if (t.endsWith("```")) t = t.dropLast(3)
        return t.trim()
    }

    private fun stripQuotes(text: String): String {
        val pairs = listOf("\"" to "\"", "“" to "”", "‘" to "’", "「" to "」", "『" to "』")
        for ((open, close) in pairs) {
            if (text.length >= 2 && text.startsWith(open) && text.endsWith(close)) {
                return text.substring(1, text.length - 1).trim()
            }
        }
        return text
    }

    private fun stripPrefix(text: String): String {
        val prefixes = listOf(
            "Translation:", "翻译：", "译文：", "翻译结果：", "中文：", "English:",
        )
        for (p in prefixes) {
            if (text.startsWith(p, ignoreCase = true)) {
                return text.substring(p.length).trim()
            }
        }
        return text
    }

    private companion object {
        const val TAG = "HubLlamaEngine"
        const val CONTEXT_SIZE = 4096
    }
}
