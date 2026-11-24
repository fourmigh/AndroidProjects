package org.caojun.quotamonitor.data

import android.content.Context
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.QuotaSnapshot
import org.caojun.quotamonitor.model.Source
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences 缓存：key 为平台配置 id，打开 App 秒开展示上次快照。
 */
object QuotaCache {

    private const val PREFS = "quota_cache_v4"

    fun save(context: Context, configId: String, snapshot: QuotaSnapshot) {
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
        prefs(context).edit().putString(configId, json.toString()).apply()
    }

    fun load(context: Context, configId: String): QuotaSnapshot? {
        val raw = prefs(context).getString(configId, null) ?: return null
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

    fun clear(context: Context, configId: String) {
        prefs(context).edit().remove(configId).apply()
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
