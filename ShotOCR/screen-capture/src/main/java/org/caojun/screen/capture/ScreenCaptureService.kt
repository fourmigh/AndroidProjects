package org.caojun.screen.capture

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * 运行在 Shizuku UserService 进程（shell/root 身份）中的固定操作：
 * 执行 `screencap -p` 并把 PNG 写入调用方提供的文件描述符。
 * 不接收任何命令字符串，避免暴露通用 shell 能力。
 */
class ScreenCaptureService : IShizukuScreen.Stub() {

    override fun captureToFile(png: ParcelFileDescriptor): Int {
        var process: Process? = null
        return try {
            val started = ProcessBuilder("sh", "-c", "screencap -p").start()
            process = started
            val stderr = StringBuilder()
            val stderrThread = thread(name = "screencap-stderr") {
                runCatching {
                    started.errorStream.bufferedReader().forEachLine {
                        if (stderr.length < MAX_STDERR) stderr.appendLine(it)
                    }
                }
            }

            FileOutputStream(png.fileDescriptor).use { out ->
                started.inputStream.copyTo(out)
            }

            val exitCode = started.waitFor()
            stderrThread.join()
            if (exitCode != 0) {
                Log.e(TAG, "screencap failed ($exitCode): $stderr")
            }
            exitCode
        } catch (t: Throwable) {
            Log.e(TAG, "capture failed", t)
            process?.destroy()
            -1
        }
    }

    companion object {
        private const val TAG = "ShizukuScreenService"
        private const val MAX_STDERR = 4096
    }
}
