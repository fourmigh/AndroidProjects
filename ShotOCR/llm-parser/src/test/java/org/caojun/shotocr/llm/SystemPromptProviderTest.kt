package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.MemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptProviderTest {

    private fun provider(
        promptStore: PromptStore = PromptStore(MemoryKeyValueStore()),
        customStore: CustomPlatformStore = CustomPlatformStore(MemoryKeyValueStore()),
        correctionStore: CorrectionStore = CorrectionStore(MemoryKeyValueStore())
    ) = SystemPromptProvider(promptStore, customStore, correctionStore)

    @Test
    fun unknownText_usesGenericPrompt() {
        val result = provider().build(null, "无平台特征的文本")

        assertEquals(ReceiptExtractionPrompt.systemInstruction(Platform.GENERIC), result)
    }

    @Test
    fun foregroundLabel_selectsPlatformPrompt() {
        val result = provider().build("微信", "任意文本")

        assertTrue(result.contains("交易单号"))
    }

    @Test
    fun textSignature_selectsPlatformPrompt() {
        val result = provider().build(null, "云闪付交易详情\n某商户\n-￥2.50")

        assertTrue(result.contains("云闪付"))
    }

    @Test
    fun customPlatform_selectedByKeyword() {
        val customStore = CustomPlatformStore(MemoryKeyValueStore())
        customStore.add("某平台", listOf("某平台特色"), "CUSTOM_PROMPT")
        val result = provider(customStore = customStore).build(null, "某平台特色 账单")

        assertEquals("CUSTOM_PROMPT", result)
    }

    @Test
    fun withCorrections_appendsExamplesBlock() {
        val correctionStore = CorrectionStore(MemoryKeyValueStore())
        correctionStore.add(Correction("阿宝烧烤&红烧牛肉面", "{\"storeName\":\"阿宝烧烤&红烧牛肉面\"}"))

        val result = provider(correctionStore = correctionStore).build(null, "无平台特征")

        assertTrue(result.contains("纠正规则"))
        assertTrue(result.contains("阿宝烧烤&红烧牛肉面"))
    }

    @Test
    fun corrections_areScopedByPlatform() {
        val correctionStore = CorrectionStore(MemoryKeyValueStore())
        correctionStore.add(Correction("微信片段", "{\"orderNumber\":\"X\"}", "wechat"))
        val provider = provider(correctionStore = correctionStore)

        val correctionRule = "orderNumber=\"X\""
        assertTrue(provider.build("微信", "任意文本").contains(correctionRule))
        assertFalse(provider.build("支付宝", "任意文本").contains(correctionRule))
    }
}
