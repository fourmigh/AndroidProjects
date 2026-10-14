package org.caojun.shotocr.llm

/**
 * 本地大模型推理引擎抽象。把具体的 LiteRT-LM 实现隔离在此接口之后，
 * 便于在 JVM 单测中注入假引擎。
 */
interface LlmEngineApi {

    /** 加载模型。幂等；耗时较长（可能约 10s），需在后台线程调用。 */
    suspend fun initialize()

    /** 同步生成文本；系统提示与用户消息按次传入。需在后台线程调用。 */
    fun generate(systemInstruction: String, userMessage: String): String

    /** 释放模型资源。 */
    fun close()
}
