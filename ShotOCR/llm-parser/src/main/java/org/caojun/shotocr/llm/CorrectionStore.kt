package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.JsonStore
import org.caojun.shotocr.prefs.KeyValueStore
import org.json.JSONObject

/**
 * 一条用户纠正示例：一段 OCR 文本片段 + 被修改字段的正确值 JSON（如 {"storeName":"..."}）。
 * 控制长度以适配模型较小的上下文（1280 tokens）。
 */
data class Correction(
    val rawTextSnippet: String,
    val changesJson: String,
    /** 所属平台 id（内置平台 id / 自定义平台 id / "generic"）。 */
    val platformId: String = "generic"
)

/** 用户纠正示例的持久化存储（上限 [MAX_COUNT] 条、片段上限 [MAX_SNIPPET] 字）。 */
class CorrectionStore(store: KeyValueStore) {

    private val json = JsonStore(store)

    fun list(): List<Correction> = json.getList(KEY) { obj ->
        Correction(
            rawTextSnippet = obj.optString("rawTextSnippet", ""),
            changesJson = obj.optString("changesJson", ""),
            platformId = obj.optString("platformId", "generic").ifBlank { "generic" }
        )
    }

    fun add(correction: Correction) {
        val entry = Correction(
            rawTextSnippet = correction.rawTextSnippet.take(MAX_SNIPPET),
            changesJson = correction.changesJson,
            platformId = correction.platformId.ifBlank { "generic" }
        )
        if (entry.changesJson.isBlank() || entry.changesJson == "{}") return
        val current = list().toMutableList()
        if (current.any {
                it.rawTextSnippet == entry.rawTextSnippet &&
                    it.changesJson == entry.changesJson &&
                    it.platformId == entry.platformId
            }
        ) {
            return
        }
        current.add(entry)
        while (current.size > MAX_COUNT) current.removeAt(0)
        save(current)
    }

    fun removeAt(index: Int) {
        val current = list().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            save(current)
        }
    }

    fun clear() = save(emptyList())

    private fun save(list: List<Correction>) {
        json.putList(KEY, list) { correction ->
            JSONObject().apply {
                put("rawTextSnippet", correction.rawTextSnippet)
                put("changesJson", correction.changesJson)
                put("platformId", correction.platformId)
            }
        }
    }

    companion object {
        private const val KEY = "corrections"
        const val MAX_COUNT = 5
        const val MAX_SNIPPET = 120
        /** 提示词中最多带入的纠正条数。 */
        const val MAX_IN_PROMPT = 2
    }
}
