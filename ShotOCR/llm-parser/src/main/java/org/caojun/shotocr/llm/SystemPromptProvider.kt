package org.caojun.shotocr.llm

import org.json.JSONObject

/**
 * 组装最终系统提示词：
 * 1) 先检测平台（前台应用 → 文本签名 → 自定义平台关键词 → 通用）；
 * 2) 取该平台/自定义的提示词（用户覆盖优先）；
 * 3) 追加该平台所属的用户纠正规则（按平台生效）。
 */
class SystemPromptProvider(
    private val promptStore: PromptStore,
    private val customStore: CustomPlatformStore,
    private val correctionStore: CorrectionStore
) {

    data class Selection(val prompt: String, val key: String)

    fun build(foregroundLabel: String?, ocrText: String): String =
        appendCorrections(resolveSelection(foregroundLabel, ocrText))

    /** 解析出基础提示词（含平台/自定义选择，不含纠正块）。 */
    fun resolveBase(foregroundLabel: String?, ocrText: String): String =
        resolveSelection(foregroundLabel, ocrText).prompt

    fun resolveSelection(foregroundLabel: String?, ocrText: String): Selection {
        PlatformDetector.detect(foregroundLabel, ocrText)?.let { platform ->
            return Selection(promptStore.systemPrompt(platform), platform.id)
        }
        customStore.list()
            .firstOrNull { custom ->
                custom.prompt.isNotBlank() &&
                    custom.keywords.any { it.isNotBlank() && ocrText.contains(it) }
            }
            ?.let { return Selection(it.prompt, it.id) }
        return Selection(promptStore.systemPrompt(Platform.GENERIC), Platform.GENERIC.id)
    }

    private fun appendCorrections(selection: Selection): String {
        val corrections = correctionStore.list()
            .filter {
                it.platformId == selection.key &&
                    it.changesJson.isNotBlank() &&
                    it.changesJson != "{}"
            }
            .takeLast(CorrectionStore.MAX_IN_PROMPT)
        if (corrections.isEmpty()) return selection.prompt

        val sb = StringBuilder(selection.prompt)
        sb.append("\n纠正规则（优先遵循）：")
        corrections.forEach { correction ->
            val fields = runCatching { JSONObject(correction.changesJson) }.getOrNull() ?: return@forEach
            val keys = fields.keys().asSequence().toList()
            if (keys.isEmpty()) return@forEach
            val mapping = keys.joinToString("，") { key -> "$key=\"${fields.optString(key)}\"" }
            sb.append("\n- ").append(mapping)
        }
        return sb.toString()
    }
}
