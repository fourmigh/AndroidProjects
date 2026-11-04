package org.caojun.shotocr.batch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.floatingbutton.FloatingButtonController
import org.caojun.shizuku.automation.ShizukuUiAutomation
import org.caojun.shizuku.automation.ShizukuUiAutomationSession
import org.caojun.shotocr.MainActivity
import org.caojun.shotocr.R
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.parsePaymentDate
import org.caojun.shotocr.accounting.ui.BillListActivity
import org.caojun.shotocr.capture.applyCaptureMode
import org.caojun.shotocr.classification.classifier.CategoryClassifier
import org.caojun.shotocr.classification.repository.CategoryRuleRepository
import org.caojun.shotocr.llm.ReceiptData
import org.caojun.shotocr.ocr.OcrEngine
import org.caojun.shotocr.processing.ProcessStep
import org.caojun.shotocr.processing.ReceiptProcessor
import org.caojun.shotocr.util.ForegroundAppResolver

/**
 * 支付宝账单批量采集：Shizuku 提权驱动 UI，逐条打开详情、截图、识别并自动保存。
 * 不使用无障碍服务；只处理用户已选中的月份。
 */
class BatchCaptureService : LifecycleService() {

    companion object {
        private const val TAG = "ShotOCR/Batch"
        internal const val ACTION_STOP = "org.caojun.shotocr.action.STOP_BATCH"
        internal const val ACTION_PAUSE = "org.caojun.shotocr.action.PAUSE_BATCH"
        internal const val ACTION_RESUME = "org.caojun.shotocr.action.RESUME_BATCH"
        internal const val ACTION_START_ORDER = "org.caojun.shotocr.action.START_ORDER_BATCH"

        private const val CHANNEL_ID = "batch_capture_channel"
        private const val ID_FOREGROUND = 1009
        private const val ID_SUMMARY = 1010

        private const val MAX_ITEMS = 100
        private const val MAX_SCROLLS = 8
        private const val WAIT_TARGET_MS = 60_000L
        private const val WAIT_ORDER_TAB_MS = 15_000L
        private const val MAX_IMAGE_BYTES = 400_000
        private const val MAX_IMAGE_BYTES_HARD = 600_000
    }

    @Volatile
    private var stopRequested = false

    @Volatile
    private var paused = false

    private var overlayCurrent = 0
    private var overlaySuccess = 0
    private var overlayDuplicate = 0
    private var overlayIncome = 0
    private var overlaySkipped = 0
    private var overlayFailed = 0

    @Volatile
    private var orderWaiting = false

    @Volatile
    private var manualOrderStart = false

    private var started = false

    override fun onCreate() {
        super.onCreate()
        BatchDiagnostics.init(applicationContext)
        BatchDiagnostics.startRun("service_create")
        AccountingManager.init(applicationContext)
        CategoryRuleRepository.init(applicationContext)
        createNotificationChannel()
        startForeground(
            ID_FOREGROUND,
            buildNotification(getString(R.string.batch_capture_preparing))
        )
        BatchDiagnostics.log("service_create", "notificationId" to ID_FOREGROUND)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        BatchDiagnostics.log(
            "service_start",
            "action" to intent?.action,
            "startId" to startId,
            "started" to started
        )

        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                updateNotification(getString(R.string.batch_capture_stopping))
                updateControlState()
                if (!started) stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                if (!paused) {
                    paused = true
                    BatchDiagnostics.log("batch_pause", "current" to overlayCurrent)
                    updateNotification(getString(R.string.batch_capture_paused))
                    updateControlState()
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                if (paused) {
                    paused = false
                    BatchDiagnostics.log("batch_resume", "current" to overlayCurrent)
                    updateControlState()
                }
                return START_NOT_STICKY
            }
            ACTION_START_ORDER -> {
                if (orderWaiting) {
                    paused = false
                    manualOrderStart = true
                    BatchDiagnostics.log("order_filter_manual_start")
                    updateControlState()
                }
                return START_NOT_STICKY
            }
        }

        if (!started) {
            started = true
            startBatch()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        BatchModeController.cancel()
        BatchControlOverlayController.stop(applicationContext)
        showFloatingButton()
        BatchDiagnostics.log("service_destroy")
        super.onDestroy()
    }

    private fun startBatch() {
        val target = BatchModeController.consume()
        if (target == null) {
            BatchDiagnostics.warn("batch_start_aborted", "reason" to "NO_ARMED_TARGET")
            postResult(
                getString(R.string.batch_capture_notification_title),
                getString(R.string.batch_capture_expired)
            )
            stopSelfSafely()
            return
        }

        BatchDiagnostics.log("batch_start", "target" to target)
        if (!BatchControlOverlayController.start(applicationContext, 0, 0, 0, false)) {
            BatchDiagnostics.warn("batch_start_aborted", "reason" to "NO_OVERLAY")
            BatchModeController.cancel()
            stopSelfSafely()
            return
        }
        FloatingButtonController.hide(applicationContext)
        updateControlState()
        lifecycleScope.launch { runBatch(target) }
    }

