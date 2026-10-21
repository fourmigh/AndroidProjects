package org.caojun.shizuku.autoreturn

/** 记录一次“去启动 Shizuku”的待返回状态，并做超时判断。 */
internal class PendingLaunchTracker(private val timeoutMs: Long) {

    private var launchedAt = 0L

    fun mark(now: Long) {
        launchedAt = now
    }

    /** 消费一次 pending；未标记或已超时返回 false。 */
    fun consume(now: Long): Boolean {
        val started = launchedAt
        if (started == 0L) return false
        launchedAt = 0L
        return now - started <= timeoutMs
    }

    fun clear() {
        launchedAt = 0L
    }
}
