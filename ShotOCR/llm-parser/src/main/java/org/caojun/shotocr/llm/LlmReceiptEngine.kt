package org.caojun.shotocr.llm

import android.content.Context

/** 进程级单例，避免重复加载大模型（加载耗时长且占内存）。 */
object LlmReceiptEngine {

    @Volatile
    private var instance: LiteRtLlmEngine? = null

    fun get(context: Context): LiteRtLlmEngine {
        return instance ?: synchronized(this) {
            instance ?: LiteRtLlmEngine(context = context.applicationContext)
                .also { instance = it }
        }
    }

    fun close() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
