package org.caojun.autoocrtask.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.automation.AutomationLauncher
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.AutomationTask
import org.caojun.autoocrtask.data.StepType
import org.caojun.autoocrtask.ui.MainActivity
import org.caojun.shizuku.automation.ShizukuUiAutomation
import org.caojun.shizuku.automation.ShizukuUiAutomationSession
import org.caojun.shotocr.ocr.OcrEngine

private enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

class RecorderOverlayService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "org.caojun.autoocrtask.action.STOP_RECORDER"

        private const val TAG = "AutoOcrTask/Recorder"
        private const val CHANNEL_ID = "recorder"
        private const val NOTIFICATION_ID = 2002
        private const val SESSION_WAIT_MS = 10_000L
        private const val TARGET_SETTLE_MS = 700L
        private const val CLICK_SETTLE_MS = 900L
    }

    private lateinit var windowManager: WindowManager
    private var controlBar: View? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelX = 0
    private var panelY = 0
    private var panelPositionSet = false
    private var controlParams: WindowManager.LayoutParams? = null
    private var controlX = 0
    private var controlY = 0
    private var controlPositionSet = false
    private var currentStatus: String? = null
    private var statusView: TextView? = null
    private var countView: TextView? = null

    @Volatile
    private var session: ShizukuUiAutomationSession? = null
    private var ocr: OcrEngine? = null
    private val ocrMutex = Mutex()
    private var sessionJob: Job? = null

    @Volatile
    private var busy = false

    @Volatile
    private var finished = false

    private var density = 1f
    private var minTopPx = 0

    private var pendingExact = false
    private var pendingUseOcr = true
    private var pendingTimeoutMs = 8_000L
    private var pendingWaitPackage = ""
    private var pendingWaitTimeoutMs = 15_000L
    private var pendingSwipeDirection = SwipeDirection.UP
    private var pendingSwipeDistance = 0.5f
    private var pendingSwipeDurationMs = 500

    private var scanMode = StepType.WAIT_TEXT
    private var scanResult: ScanResult? = null
    private var ocrRunning = false

    @Volatile
    private var connecting = false

    private val timeoutValues = listOf(3_000L, 5_000L, 8_000L, 10_000L, 15_000L, 30_000L)
    private val waitTimeoutValues = listOf(5_000L, 10_000L, 15_000L, 30_000L)
    private val swipeDistances = listOf(0.3f, 0.5f, 0.7f)
    private val swipeDurations = listOf(300, 500, 800)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density
        minTopPx = statusBarHeight()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.recorder_notification_preparing)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        lifecycleScope.launch { beginRecording() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            cancelRecording(returnToEditor = true)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        finished = true
        sessionJob?.cancel()
        sessionJob = null
        session = null
        runCatching { ocr?.release() }
        ocr = null
        removePanel()
        removeControlBar()
        super.onDestroy()
    }

    private suspend fun beginRecording() {
        val draft = RecorderSession.current(this)
        if (draft == null) {
            failAndStop(getString(R.string.run_task_missing))
            return
        }
        Log.i(TAG, "beginRecording: package=${draft.packageName}")
        connecting = true
        busy = true

        addControlBar()
        if (controlBar == null) {
            failAndStop(getString(R.string.editor_recorder_permission))
            return
        }
        updateCount(draft.recordedSteps.size)
        updateStatus(getString(R.string.recorder_status_connecting))

        // 先在 AutoOcrTask 仍在前台时绑定 Shizuku，避免目标 App 启动后
        // AutoOcrTask 退到后台导致 UserService 绑定失败。
        Log.i(TAG, "shizuku bind start")
        val ready = CompletableDeferred<ShizukuUiAutomationSession?>()
        sessionJob = lifecycleScope.launch {
            ShizukuUiAutomation(this@RecorderOverlayService).withSession { bound ->
                session = bound
                ready.complete(bound)
                awaitCancellation()
            }
            if (!ready.isCompleted) ready.complete(null)
        }

        val bound = withTimeoutOrNull(SESSION_WAIT_MS) { ready.await() }
        if (finished) return
        if (bound == null) {
            Log.w(TAG, "shizuku bind failed or timeout")
            failAndStop(getString(R.string.recorder_error_shizuku_connect))
            return
        }
        Log.i(TAG, "shizuku bind success")

        val task = AutomationTask(
            id = draft.taskId ?: "recorder",
            name = draft.name,
            packageName = draft.packageName,
            launch = draft.launch,
            steps = draft.existingSteps
        )
        pendingWaitPackage = draft.packageName

        if (!AutomationLauncher.isInstalled(this, task.packageName)) {
            failAndStop(getString(R.string.run_package_missing))
            return
        }
        if (!AutomationLauncher.launch(this, task)) {
            failAndStop(getString(R.string.run_launch_failed))
            return
        }
        Log.i(TAG, "target launched: ${task.packageName}")

        delay(TARGET_SETTLE_MS)
        connecting = false
        busy = false
        updateStatus(getString(R.string.recorder_status_ready))

        // 后台预热 OCR，首次打开识别面板时无需再等模型加载。
        lifecycleScope.launch { ensureOcr() }
    }

    // ---------- 控制条 ----------

    private fun addControlBar() {
        if (controlBar != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelBackground()
        }

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val count = TextView(this).apply {
            setTextColor(0xFF90CAF9.toInt())
            textSize = 13f
            setPadding(dp(10), 0, 0, 0)
        }
        statusView = status
        countView = count
        infoRow.addView(status, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        infoRow.addView(count)
        root.addView(infoRow)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        buttonRow.addView(
            actionButton(getString(R.string.recorder_control_add)) { showActionPanel() },
            buttonParams()
        )
        buttonRow.addView(
            actionButton(getString(R.string.recorder_control_undo)) { undo() },
            buttonParams()
        )
        buttonRow.addView(
            actionButton(getString(R.string.recorder_control_finish)) { finishRecording() },
            buttonParams()
        )
        buttonRow.addView(
            actionButton(getString(R.string.recorder_control_cancel)) { cancelRecording(true) },
            buttonParams()
        )
        root.addView(buttonRow)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val lp = WindowManager.LayoutParams(
            WRAP_CONTENT,
            WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (controlPositionSet) controlX else (screenWidth / 4)
            y = if (controlPositionSet) controlY else dp(80)
        }

        if (runCatching { windowManager.addView(root, lp) }.isSuccess) {
            controlBar = root
            controlParams = lp
            controlX = lp.x
            controlY = lp.y
            attachControlDrag(root, lp)
            if (!controlPositionSet) {
                root.post {
                    if (controlBar !== root) return@post
                    val barWidth = root.width.takeIf { it > 0 } ?: return@post
                    lp.x = ((screenWidth - barWidth) / 2).coerceAtLeast(0)
                    controlX = lp.x
                    runCatching { windowManager.updateViewLayout(root, lp) }
                }
            } else {
                statusView?.text = currentStatus
            }
            updateCount(RecorderSession.current(this)?.recordedSteps?.size ?: 0)
        }
    }

    private fun attachControlDrag(bar: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        bar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        val barWidth = bar.width.takeIf { it > 0 } ?: params.width
                        val barHeight = bar.height.takeIf { it > 0 } ?: 0
                        val maxX = (screenWidth - barWidth).coerceAtLeast(0)
                        val maxY = (screenHeight - barHeight).coerceAtLeast(0)
                        params.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(bar, params) }
                        controlX = params.x
                        controlY = params.y
                        controlPositionSet = true
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        // 部分机型 updateViewLayout 不刷新输入区域，
                        // 拖动结束后重建窗口，确保新位置的按钮可点击。
                        bar.post {
                            if (finished) return@post
                            removeControlBar()
                            addControlBar()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun removeControlBar() {
        controlBar?.let { runCatching { windowManager.removeView(it) } }
        controlBar = null
        controlParams = null
        statusView = null
        countView = null
    }

    // ---------- 动作面板 ----------

    private fun showActionPanel() {
        if (connecting) {
            toast(getString(R.string.recorder_status_connecting))
            return
        }
        if (busy) {
            toast(getString(R.string.recorder_status_scanning))
            return
        }
        removePanel()
        val root = createPanel(getString(R.string.recorder_add_step)) { removePanel() }
        val actions = listOf(
            StepType.TAP_TEXT to R.string.recorder_action_tap_text,
            StepType.WAIT_TEXT to R.string.recorder_action_wait_text,
            StepType.TAP_POINT to R.string.recorder_action_tap_point,
            StepType.WAIT_PACKAGE to R.string.recorder_action_wait_package,
            StepType.SWIPE to R.string.recorder_action_swipe,
            StepType.BACK to R.string.recorder_action_back,
            StepType.DELAY to R.string.recorder_action_delay
        )
        actions.chunked(2).forEach { rowActions ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            rowActions.forEach { (type, res) ->
                row.addView(
                    actionButton(getString(res)) { onActionPicked(type) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6)
                    }
                )
            }
            if (rowActions.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            root.addView(row)
        }
        showPanel(root)
    }

    private fun onActionPicked(type: StepType) {
        removePanel()
        when (type) {
            StepType.TAP_TEXT, StepType.WAIT_TEXT, StepType.TAP_POINT ->
                scanAndShowElementPanel(type)

            StepType.WAIT_PACKAGE -> {
                pendingWaitPackage = RecorderSession.current(this)?.packageName.orEmpty()
                pendingWaitTimeoutMs = 15_000L
                showWaitPackagePanel()
            }

            StepType.SWIPE -> {
                pendingSwipeDirection = SwipeDirection.UP
                pendingSwipeDistance = 0.5f
                pendingSwipeDurationMs = 500
                showSwipePanel()
            }

            StepType.BACK -> recordBack()
            StepType.DELAY -> showDelayPanel()
        }
    }

    // ---------- 文字/坐标面板 ----------

    private fun scanAndShowElementPanel(mode: StepType) {
        if (busy) return
        val active = session
        if (active == null) {
            toast(getString(R.string.recorder_status_connecting))
            return
        }
        busy = true
        scanMode = mode
        updateStatus(getString(R.string.recorder_status_scanning))
        lifecycleScope.launch {
            try {
                val result = scanElements(active, includeOcr = true)
                if (finished) return@launch
                scanResult = result
                pendingExact = false
                pendingUseOcr = true
                pendingTimeoutMs =
                    if (mode == StepType.WAIT_TEXT) 15_000L else 8_000L
                updateStatus(getString(R.string.recorder_status_ready))
                showElementPanel(mode)
            } catch (t: Throwable) {
                updateStatus(t.message ?: getString(R.string.recorder_status_scan_failed))
            } finally {
                busy = false
            }
        }
    }

    private fun refreshElementScan() {
        if (busy) return
        val active = session ?: return
        busy = true
        updateStatus(getString(R.string.recorder_status_scanning))
        lifecycleScope.launch {
            try {
                val result = scanElements(active, includeOcr = true)
                if (finished) return@launch
                scanResult = result
                updateStatus(getString(R.string.recorder_status_ready))
                showElementPanel(scanMode)
            } catch (t: Throwable) {
                updateStatus(t.message ?: getString(R.string.recorder_status_scan_failed))
            } finally {
                busy = false
            }
        }
    }

    private suspend fun scanElements(
        active: ShizukuUiAutomationSession,
        includeOcr: Boolean
    ): ScanResult {
        val scanner = ScreenElementScanner(
            session = active,
            ocrProvider = { ensureOcr() },
            minTopPx = minTopPx,
            density = density,
            appPackage = packageName
        )
        controlBar?.visibility = View.GONE
        panelView?.visibility = View.GONE
        return try {
            delay(150)
            scanner.scan(includeOcr)
        } finally {
            controlBar?.visibility = View.VISIBLE
            panelView?.visibility = View.VISIBLE
        }
    }

    private suspend fun ensureOcr(): OcrEngine? = ocrMutex.withLock {
        ocr?.let { return@withLock it }
        val engine = OcrEngine(applicationContext)
        try {
            withContext(Dispatchers.Default) { engine.initialize() }
            ocr = engine
            engine
        } catch (t: Throwable) {
            runCatching { engine.release() }
            null
        }
    }

    private fun showElementPanel(mode: StepType) {
        val result = scanResult ?: return
        val title = when (mode) {
            StepType.WAIT_TEXT -> getString(R.string.recorder_title_wait_text)
            StepType.TAP_POINT -> getString(R.string.recorder_title_tap_point)
            else -> getString(R.string.recorder_title_tap_text)
        }
        val root = createPanel(title) { removePanel() }

        val statusText = when {
            ocrRunning -> getString(R.string.recorder_ocr_running)
            result.ocrError == "OCR_UNAVAILABLE" ->
                getString(R.string.recorder_ocr_unavailable)

            result.ocrError == "SCREENSHOT_FAILED" ->
                getString(R.string.recorder_screenshot_failed)

            result.ocrUsed && result.ocrError != null ->
                getString(R.string.recorder_ocr_failed, result.ocrError)

            result.ocrUsed ->
                getString(R.string.recorder_scan_source, result.uiCount, result.ocrCount)

            else -> getString(R.string.recorder_scan_ui_only, result.uiCount)
        }
        root.addView(sectionLabel(statusText))

        if (result.totalCount > result.elements.size) {
            root.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.recorder_scan_truncated,
                        result.totalCount,
                        result.elements.size
                    )
                    setTextColor(0xFFFFB74D.toInt())
                    textSize = 12f
                }
            )
        }

        root.addView(
            actionButton(getString(R.string.recorder_refresh_scan)) { refreshElementScan() },
            actionButtonParams()
        )

        if (mode != StepType.TAP_POINT) {
            root.addView(buildSwitch(getString(R.string.recorder_param_exact), pendingExact) {
                pendingExact = it
            })
            root.addView(buildSwitch(getString(R.string.recorder_param_use_ocr), pendingUseOcr) {
                pendingUseOcr = it
            })
            root.addView(sectionLabel(getString(R.string.recorder_param_timeout)))
            root.addView(
                buildChoices(
                    timeoutLabels(),
                    timeoutValues.indexOf(pendingTimeoutMs).coerceAtLeast(0)
                ) { index ->
                    pendingTimeoutMs = timeoutValues[index]
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
        }

        if (result.elements.isEmpty()) {
            root.addView(
                TextView(this).apply {
                    text = getString(R.string.recorder_scan_empty_hint)
                    setTextColor(0xFFBDBDBD.toInt())
                    textSize = 13f
                    setPadding(0, dp(8), 0, dp(8))
                }
            )
        }

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        result.elements.forEach { element -> list.addView(buildElementRow(element, mode)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        showPanel(root, 0.62f)
    }

    private fun buildElementRow(element: ScreenElement, mode: StepType): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            setOnClickListener { onElementPicked(element, mode, byCoordinate = false) }
        }
        row.addView(
            TextView(this).apply {
                text = element.text
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )
        if (element.fromOcr) {
            row.addView(
                TextView(this).apply {
                    text = getString(R.string.recorder_source_ocr)
                    setTextColor(0xFF80CBC4.toInt())
                    textSize = 11f
                    setPadding(dp(6), 0, dp(4), 0)
                }
            )
        }
        if (mode != StepType.WAIT_TEXT) {
            row.addView(
                TextView(this).apply {
                    text = getString(R.string.recorder_list_coordinate)
                    setTextColor(0xFF90CAF9.toInt())
                    textSize = 13f
                    setPadding(dp(10), dp(4), 0, dp(4))
                    isClickable = true
                    setOnClickListener {
                        onElementPicked(element, mode, byCoordinate = true)
                    }
                }
            )
        }
        return row
    }

    private fun onElementPicked(
        element: ScreenElement,
        mode: StepType,
        byCoordinate: Boolean
    ) {
        if (busy) {
            toast(getString(R.string.recorder_status_scanning))
            return
        }
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)

        val step = when {
            mode == StepType.TAP_POINT || byCoordinate -> AutomationStep(
                type = StepType.TAP_POINT,
                x = element.x.toFloat() / width,
                y = element.y.toFloat() / height
            )

            mode == StepType.WAIT_TEXT -> AutomationStep(
                type = StepType.WAIT_TEXT,
                text = element.text,
                timeoutMs = pendingTimeoutMs,
                exact = pendingExact,
                useOcr = pendingUseOcr
            )

            else -> AutomationStep(
                type = StepType.TAP_TEXT,
                text = element.text,
                timeoutMs = pendingTimeoutMs,
                exact = pendingExact,
                useOcr = pendingUseOcr
            )
        }

        if (!appendStep(step)) return
        removePanel()

        if (mode == StepType.WAIT_TEXT && !byCoordinate) {
            updateStatus(getString(R.string.recorder_status_wait_text, element.text))
        } else {
            executeTap(element)
        }
    }

    private fun executeTap(element: ScreenElement) {
        lifecycleScope.launch {
            updateStatus(getString(R.string.recorder_status_clicking, element.text))
            val ok = session?.tap(element.x, element.y) == true
            if (!ok) {
                updateStatus(getString(R.string.recorder_status_click_failed))
                toast(getString(R.string.recorder_status_click_failed))
            } else {
                delay(CLICK_SETTLE_MS)
                updateStatus(getString(R.string.recorder_status_ready))
            }
        }
    }

    // ---------- 等待应用 ----------

    private fun showWaitPackagePanel() {
        removePanel()
        val root = createPanel(getString(R.string.recorder_action_wait_package)) { removePanel() }

        root.addView(
            sectionLabel(
                getString(R.string.recorder_wait_package_current, pendingWaitPackage)
            )
        )
        root.addView(
            actionButton(getString(R.string.recorder_pick_app)) { showAppPickerPanel() },
            actionButtonParams()
        )
        root.addView(sectionLabel(getString(R.string.recorder_param_timeout)))
        root.addView(
            buildChoices(
                waitTimeoutLabels(),
                waitTimeoutValues.indexOf(pendingWaitTimeoutMs).coerceAtLeast(0)
            ) { index ->
                pendingWaitTimeoutMs = waitTimeoutValues[index]
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        )
        root.addView(
            actionButton(getString(R.string.recorder_confirm_add)) {
                val step = AutomationStep(
                    type = StepType.WAIT_PACKAGE,
                    packageName = pendingWaitPackage,
                    timeoutMs = pendingWaitTimeoutMs
                )
                if (appendStep(step)) {
                    removePanel()
                    updateStatus(
                        getString(R.string.recorder_status_wait_package_added, pendingWaitPackage)
                    )
                }
            },
            actionButtonParams()
        )

        showPanel(root)
    }

    private fun showAppPickerPanel() {
        removePanel()
        val root = createPanel(getString(R.string.recorder_pick_app)) { removePanel() }
        val apps = AutomationLauncher.queryLaunchablePackages(this)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps.forEach { (pkg, label) ->
            list.addView(
                TextView(this).apply {
                    text = "$label\n$pkg"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setPadding(0, dp(10), 0, dp(10))
                    isClickable = true
                    setOnClickListener {
                        pendingWaitPackage = pkg
                        showWaitPackagePanel()
                    }
                }
            )
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        showPanel(root, 0.6f)
    }

    // ---------- 滑动 ----------

    private fun showSwipePanel() {
        removePanel()
        val root = createPanel(getString(R.string.recorder_action_swipe)) { removePanel() }

        root.addView(sectionLabel(getString(R.string.recorder_swipe_direction)))
        root.addView(
            buildChoices(
                listOf(
                    getString(R.string.recorder_swipe_up),
                    getString(R.string.recorder_swipe_down),
                    getString(R.string.recorder_swipe_left),
                    getString(R.string.recorder_swipe_right)
                ),
                SwipeDirection.entries.indexOf(pendingSwipeDirection).coerceAtLeast(0)
            ) { index ->
                pendingSwipeDirection = SwipeDirection.entries[index]
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        )

        root.addView(sectionLabel(getString(R.string.recorder_swipe_distance)))
        root.addView(
            buildChoices(
                swipeDistances.map { it.toString() },
                swipeDistances.indexOf(pendingSwipeDistance).coerceAtLeast(0)
            ) { index ->
                pendingSwipeDistance = swipeDistances[index]
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        )

        root.addView(sectionLabel(getString(R.string.recorder_swipe_duration)))
        root.addView(
            buildChoices(
                swipeDurations.map { getString(R.string.recorder_ms, it) },
                swipeDurations.indexOf(pendingSwipeDurationMs).coerceAtLeast(0)
            ) { index ->
                pendingSwipeDurationMs = swipeDurations[index]
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        )

        root.addView(
            actionButton(getString(R.string.recorder_add_swipe)) {
                val step = buildSwipeStep()
                if (appendStep(step)) {
                    removePanel()
                    executeSwipe(step)
                }
            },
            actionButtonParams()
        )
        showPanel(root)
    }

    private fun buildSwipeStep(): AutomationStep {
        val half = pendingSwipeDistance.coerceIn(0.1f, 0.9f) / 2f
        val from = (0.5f + half).coerceIn(0.05f, 0.95f)
        val to = (0.5f - half).coerceIn(0.05f, 0.95f)
        return when (pendingSwipeDirection) {
            SwipeDirection.UP -> AutomationStep(
                type = StepType.SWIPE,
                x = 0.5f, y = from, x2 = 0.5f, y2 = to,
                durationMs = pendingSwipeDurationMs
            )

            SwipeDirection.DOWN -> AutomationStep(
                type = StepType.SWIPE,
                x = 0.5f, y = to, x2 = 0.5f, y2 = from,
                durationMs = pendingSwipeDurationMs
            )

            SwipeDirection.LEFT -> AutomationStep(
                type = StepType.SWIPE,
                x = from, y = 0.5f, x2 = to, y2 = 0.5f,
                durationMs = pendingSwipeDurationMs
            )

            SwipeDirection.RIGHT -> AutomationStep(
                type = StepType.SWIPE,
                x = to, y = 0.5f, x2 = from, y2 = 0.5f,
                durationMs = pendingSwipeDurationMs
            )
        }
    }

    private fun executeSwipe(step: AutomationStep) {
        lifecycleScope.launch {
            updateStatus(getString(R.string.recorder_status_swipe))
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            session?.swipe(
                (step.x * width).toInt(),
                (step.y * height).toInt(),
                (step.x2 * width).toInt(),
                (step.y2 * height).toInt(),
                step.durationMs
            )
            delay(800)
            updateStatus(getString(R.string.recorder_status_ready))
        }
    }

    // ---------- 返回 / 延时 ----------

    private fun recordBack() {
        if (!appendStep(AutomationStep(type = StepType.BACK))) return
        removePanel()
        lifecycleScope.launch {
            updateStatus(getString(R.string.recorder_status_back))
            session?.pressBack()
            delay(700)
            updateStatus(getString(R.string.recorder_status_ready))
        }
    }

    private fun showDelayPanel() {
        removePanel()
        val root = createPanel(getString(R.string.recorder_action_delay)) { removePanel() }
        listOf(500L, 1_000L, 2_000L, 3_000L, 5_000L).forEach { ms ->
            root.addView(
                actionButton(durationLabel(ms)) {
                    val step = AutomationStep(type = StepType.DELAY, delayMs = ms)
                    if (appendStep(step)) {
                        removePanel()
                        executeDelay(ms)
                    }
                },
                actionButtonParams()
            )
        }
        showPanel(root)
    }

    private fun executeDelay(ms: Long) {
        lifecycleScope.launch {
            updateStatus(getString(R.string.recorder_status_delay, ms.toInt()))
            delay(ms)
            updateStatus(getString(R.string.recorder_status_ready))
        }
    }

    // ---------- 通用面板控件 ----------

    private fun createPanel(title: String, onClose: () -> Unit): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
            background = panelBackground()
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(this).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )
        titleRow.addView(
            TextView(this).apply {
                text = getString(R.string.common_close)
                setTextColor(0xFF90CAF9.toInt())
                textSize = 14f
                setPadding(dp(12), dp(4), 0, dp(4))
                isClickable = true
                setOnClickListener { onClose() }
            }
        )
        root.addView(titleRow)
        return root
    }

    private fun showPanel(root: View, heightFraction: Float? = null) {
        removePanel()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val width = (screenWidth * 0.92f).toInt()
        val height = if (heightFraction != null) {
            (screenHeight * heightFraction).toInt()
        } else {
            WRAP_CONTENT
        }
        val maxX = (screenWidth - width).coerceAtLeast(0)
        val initialX = if (panelPositionSet) panelX else (maxX / 2)
        val initialY = if (panelPositionSet) panelY else (screenHeight * 0.15f).toInt()
        val maxY = if (height > 0) {
            (screenHeight - height).coerceAtLeast(0)
        } else {
            (screenHeight - 100).coerceAtLeast(0)
        }

        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX.coerceIn(0, maxX)
            y = initialY.coerceIn(0, maxY)
        }

        if (runCatching { windowManager.addView(root, lp) }.isSuccess) {
            panelView = root
            panelParams = lp
            panelX = lp.x
            panelY = lp.y
            panelPositionSet = true
            attachPanelDrag(root, lp)
        }
    }

    private fun attachPanelDrag(panel: View, params: WindowManager.LayoutParams) {
        val handle = (panel as? LinearLayout)?.getChildAt(0) ?: return
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        val panelWidth = panel.width.takeIf { it > 0 } ?: params.width
                        val panelHeight = panel.height.takeIf { it > 0 } ?: 0
                        val maxX = (screenWidth - panelWidth).coerceAtLeast(0)
                        val maxY = (screenHeight - panelHeight).coerceAtLeast(0)
                        params.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(panel, params) }
                        panelX = params.x
                        panelY = params.y
                        panelPositionSet = true
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        runCatching { windowManager.updateViewLayout(panel, params) }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelParams = null
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(0xFF212121.toInt())
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF90CAF9.toInt())
        textSize = 13f
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun buildSwitch(
        text: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): Switch = Switch(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    private fun buildChoices(
        labels: List<String>,
        initialIndex: Int,
        onSelected: (Int) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val buttons = mutableListOf<TextView>()

        fun applyStyle(index: Int) {
            buttons.forEachIndexed { i, button -> styleChoice(button, i == index) }
        }

        labels.forEachIndexed { index, label ->
            val button = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                setOnClickListener {
                    applyStyle(index)
                    onSelected(index)
                }
            }
            row.addView(
                button,
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
            )
            buttons.add(button)
        }

        applyStyle(initialIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0)))
        val scroll = HorizontalScrollView(this)
        scroll.addView(row)
        return scroll
    }

    private fun styleChoice(button: TextView, selected: Boolean) {
        button.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(if (selected) 0xFF3F51B5.toInt() else 0xFF3A3A3A.toInt())
        }
        button.setTextColor(if (selected) Color.WHITE else 0xFFDDDDDD.toInt())
    }

    private fun actionButton(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xFF3F51B5.toInt())
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun actionButtonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(8)
        }

    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            marginEnd = dp(6)
        }

    private fun timeoutLabels(): List<String> =
        timeoutValues.map { getString(R.string.recorder_seconds, (it / 1000).toInt()) }

    private fun waitTimeoutLabels(): List<String> =
        waitTimeoutValues.map { getString(R.string.recorder_seconds, (it / 1000).toInt()) }

    private fun durationLabel(ms: Long): String =
        if (ms % 1000L == 0L) {
            getString(R.string.recorder_seconds, (ms / 1000).toInt())
        } else {
            getString(R.string.recorder_ms, ms.toInt())
        }

    // ---------- 记录/执行/结束 ----------

    private fun appendStep(step: AutomationStep): Boolean {
        val draft = RecorderSession.append(this, step) ?: return false
        updateCount(draft.recordedSteps.size)
        return true
    }

    private fun undo() {
        val draft = RecorderSession.undo(this) ?: return
        updateCount(draft.recordedSteps.size)
        updateStatus(getString(R.string.recorder_status_undo, draft.recordedSteps.size))
    }

    private fun finishRecording() {
        if (finished) return
        val draft = RecorderSession.current(this)
        if (draft == null || draft.recordedSteps.isEmpty()) {
            toast(getString(R.string.recorder_status_no_steps))
            return
        }
        finished = true
        RecorderSession.finish(this)
        teardown(returnToEditor = true)
    }

    private fun cancelRecording(returnToEditor: Boolean) {
        if (finished) return
        finished = true
        RecorderSession.cancel(this)
        teardown(returnToEditor)
    }

    private fun failAndStop(message: String) {
        Log.w(TAG, "failAndStop: $message")
        updateStatus(message)
        toast(message)
        teardown(returnToEditor = true)
    }

    private fun teardown(returnToEditor: Boolean) {
        Log.i(TAG, "teardown: returnToEditor=$returnToEditor")
        connecting = false
        busy = false
        ocrRunning = false
        panelPositionSet = false
        panelX = 0
        panelY = 0
        controlPositionSet = false
        controlX = 0
        controlY = 0
        removePanel()
        removeControlBar()
        sessionJob?.cancel()
        sessionJob = null
        session = null
        runCatching { ocr?.release() }
        ocr = null

        if (returnToEditor) {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }

        runCatching { stopForeground(Service.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    // ---------- 通知/状态 ----------

    private fun updateStatus(text: String) {
        currentStatus = text
        statusView?.text = text
        updateNotification(text)
    }

    private fun updateCount(count: Int) {
        countView?.text = getString(R.string.recorder_count, count)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecorderOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.recorder_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.recorder_control_cancel), stop)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recorder_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.recorder_channel_desc)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            resources.getDimensionPixelSize(id)
        } else {
            dp(24)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
