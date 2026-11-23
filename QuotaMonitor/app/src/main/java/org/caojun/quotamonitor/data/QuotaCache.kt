package org.caojun.quotamonitor.data

import android.content.Context
import org.caojun.quotamonitor.model.Platform
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.Source
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences 缓存：打开 App 秒开展示上次抓到的额度快照。
 */
object QuotaCache {

    private const val PREFS = "quota_cache_v3"

    fun save(context: Context, platform: Platform, snapshot: QuotaSnapshot) {
        val json = JSONObject().apply {
            put("planName", snapshot.planName ?: JSONObject.NULL)
            put("remainText", snapshot.remainText ?: JSONObject.NULL)
            put("endText", snapshot.endText ?: JSONObject.NULL)
            put("source", snapshot.source.name)
            put("capturedAt", snapshot.capturedAt)
            put(
                "items",
                JSONArray().apply {
                    snapshot.items.forEach { item ->
                        put(
                            JSONObject().apply {
                                put("label", item.label)
                                put("used", item.used ?: JSONObject.NULL)
                                put("total", item.total ?: JSONObject.NULL)
                                put("percent", item.percent ?: JSONObject.NULL)
                                put("percentText", item.percentText ?: JSONObject.NULL)
                                put("resetHint", item.resetHint ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
            put("lines", JSONArray(snapshot.lines))
        }
        prefs(context).edit().putString(platform.name, json.toString()).apply()
    }

    fun load(context: Context, platform: Platform): QuotaSnapshot? {
        val raw = prefs(context).getString(platform.name, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val items = buildList {
                val arr = json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val label = o.optString("label").takeIf { it.isNotBlank() && it != "null" }
                        ?: continue
                    add(
                        QuotaItem(
                            label = label,
                            used = o.optNullableDouble("used"),
                            total = o.optNullableDouble("total"),
                            percent = o.optNullableDouble("percent"),
                            percentText = o.optString("percentText").takeIf {
                                it.isNotBlank() && it != "null"
                            },
                            resetHint = o.optString("resetHint").takeIf {
                                it.isNotBlank() && it != "null"
                            },
                        )
                    )
                }
            }
            val lines = buildList {
                val arr = json.optJSONArray("lines") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val line = arr.optString(i)
                    if (line.isNotBlank()) add(line)
                }
            }
            QuotaSnapshot(
                planName = json.optString("planName").takeIf { it.isNotBlank() && it != "null" },
                items = items,
                lines = lines,
                remainText = json.optString("remainText").takeIf { it.isNotBlank() && it != "null" },
                endText = json.optString("endText").takeIf { it.isNotBlank() && it != "null" },
                source = runCatching { Source.valueOf(json.getString("source")) }.getOrDefault(Source.DOM),
                capturedAt = json.optLong("capturedAt", System.currentTimeMillis()),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
