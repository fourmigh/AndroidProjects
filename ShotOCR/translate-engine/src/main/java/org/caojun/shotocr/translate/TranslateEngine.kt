package org.caojun.shotocr.translate

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 离线翻译引擎入口（中英双向，基于 ONNX OPUS-MT）。
 *
 * 模型随 APK 打包在 `assets/translate/{zh-en,en-zh}/`，通过 mmap 零拷贝加载，
 * 不会把 235MB 的模型读进 Java 堆。
 *
 * 每个方向一个 [MarianTranslator]，切换方向时释放上一个（模型较大，不做多方向常驻）。
 * 首次翻译需加载模型，耗时较长，务必在后台线程调用（本类已内部切到 IO 调度器）。
 */
object TranslateEngine {

    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    private const val ASSET_ROOT = "translate"
    private const val TAG = "TranslateEngine"

    @Volatile
    private var appContext: Context? = null

    private val mutex = Mutex()

    private var cached: Loaded? = null

    private class Loaded(val direction: String, val translator: MarianTranslator)

    /** 可在任意位置重复调用；只需在首次翻译前调用一次即可。 */
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun isSupported(from: String, to: String): Boolean = directionOf(from, to) != null

    /**
     * 将 [text] 从 [from] 翻译到 [to]（[LANG_ZH] / [LANG_EN]）。
     *
     * 失败（模型缺失、加载失败等）会抛出异常，调用方需自行捕获并提示。
     */
    suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return ""
        val direction = directionOf(from, to)
            ?: throw IllegalArgumentException("unsupported direction: $from -> $to")

        // 先查本地词典：命中则无需加载模型（更快、更准）
        TranslationDictionary.lookup(text, from)?.let { return it }

        return withContext(Dispatchers.IO) {
            val translator = mutex.withLock { acquire(direction) }
            translator.translate(text)
        }
    }

    /** 释放已缓存的模型。 */
    suspend fun release() = mutex.withLock {
        cached?.translator?.close()
        cached = null
    }

    private fun directionOf(from: String, to: String): String? = when {
        from == LANG_ZH && to == LANG_EN -> "zh-en"
        from == LANG_EN && to == LANG_ZH -> "en-zh"
        else -> null
    }

    private fun acquire(direction: String): MarianTranslator {
        cached?.let { if (it.direction == direction) return it.translator }

        cached?.translator?.close()
        cached = null

        val context = appContext
            ?: error("TranslateEngine.initialize(context) has not been called")

        val translator = load(context, direction)
        cached = Loaded(direction, translator)
        return translator
    }

    private fun load(context: Context, direction: String): MarianTranslator {
        val dir = "$ASSET_ROOT/$direction"
        val t0 = System.nanoTime()

        val tokenizer = TranslateTokenizer.fromJson(readAssetText(context, "$dir/tokenizer.json"))
        val configJson = readAssetText(context, "$dir/config.json")
        val genJson = readAssetText(context, "$dir/generation_config.json")
        val t1 = System.nanoTime()

        val env = OrtEnvironment.getEnvironment()
        val buffers = listOf(
            mapAsset(context, "$dir/onnx/encoder_model_int8.onnx"),
            mapAsset(context, "$dir/onnx/decoder_model_merged_int8.onnx"),
        )
        val t2 = System.nanoTime()

        val options = OrtSession.SessionOptions()
        val encoderSession = try {
            env.createSession(buffers[0], options)
        } catch (t: Throwable) {
            options.close()
            tokenizer.close()
            throw TranslateException("failed to load encoder for $direction", t)
        }
        val t3 = System.nanoTime()
        val decoderSession = try {
            env.createSession(buffers[1], options)
        } catch (t: Throwable) {
            encoderSession.close()
            options.close()
            tokenizer.close()
            throw TranslateException("failed to load decoder for $direction", t)
        }
        val t4 = System.nanoTime()

        Log.d(
            TAG,
            "load[$direction] tokenizer=${ms(t0, t1)} mmap=${ms(t1, t2)} " +
                "encoderSession=${ms(t2, t3)} decoderSession=${ms(t3, t4)} total=${ms(t0, t4)}"
        )

        return try {
            MarianTranslator(env, encoderSession, decoderSession, tokenizer, buffers, configJson, genJson)
        } catch (t: Throwable) {
            encoderSession.close()
            decoderSession.close()
            tokenizer.close()
            throw TranslateException("failed to init translator for $direction", t)
        }
        // 注意：options 由 session 持有，不能提前 close。
    }

    private fun readAssetText(context: Context, path: String): String =
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun ms(from: Long, to: Long): String = "${(to - from) / 1_000_000}ms"

    /**
     * 以只读方式 mmap assets 中的文件（要求该扩展名已配置为 noCompress，否则 openFd 会抛异常）。
     */
    private fun mapAsset(context: Context, path: String): MappedByteBuffer {
        val afd = context.assets.openFd(path)
        afd.createInputStream().use { stream ->
            return stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength,
            )
        }
    }
}

/** 翻译过程中的异常（模型缺失、加载失败、推理失败等）。 */
class TranslateException(message: String, cause: Throwable? = null) : Exception(message, cause)
