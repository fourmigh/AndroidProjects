package org.caojun.player.history

import android.content.Context


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
                            episodeName = item.optString("episode", ""),
                            positionMs = item.optLong("position", 0L),
                            durationMs = item.optLong("duration", 0L),
                            episodeKey = item.optString("episodeKey", ""),
                            episodes = parseEpisodes(item.optJSONObject("episodes")),
                            videoUrl = item.optString("videoUrl", ""),
                            lastPlayer = item.optString("lastPlayer", "web"),
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

    /** 只更新进度，保留标题、URL 等已有信息。 */
    fun updateProgress(
        key: String,
        episodeKey: String,
        episodeName: String,
        positionMs: Long,
        durationMs: Long,
        videoUrl: String = "",
    ) {
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.key == key }
        if (index < 0) return

        val current = list[index]
        val nextEpisode = episodeName.ifBlank {
            if (current.episodeKey == episodeKey) current.episodeName else ""
        }
        if (current.episodeKey == episodeKey &&
            current.positionMs == positionMs &&
            current.durationMs == durationMs &&
            current.episodeName == nextEpisode &&
            (videoUrl.isBlank() || current.videoUrl == videoUrl)
        ) {
            return
        }

        val progress = EpisodeProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
        )
        list[index] = current.copy(
            episodeName = nextEpisode,
            episodeKey = episodeKey,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = progress.updatedAt,
            episodes = current.episodes + (episodeKey to progress),
            videoUrl = videoUrl.ifBlank { current.videoUrl },
        )
        save(list)
    }

    /** 取某部剧某一集已保存的进度。 */
    fun progressOf(key: String, episodeKey: String): EpisodeProgress? =
        load().firstOrNull { it.key == key }?.episodes?.get(episodeKey)

    /** 更新上次播放页地址、集名和集标识，保留已有进度。 */
    fun updatePage(key: String, url: String, episodeKey: String, episodeName: String) {
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.key == key }
        if (index < 0) return
        val current = list[index]
        if (current.url == url &&
            current.episodeKey == episodeKey &&
            current.episodeName == episodeName
        ) {
            return
        }
        list[index] = current.copy(
            url = url,
            episodeKey = episodeKey,
            episodeName = episodeName,
            updatedAt = System.currentTimeMillis(),
        )
        save(list)
    }

    /** 记住这部剧上次使用的播放器：web / media3 / external。 */
    fun setLastPlayer(key: String, player: String) {
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.key == key }
        if (index < 0) return
        if (list[index].lastPlayer == player) return
        list[index] = list[index].copy(lastPlayer = player)
        save(list)
    }

    /** 保存当前线路的 m3u8，供历史直接用 Media3 / 外部播放器打开。 */
    fun setVideoUrl(key: String, videoUrl: String) {
        if (videoUrl.isBlank()) return
        val list = load().toMutableList()
        val index = list.indexOfFirst { it.key == key }
        if (index < 0) return
        if (list[index].videoUrl == videoUrl) return
        list[index] = list[index].copy(videoUrl = videoUrl)
        save(list)
    }

    fun remove(key: String) {
        save(load().filterNot { it.key == key })
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun parseEpisodes(json: JSONObject?): Map<String, EpisodeProgress> {
        if (json == null) return emptyMap()
        val result = mutableMapOf<String, EpisodeProgress>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.optJSONObject(key) ?: continue
            result[key] = EpisodeProgress(
                positionMs = item.optLong("p", 0L),
                durationMs = item.optLong("d", 0L),
                updatedAt = item.optLong("t", 0L),
            )
        }
        return result
    }

    private fun episodesToJson(episodes: Map<String, EpisodeProgress>): JSONObject {
        val json = JSONObject()
        episodes.forEach { (key, progress) ->
            json.put(
                key,
                JSONObject().apply {
                    put("p", progress.positionMs)
                    put("d", progress.durationMs)
                    put("t", progress.updatedAt)
                }
            )
        }
        return json
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
                    put("episode", entry.episodeName)
                    put("position", entry.positionMs)
                    put("duration", entry.durationMs)
                    put("episodeKey", entry.episodeKey)
                    put("episodes", episodesToJson(entry.episodes))
                    put("videoUrl", entry.videoUrl)
                    put("lastPlayer", entry.lastPlayer)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "player_history"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
    }
}
