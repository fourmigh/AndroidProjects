package org.caojun.player.history

/** 单集播放进度。 */
data class EpisodeProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** 观看历史条目。key 用于同一影片去重，url 用于继续观看。 */
data class HistoryEntry(
    val key: String,
    val title: String,
    val url: String,
    val updatedAt: Long,
    val episodeName: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val episodeKey: String = "",
    val episodes: Map<String, EpisodeProgress> = emptyMap(),
    val videoUrl: String = "",
    val lastPlayer: String = "web",
)
