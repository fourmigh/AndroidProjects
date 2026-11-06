package org.caojun.ai.ocr

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.caojun.ai.ocr.api.IOcrCallback
import org.caojun.ai.ocr.api.IOcrService
import org.caojun.ai.ocr.api.OcrApi
import org.caojun.ai.ocr.api.OcrLine
import org.caojun.shotocr.ocr.OcrEngine

/**
 * 对外的 OCR 服务。
 *
 * 只提供底层能力：图片 -> 文本行 + 坐标。模型在进程内单例，空闲一段时间后释放。
 */
class OcrService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val engineLock = Mutex()
    private val idleHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var engine: OcrEngine? = null

    private val releaseRunnable = Runnable {
        scope.launch {
            engineLock.withLock {
                engine?.release()
                engine = null
                Log.d(TAG, "engine released after idle")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
    }

    override fun onBind(intent: Intent?): IBinder {
        idleHandler.removeCallbacks(releaseRunnable)
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        idleHandler.removeCallbacks(releaseRunnable)
        idleHandler.postDelayed(releaseRunnable, IDLE_RELEASE_MS)
        return false
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(releaseRunnable)
        scope.launch {
            try {
                engineLock.withLock {
                    engine?.release()
                    engine = null
                }
            } finally {
                scope.cancel()
            }
        }
        super.onDestroy()
    }

    private val binder = object : IOcrService.Stub() {

        override fun getApiVersion(): Int = OcrApi.API_VERSION

        override fun isReady(): Boolean = engine != null

        override fun preload() {
            scope.launch {
                runCatching { ensureEngine() }
            }
        }

        override fun recognize(
            image: ParcelFileDescriptor?,
            format: String?,
            callback: IOcrCallback?
        ) {
            if (callback == null) {
                image?.close()
                return
            }
            if (image == null) {
                callback.onError(OcrApi.ERROR_INVALID_INPUT, "image is null")
                return
            }

            scope.launch {
                try {
                    engineLock.withLock {
                        val bitmap = BitmapFactory.decodeFileDescriptor(image.fileDescriptor)
                            ?: throw IllegalStateException("failed to decode image")
                        try {
                            val results = ensureEngineLocked().detect(bitmap)
                            callback.onSuccess(
                                OcrLine.listToJson(results.map { it.toLine() })
                            )
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "recognize failed", t)
                    // 客户端可能已死亡，回调异常不应拖垮服务进程。
                    runCatching {
                        callback.onError(
                            OcrApi.ERROR_INFERENCE,
                            t.message ?: t.javaClass.simpleName
                        )
                    }
                } finally {
                    runCatching { image.close() }
                }
            }
        }
    }

    private suspend fun ensureEngine(): OcrEngine = engineLock.withLock {
        ensureEngineLocked()
    }

    private suspend fun ensureEngineLocked(): OcrEngine {
        engine?.let { return it }
        val created = OcrEngine(applicationContext)
        created.initialize()
        engine = created
        Log.d(TAG, "engine initialized")
        return created
    }

    private fun org.caojun.shotocr.ocr.OcrResult.toLine(): OcrLine = OcrLine(
        text = text,
        score = score,
        left = box.left,
        top = box.top,
        right = box.right,
        bottom = box.bottom
    )

    private companion object {
        const val TAG = "OcrService"
        const val IDLE_RELEASE_MS = 5 * 60 * 1000L
    }
}
