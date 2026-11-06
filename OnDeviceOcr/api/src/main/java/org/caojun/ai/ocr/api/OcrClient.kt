package org.caojun.ai.ocr.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR provider 的客户端封装。
 *
 * 每次调用会绑定 provider 的服务；provider 侧会把模型保留一段时间，
 * 所以连续调用不需要重新加载模型。失败会抛出 [OcrException]。
 */
class OcrClient(context: Context) {

    private val appContext = context.applicationContext

    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        val binding = bind() ?: return@withContext false
        try {
            binding.service.isReady()
        } finally {
            unbind(binding)
        }
    }

    /** 预加载模型，不返回识别结果。 */
    suspend fun preload() = withContext(Dispatchers.IO) {
        val binding = bind() ?: throw OcrException(
            OcrApi.ERROR_NOT_READY,
            "OCR provider is not installed"
        )
        try {
            binding.service.preload()
        } finally {
            unbind(binding)
        }
    }

    /**
     * 识别一张图片。
     *
     * 图片通过临时文件 + ParcelFileDescriptor 传递，避开 Binder 1MB 事务限制。
     */
    suspend fun recognize(bitmap: Bitmap, timeoutMs: Long = 30_000L): List<OcrLine> =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("ocr-", ".png", appContext.cacheDir)
            try {
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw OcrException(
                            OcrApi.ERROR_INVALID_INPUT,
                            "failed to encode bitmap"
                        )
                    }
                }

                val binding = bind() ?: throw OcrException(
                    OcrApi.ERROR_NOT_READY,
                    "OCR provider is not installed"
                )
                try {
                    val version = binding.service.apiVersion
                    if (version < OcrApi.MIN_SUPPORTED_API_VERSION) {
                        throw OcrException(
                            OcrApi.ERROR_UNSUPPORTED,
                            "OCR provider API $version is too old, need ${OcrApi.MIN_SUPPORTED_API_VERSION}"
                        )
                    }

                    withTimeout(timeoutMs) {
                        ParcelFileDescriptor.open(
                            file,
                            ParcelFileDescriptor.MODE_READ_ONLY
                        ).use { pfd ->
                            suspendCancellableCoroutine { cont ->
                                binding.service.recognize(
                                    pfd,
                                    OcrApi.FORMAT_PNG,
                                    object : IOcrCallback.Stub() {
                                        override fun onSuccess(resultJson: String) {
                                            val lines = runCatching {
                                                OcrLine.listFromJson(resultJson)
                                            }.getOrElse { e ->
                                                if (cont.isActive) cont.resumeWithException(e)
                                                return
                                            }
                                            if (cont.isActive) cont.resume(lines)
                                        }

                                        override fun onError(code: Int, message: String?) {
                                            if (cont.isActive) {
                                                cont.resumeWithException(
                                                    OcrException(code, message)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } finally {
                    unbind(binding)
                }
            } finally {
                file.delete()
            }
        }

    private class Binding(
        val service: IOcrService,
        val connection: ServiceConnection
    )

    private suspend fun bind(): Binding? = suspendCancellableCoroutine { cont ->
        val intent = Intent().setClassName(OcrApi.PACKAGE, OcrApi.SERVICE_CLASS)
        var completed = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (completed) return
                completed = true
                if (binder == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            OcrException(OcrApi.ERROR_NOT_READY, "binder is null")
                        )
                    }
                    return
                }
                val service = IOcrService.Stub.asInterface(binder)
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
