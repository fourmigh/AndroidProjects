package org.caojun.ai.translate.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 翻译 provider 的客户端封装。
 *
 * 每次调用会绑定 provider 的服务，provider 会保留已加载的模型一段时间。
 * 失败会抛出 [TranslateException]。
 */
class TranslateClient(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getSupportedDirections(): List<String> = withContext(Dispatchers.IO) {
        val binding = bind() ?: throw TranslateException(
            TranslateApi.ERROR_NOT_READY,
            "translate provider is not installed"
        )
        try {
            binding.service.supportedDirections?.toList() ?: emptyList()
        } finally {
            unbind(binding)
        }
    }

    suspend fun isDirectionReady(from: String, to: String): Boolean =
        withContext(Dispatchers.IO) {
            val binding = bind() ?: return@withContext false
            try {
                binding.service.isDirectionReady(from, to)
            } finally {
                unbind(binding)
            }
        }

    suspend fun preload(from: String, to: String) = withContext(Dispatchers.IO) {
        val binding = bind() ?: throw TranslateException(
            TranslateApi.ERROR_NOT_READY,
            "translate provider is not installed"
        )
        try {
            binding.service.preload(from, to)
        } finally {
            unbind(binding)
        }
    }

    suspend fun translate(
        text: String,
        from: String,
        to: String,
        timeoutMs: Long = 60_000L
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        val binding = bind() ?: throw TranslateException(
            TranslateApi.ERROR_NOT_READY,
            "translate provider is not installed"
        )
        try {
            val version = binding.service.apiVersion
            if (version < TranslateApi.MIN_SUPPORTED_API_VERSION) {
                throw TranslateException(
                    TranslateApi.ERROR_UNSUPPORTED,
                    "translate provider API $version is too old, need ${TranslateApi.MIN_SUPPORTED_API_VERSION}"
                )
            }

            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    binding.service.translate(
                        text,
                        from,
                        to,
                        object : ITranslateCallback.Stub() {
                            override fun onSuccess(translatedText: String) {
                                if (cont.isActive) cont.resume(translatedText)
                            }

                            override fun onError(code: Int, message: String?) {
                                if (cont.isActive) {
                                    cont.resumeWithException(TranslateException(code, message))
                                }
                            }
                        }
                    )
                }
            }
        } finally {
            unbind(binding)
        }
    }

    private class Binding(
        val service: ITranslateService,
        val connection: ServiceConnection
    )

    private suspend fun bind(): Binding? = suspendCancellableCoroutine { cont ->
        val intent = Intent().setClassName(TranslateApi.PACKAGE, TranslateApi.SERVICE_CLASS)
        var completed = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (completed) return
                completed = true
                if (binder == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            TranslateException(
                                TranslateApi.ERROR_NOT_READY,
                                "binder is null"
                            )
                        )
                    }
                    return
                }
                val service = ITranslateService.Stub.asInterface(binder)
                if (cont.isActive) cont.resume(Binding(service, this))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 下一次调用会重新 bind。
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

    private fun unbind(binding: Binding) {
        runCatching { appContext.unbindService(binding.connection) }
    }
}
