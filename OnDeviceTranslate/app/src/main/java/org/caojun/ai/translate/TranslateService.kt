package org.caojun.ai.translate

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.caojun.ai.translate.api.ITranslateCallback
import org.caojun.ai.translate.api.ITranslateService
import org.caojun.ai.translate.api.TranslateApi
import org.caojun.shotocr.translate.TranslateEngine

/**
 * 对外的离线翻译服务。
 *
 * 只提供底层能力：文本 -> 文本。模型在进程内单例，空闲一段时间后释放。
 */
class TranslateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val idleHandler = Handler(Looper.getMainLooper())

    private val releaseRunnable = Runnable {
        scope.launch {
            runCatching { TranslateEngine.release() }
                .onSuccess { Log.d(TAG, "engine released after idle") }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TranslateEngine.initialize(applicationContext)
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
                runCatching { TranslateEngine.release() }
            } finally {
                scope.cancel()
            }
        }
        super.onDestroy()
    }

    private val binder = object : ITranslateService.Stub() {

        override fun getApiVersion(): Int = TranslateApi.API_VERSION

        override fun getSupportedDirections(): Array<String> = SUPPORTED_DIRECTIONS

        override fun isDirectionReady(from: String?, to: String?): Boolean =
            from != null && to != null && TranslateApi.directionOf(from, to) != null

        override fun preload(from: String?, to: String?) {
            if (from == null || to == null) return
            scope.launch {
                runCatching { TranslateEngine.preload(from, to) }
                    .onFailure { Log.e(TAG, "preload $from-$to failed", it) }
            }
        }

        override fun translate(
            text: String?,
            from: String?,
            to: String?,
            callback: ITranslateCallback?
        ) {
            if (callback == null) return
            if (text.isNullOrEmpty()) {
                callback.onSuccess("")
                return
            }
            if (from == null || to == null) {
                callback.onError(TranslateApi.ERROR_INVALID_INPUT, "from/to is null")
                return
            }
            if (TranslateApi.directionOf(from, to) == null) {
                callback.onError(
                    TranslateApi.ERROR_UNSUPPORTED,
                    "unsupported direction: $from -> $to"
                )
                return
            }

            scope.launch {
                try {
                    val result = TranslateEngine.translate(text, from, to)
                    callback.onSuccess(result)
                } catch (t: Throwable) {
                    Log.e(TAG, "translate failed", t)
                    runCatching {
                        callback.onError(
                            TranslateApi.ERROR_INFERENCE,
                            t.message ?: t.javaClass.simpleName
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "TranslateService"
        const val IDLE_RELEASE_MS = 5 * 60 * 1000L
        val SUPPORTED_DIRECTIONS = arrayOf(
            TranslateApi.DIRECTION_ZH_EN,
            TranslateApi.DIRECTION_EN_ZH
        )
    }
}
