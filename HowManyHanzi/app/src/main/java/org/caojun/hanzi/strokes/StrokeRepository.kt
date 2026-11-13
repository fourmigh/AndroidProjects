package org.caojun.hanzi.strokes

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MedianPoint(val x: Float, val y: Float)

/** 一个字模：strokes 是笔画轮廓，medians 是逐笔中心线（用于书写动画）。 */
data class StrokeData(
    val strokes: List<String>,
    val medians: List<List<MedianPoint>> = emptyList(),
)

/** 从 assets/strokes/<码点十六进制>.json 读取笔顺字模。 */
class StrokeRepository(private val context: Context) {
    private val cache = object : LruCache<String, StrokeData>(60) {
        override fun sizeOf(key: String, value: StrokeData): Int = 1
    }

    suspend fun load(ch: String): StrokeData? = withContext(Dispatchers.IO) {
        cache.get(ch)?.let { return@withContext it }
        if (ch.isEmpty()) return@withContext null
        val name = ch.codePointAt(0).toString(16) + ".json"
        try {
            val text = context.assets.open("strokes/$name")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(text)
            val arr = obj.getJSONArray("s")
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val d = arr.optString(i)
                if (d.isNotEmpty()) list.add(d)
            }
            if (list.isEmpty()) return@withContext null
            StrokeData(list, parseMedians(obj)).also { cache.put(ch, it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMedians(obj: JSONObject): List<List<MedianPoint>> {
        val arr = obj.optJSONArray("m") ?: return emptyList()
        val out = ArrayList<List<MedianPoint>>(arr.length())
        for (i in 0 until arr.length()) {
            val line = arr.optJSONArray(i) ?: continue
            val pts = ArrayList<MedianPoint>(line.length())
            for (j in 0 until line.length()) {
                val p = line.optJSONArray(j) ?: continue
                pts.add(MedianPoint(p.optDouble(0, 0.0).toFloat(), p.optDouble(1, 0.0).toFloat()))
            }
            out.add(pts)
        }
        return out
    }
}
