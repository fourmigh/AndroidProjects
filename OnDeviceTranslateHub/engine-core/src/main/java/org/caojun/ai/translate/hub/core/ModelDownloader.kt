package org.caojun.ai.translate.hub.core

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/** 下载进度快照。 */
data class DownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int,
) {
    val isRunning: Boolean
        get() = status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING

    val isSuccess: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
}

/**
 * 用系统 DownloadManager 下载模型。
 *
 * 交给系统的好处：断点续传、失败重试、通知、随进程死亡存活、Wi-Fi 约束。
 * 多源回退由调用方按 [CatalogFile.urls] 逐个重试。
 */
class ModelDownloader(context: Context) {

    private val appContext = context.applicationContext
    private val dm: DownloadManager
        get() = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(
        model: CatalogModel,
        file: CatalogFile,
        urlIndex: Int = 0,
        allowMetered: Boolean = false,
    ): Long {
        val url = file.urls.getOrNull(urlIndex) ?: file.urls.first()

        // 目标文件名带时间戳：DownloadManager 对「相同 URL + 相同目标路径」的请求会
        // 直接复用旧任务（返回已完成的 id），导致删除后重新下载时“秒完成”却拿不到文件。
        val uniqueName = "${System.currentTimeMillis()}_${file.name}"
        val relative = "models/${model.id}.part/$uniqueName"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(model.name)
            .setDescription("正在下载 ${file.name}")
            .setDestinationInExternalFilesDir(appContext, null, relative)
            .setAllowedOverMetered(allowMetered)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setMimeType("application/octet-stream")

        val id = dm.enqueue(request)
        Log.d(TAG, "enqueue ${model.id} url#${urlIndex} id=$id dest=$relative")
        return id
    }

    fun query(downloadId: Long): DownloadSnapshot? {
        val cursor: Cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return DownloadSnapshot(
                status = it.getIntOrZero(DownloadManager.COLUMN_STATUS),
                downloadedBytes = it.getLongOrZero(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                totalBytes = it.getLongOrZero(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                reason = it.getIntOrZero(DownloadManager.COLUMN_REASON),
            )
        }
    }

    fun cancel(downloadId: Long) {
        runCatching { dm.remove(downloadId) }
    }

    private fun Cursor.getIntOrZero(column: String): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0 else getInt(index)
    }

    private fun Cursor.getLongOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private companion object {
        const val TAG = "HubDownloader"
    }
}
