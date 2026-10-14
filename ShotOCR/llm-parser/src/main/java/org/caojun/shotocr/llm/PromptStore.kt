package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.KeyValueStore

/** 按平台的系统提示词覆盖存储；未设置时回退到内置默认。 */
class PromptStore(private val store: KeyValueStore) {

    fun systemPrompt(platform: Platform): String =
        store.getString(key(platform))?.takeIf { it.isNotBlank() }
            ?: ReceiptExtractionPrompt.systemInstruction(platform)

    fun setSystemPrompt(platform: Platform, prompt: String) {
        if (prompt.isBlank()) store.remove(key(platform)) else store.putString(key(platform), prompt)
    }

    /** 恢复该平台的内置默认提示词。 */
    fun reset(platform: Platform) {
        store.remove(key(platform))
    }

    fun isCustomized(platform: Platform): Boolean = !store.getString(key(platform)).isNullOrBlank()

    private fun key(platform: Platform) = "system_prompt_${platform.id}"
}
