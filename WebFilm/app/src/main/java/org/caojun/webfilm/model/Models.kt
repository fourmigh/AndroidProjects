package org.caojun.webfilm.model

/** 一条可交给外部播放器的视频线路。 */
data class VideoLine(
    val name: String,
    val url: String,
    val current: Boolean = false,
)

/** 从网页导航解析出来的栏目，iconKey 用于映射本地图标。 */
data class NavItem(
    val label: String,
    val path: String,
    val iconKey: String,
)
