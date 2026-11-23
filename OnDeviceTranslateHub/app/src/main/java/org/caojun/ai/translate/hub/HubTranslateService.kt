package org.caojun.ai.translate.hub

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
import kotlinx.coroutines.withTimeout
import org.caojun.ai.translate.hub.core.HubModelManager
import org.caojun.ai.translate.provider.ITranslateCallback
import org.caojun.ai.translate.provider.ITranslateProvider
import org.caojun.ai.translate.provider.LanguageCatalog
import org.caojun.ai.translate.provider.TranslateProviderApi

/**
 * 对外的翻译 provider。
 *
 * 能力完全由「已下载的模型」决定：没有模型时 `modelReady=false`，
 * 客户端会引导用户打开本 App 下载。
 */
class HubTranslateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val idleHandler = Handler(Looper.getMainLooper())

    private val releaseRunnable = Runnable {
        scope.launch {
            runCatching { HubModelManager.release() }
                .onSuccess { Log.d(TAG, "engine released after idle") }
        }
    }

    override fun onCreate() {
        super.onCreate()
        HubModelManager.init(applicationContext)
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
                runCatching { HubModelManager.release() }
            } finally {
                scope.cancel()
            }
        }
        super.onDestroy()
    }

    private val binder = object : ITranslateProvider.Stub() {

        override fun getApiVersion(): Int = TranslateProviderApi.API_VERSION

        override fun getCapability(): String = HubModelManager.capabilityJson()

        override fun preload(from: String, to: String) {
            val target = LanguageCatalog.normalize(from) ?: return
            val dest = LanguageCatalog.normalize(to) ?: return
            HubModelManager.preload(target, dest)
        }

        override fun translate(
            text: String,
            from: String,
            to: String,
            optionsJson: String,
            callback: ITranslateCallback,
        ) {
            val target = LanguageCatalog.normalize(from)
            val dest = LanguageCatalog.normalize(to)
            if (target == null || dest == null) {
                callback.onError(TranslateProviderApi.ERROR_INVALID_INPUT, "unknown language: $from -> $to")
                return
            }
            if (text.isBlank()) {
                callback.onSuccess("")
                return
            }

            scope.launch {
                try {
                    val result = withTimeout(TIMEOUT_MS) {
                        HubModelManager.translate(text, target, dest)
                    }
                    callback.onSuccess(result)
                } catch (t: Throwable) {
                    Log.e(TAG, "translate failed", t)
                    runCatching {
                        callback.onError(
                            TranslateProviderApi.ERROR_INFERENCE,
                            t.message ?: t.javaClass.simpleName
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "HubTranslateService"
        const val IDLE_RELEASE_MS = 15 * 60 * 1000L
        const val TIMEOUT_MS = 120_000L
    }
}
