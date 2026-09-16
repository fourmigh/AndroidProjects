package org.caojun.shotocr.monitor

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreenshotMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ShotOCR/Monitor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _screenshots = MutableSharedFlow<Screenshot>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val screenshots: SharedFlow<Screenshot> = _screenshots.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _recentScreenshots = MutableStateFlow<List<Screenshot>>(emptyList())
    val recentScreenshots: StateFlow<List<Screenshot>> = _recentScreenshots.asStateFlow()

    private var contentObserver: ContentObserver? = null
    private var lastUri: Uri? = null
    private var lastTime: Long = 0

    private val debounceMs = 1000L
    private val maxRecent = 20

    fun start() {
        if (_isRunning.value) {
            Log.w(TAG, "start: Monitor already running")
            return
        }

        Log.d(TAG, "start: Registering ContentObserver on EXTERNAL_CONTENT_URI")
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d(TAG, "onChange: Called with null uri (selfChange=$selfChange), scanning recent media")
                scope.launch {
                    val recentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
                    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    val cursor = context.contentResolver.query(
                        recentUri, projection, null, null, sortOrder
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            val id = it.getLong(idColumn)
                            val uri = android.content.ContentUris.withAppendedId(recentUri, id)
                            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                            val dateAdded = it.getLong(dateColumn)
                            val now = System.currentTimeMillis()
                            if (now - dateAdded < debounceMs * 2) {
                                Log.d(TAG, "onChange: Found recent media, processing uri=$uri")
                                handleMediaChange(uri)
                            } else {
                                Log.d(TAG, "onChange: Recent media too old (dateAdded=$dateAdded, now=$now), skipping")
                            }
                        }
                    }
                }
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d(TAG, "onChange: Called with uri=$uri (selfChange=$selfChange)")
                if (uri == null) {
                    Log.w(TAG, "onChange: URI is null, scanning recent media")
                    onChange(selfChange)
                    return
                }
                handleMediaChange(uri)
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver!!
        )
        _isRunning.value = true
        Log.d(TAG, "start: ContentObserver registered successfully, monitor is running")
    }

    fun stop() {
        Log.d(TAG, "stop: Stopping monitor")
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "stop: ContentObserver unregistered")
        }
        contentObserver = null
        _isRunning.value = false
        Log.d(TAG, "stop: Monitor stopped")
    }

    private fun handleMediaChange(uri: Uri) {
        val now = System.currentTimeMillis()
        if (uri == lastUri && now - lastTime < debounceMs) {
            Log.d(TAG, "handleMediaChange: Debounced, skipping uri=$uri")
            return
        }

        Log.d(TAG, "handleMediaChange: Processing uri=$uri")
        scope.launch {
            Log.d(TAG, "handleMediaChange: Calling classifyScreenshot")
            val screenshot = ScreenshotDetector.classifyScreenshot(
                context.contentResolver, uri
            )
            if (screenshot == null) {
                Log.d(TAG, "handleMediaChange: classifyScreenshot returned null (pending/not screenshot), NOT updating lastUri so next onChange can retry")
                return@launch
            }

            lastUri = uri
            lastTime = System.currentTimeMillis()
            Log.d(TAG, "handleMediaChange: Screenshot detected: ${screenshot.displayName}, path=${screenshot.path}")
            _screenshots.emit(screenshot)
            _recentScreenshots.value = _recentScreenshots.value
                .plus(screenshot)
                .takeLast(maxRecent)
            Log.d(TAG, "handleMediaChange: Screenshot emitted to flow")
        }
    }

    fun clearRecent() {
        _recentScreenshots.value = emptyList()
    }

    fun destroy() {
        Log.d(TAG, "destroy: Destroying monitor")
        stop()
        scope.cancel()
    }
}
