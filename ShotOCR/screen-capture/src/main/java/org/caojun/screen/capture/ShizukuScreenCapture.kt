package org.caojun.screen.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.caojun.shizuku.RikkaShizukuUserServiceHost
import org.caojun.shizuku.ShizukuUserServiceHost
import java.io.File

/**
 * 通过 Shizuku UserService 执行 `screencap -p`。
 * 截图先写入 App 缓存文件（fd 传递），避免 Binder 1MB 事务上限。
 */
class ShizukuScreenCapture(
    private val context: Context,
    private val host: ShizukuUserServiceHost
) : ScreenCapture {

    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) { captureInternal() }
    }

    private suspend fun captureInternal(): Bitmap? {
        val file = File.createTempFile("screen-", ".png", context.cacheDir)
        return try {
            val exitCode = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            ).use { png ->
                host.withService(ScreenCaptureService::class.java) { binder ->
                    IShizukuScreen.Stub.asInterface(binder).captureToFile(png)
                }
            } ?: return null

            if (exitCode != 0) {
                Log.e(TAG, "screencap exit code $exitCode")
                return null
            }
            BitmapFactory.decodeFile(file.absolutePath)
        } finally {
            file.delete()
        }
    }

    companion object {
        private const val TAG = "ShizukuScreenCapture"
        private const val TIMEOUT_MS = 15_000L
    }
}

fun ShizukuScreenCapture(context: Context): ShizukuScreenCapture =
    ShizukuScreenCapture(context, RikkaShizukuUserServiceHost(context))
