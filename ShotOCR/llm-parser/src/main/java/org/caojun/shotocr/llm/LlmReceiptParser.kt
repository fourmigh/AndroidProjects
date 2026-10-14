package org.caojun.shotocr.llm

import kotlinx.coroutines.runBlocking

/**
 * 用本地大模型把 OCR 文本解析为 [ReceiptData]。
 *
 * 注意：[parse] 为阻塞调用（含模型懒加载与本地推理），请在后台线程执行。
 * 模型上下文较小（1280 tokens），这里会对输入做字符预算截断，并在超长时降级重试。
 */
class LlmReceiptParser(
    private val engine: LlmEngineApi,
    private val promptProvider: SystemPromptProvider,
    private val maxInputChars: Int = MAX_INPUT_CHARS
) {

    fun parse(text: String, sourceHint: String? = null): ReceiptData {
        runBlocking { engine.initialize() }
        val systemInstruction = promptProvider.build(sourceHint, text)
        val output = generateSafely(systemInstruction, text)
        val data = ReceiptJsonMapper.map(output, text)
        // 订单号确定性兜底：按标签就近取支付订单号（排除商户单号）。
        val resolved = OrderNumberResolver.resolve(text)
        return if (resolved != null) data.copy(orderNumber = resolved) else data
    }

    private fun buildUserMessage(systemInstruction: String, text: String, budget: Int): String {
        val available = (budget - systemInstruction.length - ReceiptExtractionPrompt.USER_PREFIX.length)
            .coerceAtLeast(MIN_OCR_CHARS)
        val trimmed = if (text.length <= available) text else text.take(available)
        return ReceiptExtractionPrompt.userMessage(trimmed)
    }

    private fun generateSafely(systemInstruction: String, text: String): String {
        return try {
            engine.generate(systemInstruction, buildUserMessage(systemInstruction, text, maxInputChars))
        } catch (e: Exception) {
            if (isTooLong(e)) {
                // 降级：进一步压缩输入再试一次
                engine.generate(systemInstruction, buildUserMessage(systemInstruction, text, AGGRESSIVE_INPUT_CHARS))
            } else {
                throw e
            }
        }
    }

    private fun isTooLong(e: Exception): Boolean {
        val message = (e.message ?: "").lowercase()
        return message.contains("too long") ||
            message.contains("maximum number of tokens") ||
            message.contains("invalid_argument")
    }

    companion object {
        /** 系统提示 + 用户文本的字符预算（近似 token 上限 1280，中文约 0.6~0.7 token/字）。 */
        const val MAX_INPUT_CHARS = 1400
        private const val AGGRESSIVE_INPUT_CHARS = 700
        private const val MIN_OCR_CHARS = 120
    }
}
