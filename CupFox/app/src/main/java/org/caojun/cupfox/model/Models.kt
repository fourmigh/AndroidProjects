package org.caojun.cupfox.model

/** 一条可交给外部播放器的视频线路。 */
data class VideoLine(
    val name: String,
    val url: String,
    val current: Boolean = false,
)

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

/** 从网页导航解析出来的栏目，iconKey 用于映射本地图标。 */
data class NavItem(
    val label: String,
    val path: String,
    val iconKey: String,
)
