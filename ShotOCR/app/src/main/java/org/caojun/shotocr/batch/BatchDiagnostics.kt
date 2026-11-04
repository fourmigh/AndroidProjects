package org.caojun.shotocr.batch

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 批量识别诊断日志。统一格式，便于从 logcat 直接检索失败阶段：
 * BATCH_DIAG|run=...|stage=...|key=value...
 *
 * 不记录商户名、金额、订单号或完整 UI 文本。
 */
object BatchDiagnostics {

    const val TAG = "ShotOCR/BatchDiag"

    @Volatile
    private var runId: String = "-"

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        val file = File(context.applicationContext.filesDir, "batch_diag.log")
        if (file.length() > MAX_FILE_BYTES) {
            runCatching { file.writeText("") }
        }
        logFile = file
    }

    fun startRun(source: String): String {
        val id = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        runId = id
        logFile?.let { file ->
            if (file.length() > MAX_FILE_BYTES) {
                runCatching { file.writeText("") }
            }
        }
        log("run_start", "source" to source)
        return id
    }

    fun currentRunId(): String = runId

    fun log(stage: String, vararg fields: Pair<String, Any?>) {
        val line = format(stage, fields)
        Log.i(TAG, line)
        writeFile(line)
    }

    /** 仅用于定位识别失败，记录完整 OCR 文本；长度单独限制。 */
    fun raw(stage: String, text: String, vararg fields: Pair<String, Any?>) {
        val line = format(stage, fields) + "|text=" + text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('|', '/')
            .take(MAX_RAW_TEXT_CHARS)
        Log.d(TAG, line)
        writeFile(line)
    }

    fun verbose(stage: String, vararg fields: Pair<String, Any?>) {
        val line = format(stage, fields)
        Log.d(TAG, line)
        writeFile(line)
    }

    fun warn(stage: String, vararg fields: Pair<String, Any?>) {
        val line = format(stage, fields)
        Log.w(TAG, line)
        writeFile(line)
    }

    fun error(stage: String, throwable: Throwable, vararg fields: Pair<String, Any?>) {
        val line = format(stage, fields) + "|error=" + safe(throwable)
        Log.e(TAG, line, throwable)
        writeFile(line)
    }

    private fun format(stage: String, fields: Array<out Pair<String, Any?>>): String {
        val builder = StringBuilder("BATCH_DIAG|time=")
            .append(SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date()))
            .append("|run=")
            .append(runId)
            .append("|stage=")
            .append(stage)
        fields.forEach { (key, value) ->
            builder.append('|').append(key).append('=').append(safe(value))
        }
        return builder.toString()
    }

    private fun writeFile(line: String) {
        val file = logFile ?: return
        synchronized(this) {
            runCatching {
                file.appendText(line + "\n")
                if (file.length() > MAX_FILE_BYTES) {
                    file.writeText("")
                }
            }
        }
    }

    private fun safe(value: Any?): String {
        if (value == null) return "null"
        return value.toString()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('|', '/')
            .take(MAX_VALUE_CHARS)
    }

    private const val MAX_VALUE_CHARS = 160
    private const val MAX_RAW_TEXT_CHARS = 4_000
    private const val MAX_FILE_BYTES = 512_000L
}
