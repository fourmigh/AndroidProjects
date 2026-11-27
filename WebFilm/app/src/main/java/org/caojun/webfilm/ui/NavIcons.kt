package org.caojun.webfilm.ui

import org.caojun.webfilm.R

/** 把网页栏目名映射成本地图标。 */
fun navIconRes(key: String): Int = when (key) {
    "home" -> R.drawable.ic_home
    "movie" -> R.drawable.ic_movie
    "tv" -> R.drawable.ic_tv
    "variety" -> R.drawable.ic_variety
    "anime" -> R.drawable.ic_anime
    "short" -> R.drawable.ic_short
    "news" -> R.drawable.ic_news
    "star" -> R.drawable.ic_anime
    else -> R.drawable.ic_playlist
}
