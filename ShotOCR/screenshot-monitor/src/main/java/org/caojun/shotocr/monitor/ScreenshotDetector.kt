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

    fun classifyScreenshot(
        contentResolver: ContentResolver,
        uri: Uri
    ): Screenshot? {
        Log.d(TAG, "classifyScreenshot: Processing uri=$uri")

        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )

        val cursor = try {
            contentResolver.query(uri, projection, null, null, null)
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
            Log.w(TAG, "classifyScreenshot: MediaStore query returned null for uri=$uri, trying fallback")
            return fallbackClassify(uri)
        }

        cursor.use {
            if (!it.moveToFirst()) {
                Log.w(TAG, "classifyScreenshot: Cursor is empty for uri=$uri, trying fallback")
                return fallbackClassify(uri)
            }

            val displayName = it.getString(0)
            val relativePath = it.getString(1) ?: ""
            val dateAdded = it.getLong(2)

            Log.d(TAG, "classifyScreenshot: displayName=$displayName, relativePath=$relativePath, dateAdded=$dateAdded")

            if (displayName == null) {
                Log.w(TAG, "classifyScreenshot: displayName is null, trying fallback")
                return fallbackClassify(uri)
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

    private fun fallbackClassify(uri: Uri): Screenshot? {
        Log.d(TAG, "fallbackClassify: Attempting to classify from URI path: $uri")
        val uriString = uri.toString()

        val pathSegments = uri.pathSegments
        if (pathSegments.isNullOrEmpty()) {
            Log.w(TAG, "fallbackClassify: No path segments in uri")
            return null
        }

        val lastSegment = pathSegments.lastOrNull()
        Log.d(TAG, "fallbackClassify: Last path segment=$lastSegment")

        if (lastSegment != null) {
            val lower = lastSegment.lowercase()
            val isScreenshot = screenshotKeywords.any { kw -> lower.contains(kw) }
            Log.d(TAG, "fallbackClassify: Keyword match=$isScreenshot (segment='$lower')")

            if (isScreenshot) {
                val timestamp = System.currentTimeMillis()
                return Screenshot(
                    uri = uri,
                    path = uriString,
                    displayName = lastSegment,
                    timestamp = timestamp
                )
            }
        }

        for (segment in pathSegments) {
            val lower = segment.lowercase()
            if (pathKeywords.any { kw -> lower.contains(kw) }) {
                Log.d(TAG, "fallbackClassify: Path keyword match in segment='$segment'")
                val timestamp = System.currentTimeMillis()
                return Screenshot(
                    uri = uri,
                    path = uriString,
                    displayName = segment,
                    timestamp = timestamp
                )
            }
        }

        Log.d(TAG, "fallbackClassify: No screenshot pattern found")
        return null
    }
}
