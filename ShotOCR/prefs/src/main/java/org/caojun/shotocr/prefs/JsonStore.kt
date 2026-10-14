package org.caojun.shotocr.prefs

import org.json.JSONArray
import org.json.JSONObject

/**
 * 通用 JSON 列表持久化：把 [List] 以 JSON 数组字符串存进 [KeyValueStore]。
 * 与业务无关，序列化/反序列化由调用方提供。
 */
class JsonStore(private val store: KeyValueStore) {

    fun <T> getList(key: String, fromJson: (JSONObject) -> T): List<T> {
        val raw = store.getString(key) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let(fromJson)
            }
        }.getOrDefault(emptyList())
    }

    fun <T> putList(key: String, list: List<T>, toJson: (T) -> JSONObject) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        store.putString(key, array.toString())
    }
}
