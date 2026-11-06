package org.caojun.ai.llm

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.caojun.ai.llm.api.ILlmCallback
import org.caojun.ai.llm.api.ILlmService
import org.caojun.ai.llm.api.LlmApi
import org.caojun.shotocr.llm.LiteRtLlmEngine
import org.caojun.shotocr.llm.LlmModelMissingException

/**
 * 对外的本地大模型服务。
 *
 * 只提供底层能力：system + user -> 文本。模型在进程内单例，空闲一段时间后释放。
 * LLM 推理串行执行，多个客户端同时调用会排队。
 */
class LlmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val engineLock = Mutex()
    private val idleHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var engine: LiteRtLlmEngine? = null

    private val releaseRunnable = Runnable {
        scope.launch {
            engineLock.withLock {
                runCatching { engine?.close() }
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
                    runCatching { engine?.close() }
                    engine = null
                }
            } finally {
                scope.cancel()
            }
        }
        super.onDestroy()
    }

    private val binder = object : ILlmService.Stub() {

        override fun getApiVersion(): Int = LlmApi.API_VERSION

        override fun isReady(): Boolean = engine != null

        override fun preload() {
            scope.launch {
                runCatching { ensureEngineLocked() }
                    .onFailure { Log.e(TAG, "preload failed", it) }
            }
        }

        override fun generate(
            systemInstruction: String?,
            userMessage: String?,
            callback: ILlmCallback?
        ) {
            if (callback == null) return
            if (userMessage.isNullOrBlank()) {
                callback.onError(LlmApi.ERROR_INVALID_INPUT, "user message is empty")
                return
            }

            scope.launch {
                try {
                    engineLock.withLock {
                        val current = ensureEngineLocked()
                        val result = current.generate(systemInstruction.orEmpty(), userMessage)
                        callback.onSuccess(result)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "generate failed", t)
                    runCatching {
                        callback.onError(
                            errorCode(t),
                            t.message ?: t.javaClass.simpleName
                        )
                    }
                }
            }
        }
    }

    private suspend fun ensureEngineLocked(): LiteRtLlmEngine {
        engine?.let { return it }
        val created = LiteRtLlmEngine(applicationContext)
        created.initialize()
        engine = created
        Log.d(TAG, "engine initialized")
        return created
    }

    private fun errorCode(t: Throwable): Int = when (t) {
        is LlmModelMissingException -> LlmApi.ERROR_NOT_READY
        else -> LlmApi.ERROR_INFERENCE
    }

    private companion object {
        const val TAG = "LlmService"
        const val IDLE_RELEASE_MS = 5 * 60 * 1000L
    }
}
