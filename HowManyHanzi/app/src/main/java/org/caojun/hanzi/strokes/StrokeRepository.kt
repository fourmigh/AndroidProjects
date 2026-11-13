package org.caojun.hanzi.strokes

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class StrokeData(val strokes: List<String>)

/** 从 assets/strokes/<码点十六进制>.json 读取笔顺字模。 */
class StrokeRepository(private val context: Context) {
    private val cache = object : LruCache<String, StrokeData>(40) {
        override fun sizeOf(key: String, value: StrokeData): Int = 1
    }

    suspend fun load(ch: String): StrokeData? = withContext(Dispatchers.IO) {
        cache.get(ch)?.let { return@withContext it }
        if (ch.isEmpty()) return@withContext null
        val name = ch.codePointAt(0).toString(16) + ".json"
        try {
            val text = context.assets.open("strokes/$name")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(text).getJSONArray("s")
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val d = arr.optString(i)
                if (d.isNotEmpty()) list.add(d)
            }
            if (list.isEmpty()) null else StrokeData(list).also { cache.put(ch, it) }
        } catch (e: Exception) {
            null
        }
    }
}
