package org.caojun.shotocr.batch

import android.os.SystemClock
import java.time.YearMonth

/**
 * 记录“下一步点击悬浮按钮要批量识别哪个月”。
 * 只在内存中保存，5 分钟内有效；批量服务启动时消费一次。
 */
object BatchModeController {

    private const val TIMEOUT_MS = 5 * 60_000L

    @Volatile
    private var target: YearMonth? = null

    @Volatile
    private var armedAt = 0L

    fun arm(yearMonth: YearMonth) {
        target = yearMonth
        armedAt = SystemClock.elapsedRealtime()
        BatchDiagnostics.log("mode_arm", "month" to yearMonth)
    }

    fun cancel() {
        val hadTarget = target != null
        target = null
        armedAt = 0L
        if (hadTarget) {
            BatchDiagnostics.log("mode_cancel")
        }
    }

    fun isArmed(): Boolean = target() != null

    fun target(): YearMonth? {
        val value = target ?: return null
        if (SystemClock.elapsedRealtime() - armedAt > TIMEOUT_MS) {
            BatchDiagnostics.warn("mode_expired", "month" to value)
            cancel()
            return null
        }
        return value
    }

    fun consume(): YearMonth? {
        val value = target()
        BatchDiagnostics.log("mode_consume", "month" to value)
        cancel()
        return value
    }
}
