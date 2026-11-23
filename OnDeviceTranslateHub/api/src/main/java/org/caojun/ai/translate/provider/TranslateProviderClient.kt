package org.caojun.ai.translate.provider

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
 * 绑定单个翻译 provider 的客户端。
 *
 * 每次调用 bind → 调用 → unbind；provider 内部会自己缓存已加载的模型。
 */
class TranslateProviderClient(context: Context, private val packageName: String) {

    private val appContext = context.applicationContext

    suspend fun capability(): TranslateCapability? = withContext(Dispatchers.IO) {
        val binding = bind() ?: return@withContext null
        try {
            if (binding.service.apiVersion < TranslateProviderApi.MIN_SUPPORTED_API_VERSION) {
                return@withContext null
            }
            TranslateCapability.parse(binding.service.capability)
        } finally {
            unbind(binding)
        }
    }

    suspend fun preload(from: String, to: String) = withContext(Dispatchers.IO) {
        val binding = bind() ?: throw TranslateProviderException(
            TranslateProviderApi.ERROR_NOT_READY,
            "$packageName is not installed"
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
        optionsJson: String = "",
        timeoutMs: Long = 90_000L,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        val binding = bind() ?: throw TranslateProviderException(
            TranslateProviderApi.ERROR_NOT_READY,
            "$packageName is not installed"
        )
        try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    binding.service.translate(text, from, to, optionsJson, object : ITranslateCallback.Stub() {
                        override fun onSuccess(translatedText: String) {
                            if (cont.isActive) cont.resume(translatedText)
                        }

                        override fun onError(code: Int, message: String) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    TranslateProviderException(code, message)
                                )
                            }
                        }
                    })
                }
            }
        } finally {
            unbind(binding)
        }
    }

    private class Binding(val service: ITranslateProvider, val connection: ServiceConnection)

    private suspend fun bind(): Binding? = suspendCancellableCoroutine { cont ->
        val intent = Intent(TranslateProviderApi.ACTION_PROVIDER).setPackage(packageName)
        var completed = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (completed) return
                completed = true
                if (binder == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            TranslateProviderException(TranslateProviderApi.ERROR_NOT_READY, "binder is null")
                        )
                    }
                    return
                }
                val service = ITranslateProvider.Stub.asInterface(binder)
                if (cont.isActive) cont.resume(Binding(service, this))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 下次调用会重新 bind
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
