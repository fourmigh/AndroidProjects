package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.MemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptStoreTest {

    @Test
    fun default_usesBuiltInPromptPerPlatform() {
        val store = PromptStore(MemoryKeyValueStore())

        assertEquals(
            ReceiptExtractionPrompt.systemInstruction(Platform.GENERIC),
            store.systemPrompt(Platform.GENERIC)
        )
        assertFalse(store.isCustomized(Platform.GENERIC))
    }

    @Test
    fun set_overridesAndReset_restoresDefault() {
        val store = PromptStore(MemoryKeyValueStore())

        store.setSystemPrompt(Platform.WECHAT, "自定义提示词")
        assertEquals("自定义提示词", store.systemPrompt(Platform.WECHAT))
        assertTrue(store.isCustomized(Platform.WECHAT))

        store.reset(Platform.WECHAT)
        assertEquals(ReceiptExtractionPrompt.systemInstruction(Platform.WECHAT), store.systemPrompt(Platform.WECHAT))
        assertFalse(store.isCustomized(Platform.WECHAT))
    }
}
