package org.caojun.ai.llm.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * LLM provider 的客户端封装，实现 [LlmEngineApi]，业务代码可以直接替换使用。
 *
 * 连接会在客户端生命周期内保持，直到调用 [close]。
 */
class LlmClient(context: Context) : LlmEngineApi {

    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var binding: Binding? = null

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        val b = ensureBound()
        val version = b.service.apiVersion
        if (version < LlmApi.MIN_SUPPORTED_API_VERSION) {
            throw LlmException(
                LlmApi.ERROR_UNSUPPORTED,
                "LLM provider API $version is too old, need ${LlmApi.MIN_SUPPORTED_API_VERSION}"
            )
        }
        b.service.preload()
    }

    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        val b = ensureBound()
        b.service.isReady()
    }

    override fun generate(systemInstruction: String, userMessage: String): String {
        val b = runBlocking { ensureBound() }
        val latch = CountDownLatch(1)
        var text: String? = null
        var error: LlmException? = null
        var failed: Throwable? = null

        b.service.generate(
            systemInstruction,
            userMessage,
            object : ILlmCallback.Stub() {
                override fun onSuccess(resultText: String) {
                    text = resultText
                    latch.countDown()
                }

                override fun onError(code: Int, message: String?) {
                    error = LlmException(code, message)
                    latch.countDown()
                }
            }
        )

        val finished = runCatching { latch.await(GENERATE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .getOrElse {
                failed = it
                false
            }

        failed?.let { throw LlmException(LlmApi.ERROR_INFERENCE, it.message) }
        if (!finished) {
            throw LlmException(LlmApi.ERROR_BUSY, "LLM request timed out")
        }
        error?.let { throw it }
        return text.orEmpty()
    }

    override fun close() {
        val current = synchronized(lock) {
            binding.also { binding = null }
        }
        current?.let { runCatching { appContext.unbindService(it.connection) } }
    }

    private class Binding(
        val service: ILlmService,
        val connection: ServiceConnection
    )

    private suspend fun ensureBound(): Binding {
        synchronized(lock) { binding }?.let { return it }

        val created = bind() ?: throw LlmException(
            LlmApi.ERROR_NOT_READY,
            "LLM provider is not installed"
        )

        synchronized(lock) {
            val existing = binding
            if (existing != null) {
                runCatching { appContext.unbindService(created.connection) }
                return existing
            }
            binding = created
            return created
        }
    }

    private suspend fun bind(): Binding? = suspendCancellableCoroutine { cont ->
        val intent = Intent().setClassName(LlmApi.PACKAGE, LlmApi.SERVICE_CLASS)
        var completed = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (completed) return
                completed = true
                if (binder == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            LlmException(LlmApi.ERROR_NOT_READY, "binder is null")
                        )
                    }
                    return
                }
                val service = ILlmService.Stub.asInterface(binder)
                if (cont.isActive) cont.resume(Binding(service, this))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 连接断开后清空缓存，下一次调用会重新 bind。
                synchronized(lock) { binding = null }
            }
        }

        val ok = runCatching {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)

        if (!ok) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation {
            runCatching { appContext.unbindService(connection) }
        }
    }

    private companion object {
        const val GENERATE_TIMEOUT_SECONDS = 180L
    }
}
