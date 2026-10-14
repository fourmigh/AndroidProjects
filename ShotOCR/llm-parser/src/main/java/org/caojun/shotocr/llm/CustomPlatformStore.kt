package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.JsonStore
import org.caojun.shotocr.prefs.KeyValueStore
import org.json.JSONArray
import org.json.JSONObject

/** 用户自定义平台：名称 + 识别关键词 + 提示词。 */
data class CustomPlatform(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val prompt: String
)

/** 自定义平台的持久化存储。 */
class CustomPlatformStore(store: KeyValueStore) {

    private val json = JsonStore(store)

    fun list(): List<CustomPlatform> = json.getList(KEY) { obj ->
        val keywordsArray = obj.optJSONArray("keywords")
        val keywords = if (keywordsArray != null) {
            (0 until keywordsArray.length()).mapNotNull { keywordsArray.optString(it).ifBlank { null } }
        } else {
            emptyList()
        }
        CustomPlatform(
            id = obj.optString("id", ""),
            name = obj.optString("name", ""),
            keywords = keywords,
            prompt = obj.optString("prompt", "")
        )
    }

    fun add(name: String, keywords: List<String>, prompt: String): CustomPlatform {
        val entry = CustomPlatform(
            id = "custom_" + System.currentTimeMillis(),
            name = name,
            keywords = keywords,
            prompt = prompt
        )
        val current = list().toMutableList()
        current.add(entry)
        save(current)
        return entry
    }

    fun update(platform: CustomPlatform) {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == platform.id }
        if (index >= 0) {
            current[index] = platform
            save(current)
        }
    }

    fun remove(id: String) {
        val current = list().toMutableList()
        current.removeAll { it.id == id }
        save(current)
    }

    fun clear() = save(emptyList())

    private fun save(list: List<CustomPlatform>) {
        json.putList(KEY, list) { platform ->
            JSONObject().apply {
                put("id", platform.id)
                put("name", platform.name)
                put("prompt", platform.prompt)
                put("keywords", JSONArray().apply { platform.keywords.forEach { put(it) } })
            }
        }
    }

    companion object {
        private const val KEY = "custom_platforms"
    }
}
