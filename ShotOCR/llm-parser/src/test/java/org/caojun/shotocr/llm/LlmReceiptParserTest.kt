package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.MemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmReceiptParserTest {

    private class FakeEngine(private val response: String) : LlmEngineApi {
        var initialized = false
        var lastSystem: String? = null
        var lastUser: String? = null

        override suspend fun initialize() {
            initialized = true
        }

        override fun generate(systemInstruction: String, userMessage: String): String {
            lastSystem = systemInstruction
            lastUser = userMessage
            return response
        }

        override fun close() = Unit
    }

    private fun provider(): SystemPromptProvider = SystemPromptProvider(
        PromptStore(MemoryKeyValueStore()),
        CustomPlatformStore(MemoryKeyValueStore()),
        CorrectionStore(MemoryKeyValueStore())
    )

    @Test
    fun parse_initializesEngineAndMapsJson() {
        val engine = FakeEngine(
            "{\"amount\":126.37,\"discount\":28.63,\"originalAmount\":155.0,\"storeName\":\"郑远元旗舰店\",\"sourceApp\":\"京东\"}"
        )
        val parser = LlmReceiptParser(engine, provider())

        val data = parser.parse("ocr text")

        assertTrue(engine.initialized)
        assertEquals(126.37, data.amount!!, 0.001)
        assertEquals(28.63, data.discount!!, 0.001)
        assertEquals(155.0, data.originalAmount!!, 0.001)
        assertEquals("郑远元旗舰店", data.storeName)
        assertEquals("京东", data.sourceApp)
        assertEquals("ocr text", data.rawText)
    }

    @Test
    fun parse_promptContainsOcrText() {
        val engine = FakeEngine("{}")
        LlmReceiptParser(engine, provider()).parse("库迪咖啡（北京）有限公司")

        assertTrue(engine.lastUser!!.contains("库迪咖啡（北京）有限公司"))
    }

    @Test
    fun parse_sourceHint_selectsPlatformPrompt() {
        val engine = FakeEngine("{}")
        LlmReceiptParser(engine, provider()).parse("任意文本", "微信")

        assertTrue(engine.lastSystem!!.contains("微信"))
    }

    @Test
    fun parse_overridesOrderNumberFromOcr() {
        val engine = FakeEngine("{\"orderNumber\":\"POCC202610051629340913218207\"}")
        val data = LlmReceiptParser(engine, provider())
            .parse("支付方式\n4500000453202610059952337463\n交易单号\n商户单号\nPOCC202610051629340913218207")

        assertEquals("4500000453202610059952337463", data.orderNumber)
    }

    @Test
    fun parse_correctsExampleLeakAndPaymentTime() {
        val response = """
            {"amount":9.77,"discount":1.43,"originalAmount":11.2,"storeName":"肯德基",
             "paymentTime":null,"paymentMethod":"网商银行储蓄卡(3841)",
             "orderNumber":"2026100823001465011447014045","sourceApp":"支付宝"}
        """.trimIndent()
        val ocr = """
            13:00:59144H支天福
            日为90
            账单详情
            胡记锅贴
            -6-97
            交易成功
            订单金额
            7.00
            支付遇财神√-0.03
            支付时间
            2026-10-2007:07:57
            付款方式
            花呗>
            订单号
            2026102023001465011448654929
            商家订单号
            17924512735002374665014
        """.trimIndent()

        val data = LlmReceiptParser(FakeEngine(response), provider()).parse(ocr)

        assertEquals(6.97, data.amount!!, 0.001)
        assertEquals(0.03, data.discount!!, 0.001)
        assertEquals(7.0, data.originalAmount!!, 0.001)
        assertEquals("2026-10-20 07:07:57", data.paymentTime)
        assertEquals("2026102023001465011448654929", data.orderNumber)
    }
}
