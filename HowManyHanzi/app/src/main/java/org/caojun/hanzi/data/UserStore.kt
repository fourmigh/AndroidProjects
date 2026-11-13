package org.caojun.hanzi.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryRecord(
    val scopeName: String,
    val avgLevelText: String,
    val avgLevel: Double,
    val mastery: Int,
    val estimated: Int,
    val count: Int,
    val createdAt: Long,
    val dateText: String,
)

data class RecentRecord(
    val char: String,
    val level: Int,
    val levelName: String,
    val dateText: String,
    val createdAt: Long,
)

data class RareRecord(val char: String, val at: Long, val dateText: String)

/** 对应小程序 lib/storage.js，用 SharedPreferences 保存 JSON。 */
class UserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hmh", Context.MODE_PRIVATE)

    var scope: Int
        get() = prefs.getInt("scope", 4)
        set(value) = prefs.edit().putInt("scope", value).apply()

    var sampleSize: Int
        get() = prefs.getInt("sampleSize", 20)
        set(value) = prefs.edit().putInt("sampleSize", value).apply()

    var strokeDuration: Int
        get() = prefs.getInt("strokeDuration", 2000)
        set(value) = prefs.edit().putInt("strokeDuration", value).apply()

    var strokePause: Boolean
        get() = prefs.getBoolean("strokePause", true)
        set(value) = prefs.edit().putBoolean("strokePause", value).apply()

    var letterDuration: Int
        get() = prefs.getInt("letterDuration", 800)
        set(value) = prefs.edit().putInt("letterDuration", value).apply()

    var letterSize: Int
        get() = prefs.getInt("letterSize", 95)
        set(value) = prefs.edit().putInt("letterSize", value).apply()

    var letterGap: Int
        get() = prefs.getInt("letterGap", 8)
        set(value) = prefs.edit().putInt("letterGap", value).apply()

    fun getCharLevels(): Map<String, Int> {
        val raw = prefs.getString("charLevels", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = HashMap<String, Int>(obj.length() * 2)
            obj.keys().forEach { ch -> map[ch] = obj.optInt(ch, 0) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveCharLevel(ch: String, level: Int) {
        val map = HashMap(getCharLevels())
        val old = map[ch]
        if (old == null || level > old) {
            map[ch] = level
            writeCharLevels(map)
        }
    }

    private fun writeCharLevels(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (ch, lv) -> obj.put(ch, lv) }
        prefs.edit().putString("charLevels", obj.toString()).apply()
    }

    fun removeChar(ch: String) {
        val map = HashMap(getCharLevels())
        if (map.remove(ch) != null) writeCharLevels(map)
        val recent = getRecentChars().filter { it.char != ch }
        writeRecent(recent)
    }

    fun getHistory(): List<HistoryRecord> = readArray("history") { o ->
        HistoryRecord(
            scopeName = o.optString("scopeName"),
            avgLevelText = o.optString("avgLevelText"),
            avgLevel = o.optDouble("avgLevel", 0.0),
            mastery = o.optInt("mastery"),
            estimated = o.optInt("estimated"),
            count = o.optInt("count"),
            createdAt = o.optLong("createdAt"),
            dateText = o.optString("dateText"),
        )
    }

    fun addHistory(record: HistoryRecord) {
        val list = ArrayList(getHistory())
        list.add(0, record)
        val trimmed = list.take(50)
        val arr = JSONArray()
        trimmed.forEach { h ->
            arr.put(
                JSONObject()
                    .put("scopeName", h.scopeName)
                    .put("avgLevelText", h.avgLevelText)
                    .put("avgLevel", h.avgLevel)
                    .put("mastery", h.mastery)
                    .put("estimated", h.estimated)
                    .put("count", h.count)
                    .put("createdAt", h.createdAt)
                    .put("dateText", h.dateText)
            )
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun removeHistoryAt(index: Int) {
        val list = ArrayList(getHistory())
        if (index < 0 || index >= list.size) return
        list.removeAt(index)
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(
                JSONObject()
                    .put("scopeName", h.scopeName)
                    .put("avgLevelText", h.avgLevelText)
                    .put("avgLevel", h.avgLevel)
                    .put("mastery", h.mastery)
                    .put("estimated", h.estimated)
                    .put("count", h.count)
                    .put("createdAt", h.createdAt)
                    .put("dateText", h.dateText)
            )
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun getRecentChars(): List<RecentRecord> = readArray("recentChars") { o ->
        RecentRecord(
            char = o.optString("char"),
            level = o.optInt("level"),
            levelName = o.optString("levelName"),
            dateText = o.optString("dateText"),
            createdAt = o.optLong("createdAt"),
        )
    }

    private fun writeRecent(list: List<RecentRecord>) {
        val arr = JSONArray()
        list.take(300).forEach { r ->
            arr.put(
                JSONObject()
                    .put("char", r.char)
                    .put("level", r.level)
                    .put("levelName", r.levelName)
                    .put("dateText", r.dateText)
                    .put("createdAt", r.createdAt)
            )
        }
        prefs.edit().putString("recentChars", arr.toString()).apply()
    }

    fun addCharRecord(char: String, level: Int, levelName: String, dateText: String) {
        val list = ArrayList(getRecentChars())
        list.add(0, RecentRecord(char, level, levelName, dateText, System.currentTimeMillis()))
        writeRecent(list)
        saveCharLevel(char, level)
    }

    fun getRareChars(): List<RareRecord> = readArray("rareChars") { o ->
        RareRecord(
            char = o.optString("char"),
            at = o.optLong("at"),
            dateText = o.optString("dateText"),
        )
    }

    fun addRareChar(ch: String): Boolean {
        val list = ArrayList(getRareChars())
        if (list.any { it.char == ch }) return false
        list.add(0, RareRecord(ch, System.currentTimeMillis(), todayText()))
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("char", r.char).put("at", r.at).put("dateText", r.dateText))
        }
        prefs.edit().putString("rareChars", arr.toString()).apply()
        return true
    }

    fun clearAll() {
        prefs.edit()
            .remove("history")
            .remove("recentChars")
            .remove("charLevels")
            .apply()
    }

    private fun <T> readArray(key: String, mapper: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<T>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(mapper(it)) }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

        fun todayText(): String = dayFormat.format(Date())
        fun nowText(): String = timeFormat.format(Date())
    }
}