    private suspend fun runBatchLegacy(target: YearMonth) {
        val runStarted = SystemClock.elapsedRealtime()
        BatchDiagnostics.log("run_begin", "target" to target)

        var success = 0
        var duplicate = 0
        var skipped = 0
        var failed = 0
        var failureMessage: String? = null

        val screenHeight = resources.displayMetrics.heightPixels

        try {
            val sessionStarted = SystemClock.elapsedRealtime()
            val bound = ShizukuUiAutomation(this).withSession { session ->
                BatchDiagnostics.log(
                    "shizuku_bind_end",
                    "elapsedMs" to (SystemClock.elapsedRealtime() - sessionStarted)
                )
                if (!waitForTarget(session, target)) {
                    failureMessage = if (stopRequested) {
                        getString(R.string.batch_capture_stopped)
                    } else {
                        getString(
                            R.string.batch_capture_month_not_found,
                            target.year,
                            target.monthValue
                        )
                    }
                    return@withSession
                }

                val processor = ReceiptProcessor(applicationContext)
                try {
                    val processed = mutableSetOf<String>()
                    var count = 0
                    var scrolls = 0

                    while (!stopRequested && count < MAX_ITEMS) {
                        val root = session.dumpUi()?.let(UiTreeParser::parse)
                        if (root == null) {
                            BatchDiagnostics.warn("loop_dump_failed", "index" to count)
                            break
                        }

                        val rows = AlipayBatchProfile.findRows(
                            root,
                            resources.displayMetrics.density
                        )
                        val next = rows.firstOrNull { it.signature !in processed }

                        if (next == null) {
                            if (scrolls >= MAX_SCROLLS) {
                                BatchDiagnostics.log(
                                    "loop_end",
                                    "reason" to "MAX_SCROLLS",
                                    "rows" to rows.size,
                                    "index" to count
                                )
                                break
                            }
                            val scroll = AlipayBatchProfile.findScrollable(root)
                            if (scroll == null) {
                                BatchDiagnostics.log(
                                    "loop_end",
                                    "reason" to "NO_SCROLLABLE",
                                    "rows" to rows.size,
                                    "index" to count
                                )
                                break
                            }
                            val x = scroll.centerX
                            val fromY = scroll.top + scroll.height * 3 / 4
                            val toY = scroll.top + scroll.height / 4
                            if (!session.swipe(x, fromY, x, toY, 500)) {
                                BatchDiagnostics.warn(
                                    "scroll_failed",
                                    "rows" to rows.size,
                                    "index" to count
                                )
                                break
                            }
                            scrolls++
                            delay(900)
                            continue
                        }

                        scrolls = 0
                        processed += next.signature
                        count++
                        BatchDiagnostics.log(
                            "row_begin",
                            "index" to count,
                            "rows" to rows.size,
                            "sig" to shortSignature(next.signature),
                            "scrolls" to scrolls
                        )
                        updateNotification(
                            getString(
                                R.string.batch_capture_progress,
                                target.toString(),
                                count,
                                success
                            )
                        )

                        if (!session.tap(next.bounds.centerX, next.bounds.centerY)) {
                            BatchDiagnostics.warn(
                                "row_tap_failed",
                                "index" to count,
                                "sig" to shortSignature(next.signature)
                            )
                            failed++
                            delay(300)
                            continue
                        }
                        BatchDiagnostics.verbose(
                            "row_tapped",
                            "index" to count,
                            "x" to next.bounds.centerX,
                            "y" to next.bounds.centerY
                        )
                        delay(900)

                        val detailRoot = waitForDetailRoot(session)
                        if (detailRoot == null) {
                            BatchDiagnostics.warn("detail_missing", "index" to count)
                            val current = session.dumpUi()?.let(UiTreeParser::parse)
                            if (current != null && !looksLikeBillList(current, screenHeight)) {
                                session.pressBack()
                                delay(600)
                            }
                            failed++
                            continue
                        }
                        BatchDiagnostics.log("detail_found", "index" to count)

                        FloatingButtonController.hide(applicationContext)
                        delay(250)
                        val bitmap = session.screenshot()
                        if (bitmap == null) {
                            BatchDiagnostics.warn("screenshot_failed", "index" to count)
                            returnFromDetail(session)
                            failed++
                            continue
                        }

                        val imageBytes = encodeScreenshotForStorage(bitmap)
                        BatchDiagnostics.log(
                            "screenshot_ok",
                            "index" to count,
                            "width" to bitmap.width,
                            "height" to bitmap.height,
                            "bytes" to imageBytes?.size
                        )
                        val capturedAt = System.currentTimeMillis()
                        returnFromDetail(session)

                        val notifier = BatchNotifier()
                        val processStarted = SystemClock.elapsedRealtime()
                        try {
                            withContext(Dispatchers.Default) {
                                processor.process(
                                    bitmap,
                                    capturedAt,
                                    null,
                                    notifier,
                                    allowMissingPaymentTime = true
                                )
                            }
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                        BatchDiagnostics.log(
                            "process_end",
                            "index" to count,
                            "elapsedMs" to (SystemClock.elapsedRealtime() - processStarted),
                            "hasData" to (notifier.confirmed != null),
                            "duplicate" to notifier.duplicate,
                            "notReceipt" to notifier.notReceipt,
                            "failureStep" to notifier.failureStep,
                            "hasPaymentTime" to (notifier.confirmed?.paymentTime != null),
                            "hasOrderNumber" to (notifier.confirmed?.orderNumber != null)
                        )

                        val data = notifier.confirmed
                        if (data == null) {
                            if (notifier.duplicate) duplicate++ else failed++
                        } else {
                            val date = parsePaymentDate(data.paymentTime)
                            if (date != null && YearMonth.from(date) != target) {
                                BatchDiagnostics.log(
                                    "row_skipped",
                                    "index" to count,
                                    "reason" to "OUT_OF_MONTH",
                                    "paymentMonth" to YearMonth.from(date)
                                )
                                skipped++
                            } else {
                                try {
                                    saveReceipt(
                                        data = data,
                                        fullText = notifier.fullText,
                                        sourceApp = notifier.sourceApp ?: data.sourceApp,
                                        imageBytes = imageBytes
                                    )
                                    BatchDiagnostics.log("row_saved", "index" to count)
                                    success++
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    BatchDiagnostics.error("save_failed", t, "index" to count)
                                    failed++
                                }
                            }
                        }

                        updateNotification(
                            getString(
                                R.string.batch_capture_progress,
                                target.toString(),
                                count,
                                success
                            )
                        )
                    }
                } finally {
                    processor.release()
                }
            }

            if (bound == null) {
                BatchDiagnostics.warn("shizuku_session_unavailable")
                failureMessage = getString(R.string.batch_capture_session_failed)
            } else {
                BatchDiagnostics.log(
                    "shizuku_session_end",
                    "elapsedMs" to (SystemClock.elapsedRealtime() - sessionStarted)
                )
            }
        } catch (t: Throwable) {
            BatchDiagnostics.error("run_failed", t)
            if (failureMessage == null) {
                failureMessage = t.message ?: t.javaClass.simpleName
            }
        } finally {
            BatchModeController.cancel()
            BatchControlOverlayController.stop(applicationContext)
            showFloatingButton()
            BatchDiagnostics.log(
                "run_end",
                "success" to success,
                "duplicate" to duplicate,
                "skipped" to skipped,
                "failed" to failed,
                "stopped" to stopRequested,
                "failure" to failureMessage,
                "elapsedMs" to (SystemClock.elapsedRealtime() - runStarted)
            )
            val message = failureMessage
            if (message != null) {
                postResult(getString(R.string.batch_capture_notification_title), message)
            } else {
                postResult(
                    getString(R.string.batch_capture_summary_title),
                    getString(R.string.batch_capture_summary, success, duplicate, 0, skipped, failed)
                )
            }
            stopSelfSafely()
        }
    }

    private suspend fun runBatch(target: YearMonth) {
        val runStarted = SystemClock.elapsedRealtime()
        BatchDiagnostics.log("run_begin", "target" to target, "mode" to "OCR")

        var success = 0
        var duplicate = 0
        var income = 0
        var skipped = 0
        var failed = 0
        var failureMessage: String? = null
        var completionMessage: String? = null
        var boundaryReached = false
        val failures = mutableListOf<BatchFailureItem>()
        fun recordFailure(
            index: Int,
            reason: BatchFailureReason,
            summary: String?,
            detail: String? = null
        ) {
            failures.add(
                BatchFailureItem(
                    index = index,
                    summary = summary.orEmpty()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(48),
                    reason = reason,
                    detail = detail
                )
            )
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        overlayCurrent = 0
        overlaySuccess = 0
        overlayDuplicate = 0
        overlayIncome = 0
        overlaySkipped = 0
        overlayFailed = 0
        orderWaiting = false
        manualOrderStart = false
        updateControlState()

        try {
            val sessionStarted = SystemClock.elapsedRealtime()
            val bound = ShizukuUiAutomation(this).withSession { session ->
                BatchDiagnostics.log(
                    "shizuku_bind_end",
                    "elapsedMs" to (SystemClock.elapsedRealtime() - sessionStarted)
                )

                val ocrEngine = OcrEngine(applicationContext)
                val analyzer = AlipayOcrScreenAnalyzer(ocrEngine)
                try {
                    if (!ensureOrderFilter(session, analyzer)) {
                        failureMessage = if (stopRequested) {
                            getString(R.string.batch_capture_stopped)
                        } else {
                            getString(R.string.batch_capture_order_filter_failed)
                        }
                        return@withSession
                    }

                    if (!selectTargetMonth(session, analyzer, target)) {
                        failureMessage = if (stopRequested) {
                            getString(R.string.batch_capture_stopped)
                        } else {
                            getString(
                                R.string.batch_capture_month_switch_failed,
                                target.year,
                                target.monthValue
                            )
                        }
                        return@withSession
                    }

                    val initial = waitForTargetOcr(session, analyzer, target)
                    if (initial == null) {
                        failureMessage = if (stopRequested) {
                            getString(R.string.batch_capture_stopped)
                        } else {
                            getString(
                                R.string.batch_capture_month_not_found,
                                target.year,
                                target.monthValue
                            )
                        }
                        return@withSession
                    }

                    val processor = ReceiptProcessor(applicationContext, ocrEngine)
                    try {
                        var listSnapshot: AlipayOcrSnapshot = initial
                        val processed = mutableSetOf<String>()
                        var count = 0
                        var scrolls = 0

                        while (!stopRequested && count < MAX_ITEMS) {
                            awaitResume()
                            if (stopRequested) break
                            val next = listSnapshot.rows.firstOrNull {
                                it.signature !in processed &&
                                    !AlipayBatchProfile.isSummaryRow(it.text)
                            }
                            if (next == null) {
                                if (scrolls >= MAX_SCROLLS) {
                                    BatchDiagnostics.log(
                                        "loop_end",
                                        "reason" to "MAX_SCROLLS",
                                        "rows" to listSnapshot.rows.size,
                                        "index" to count
                                    )
                                    completionMessage = getString(
                                        R.string.batch_capture_list_complete,
                                        target.monthValue
                                    )
                                    break
                                }
                                if (!scrollList(session, screenWidth, screenHeight)) {
                                    BatchDiagnostics.warn("scroll_failed", "index" to count)
                                    break
                                }
                                delay(900)
                                val refreshed = captureListSnapshot(session, analyzer)
                                if (refreshed == null) {
                                    BatchDiagnostics.warn("list_refresh_failed", "index" to count)
                                    break
                                }
                                listSnapshot = refreshed
                                scrolls++
                                continue
                            }

                            scrolls = 0
                            processed += next.signature
                            count++
                            BatchDiagnostics.log(
                                "row_begin",
                                "index" to count,
                                "rows" to listSnapshot.rows.size,
                                "sig" to shortSignature(next.signature),
                                "summary" to AlipayBatchProfile.isSummaryRow(next.text),
                                "text" to next.text
                            )
                            if (IncomeFilter.isIncome(next.text)) {
                                BatchDiagnostics.log(
                                    "row_income",
                                    "index" to count,
                                    "stage" to "LIST"
                                )
                                income++
                                publishOverlay(success, duplicate, income, skipped, failed)
                                updateNotification(
                                    getString(
                                        R.string.batch_capture_progress,
                                        target.toString(),
                                        count,
                                        success
                                    )
                                )
                                continue
                            }

                            updateNotification(
                                getString(
                                    R.string.batch_capture_progress,
                                    target.toString(),
                                    count,
                                    success
                                )
                            )

                            if (!session.tap(next.bounds.centerX, next.bounds.centerY)) {
                                BatchDiagnostics.warn(
                                    "row_tap_failed",
                                    "index" to count,
                                    "sig" to shortSignature(next.signature)
                                )
                                recordFailure(count, BatchFailureReason.TAP_FAILED, next.text)
                                failed++
                                publishOverlay(success, duplicate, income, skipped, failed)
                                delay(300)
                                continue
                            }
                            delay(900)

                            val detailScreen = captureStableDetail(session, analyzer, count)
                            if (detailScreen != null &&
                                AlipayBatchProfile.isSummaryPage(detailScreen.snapshot.rawText)
                            ) {
                                // 月度小结是列表末尾的统计入口，不是账单，也不应计入任何统计。
                                BatchDiagnostics.log(
                                    "batch_end_summary",
                                    "index" to count,
                                    "target" to target
                                )
                                detailScreen.bitmap.recycle()
                                boundaryReached = true
                                completionMessage = getString(
                                    R.string.batch_capture_reached_summary,
                                    target.monthValue
                                )
                                break
                            }
                            if (detailScreen == null || !detailScreen.snapshot.isDetail) {
                                BatchDiagnostics.warn("detail_missing", "index" to count)
                                recordFailure(
                                    count,
                                    BatchFailureReason.DETAIL_MISSING,
                                    next.text
                                )
                                detailScreen?.bitmap?.recycle()
                                failed++
                                publishOverlay(success, duplicate, income, skipped, failed)
                                val refreshed = returnToListOcr(session, analyzer, target)
                                if (refreshed != null) listSnapshot = refreshed
                                continue
                            }
                            BatchDiagnostics.log("detail_found", "index" to count)

                            if (IncomeFilter.isIncome(detailScreen.snapshot.rawText)) {
                                BatchDiagnostics.log(
                                    "row_income",
                                    "index" to count,
                                    "stage" to "DETAIL"
                                )
                                detailScreen.bitmap.recycle()
                                income++
                                publishOverlay(success, duplicate, income, skipped, failed)
                                val refreshed = returnToListOcr(session, analyzer, target)
                                if (refreshed != null) listSnapshot = refreshed
                                continue
                            }

                            val storedBitmap = captureCleanScreenshot(session)
                                ?: detailScreen.bitmap
                            val imageBytes = encodeScreenshotForStorage(storedBitmap)
                            if (storedBitmap !== detailScreen.bitmap &&
                                !storedBitmap.isRecycled
                            ) {
                                storedBitmap.recycle()
                            }
                            BatchDiagnostics.log(
                                "screenshot_ok",
                                "index" to count,
                                "width" to detailScreen.bitmap.width,
                                "height" to detailScreen.bitmap.height,
                                "bytes" to imageBytes?.size
                            )
                            val capturedAt = System.currentTimeMillis()
                            val refreshed = returnToListOcr(session, analyzer, target)
                            if (refreshed == null) {
                                BatchDiagnostics.warn("return_list_failed", "index" to count)
                            } else {
                                listSnapshot = refreshed
                            }

                            val notifier = BatchNotifier()
                            val processStarted = SystemClock.elapsedRealtime()
                            try {
                                withContext(Dispatchers.Default) {
                                    processor.process(
                                        detailScreen.bitmap,
                                        capturedAt,
                                        null,
                                        notifier,
                                        precomputedText = detailScreen.snapshot.rawText,
                                        allowMissingPaymentTime = true
                                    )
                                }
                            } finally {
                                if (!detailScreen.bitmap.isRecycled) {
                                    detailScreen.bitmap.recycle()
                                }
                            }
                            val signals = AlipayOcrAnalyzer.signals(detailScreen.snapshot.rawText)
                            BatchDiagnostics.log(
                                "process_end",
                                "index" to count,
                                "elapsedMs" to (SystemClock.elapsedRealtime() - processStarted),
                                "hasData" to (notifier.confirmed != null),
                                "duplicate" to notifier.duplicate,
                                "notReceipt" to notifier.notReceipt,
                                "failureStep" to notifier.failureStep,
                                "hasPaymentTime" to (notifier.confirmed?.paymentTime != null),
                                "hasOrderNumber" to (notifier.confirmed?.orderNumber != null),
                                "ocrChars" to signals.chars,
                                "ocrAmount" to signals.amount,
                                "ocrTitle" to signals.title,
                                "ocrPayment" to signals.payment,
                                "ocrOrder" to signals.order,
                                "ocrTime" to signals.time
                            )

                            val data = notifier.confirmed
                            if (data == null) {
                                if (notifier.duplicate) {
                                    duplicate++
                                } else {
                                    BatchDiagnostics.raw(
                                        "failure_raw",
                                        detailScreen.snapshot.rawText,
                                        "index" to count,
                                        "notReceipt" to notifier.notReceipt,
                                        "failureStep" to notifier.failureStep
                                    )
                                    saveFailureImage(count, imageBytes)
                                    val reason = when {
                                        notifier.notReceipt &&
                                            (!signals.amount ||
                                                !signals.title ||
                                                (!signals.payment && !signals.time)) ->
                                            BatchFailureReason.DETAIL_INCOMPLETE

                                        notifier.notReceipt -> BatchFailureReason.NOT_RECEIPT
                                        notifier.failureStep != null ->
                                            BatchFailureReason.OCR_OR_AI_FAILED

                                        else -> BatchFailureReason.UNKNOWN
                                    }
                                    recordFailure(
                                        count,
                                        reason,
                                        next.text,
                                        notifier.failureStep?.name
                                    )
                                    failed++
                                }
                            } else {
                                val date = parsePaymentDate(data.paymentTime)
                                val paymentMonth = date?.let(YearMonth::from)
                                if (paymentMonth != null && paymentMonth != target) {
                                    val older = paymentMonth.isBefore(target)
                                    BatchDiagnostics.log(
                                        "row_skipped",
                                        "index" to count,
                                        "reason" to if (older) "OLDER_MONTH" else "OUT_OF_MONTH",
                                        "paymentMonth" to paymentMonth
                                    )
                                    skipped++
                                    if (older) {
                                        boundaryReached = true
                                        completionMessage = getString(
                                            R.string.batch_capture_reached_older_month,
                                            paymentMonth.year,
                                            paymentMonth.monthValue
                                        )
                                    }
                                } else {
                                    try {
                                        saveReceipt(
                                            data = data,
                                            fullText = notifier.fullText,
                                            sourceApp = notifier.sourceApp ?: data.sourceApp,
                                            imageBytes = imageBytes
                                        )
                                        BatchDiagnostics.log("row_saved", "index" to count)
                                        success++
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (t: Throwable) {
                                        BatchDiagnostics.error("save_failed", t, "index" to count)
                                        recordFailure(
                                            count,
                                            BatchFailureReason.SAVE_FAILED,
                                            next.text,
                                            t.message
                                        )
                                        failed++
                                    }
                                }
                            }

                            publishOverlay(success, duplicate, income, skipped, failed)
                            updateNotification(
                                getString(
                                    R.string.batch_capture_progress,
                                    target.toString(),
                                    count,
                                    success
                                )
                            )
                            if (boundaryReached || refreshed == null) break
                        }
                    } finally {
                        processor.release()
                    }
                } finally {
                    ocrEngine.release()
                }
            }

            if (bound == null) {
                BatchDiagnostics.warn("shizuku_session_unavailable")
                failureMessage = getString(R.string.batch_capture_session_failed)
            } else {
                BatchDiagnostics.log(
                    "shizuku_session_end",
                    "elapsedMs" to (SystemClock.elapsedRealtime() - sessionStarted)
                )
            }
        } catch (t: Throwable) {
            BatchDiagnostics.error("run_failed", t)
            if (failureMessage == null) {
                failureMessage = t.message ?: t.javaClass.simpleName
            }
        } finally {
            BatchModeController.cancel()
            BatchControlOverlayController.stop(applicationContext)
            showFloatingButton()
            val elapsedMs = SystemClock.elapsedRealtime() - runStarted
            val finalMessage = failureMessage ?: completionMessage
            val processed = success + duplicate + income + skipped + failed
            BatchResultStore.last = BatchResult(
                target = target,
                success = success,
                duplicate = duplicate,
                income = income,
                skipped = skipped,
                failed = failed,
                failures = failures.toList(),
                elapsedMs = elapsedMs,
                message = finalMessage
            )
            BatchDiagnostics.log(
                "run_end",
                "processed" to processed,
                "success" to success,
                "duplicate" to duplicate,
                "income" to income,
                "skipped" to skipped,
                "failed" to failed,
                "stopped" to stopRequested,
                "failure" to failureMessage,
                "completion" to completionMessage,
                "elapsedMs" to elapsedMs
            )
            if (processed != overlayCurrent) {
                BatchDiagnostics.warn(
                    "run_count_mismatch",
                    "processed" to processed,
                    "overlayCurrent" to overlayCurrent
                )
            }
            if (failureMessage != null) {
                postResult(
                    getString(R.string.batch_capture_notification_title),
                    failureMessage,
                    openResult = true
                )
            } else {
                val summary = getString(
                    R.string.batch_capture_summary,
                    success,
                    duplicate,
                    income,
                    skipped,
                    failed
                )
                postResult(
                    getString(R.string.batch_capture_summary_title),
                    completionMessage?.let { "$summary\n$it" } ?: summary,
                    openResult = true
                )
            }
            if (!stopRequested) {
                returnToApp()
            }
            stopSelfSafely()
        }
    }

    private data class CapturedScreen(
        val bitmap: Bitmap,
        val snapshot: AlipayOcrSnapshot
    )

    /** 详情页 XRiver 加载较慢，等待关键字段稳定后再截图识别。 */
    private suspend fun captureStableDetail(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer,
        index: Int
    ): CapturedScreen? {
        var last: CapturedScreen? = null
        repeat(4) {
            val screen = captureScreen(session, analyzer)
            if (screen == null) {
                delay(600)
                return@repeat
            }
            last?.bitmap?.takeIf { it !== screen.bitmap }?.recycle()
            last = screen
            val signals = AlipayOcrAnalyzer.signals(screen.snapshot.rawText)
            if (screen.snapshot.isDetail &&
                signals.amount &&
                signals.title &&
                (signals.payment || signals.time)
            ) {
                BatchDiagnostics.log(
                    "detail_ready",
                    "chars" to signals.chars,
                    "payment" to signals.payment,
                    "time" to signals.time
                )
                return screen
            }
            delay(700)
        }
        last?.let { screen ->
            val signals = AlipayOcrAnalyzer.signals(screen.snapshot.rawText)
            BatchDiagnostics.raw(
                "detail_unstable",
                screen.snapshot.rawText,
                "index" to index,
                "chars" to signals.chars,
                "amount" to signals.amount,
                "title" to signals.title,
                "payment" to signals.payment,
                "time" to signals.time,
                "isDetail" to screen.snapshot.isDetail
            )
        }
        return last
    }

    private suspend fun captureCleanScreenshot(
        session: ShizukuUiAutomationSession
    ): Bitmap? {
        BatchControlOverlayController.hideForCapture(applicationContext)
        delay(150)
        return try {
            session.screenshot()
        } finally {
            BatchControlOverlayController.showAfterCapture(applicationContext)
        }
    }

    private suspend fun captureScreen(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer
    ): CapturedScreen? {
        val bitmap = session.screenshot()
        if (bitmap == null) {
            BatchDiagnostics.warn("screenshot_failed")
            return null
        }
        return try {
            CapturedScreen(bitmap, analyzer.analyze(bitmap))
        } catch (t: Throwable) {
            bitmap.recycle()
            BatchDiagnostics.error("ocr_failed", t)
            null
        }
    }

    private suspend fun captureListSnapshot(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer
    ): AlipayOcrSnapshot? {
        val captured = captureScreen(session, analyzer) ?: return null
        captured.bitmap.recycle()
        return captured.snapshot
    }

    /**
     * 自动切到支付宝账单的“订单”标签；找不到时提示用户手动切换并等待。
     */
    private suspend fun ensureOrderFilter(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer
    ): Boolean {
        var lastTapAt = 0L
        var lastStateKey: String? = null
        var lastStateLogAt = 0L
        var searchTapped = false
        var fixedOrderTapped = false
        var fixedSearchTapped = false
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        navigateToMineBill(session, analyzer)
        if (stopRequested) return false
        val autoDeadline = SystemClock.elapsedRealtime() + WAIT_ORDER_TAB_MS

        fun logState(snapshot: AlipayOcrSnapshot, now: Long) {
            val key = "${snapshot.orderTab != null}|${snapshot.orderTabSelected}|" +
                "${snapshot.rows.size}|${snapshot.textCount}"
            if (key == lastStateKey && now - lastStateLogAt < 5_000L) return
            lastStateKey = key
            lastStateLogAt = now
            BatchDiagnostics.log(
                "order_filter_state",
                "tab" to (snapshot.orderTab != null),
                "selected" to snapshot.orderTabSelected,
                "rows" to snapshot.rows.size,
                "texts" to snapshot.textCount
            )
        }

        while (!stopRequested && SystemClock.elapsedRealtime() < autoDeadline) {
            awaitResume()
            if (stopRequested) return false
            if (!isAlipayForeground()) {
                delay(1000)
                continue
            }
            val captured = captureScreen(session, analyzer)
            if (captured == null) {
                delay(800)
                continue
            }
            val snapshot = captured.snapshot
            captured.bitmap.recycle()
            val now = SystemClock.elapsedRealtime()
            logState(snapshot, now)
            if (snapshot.orderTabSelected) {
                BatchDiagnostics.log("order_filter_ready", "mode" to "AUTO")
                return true
            }
            val tab = snapshot.orderTab
            if (tab != null && now - lastTapAt >= 1_200L) {
                lastTapAt = now
                BatchDiagnostics.log(
                    "order_filter_tap",
                    "x" to tab.centerX,
                    "y" to tab.centerY
                )
                session.tap(tab.centerX, tab.centerY)
                delay(1_000)
            } else if (isOrderFilterPage(snapshot.rawText) && !fixedOrderTapped) {
                fixedOrderTapped = true
                val x = (width * 0.73f).toInt()
                val y = (height * 0.14f).toInt()
                BatchDiagnostics.log("order_filter_tap_fixed", "x" to x, "y" to y)
                session.tap(x, y)
                delay(1_000)
                BatchDiagnostics.log("order_filter_ready", "mode" to "FIXED_TAP")
                return true
            } else if (isBillMainPage(snapshot.rawText) && !fixedSearchTapped) {
                fixedSearchTapped = true
                val x = (width * 0.35f).toInt()
                val y = (height * 0.06f).toInt()
                BatchDiagnostics.log("order_filter_open_search_fixed", "x" to x, "y" to y)
                session.tap(x, y)
                delay(1_500)
            } else if (!searchTapped && !snapshot.isDetail) {
                val search = snapshot.searchEntry
                if (search != null) {
                    searchTapped = true
                    BatchDiagnostics.log(
                        "order_filter_open_search",
                        "x" to search.centerX,
                        "y" to search.centerY
                    )
                    session.tap(search.centerX, search.centerY)
                    delay(1_500)
                } else {
                    delay(800)
                }
            } else {
                delay(800)
            }
        }

        if (stopRequested) return false
        BatchDiagnostics.warn("order_filter_prompt")
        orderWaiting = true
        manualOrderStart = false
        updateControlState()
        updateNotification(getString(R.string.batch_capture_wait_order))
        Toast.makeText(
            applicationContext,
            R.string.batch_capture_switch_order,
            Toast.LENGTH_LONG
        ).show()

        while (!stopRequested) {
            awaitResume()
            if (stopRequested) break
            if (manualOrderStart) {
                BatchDiagnostics.log("order_filter_ready", "mode" to "MANUAL_START")
                orderWaiting = false
                updateControlState()
                return true
            }
            if (!isAlipayForeground()) {
                delay(1000)
                continue
            }
            val captured = captureScreen(session, analyzer)
            if (captured == null) {
                delay(800)
                continue
            }
            val snapshot = captured.snapshot
            captured.bitmap.recycle()
            logState(snapshot, SystemClock.elapsedRealtime())
            if (snapshot.orderTabSelected) {
                BatchDiagnostics.log("order_filter_ready", "mode" to "MANUAL")
                orderWaiting = false
                updateControlState()
                return true
            }
            delay(1000)
        }

        orderWaiting = false
        BatchDiagnostics.warn("order_filter_failed", "reason" to "STOPPED")
        return false
    }

    /**
     * 从支付宝任意页面导航到“我的 -> 账单”；订单分类页由该入口提供。
     */
    private suspend fun navigateToMineBill(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        var backCount = 0
        var homeTapCount = 0
        var billTapCount = 0
        var billEntered = false
        var billSearchOpened = false

        while (!stopRequested && SystemClock.elapsedRealtime() < deadline) {
            awaitResume()
            if (stopRequested) return false
            if (!isAlipayForeground()) {
                delay(800)
                continue
            }
            val captured = captureScreen(session, analyzer)
            if (captured == null) {
                delay(800)
                continue
            }
            val snapshot = captured.snapshot
            captured.bitmap.recycle()

            if (snapshot.orderTab != null) {
                BatchDiagnostics.log("order_nav_ready", "reason" to "ORDER_TAB")
                return true
            }

            val text = snapshot.rawText
            if (isOrderFilterPage(text)) {
                BatchDiagnostics.log("order_nav_ready", "reason" to "FILTER_PAGE")
                return true
            }
            BatchDiagnostics.log(
                "order_nav_state",
                "mineTab" to (snapshot.mineTab != null),
                "billEntry" to (snapshot.billEntry != null),
                "billMain" to isBillMainPage(text),
                "minePage" to isMinePage(text)
            )
            if ((snapshot.isDetail ||
                    text.contains("支付时间") ||
                    text.contains("交易详情")) &&
                backCount < 3
            ) {
                backCount++
                BatchDiagnostics.log("order_nav_detail_back", "back" to backCount)
                session.pressBack()
                delay(1_000)
                continue
            }

            if ((text.contains("点击领取") || text.contains("恭喜你获得")) &&
                backCount < 2
            ) {
                backCount++
                BatchDiagnostics.log("order_nav_dismiss_popup", "back" to backCount)
                session.pressBack()
                delay(800)
                continue
            }

            if (isMinePage(text)) {
                val bill = snapshot.billEntry
                if (bill != null) {
                    BatchDiagnostics.log(
                        "order_nav_bill",
                        "x" to bill.centerX,
                        "y" to bill.centerY
                    )
                    session.tap(bill.centerX, bill.centerY)
                    billEntered = true
                    delay(1_500)
                    continue
                }
                if (billTapCount < 2) {
                    billTapCount++
                    val x = width / 5
                    val y = (height * 0.28f).toInt()
                    BatchDiagnostics.log("order_nav_bill_fallback", "x" to x, "y" to y)
                    session.tap(x, y)
                    billEntered = true
                    delay(1_500)
                    continue
                }
            } else {
                val mine = snapshot.mineTab
                if (mine != null) {
                    BatchDiagnostics.log(
                        "order_nav_mine",
                        "x" to mine.centerX,
                        "y" to mine.centerY
                    )
                    session.tap(mine.centerX, mine.centerY)
                    delay(1_500)
                    continue
                }
                if (isAlipayHomePage(text) && homeTapCount < 2) {
                    homeTapCount++
                    val x = width * 9 / 10
                    val y = height * 94 / 100
                    BatchDiagnostics.log("order_nav_mine_fallback", "x" to x, "y" to y)
                    session.tap(x, y)
                    delay(1_500)
                    continue
                }
            }

            if (isBillMainPage(text)) {
                if (billEntered && !billSearchOpened) {
                    billSearchOpened = true
                    val x = (width * 0.35f).toInt()
                    val y = (height * 0.06f).toInt()
                    BatchDiagnostics.log("order_nav_open_search", "x" to x, "y" to y)
                    session.tap(x, y)
                    delay(1_500)
                    continue
                }
                if (!billEntered && backCount < 2) {
                    backCount++
                    BatchDiagnostics.log("order_nav_back", "back" to backCount)
                    session.pressBack()
                    delay(1_200)
                    continue
                }
            }
            delay(800)
        }
        BatchDiagnostics.warn("order_nav_failed")
        return false
    }

    private fun isMinePage(text: String): Boolean =
        text.contains("总资产") && text.contains("账单") && text.contains("花呗")

    private fun isAlipayHomePage(text: String): Boolean =
        (text.contains("扫一扫") && text.contains("收付款")) ||
            (text.contains("为你推荐") && text.contains("我的") && text.contains("理财"))

    private fun isOrderFilterPage(text: String): Boolean =
        text.contains("退款") &&
            text.contains("转账") &&
            text.contains("筛选")

    private fun isBillMainPage(text: String): Boolean =
        text.contains("收支分析") &&
            !isOrderFilterPage(text) &&
            !text.contains("搜索交易记录") &&
            !text.contains("总资产")

    private suspend fun isAlipayForeground(): Boolean {
        val foreground = withContext(Dispatchers.IO) {
            ForegroundAppResolver.getForegroundPackage(
                applicationContext,
                System.currentTimeMillis()
            )
        }
        return foreground == null ||
            foreground == AlipayBatchProfile.ALIPAY_PACKAGE
    }

    private fun hasClass(node: UiNode, className: String): Boolean =
        node.className?.contains(className) == true ||
            node.children.any { hasClass(it, className) }

    private fun findClickableText(node: UiNode, text: String): UiNode? {
        if (node.text?.trim() == text &&
            node.bounds != null &&
            (node.clickable || node.className?.contains("Button") == true)
        ) {
            return node
        }
        for (child in node.children) {
            findClickableText(child, text)?.let { return it }
        }
        return null
    }

    private fun collectNumberPickers(
        node: UiNode,
        result: MutableList<UiNode> = mutableListOf()
    ): List<UiNode> {
        if (node.className?.contains("NumberPicker") == true) {
            result.add(node)
        }
        node.children.forEach { collectNumberPickers(it, result) }
        return result
    }

    private fun isYearPicker(node: UiNode): Boolean =
        node.allTexts().any { it.trim().matches(Regex("""\d{4}""")) }

    private fun findYearPicker(root: UiNode): UiNode? =
        collectNumberPickers(root).firstOrNull(::isYearPicker)

    private fun findSelectedYear(root: UiNode): Int? {
        val picker = findYearPicker(root) ?: return null
        findTextNode(picker) { node ->
            node.className?.contains("TextView") == true &&
                node.text?.trim()?.matches(Regex("""\d{4}""")) == true
        }?.text?.trim()?.toIntOrNull()?.let { return it }
        return null
    }

    private fun findTextNode(node: UiNode, predicate: (UiNode) -> Boolean): UiNode? {
        if (predicate(node)) return node
        for (child in node.children) {
            findTextNode(child, predicate)?.let { return it }
        }
        return null
    }

    private suspend fun selectYearByUi(
        session: ShizukuUiAutomationSession,
        targetYear: Int,
        currentYear: Int
    ): Boolean {
        var year = currentYear
        var swipeDown = targetYear < currentYear
        repeat(10) {
            val root = session.dumpUi()?.let(UiTreeParser::parse) ?: return false
            if (!hasClass(root, "DatePicker")) return false
            val selected = findSelectedYear(root) ?: year
            year = selected
            if (selected == targetYear) {
                BatchDiagnostics.log("target_year_selected", "year" to targetYear)
                return true
            }

            val targetNode = findClickableText(root, targetYear.toString())
            if (targetNode != null) {
                val bounds = targetNode.bounds ?: return false
                BatchDiagnostics.log(
                    "target_year_ui",
                    "year" to targetYear,
                    "x" to bounds.centerX,
                    "y" to bounds.centerY
                )
                session.tap(bounds.centerX, bounds.centerY)
                delay(350)
                return@repeat
            }

            val picker = findYearPicker(root) ?: return false
            val bounds = picker.bounds ?: return false
            val x = bounds.centerX
            val centerY = bounds.centerY
            val span = (bounds.height * 0.15f).toInt().coerceIn(80, 240)
            val fromY = if (swipeDown) centerY - span else centerY + span
            val toY = if (swipeDown) centerY + span else centerY - span
            BatchDiagnostics.log(
                "target_year_swipe",
                "target" to targetYear,
                "current" to selected,
                "down" to swipeDown,
                "x" to x,
                "fromY" to fromY,
                "toY" to toY
            )
            if (!session.swipe(x, fromY, x, toY, 300)) return false
            delay(450)
            val nextRoot = session.dumpUi()?.let(UiTreeParser::parse)
            val nextYear = nextRoot?.let(::findSelectedYear) ?: selected
            if (nextYear == selected ||
                abs(targetYear - nextYear) >= abs(targetYear - selected)
            ) {
                swipeDown = !swipeDown
            }
        }
        BatchDiagnostics.warn(
            "target_year_switch_failed",
            "target" to targetYear,
            "current" to year
        )
        return false
    }

    private suspend fun selectMonthByUi(
        session: ShizukuUiAutomationSession,
        target: YearMonth,
        width: Int,
        height: Int,
        currentMonth: YearMonth?
    ): Boolean {
        var root = session.dumpUi()?.let(UiTreeParser::parse) ?: return false
        if (!hasClass(root, "DatePicker")) return false
        val currentYear = currentMonth?.year ?: findSelectedYear(root) ?: return false
        if (currentYear != target.year && !selectYearByUi(session, target.year, currentYear)) {
            return false
        }

        root = session.dumpUi()?.let(UiTreeParser::parse) ?: return false
        if (!hasClass(root, "DatePicker")) return false
        val monthNode = findClickableText(root, target.monthValue.toString())
        if (monthNode != null) {
            val bounds = monthNode.bounds ?: return false
            BatchDiagnostics.log(
                "target_month_ui",
                "target" to target,
                "x" to bounds.centerX,
                "y" to bounds.centerY,
                "class" to monthNode.className
            )
            if (!session.tap(bounds.centerX, bounds.centerY)) return false
            delay(350)
        }
        val confirmX = width / 2
        val confirmY = (height * 0.93f).toInt()
        BatchDiagnostics.log("target_month_ui_confirm", "x" to confirmX, "y" to confirmY)
        session.tap(confirmX, confirmY)
        return true
    }

    /**
     * 在“订单”列表中点开月份选择器，切到目标月份并确认。
     * 当前月份已经是目标月份时不执行任何点击。
     */
    private suspend fun selectTargetMonth(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer,
        target: YearMonth
    ): Boolean {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        repeat(3) { attempt ->
            if (stopRequested) return false
            val before = captureScreen(session, analyzer)
            if (before == null) {
                delay(700)
                return@repeat
            }
            val beforeSnapshot = before.snapshot
            before.bitmap.recycle()
            if (beforeSnapshot.month == target) {
                BatchDiagnostics.log(
                    "target_month_switch",
                    "attempt" to (attempt + 1),
                    "result" to "ALREADY_TARGET",
                    "target" to target
                )
                return true
            }

            val openedSnapshot: AlipayOcrSnapshot
            if (beforeSnapshot.monthPicker != null) {
                openedSnapshot = beforeSnapshot
            } else {
                val anchor = beforeSnapshot.monthAnchor
                val x = anchor?.centerX ?: (width * 0.14f).toInt()
                val y = anchor?.centerY ?: (height * 0.20f).toInt()
                BatchDiagnostics.log(
                    "target_month_open",
                    "attempt" to (attempt + 1),
                    "x" to x,
                    "y" to y,
                    "month" to beforeSnapshot.month
                )
                if (!session.tap(x, y)) {
                    delay(700)
                    return@repeat
                }
                delay(800)
                val opened = captureScreen(session, analyzer) ?: run {
                    delay(700)
                    return@repeat
                }
                openedSnapshot = opened.snapshot
                opened.bitmap.recycle()
            }

            if (selectMonthByUi(session, target, width, height, beforeSnapshot.month)) {
                delay(900)
                val verified = captureScreen(session, analyzer)
                if (verified != null) {
                    val verifiedSnapshot = verified.snapshot
                    verified.bitmap.recycle()
                    if (verifiedSnapshot.month == target) {
                        BatchDiagnostics.log(
                            "target_month_switch",
                            "attempt" to (attempt + 1),
                            "result" to "OK_BY_UI",
                            "target" to target
                        )
                        return true
                    }
                }
            }

            val picker = openedSnapshot.monthPicker
            if (picker == null) {
                BatchDiagnostics.raw(
                    "target_month_picker_raw",
                    openedSnapshot.rawText,
                    "attempt" to (attempt + 1)
                )
                BatchDiagnostics.warn(
                    "target_month_switch_failed",
                    "attempt" to (attempt + 1),
                    "reason" to "PICKER_NOT_FOUND"
                )
                delay(700)
                return@repeat
            }

            val originalMonth = beforeSnapshot.month
            if (originalMonth != null && originalMonth.year != target.year) {
                val yearBox = picker.years
                    .filter { it.text.trim() == target.year.toString() }
                    .minByOrNull { abs(it.centerX - width * 0.38f) }
                if (yearBox != null) {
                    BatchDiagnostics.log(
                        "target_month_year",
                        "year" to target.year,
                        "x" to yearBox.centerX,
                        "y" to yearBox.centerY
                    )
                    session.tap(yearBox.centerX, yearBox.centerY)
                    delay(500)
                }
            }

            val monthBox = picker.months
                .filter { it.text.trim().toIntOrNull() == target.monthValue }
                .minByOrNull { abs(it.centerX - width * 0.63f) }
            if (monthBox == null) {
                BatchDiagnostics.warn(
                    "target_month_switch_failed",
                    "attempt" to (attempt + 1),
                    "reason" to "TARGET_MONTH_NOT_FOUND",
                    "target" to target
                )
                delay(700)
                return@repeat
            }
            BatchDiagnostics.log(
                "target_month_click",
                "target" to target,
                "x" to monthBox.centerX,
                "y" to monthBox.centerY
            )
            session.tap(monthBox.centerX, monthBox.centerY)
            delay(500)
            val confirm = picker.confirm
            if (confirm != null) {
                BatchDiagnostics.log(
                    "target_month_confirm",
                    "x" to confirm.centerX,
                    "y" to confirm.centerY
                )
                session.tap(confirm.centerX, confirm.centerY)
            } else {
                val fallbackX = width / 2
                val fallbackY = (height * 0.93f).toInt()
                BatchDiagnostics.warn(
                    "target_month_confirm_fallback",
                    "x" to fallbackX,
                    "y" to fallbackY
                )
                session.tap(fallbackX, fallbackY)
            }
            delay(900)

            val verified = captureScreen(session, analyzer) ?: return@repeat
            val verifiedSnapshot = verified.snapshot
            verified.bitmap.recycle()
            if (verifiedSnapshot.month == target) {
                BatchDiagnostics.log(
                    "target_month_switch",
                    "attempt" to (attempt + 1),
                    "result" to "OK",
                    "target" to target
                )
                return true
            }
            BatchDiagnostics.warn(
                "target_month_switch_failed",
                "attempt" to (attempt + 1),
                "reason" to "MONTH_MISMATCH",
                "target" to target,
                "month" to verifiedSnapshot.month
            )
            delay(700)
        }
        BatchDiagnostics.warn("target_month_switch_failed", "reason" to "ATTEMPTS_EXHAUSTED")
        return false
    }

    private suspend fun waitForTargetOcr(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer,
        target: YearMonth
    ): AlipayOcrSnapshot? {
        val deadline = SystemClock.elapsedRealtime() + WAIT_TARGET_MS
        var hint: String? = null
        var lastKey: String? = null
        var lastLogAt = 0L
        var lastReason = "NOT_CHECKED"
        var lastSnapshot: AlipayOcrSnapshot? = null

        fun shouldLog(key: String, now: Long): Boolean =
            key != lastKey || now - lastLogAt >= 5_000L

        while (!stopRequested && SystemClock.elapsedRealtime() < deadline) {
            awaitResume()
            if (stopRequested) break
            val foreground = withContext(Dispatchers.IO) {
                ForegroundAppResolver.getForegroundPackage(
                    applicationContext,
                    System.currentTimeMillis()
                )
            }
            if (foreground != null &&
                foreground != AlipayBatchProfile.ALIPAY_PACKAGE
            ) {
                val now = SystemClock.elapsedRealtime()
                if (shouldLog("FOREGROUND_NOT_ALIPAY", now)) {
                    BatchDiagnostics.log(
                        "wait",
                        "reason" to "FOREGROUND_NOT_ALIPAY",
                        "target" to target,
                        "pkg" to foreground
                    )
                    lastKey = "FOREGROUND_NOT_ALIPAY"
                    lastLogAt = now
                    lastReason = "FOREGROUND_NOT_ALIPAY"
                }
                if (hint != "app") {
                    hint = "app"
                    updateNotification(getString(R.string.batch_capture_wait_app))
                }
                delay(1000)
                continue
            }

            val captured = captureScreen(session, analyzer)
            if (captured == null) {
                val now = SystemClock.elapsedRealtime()
                if (shouldLog("SCREENSHOT_FAILED", now)) {
                    BatchDiagnostics.warn(
                        "wait",
                        "reason" to "SCREENSHOT_FAILED",
                        "target" to target
                    )
                    lastKey = "SCREENSHOT_FAILED"
                    lastLogAt = now
                    lastReason = "SCREENSHOT_FAILED"
                }
                delay(1000)
                continue
            }
            val snapshot = captured.snapshot
            captured.bitmap.recycle()

            val reason = when {
                snapshot.month == null -> "MONTH_MISSING"
                snapshot.month != target -> "MONTH_MISMATCH"
                snapshot.rows.isEmpty() -> "ROWS_MISSING"
                else -> "READY"
            }
            val now = SystemClock.elapsedRealtime()
            val key = "$reason|${snapshot.month}|${snapshot.rows.size}|${snapshot.textCount}"
            if (shouldLog(key, now)) {
                BatchDiagnostics.log(
                    "wait",
                    "reason" to reason,
                    "target" to target,
                    "month" to snapshot.month,
                    "monthText" to snapshot.monthText,
                    "monthLikeTexts" to snapshot.monthLikeTextCount,
                    "rows" to snapshot.rows.size,
                    "texts" to snapshot.textCount,
                    "detail" to snapshot.isDetail
                )
                lastKey = key
                lastLogAt = now
            }
            lastReason = reason
            lastSnapshot = snapshot

            if (reason == "READY") {
                BatchDiagnostics.log(
                    "target_ready",
                    "target" to target,
                    "month" to snapshot.month,
                    "rows" to snapshot.rows.size,
                    "texts" to snapshot.textCount
                )
                return snapshot
            }

            if (hint != "month") {
                hint = "month"
                updateNotification(
                    getString(
                        R.string.batch_capture_wait_month,
                        target.year,
                        target.monthValue
                    )
                )
            }
            delay(1000)
        }

        BatchDiagnostics.warn(
            "wait_end",
            "reason" to if (stopRequested) "STOPPED" else lastReason,
            "target" to target,
            "month" to lastSnapshot?.month,
            "monthText" to lastSnapshot?.monthText,
            "monthLikeTexts" to lastSnapshot?.monthLikeTextCount,
            "rows" to lastSnapshot?.rows?.size,
            "texts" to lastSnapshot?.textCount,
            "detail" to lastSnapshot?.isDetail
        )
        return null
    }

    /**
     * 用原详情截图做 OCR，避免“更多”弹层覆盖金额/商户；
     * “更多”后的截图只用于提取订单号 hint。
     */
    private suspend fun expandOrderInfoForHint(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer,
        detailScreen: CapturedScreen,
        index: Int
    ): String? {
        val visible = AlipayOcrAnalyzer.findOrderNumber(detailScreen.snapshot.rawText)
        if (visible != null) {
            BatchDiagnostics.log("order_hint", "index" to index, "source" to "DETAIL")
            return visible
        }

        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        var more = detailScreen.snapshot.moreTarget
        var moreScrolls = 0
        // 只滚动约两行文字的高度，避免把顶部商户名划出屏幕。
        val twoLineScroll = (screenHeight * 0.06f).toInt().coerceIn(80, 180)
        while (more == null || !isMoreTargetReachable(more, screenHeight)) {
            if (moreScrolls >= 2) {
                BatchDiagnostics.log(
                    "more_skip",
                    "index" to index,
                    "reason" to if (more == null) "NO_TARGET" else "TOO_LOW"
                )
                return null
            }
            moreScrolls++
            val fromY = (screenHeight * 0.70f).toInt()
            val toY = fromY - twoLineScroll
            BatchDiagnostics.log(
                "more_scroll",
                "index" to index,
                "attempt" to moreScrolls,
                "fromY" to fromY,
                "toY" to toY,
                "scrollPx" to twoLineScroll
            )
            if (!session.swipe(screenWidth / 2, fromY, screenWidth / 2, toY, 300)) {
                BatchDiagnostics.warn("more_scroll_failed", "index" to index)
                return null
            }
            delay(500)
            val refreshed = captureScreen(session, analyzer)
            if (refreshed == null) {
                BatchDiagnostics.warn("more_refresh_failed", "index" to index)
                return null
            }
            refreshed.bitmap.recycle()
            more = refreshed.snapshot.moreTarget
        }

        val target = more ?: return null
        BatchDiagnostics.log(
            "more_tap",
            "index" to index,
            "x" to target.bounds.centerX,
            "y" to target.bounds.centerY,
            "scrolls" to moreScrolls
        )
        if (!session.tap(target.bounds.centerX, target.bounds.centerY)) {
            BatchDiagnostics.warn("more_tap_failed", "index" to index)
            return null
        }
        delay(700)

        val expanded = captureScreen(session, analyzer)
        if (expanded == null) {
            BatchDiagnostics.warn("more_dump_failed", "index" to index)
            return null
        }
        val hint = if (expanded.snapshot.isDetail) {
            AlipayOcrAnalyzer.findOrderNumber(expanded.snapshot.rawText)
        } else {
            null
        }
        expanded.bitmap.recycle()
        BatchDiagnostics.log(
            "more_result",
            "index" to index,
            "orderVisible" to (hint != null)
        )
        return hint
    }

    private fun isMoreTargetReachable(
        more: MoreOcrTarget,
        screenHeight: Int
    ): Boolean =
        more.bounds.centerY.toFloat() < screenHeight * 0.96f &&
            more.bounds.bottom.toFloat() <= screenHeight * 0.98f

    private suspend fun returnToListOcr(
        session: ShizukuUiAutomationSession,
        analyzer: AlipayOcrScreenAnalyzer,
        target: YearMonth
    ): AlipayOcrSnapshot? {
        if (!session.pressBack()) {
            BatchDiagnostics.warn("back_failed")
            return null
        }
        delay(700)
        var snapshot = captureListSnapshot(session, analyzer) ?: return null
        if (snapshot.isDetail || AlipayBatchProfile.isSummaryPage(snapshot.rawText)) {
            if (!session.pressBack()) return null
            delay(700)
            snapshot = captureListSnapshot(session, analyzer) ?: return null
        }
        BatchDiagnostics.log(
            "back_done",
            "month" to snapshot.month,
            "rows" to snapshot.rows.size,
            "detail" to snapshot.isDetail,
            "summary" to AlipayBatchProfile.isSummaryPage(snapshot.rawText)
        )
        val isList = !snapshot.isDetail &&
            !AlipayBatchProfile.isSummaryPage(snapshot.rawText) &&
            snapshot.month == target
        return if (isList) snapshot else null
    }

    private suspend fun scrollList(
        session: ShizukuUiAutomationSession,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val x = screenWidth / 2
        val fromY = screenHeight * 3 / 4
        val toY = screenHeight / 4
        return session.swipe(x, fromY, x, toY, 500)
    }

    private suspend fun waitForTarget(
        session: ShizukuUiAutomationSession,
        target: YearMonth
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + WAIT_TARGET_MS
        val screenHeight = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.density
        var hint: String? = null
        var lastKey: String? = null
        var lastLogAt = 0L
        var dumpFailures = 0
        var lastReason = "NOT_CHECKED"
        var lastSnapshot: AlipayUiSnapshot? = null

        fun shouldLog(key: String, now: Long): Boolean =
            key != lastKey || now - lastLogAt >= 5_000L

        while (!stopRequested && SystemClock.elapsedRealtime() < deadline) {
            val root = session.dumpUi()?.let(UiTreeParser::parse)
            if (root == null) {
                dumpFailures++
                val now = SystemClock.elapsedRealtime()
                if (shouldLog("DUMP_FAILED", now)) {
                    BatchDiagnostics.warn(
                        "wait",
                        "reason" to "DUMP_FAILED",
                        "failures" to dumpFailures,
                        "target" to target
                    )
                    lastKey = "DUMP_FAILED"
                    lastLogAt = now
                    lastReason = "DUMP_FAILED"
                }
                delay(1000)
                continue
            }

            val snapshot = AlipayBatchProfile.snapshot(root, screenHeight, density)
            val reason = when {
                snapshot.packageName != null &&
                    snapshot.packageName != AlipayBatchProfile.ALIPAY_PACKAGE ->
                    "FOREGROUND_NOT_ALIPAY"

                snapshot.month == null -> "MONTH_MISSING"
                snapshot.month != target -> "MONTH_MISMATCH"
                snapshot.rowCount == 0 -> "ROWS_MISSING"
                else -> "READY"
            }
            val now = SystemClock.elapsedRealtime()
            val key = "$reason|${snapshot.packageName}|${snapshot.month}|${snapshot.rowCount}|${snapshot.nodeCount}"
            if (shouldLog(key, now)) {
                BatchDiagnostics.log(
                    "wait",
                    "reason" to reason,
                    "target" to target,
                    "pkg" to snapshot.packageName,
                    "month" to snapshot.month,
                    "monthText" to snapshot.monthText,
                    "rows" to snapshot.rowCount,
                    "nodes" to snapshot.nodeCount,
                    "clickable" to snapshot.clickableCount,
                    "scrollable" to snapshot.scrollableCount,
                    "moneyTexts" to snapshot.moneyTextCount,
                    "monthLikeTexts" to snapshot.monthLikeTextCount
                )
                lastKey = key
                lastLogAt = now
            }
            lastReason = reason
            lastSnapshot = snapshot

            if (reason == "READY") {
                BatchDiagnostics.log(
                    "target_ready",
                    "target" to target,
                    "pkg" to snapshot.packageName,
                    "month" to snapshot.month,
                    "rows" to snapshot.rowCount,
                    "nodes" to snapshot.nodeCount
                )
                return true
            }

            if (reason == "FOREGROUND_NOT_ALIPAY") {
                if (hint != "app") {
                    hint = "app"
                    updateNotification(getString(R.string.batch_capture_wait_app))
                }
            } else if (hint != "month") {
                hint = "month"
                updateNotification(
                    getString(
                        R.string.batch_capture_wait_month,
                        target.year,
                        target.monthValue
                    )
                )
            }
            delay(1000)
        }

        BatchDiagnostics.warn(
            "wait_end",
            "reason" to if (stopRequested) "STOPPED" else lastReason,
            "target" to target,
            "pkg" to lastSnapshot?.packageName,
            "month" to lastSnapshot?.month,
            "monthText" to lastSnapshot?.monthText,
            "rows" to lastSnapshot?.rowCount,
            "nodes" to lastSnapshot?.nodeCount,
            "monthLikeTexts" to lastSnapshot?.monthLikeTextCount,
            "dumpFailures" to dumpFailures
        )
        return false
    }

    private suspend fun waitForDetailRoot(session: ShizukuUiAutomationSession): UiNode? {
        repeat(4) { attempt ->
            val root = session.dumpUi()?.let(UiTreeParser::parse)
            if (root != null && AlipayBatchProfile.isDetailPage(root)) {
                BatchDiagnostics.verbose("detail_wait", "attempt" to (attempt + 1), "result" to "found")
                return root
            }
            BatchDiagnostics.verbose(
                "detail_wait",
                "attempt" to (attempt + 1),
                "result" to if (root == null) "dump_failed" else "not_detail",
                "pkg" to root?.packageName
            )
            delay(600)
        }
        return null
    }

    private fun shortSignature(signature: String): String =
        Integer.toHexString(signature.hashCode())

    /** 详情页内订单号默认折叠；有唯一“更多”时展开，后续截图即可包含订单号。 */
    private suspend fun expandOrderInfoIfNeeded(
        session: ShizukuUiAutomationSession,
        detailRoot: UiNode
    ) {
        if (AlipayBatchProfile.findOrderNumber(detailRoot) != null) {
            BatchDiagnostics.log("more_skip", "reason" to "ORDER_VISIBLE")
            return
        }

        val more = AlipayBatchProfile.findMoreButton(detailRoot)
        if (more == null) {
            BatchDiagnostics.log("more_skip", "reason" to "NO_UNAMBIGUOUS_MORE")
            return
        }
        BatchDiagnostics.log(
            "more_tap",
            "x" to more.bounds.centerX,
            "y" to more.bounds.centerY
        )
        if (!session.tap(more.bounds.centerX, more.bounds.centerY)) {
            BatchDiagnostics.warn("more_tap_failed")
            return
        }
        delay(700)

        val expanded = session.dumpUi()?.let(UiTreeParser::parse)
        if (expanded == null) {
            BatchDiagnostics.warn("more_dump_failed")
            return
        }
        if (expanded.packageName != null &&
            expanded.packageName != AlipayBatchProfile.ALIPAY_PACKAGE
        ) {
            BatchDiagnostics.warn("more_page_changed", "pkg" to expanded.packageName)
            return
        }
        BatchDiagnostics.log(
            "more_result",
            "orderVisible" to (AlipayBatchProfile.findOrderNumber(expanded) != null)
        )
    }

    /** 页内展开可能让一次返回只收起内容；若仍是详情页则再返回一次。 */
    private suspend fun returnFromDetail(session: ShizukuUiAutomationSession) {
        if (!session.pressBack()) {
            BatchDiagnostics.warn("back_failed")
            return
        }
        var backs = 1
        delay(700)

        val root = session.dumpUi()?.let(UiTreeParser::parse)
        if (root == null) {
            BatchDiagnostics.warn("back_dump_failed", "backs" to backs)
            return
        }
        if (root.packageName == AlipayBatchProfile.ALIPAY_PACKAGE &&
            AlipayBatchProfile.isDetailPage(root)
        ) {
            session.pressBack()
            backs++
            delay(700)
        }
        BatchDiagnostics.log(
            "back_done",
            "backs" to backs,
            "pkg" to root.packageName,
            "detail" to AlipayBatchProfile.isDetailPage(root)
        )
    }

    /** 失败回退前判断当前是否仍像账单列表，避免把列表也按返回键退出。 */
    private fun looksLikeBillList(root: UiNode, screenHeight: Int): Boolean {
        if (AlipayBatchProfile.isDetailPage(root)) return false
        if (AlipayBatchProfile.monthFromRoot(root, screenHeight) != null) return true
        return AlipayBatchProfile.findRows(root, resources.displayMetrics.density).size >= 2
    }

    private fun saveFailureImage(index: Int, imageBytes: ByteArray?) {
        if (imageBytes == null) return
        runCatching {
            val file = File(cacheDir, "batch_fail_$index.jpg")
            file.writeBytes(imageBytes)
            BatchDiagnostics.log(
                "failure_image",
                "index" to index,
                "bytes" to imageBytes.size,
                "path" to file.absolutePath
            )
        }.onFailure { BatchDiagnostics.error("failure_image_failed", it, "index" to index) }
    }

    private suspend fun saveReceipt(
        data: ReceiptData,
        fullText: String,
        sourceApp: String?,
        imageBytes: ByteArray?
    ) {
        val storeName = data.storeName.orEmpty()
        val categoryId = CategoryClassifier.suggest(storeName, fullText)
            ?: EditableReceipt.DEFAULT_CATEGORY_ID

        AccountingManager.save(
            EditableReceipt(
                screenshotUri = Uri.EMPTY,
                screenshotImage = imageBytes,
                rawText = fullText,
                amount = data.amount?.toString().orEmpty(),
                discount = data.discount?.toString().orEmpty(),
                originalAmount = data.originalAmount?.toString().orEmpty(),
                storeName = storeName,
                paymentTime = data.paymentTime.orEmpty(),
                paymentMethod = data.paymentMethod.orEmpty(),
                orderNumber = data.orderNumber.orEmpty(),
                sourceApp = sourceApp.orEmpty(),
                categoryId = categoryId,
                origin = EditableReceipt.ORIGIN_AUTO_BATCH
            )
        )
    }

    private suspend fun encodeScreenshotForStorage(bitmap: Bitmap): ByteArray? =
        withContext(Dispatchers.Default) {
            val attempts = listOf(1280 to 75, 960 to 65)
            var last: ByteArray? = null
            for ((maxSide, quality) in attempts) {
                val bytes = compressScreenshot(bitmap, maxSide, quality) ?: continue
                last = bytes
                if (bytes.size <= MAX_IMAGE_BYTES) return@withContext bytes
            }
            last?.takeIf { it.size <= MAX_IMAGE_BYTES_HARD }
        }

    private fun compressScreenshot(bitmap: Bitmap, maxSide: Int, quality: Int): ByteArray? {
        val longestSide = max(bitmap.width, bitmap.height)
        val scale = if (longestSide > maxSide) {
            maxSide.toFloat() / longestSide
        } else {
            1f
        }
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return try {
            ByteArrayOutputStream().use { out ->
                if (scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    out.toByteArray()
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "encode screenshot failed", t)
            null
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun publishOverlay(
        success: Int,
        duplicate: Int,
        income: Int,
        skipped: Int,
        failed: Int
    ) {
        overlaySuccess = success
        overlayDuplicate = duplicate
        overlayIncome = income
        overlaySkipped = skipped
        overlayFailed = failed
        overlayCurrent = success + duplicate + income + skipped + failed
        updateControlState()
        val runtime = Runtime.getRuntime()
        BatchDiagnostics.log(
            "memory",
            "index" to overlayCurrent,
            "javaMb" to (runtime.totalMemory() - runtime.freeMemory()) / 1048576L,
            "maxMb" to runtime.maxMemory() / 1048576L,
            "nativeMb" to Debug.getNativeHeapAllocatedSize() / 1048576L
        )
    }

    private fun updateControlState() {
        BatchControlOverlayController.update(
            applicationContext,
            overlayCurrent,
            overlaySuccess,
            overlayFailed,
            paused,
            orderWaiting,
            overlayDuplicate,
            overlayIncome,
            overlaySkipped
        )
    }

    private suspend fun awaitResume() {
        if (!paused) return
        updateNotification(getString(R.string.batch_capture_paused))
        updateControlState()
        while (paused && !stopRequested) {
            delay(200)
        }
        if (stopRequested) return
        waitForAlipayAfterPause()
        updateControlState()
    }

    /** 暂停后可能切到其他 App；继续时必须回到支付宝再操作。 */
    private suspend fun waitForAlipayAfterPause() {
        var hintShown = false
        while (!stopRequested) {
            val foreground = withContext(Dispatchers.IO) {
                ForegroundAppResolver.getForegroundPackage(
                    applicationContext,
                    System.currentTimeMillis()
                )
            }
            if (foreground == null ||
                foreground == AlipayBatchProfile.ALIPAY_PACKAGE
            ) {
                return
            }
            if (!hintShown) {
                hintShown = true
                updateNotification(getString(R.string.batch_capture_wait_app))
            }
            delay(1000)
        }
    }

    private fun showFloatingButton() {
        FloatingButtonController.show(applicationContext)
        applyCaptureMode(applicationContext)
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(
            ID_FOREGROUND,
            buildNotification(text)
        )
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.batch_capture_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun returnToApp() {
        val ok = runCatching {
            startActivity(
                Intent(this, BatchResultActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.isSuccess
        BatchDiagnostics.log("return_app", "ok" to ok)
    }

    private fun postResult(title: String, text: String, openResult: Boolean = false) {
        val target = if (openResult) BatchResultActivity::class.java else BillListActivity::class.java
        val pendingIntent = PendingIntent.getActivity(
            this,
            ID_SUMMARY,
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_SUMMARY, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.batch_capture_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.batch_capture_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private class BatchNotifier : ReceiptProcessor.Notifier {
        var confirmed: ReceiptData? = null
        var fullText: String = ""
        var sourceApp: String? = null
        var duplicate = false
        var notReceipt = false
        var failureStep: ProcessStep? = null

        override fun updateProgress(step: ProcessStep, detail: String?) = Unit

        override fun clearProgress() = Unit

        override fun notifyFailure(step: ProcessStep, message: String?) {
            failureStep = step
        }

        override fun notifyNotReceipt() {
            notReceipt = true
        }

        override fun notifyDuplicate(duplicateId: Long, screenshotUri: String?) {
            duplicate = true
        }

        override fun notifyConfirm(
            receiptData: ReceiptData,
            rawText: String,
            sourceApp: String?,
            screenshotUri: String?
        ) {
            confirmed = receiptData
            fullText = rawText
            this.sourceApp = sourceApp
        }
    }
}
