package org.caojun.shizuku.automation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.caojun.shizuku.RikkaShizukuUserServiceHost

/** 绑定一次 UserService，整个批量流程内复用 dump/tap/swipe/back/截图。 */
class ShizukuUiAutomation(context: Context) {

    private val appContext = context.applicationContext
    private val host = RikkaShizukuUserServiceHost(appContext)

    private companion object {
        const val TAG = "ShizukuUiAutomation"
    }

    suspend fun <T> withSession(block: suspend (ShizukuUiAutomationSession) -> T): T? {
        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "session start: ${ShizukuAutomationService::class.java.name}")
        var result: T? = null
        try {
            result = host.withService(
                ShizukuAutomationService::class.java,
                timeoutMs = 20_000L
            ) { binder ->
                block(
                    ShizukuUiAutomationSession(
                        appContext,
                        IShizukuAutomation.Stub.asInterface(binder)
                    )
                )
            }
            return result
        } finally {
            Log.i(
                TAG,
                "session end: bound=${result != null} elapsedMs=${SystemClock.elapsedRealtime() - started}"
            )
        }
    }
}

class ShizukuUiAutomationSession internal constructor(
    private val context: Context,
    private val api: IShizukuAutomation
) {

    private companion object {
        const val TAG = "ShizukuUiAutomation"
    }

    suspend fun dumpUi(): String? = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val file = File.createTempFile("ui-", ".xml", context.cacheDir)
        try {
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            ).use { pfd ->
                val bytes = api.dumpUi(pfd)
                if (bytes <= 0) {
                    Log.w(TAG, "dumpUi failed: result=$bytes")
                    return@withContext null
                }
                Log.d(
                    TAG,
                    "dumpUi read: bytes=$bytes elapsedMs=${SystemClock.elapsedRealtime() - started}"
                )
            }
            file.readText()
        } catch (t: Throwable) {
            Log.w(TAG, "dumpUi error", t)
            null
        } finally {
            file.delete()
        }
    }

    suspend fun screenshot(): Bitmap? = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val file = File.createTempFile("batch-", ".png", context.cacheDir)
        try {
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            ).use { pfd ->
                val result = api.screenshot(pfd)
                if (result != 0) {
                    Log.w(TAG, "screenshot failed: result=$result")
                    return@withContext null
                }
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            Log.d(
                TAG,
                "screenshot read: ${bitmap?.width}x${bitmap?.height} elapsedMs=${SystemClock.elapsedRealtime() - started}"
            )
            bitmap
        } catch (t: Throwable) {
            Log.w(TAG, "screenshot error", t)
            null
        } finally {
            file.delete()
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean =
        withContext(Dispatchers.IO) {
            val ok = api.tap(x, y) == 0
            if (ok) Log.d(TAG, "tap ok: $x,$y") else Log.w(TAG, "tap failed: $x,$y")
            ok
        }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean =
        withContext(Dispatchers.IO) {
            val ok = api.swipe(x1, y1, x2, y2, durationMs) == 0
            if (ok) {
                Log.d(TAG, "swipe ok: $x1,$y1 -> $x2,$y2")
            } else {
                Log.w(TAG, "swipe failed: $x1,$y1 -> $x2,$y2")
            }
            ok
        }

    suspend fun pressBack(): Boolean =
        withContext(Dispatchers.IO) {
            val ok = api.pressBack() == 0
            if (ok) Log.d(TAG, "back ok") else Log.w(TAG, "back failed")
            ok
        }
}
