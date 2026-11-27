package org.caojun.cupfox.data

import android.content.Context
import org.caojun.cupfox.model.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/** 观看历史，JSON 存在 SharedPreferences 中，最多 [MAX_ENTRIES] 条。 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    if (url.isBlank()) continue
                    add(
                        HistoryEntry(
                            key = item.optString("key", url),
                            title = item.optString("title", "未命名"),
                            url = url,
                            updatedAt = item.optLong("at", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(entry: HistoryEntry) {
        val list = load().toMutableList()
        list.removeAll { it.key == entry.key }
        list.add(0, entry)
        save(list.take(MAX_ENTRIES))
    }

    fun remove(key: String) {
        save(load().filterNot { it.key == key })
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("key", entry.key)
                    put("title", entry.title)
                    put("url", entry.url)
                    put("at", entry.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "cupfox_history"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
    }
}
