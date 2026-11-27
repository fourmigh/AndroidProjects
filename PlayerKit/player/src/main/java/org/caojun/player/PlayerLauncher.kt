package org.caojun.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 启动内置 Media3 播放器或系统外部播放器。 */
object PlayerLauncher {

    fun launchMedia3(
        context: Context,
        url: String,
        title: String,
        episodeName: String,
        historyKey: String,
        episodeKey: String,
        referer: String,
        startMs: Long,
    ) {
        val intent = Intent(context, Media3PlayerActivity::class.java).apply {
            putExtra(Media3PlayerActivity.EXTRA_URL, url)
            putExtra(Media3PlayerActivity.EXTRA_TITLE, title)
            putExtra(Media3PlayerActivity.EXTRA_EPISODE, episodeName)
            putExtra(Media3PlayerActivity.EXTRA_HISTORY_KEY, historyKey)
            putExtra(Media3PlayerActivity.EXTRA_EPISODE_KEY, episodeKey)
            putExtra(Media3PlayerActivity.EXTRA_REFERER, referer)
            putExtra(Media3PlayerActivity.EXTRA_START_MS, startMs)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 返回是否成功交给外部应用。 */
    fun launchExternal(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val mimeTypes = listOf(
            "application/x-mpegURL",
            "application/vnd.apple.mpegurl",
            "video/*",
        )
        for (mime in mimeTypes) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: Exception) {
            }
        }
        return false
    }
}
