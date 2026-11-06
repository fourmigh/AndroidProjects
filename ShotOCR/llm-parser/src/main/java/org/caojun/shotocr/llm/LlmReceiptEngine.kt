package org.caojun.shotocr.llm

import android.content.Context
import org.caojun.ai.llm.api.LlmClient
import org.caojun.ai.llm.api.LlmEngineApi

/**
 * 业务侧使用的 LLM 引擎入口。
 *
 * 真正的推理在独立的「本地大模型服务」App 里，这里保持进程级单例以避免重复建立连接。
 */
object LlmReceiptEngine {

    @Volatile
    private var instance: LlmClient? = null

    fun get(context: Context): LlmEngineApi {
        return instance ?: synchronized(this) {
            instance ?: LlmClient(context.applicationContext).also { instance = it }
        }
    }

    fun close() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
