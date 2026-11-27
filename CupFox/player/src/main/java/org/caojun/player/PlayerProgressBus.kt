package org.caojun.player

/** 宿主实现这个接口，把播放器进度写进自己的观看历史。 */
interface PlayerProgressListener {
    fun onProgress(
        key: String,
        episodeKey: String,
        episodeName: String,
        positionMs: Long,
        durationMs: Long,
    )

    fun onLastPlayerChanged(key: String, player: PlayerType)

    fun onVideoUrl(key: String, videoUrl: String)
}

/** 播放器模块 -> 宿主的同进程回调。 */
object PlayerProgressBus {
    var listener: PlayerProgressListener? = null
}
