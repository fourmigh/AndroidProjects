package org.caojun.shotocr.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 基于 Google LiteRT-LM 的本地文本大模型引擎。
 *
 * 模型文件需放在 assets 下（见 [DEFAULT_ASSET]）。首次初始化时复制到 filesDir，
 * 因为 LiteRT 需要真实文件路径；cacheDir 用于加速二次加载。
 */
class LiteRtLlmEngine(
    context: Context,
    private val assetName: String = DEFAULT_ASSET
) : LlmEngineApi {

    companion object {
        private const val TAG = "ShotOCR/Llm"
        const val DEFAULT_ASSET = "llm/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
        const val MODEL_FILE_NAME = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    }

    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var engine: Engine? = null

    override suspend fun initialize() {
        if (engine != null) return
        val modelFile = ensureModelFile()
        synchronized(lock) {
            if (engine != null) return
            runCatching { Engine.setNativeMinLogSeverity(LogSeverity.ERROR) }
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath
            )
            val created = Engine(config)
            created.initialize()
            engine = created
            Log.d(TAG, "LiteRT-LM initialized: ${modelFile.absolutePath}")
        }
    }

    override fun generate(systemInstruction: String, userMessage: String): String {
        val current = engine ?: throw IllegalStateException("LLM engine not initialized")
        current.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                maxOutputToken = 256
            )
        ).use { conversation ->
            val message = conversation.sendMessage(userMessage)
            val text = message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            return text.ifEmpty { message.toString() }
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { engine?.close() }
            engine = null
        }
    }

    private suspend fun ensureModelFile(): File = withContext(Dispatchers.IO) {
        val target = File(appContext.filesDir, MODEL_FILE_NAME)
        if (!target.exists() || target.length() == 0L) {
            if (!assetExists(assetName)) {
                throw LlmModelMissingException(assetName)
            }
            appContext.assets.open(assetName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target
    }

    private fun assetExists(path: String): Boolean {
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        return runCatching {
            appContext.assets.list(dir)?.contains(name) == true
        }.getOrDefault(false)
    }
}
