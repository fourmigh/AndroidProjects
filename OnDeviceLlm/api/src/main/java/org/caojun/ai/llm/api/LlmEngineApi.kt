package org.caojun.ai.llm.api

/**
 * 本地大模型推理引擎抽象。
 *
 * provider 侧的 LiteRtLlmEngine 和客户端侧的 LlmClient 都实现这个接口，
 * 业务代码（如账单解析）可以直接依赖它，不感知是否跨进程。
 */
interface LlmEngineApi {

    /** 加载模型。幂等；耗时较长，需在后台线程调用。 */
    suspend fun initialize()

    /** 同步生成文本；需在后台线程调用。 */
    fun generate(systemInstruction: String, userMessage: String): String

    /** 释放模型资源或断开连接。 */
    fun close()
}
