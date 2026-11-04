package org.caojun.shizuku.automation

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 以 shell 身份执行固定的 UI 自动化操作：
 * uiautomator dump / screencap / input tap|swipe|keyevent。
 * 不接收任意命令字符串。
 */
class ShizukuAutomationService : IShizukuAutomation.Stub() {

    override fun dumpUi(out: ParcelFileDescriptor): Int {
        val started = SystemClock.elapsedRealtime()
        val path = "/data/local/tmp/shotocr_ui_${System.nanoTime()}.xml"
        val file = File(path)
        val code = runCommand(listOf("uiautomator", "dump", path))
        if (code != 0) {
            Log.w(TAG, "dumpUi failed: exit=$code elapsedMs=${SystemClock.elapsedRealtime() - started}")
            file.delete()
            return -1
        }
        return try {
            FileOutputStream(out.fileDescriptor).use { fos ->
                file.inputStream().use { it.copyTo(fos) }
            }
            val bytes = file.length().toInt()
            Log.d(TAG, "dumpUi ok: bytes=$bytes elapsedMs=${SystemClock.elapsedRealtime() - started}")
            bytes
        } catch (t: Throwable) {
            Log.w(TAG, "dumpUi copy failed", t)
            -1
        } finally {
            file.delete()
        }
    }

    override fun screenshot(out: ParcelFileDescriptor): Int {
        val started = SystemClock.elapsedRealtime()
        return try {
            val process = ProcessBuilder("sh", "-c", "screencap -p").start()
            val drain = thread(name = "screencap-stderr") {
                runCatching { process.errorStream.readBytes() }
            }
            FileOutputStream(out.fileDescriptor).use { fos ->
                process.inputStream.copyTo(fos)
            }
            val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
            }
            drain.join(500)
            val exit = if (finished) process.exitValue() else -1
            Log.d(TAG, "screenshot exit=$exit elapsedMs=${SystemClock.elapsedRealtime() - started}")
            exit
        } catch (t: Throwable) {
            Log.w(TAG, "screenshot failed", t)
            -1
        }
    }

    override fun tap(x: Int, y: Int): Int =
        runCommand(listOf("input", "tap", x.toString(), y.toString()))

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Int =
        runCommand(
            listOf(
                "input", "swipe",
                x1.toString(), y1.toString(), x2.toString(), y2.toString(),
                durationMs.toString()
            )
        )

    override fun pressBack(): Int = runCommand(listOf("input", "keyevent", "4"))

    private fun runCommand(command: List<String>): Int {
        val name = command.firstOrNull().orEmpty()
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val drain = thread(name = "automation-output") {
                runCatching { process.inputStream.readBytes() }
            }
            val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                Log.w(TAG, "command timeout: $name")
            }
            drain.join(500)
            val exit = if (finished) process.exitValue() else -1
            if (exit == 0) {
                Log.d(TAG, "command ok: $name")
            } else {
                Log.w(TAG, "command failed: $name exit=$exit")
            }
            exit
        } catch (t: Throwable) {
            Log.w(TAG, "command error: $name", t)
            -1
        }
    }

    companion object {
        private const val TAG = "ShizukuAutomationService"
        private const val TIMEOUT_MS = 20_000L
    }
}
