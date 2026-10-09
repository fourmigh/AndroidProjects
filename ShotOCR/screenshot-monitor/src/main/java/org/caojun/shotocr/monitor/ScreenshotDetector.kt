package org.caojun.shotocr.monitor

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object ScreenshotDetector {

    private const val TAG = "ShotOCR/Detector"

    private val screenshotKeywords = listOf(
        "screenshot", "screen_shot", "screen-shot", "screencap",
        "screen_capture", "screen-"
    )

    private val pathKeywords = listOf(
        "/screenshots/", "/screenshot/", "/screencapture/"
    )

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 判断某个 MediaStore uri 是否为「新产生的截图」。
     * 已删除、回收站(trashed)、写入中(pending) 的项目一律返回 null，
     * 避免相册删除等操作被误判为截图事件。
     */
    fun classifyScreenshot(
        contentResolver: ContentResolver,
        uri: Uri
    ): Screenshot? {
        Log.d(TAG, "classifyScreenshot: Processing uri=$uri")

        val projection = mutableListOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.IS_PENDING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.MediaColumns.IS_TRASHED)
        }

        val cursor = try {
            contentResolver.query(uri, projection.toTypedArray(), null, null, null)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("pending", ignoreCase = true) || msg.contains("trashed", ignoreCase = true)) {
                Log.w(TAG, "classifyScreenshot: Item is pending/trashed for uri=$uri, will retry on next onChange")
            } else {
                Log.e(TAG, "classifyScreenshot: Query exception for uri=$uri", e)
            }
            null
        }

        if (cursor == null) {
            Log.w(TAG, "classifyScreenshot: MediaStore query returned null for uri=$uri, skip")
            return null
        }

        cursor.use {
            if (!it.moveToFirst()) {
                Log.w(TAG, "classifyScreenshot: Cursor is empty (deleted/not found) for uri=$uri, skip")
                return null
            }

            val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
            val relativePath = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)) ?: ""
            val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pending = it.getInt(it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING))
                if (pending == 1) {
                    Log.d(TAG, "classifyScreenshot: Item is still pending for uri=$uri, skip")
                    return null
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val trashed = it.getInt(it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED))
                if (trashed == 1) {
                    Log.d(TAG, "classifyScreenshot: Item is trashed for uri=$uri, skip")
                    return null
                }
            }

            if (displayName == null) {
                Log.w(TAG, "classifyScreenshot: displayName is null for uri=$uri, skip")
                return null
            }

            val text = "$displayName $relativePath".lowercase()
            val isScreenshot = screenshotKeywords.any { kw -> text.contains(kw) } ||
                    pathKeywords.any { kw -> text.contains(kw) }

            Log.d(TAG, "classifyScreenshot: isScreenshot=$isScreenshot (text='$text')")

            if (!isScreenshot) return null

            return Screenshot(
                uri = uri,
                path = "$relativePath$displayName",
                displayName = displayName,
                timestamp = dateAdded * 1000
            )
        }
    }
}
