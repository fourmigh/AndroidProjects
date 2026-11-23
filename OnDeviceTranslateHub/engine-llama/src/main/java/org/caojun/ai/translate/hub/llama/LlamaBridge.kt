package org.caojun.ai.translate.hub.llama

import android.util.Log

/**
 * llama.cpp 的 JNI 桥。
 *
 * 只做最小封装：加载/卸载模型、读元数据、应用 chat template、单轮生成。
 * 所有方法由 Kotlin 侧的 Mutex 保证串行。
 */
object LlamaBridge {

    private const val TAG = "HubLlamaBridge"

    /** 库是否可用（模拟器/x86 设备上不可用）。 */
    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("hubllama")
            true
        }.onFailure { Log.e(TAG, "loadLibrary failed", it) }.getOrDefault(false)
    }

    @JvmStatic
    external fun nativeEngineVersion(): Int

    @JvmStatic
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): String?

    @JvmStatic
    external fun nativeUnload()

    /** 模型架构，如 `hunyuan-dense` / `qwen35` / `llama`。 */
    @JvmStatic
    external fun nativeArchitecture(): String

    /** 模型内置 chat template，读不到时返回空串。 */
    @JvmStatic
    external fun nativeChatTemplate(): String

    /** 用内置模板包装单条 user 消息；返回空数组表示模板不可用。 */
    @JvmStatic
    external fun nativeApplyChat(userContent: String, addAssistant: Boolean): ByteArray

    /** 同步生成；返回 UTF-8 字节（token 边界可能产生非法序列，交由 Kotlin 解码）。 */
    @JvmStatic
    external fun nativeTranslate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topK: Int,
        topP: Float,
        repPenalty: Float,
        stopCsv: String,
    ): ByteArray

    /** 上一次推理失败的原因（native 侧记录）。 */
    @JvmStatic
    external fun nativeLastError(): String

    /** 上一次推理的性能统计：prefill/decode 毫秒、生成步数。 */
    @JvmStatic
    external fun nativeLastStats(): String

    /** 调试用：分词。 */
    @JvmStatic
    external fun nativeTokenize(text: String, addSpecial: Boolean): IntArray

    /** 调试用：把 token 还原成 UTF-8 字节。 */
    @JvmStatic
    external fun nativeDetokenize(tokens: IntArray): ByteArray
}
