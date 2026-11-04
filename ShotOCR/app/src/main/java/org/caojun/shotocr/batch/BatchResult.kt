package org.caojun.shotocr.batch

import java.time.YearMonth

enum class BatchFailureReason {
    TAP_FAILED,
    DETAIL_MISSING,
    DETAIL_INCOMPLETE,
    SCREENSHOT_FAILED,
    OCR_OR_AI_FAILED,
    NOT_RECEIPT,
    SAVE_FAILED,
    RETURN_LIST_FAILED,
    UNKNOWN
}

data class BatchFailureItem(
    val index: Int,
    val summary: String,
    val reason: BatchFailureReason,
    val detail: String? = null
)

data class BatchResult(
    val target: YearMonth,
    val success: Int,
    val duplicate: Int,
    val income: Int,
    val skipped: Int,
    val failed: Int,
    val failures: List<BatchFailureItem>,
    val elapsedMs: Long,
    val message: String? = null
)

object BatchResultStore {
    @Volatile
    var last: BatchResult? = null
}
