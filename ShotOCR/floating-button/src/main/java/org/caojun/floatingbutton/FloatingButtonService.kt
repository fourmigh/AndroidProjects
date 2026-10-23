package org.caojun.floatingbutton

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * 普通绑定服务：悬浮按钮 + 可选的识别进度列表。
 * 不使用前台服务、不发送常驻通知。
 */
class FloatingButtonService : Service() {

    companion object {
        internal const val ACTION_HIDE = "org.caojun.floatingbutton.action.HIDE"
        internal const val ACTION_SHOW = "org.caojun.floatingbutton.action.SHOW"
        internal const val ACTION_REFRESH_MODE = "org.caojun.floatingbutton.action.REFRESH_MODE"
        internal const val ACTION_PROGRESS_SHOW = "org.caojun.floatingbutton.action.PROGRESS_SHOW"
        internal const val ACTION_PROGRESS_UPDATE = "org.caojun.floatingbutton.action.PROGRESS_UPDATE"
        internal const val ACTION_PROGRESS_FINISH = "org.caojun.floatingbutton.action.PROGRESS_FINISH"
        internal const val ACTION_PROGRESS_CLEAR = "org.caojun.floatingbutton.action.PROGRESS_CLEAR"
        internal const val ACTION_PROGRESS_MESSAGE = "org.caojun.floatingbutton.action.PROGRESS_MESSAGE"
        internal const val EXTRA_STEPS = "org.caojun.floatingbutton.extra.STEPS"
        internal const val EXTRA_CURRENT_INDEX = "org.caojun.floatingbutton.extra.CURRENT_INDEX"
        internal const val EXTRA_FRACTION = "org.caojun.floatingbutton.extra.FRACTION"
        internal const val EXTRA_MESSAGE = "org.caojun.floatingbutton.extra.MESSAGE"

        private const val EDGE_ANIM_MS = 420L
        private const val REVEAL_ANIM_MS = 150L
        private const val AUTO_HIDE_MS = 2_500L
        private const val PROGRESS_TICK_MS = 150L
        private const val PROGRESS_LINGER_MS = 3_000L
        private val PENDING_COLOR = 0x80FFFFFF.toInt()
        private val CURRENT_COLOR = 0xFFFFD54F.toInt()
    }

    private lateinit var windowManager: WindowManager
    private var buttonView: ImageView? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var edgeAnimator: ValueAnimator? = null

    private var progressView: LinearLayout? = null
    private var progressTitleCount: TextView? = null
    private var progressTotal: TextView? = null
    private var progressMessage: TextView? = null
    private val progressLabels = mutableListOf<TextView>()
    private val progressDurations = mutableListOf<TextView>()
    private val timeline = ProgressTimeline()

    private val handler = Handler(Looper.getMainLooper())
    private val restoreRunnable = Runnable { setVisible(true) }
    private val finishHideRunnable = Runnable { clearProgressInternal() }
    private val autoHideRunnable = Runnable { hideToHalf() }
    private val progressTicker = object : Runnable {
        override fun run() {
            renderProgress()
            if (!timeline.isFinished) {
                handler.postDelayed(this, PROGRESS_TICK_MS)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_HIDE -> setVisible(false)
                ACTION_SHOW -> setVisible(true)
                ACTION_REFRESH_MODE -> refreshButtonMode()
                ACTION_PROGRESS_SHOW -> {
                    val steps = intent.getStringArrayListExtra(EXTRA_STEPS).orEmpty()
                    if (steps.isEmpty()) return
                    val current = intent.getIntExtra(EXTRA_CURRENT_INDEX, 1)
                    showProgress(steps, current)
                }
                ACTION_PROGRESS_UPDATE -> {
                    val current = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
                    if (current > 0) updateProgress(current)
                }
                ACTION_PROGRESS_FINISH -> finishProgress()
                ACTION_PROGRESS_CLEAR -> clearProgressInternal()
                ACTION_PROGRESS_MESSAGE ->
                    intent.getStringExtra(EXTRA_MESSAGE)?.let { showProgressMessage(it) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(ACTION_HIDE)
                addAction(ACTION_SHOW)
                addAction(ACTION_REFRESH_MODE)
                addAction(ACTION_PROGRESS_SHOW)
                addAction(ACTION_PROGRESS_UPDATE)
                addAction(ACTION_PROGRESS_FINISH)
                addAction(ACTION_PROGRESS_CLEAR)
                addAction(ACTION_PROGRESS_MESSAGE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addButton()
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val lp = buttonParams ?: return
        val view = buttonView ?: return
        val mode = FloatingButtonSettings(this).mode
        if (mode == FloatingButtonMode.OFF) return

        cancelAutoHide()
        cancelEdgeAnimation()

        val size = view.width.takeIf { it > 0 } ?: lp.width
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val maxX = (width - size).coerceAtLeast(0)
        val maxY = (height - size).coerceAtLeast(0)
        val isLeft = lp.x + size / 2 < width / 2

        lp.x = when (mode) {
            FloatingButtonMode.EDGE_HIDE ->
                if (isLeft) -size / 2 else width - size / 2
            FloatingButtonMode.ALWAYS_SHOW ->
                if (isLeft) 0 else maxX
            FloatingButtonMode.OFF -> return
        }
        lp.y = lp.y.coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(view, lp) }
        savePosition(lp.x, lp.y)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cancelAutoHide()
        cancelEdgeAnimation()
        runCatching { unregisterReceiver(receiver) }
        removeProgressView()
        removeButton()
        super.onDestroy()
    }

    private fun addButton() {
        if (buttonView != null) return
        val options = FloatingButtonController.currentOptions() ?: return

        val density = resources.displayMetrics.density
        val size = (options.sizeDp * density).toInt().coerceAtLeast(1)
        val padding = (size * 0.22f).toInt()

        val view = ImageView(this).apply {
            setImageResource(options.iconRes)
            contentDescription = options.contentDescription
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(padding, padding, padding, padding)
            background = ContextCompat.getDrawable(
                this@FloatingButtonService,
                R.drawable.bg_floating_button
            )
            elevation = 6f * density
        }

        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - size).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - size).coerceAtLeast(0)
        val settings = FloatingButtonSettings(this)
        val mode = settings.mode
        if (mode == FloatingButtonMode.OFF) return

        val halfHidden = mode == FloatingButtonMode.EDGE_HIDE
        val leftHalfX = -size / 2
        val rightHalfX = metrics.widthPixels - size / 2
        val savedX = settings.x

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = when {
                savedX == FloatingButtonSettings.UNSET -> if (halfHidden) {
                    rightHalfX
                } else {
                    maxX - (16 * density).toInt()
                }
                halfHidden -> if (savedX < metrics.widthPixels / 2) leftHalfX else rightHalfX
                else -> savedX.coerceIn(0, maxX)
            }
            y = if (settings.y != FloatingButtonSettings.UNSET) {
                settings.y.coerceIn(0, maxY)
            } else {
                (maxY * 0.4f).toInt()
            }
        }

        view.setOnTouchListener(DragTouchListener(view, lp))
        runCatching { windowManager.addView(view, lp) }.onFailure { return }
        buttonView = view
        buttonParams = lp
    }

    private fun removeButton() {
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        buttonView = null
        buttonParams = null
    }

    private fun setVisible(visible: Boolean) {
        if (visible && progressView != null) return
        handler.removeCallbacks(restoreRunnable)
        if (visible && buttonView == null) addButton()
        val view = buttonView ?: return

        val wasVisible = view.visibility == View.VISIBLE
        if (!visible) {
            cancelAutoHide()
            cancelEdgeAnimation()
            view.visibility = View.GONE
            return
        }

        view.visibility = View.VISIBLE
        if (!wasVisible && FloatingButtonSettings(this).mode == FloatingButtonMode.EDGE_HIDE) {
            buttonParams?.let { snapToEdge(it) }
        }
    }

    private fun showProgress(steps: List<String>, currentIndex: Int) {
        handler.removeCallbacks(restoreRunnable)
        handler.removeCallbacks(finishHideRunnable)
        handler.removeCallbacks(progressTicker)
        cancelAutoHide()
        cancelEdgeAnimation()
        buttonView?.visibility = View.GONE

        timeline.start(steps, currentIndex, SystemClock.elapsedRealtime())
        removeProgressView()
        createProgressView(steps)
        renderProgress()
        handler.postDelayed(progressTicker, PROGRESS_TICK_MS)
    }

    private fun updateProgress(currentIndex: Int) {
        if (!timeline.isStarted || timeline.isFinished) return
        timeline.update(currentIndex, SystemClock.elapsedRealtime())
        renderProgress()
    }

    private fun finishProgress() {
        if (!timeline.isStarted || timeline.isFinished) return
        timeline.finish(SystemClock.elapsedRealtime())
        renderProgress()
        handler.removeCallbacks(progressTicker)
        handler.postDelayed(finishHideRunnable, PROGRESS_LINGER_MS)
    }

    private fun clearProgressInternal() {
        handler.removeCallbacks(progressTicker)
        handler.removeCallbacks(finishHideRunnable)
        cancelAutoHide()
        removeProgressView()
        timeline.reset()
        handler.removeCallbacks(restoreRunnable)
        setVisible(true)
    }

    private fun createProgressView(steps: List<String>) {
        val density = resources.displayMetrics.density
        val padding = (14 * density).toInt()
        val maxWidth = resources.displayMetrics.widthPixels - (32 * density).toInt()
        val width = min((320 * density).toInt(), maxWidth)
            .coerceAtLeast((200 * density).toInt())

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(0xE6212121.toInt())
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = getString(R.string.floating_progress_title)
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        val count = TextView(this).apply {
            setTextColor(PENDING_COLOR)
            textSize = 13f
            gravity = Gravity.END
        }
        titleRow.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(
            count,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(titleRow)

        container.addView(
            View(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (8 * density).toInt()
            )
        )

        progressLabels.clear()
        progressDurations.clear()
        steps.forEach { label ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val durationView = TextView(this).apply {
                text = "—"
                textSize = 12f
                setTextColor(PENDING_COLOR)
                gravity = Gravity.END
            }
            row.addView(
                labelView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(
                durationView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(row)
            progressLabels.add(labelView)
            progressDurations.add(durationView)
        }

        val totalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val totalLabel = TextView(this).apply {
            text = getString(R.string.floating_progress_total)
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val totalValue = TextView(this).apply {
            text = "—"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.END
        }
        totalRow.addView(
            totalLabel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        totalRow.addView(
            totalValue,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(totalRow)

        val message = TextView(this).apply {
            setTextColor(0xFFFF8A80.toInt())
            textSize = 13f
            visibility = View.GONE
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        container.addView(message)

        progressTitleCount = count
        progressTotal = totalValue
        progressMessage = message

        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (64 * density).toInt()
        }

        if (runCatching { windowManager.addView(container, lp) }.isFailure) return
        progressView = container
    }

    private fun showProgressMessage(message: String) {
        val view = progressMessage ?: return
        view.text = message
        view.visibility = View.VISIBLE
    }

    private fun renderProgress() {
        val now = SystemClock.elapsedRealtime()
        val rows = timeline.rows(now)
        val total = timeline.totalSteps
        val current = timeline.current
        progressTitleCount?.text = "$current/$total"

        rows.forEachIndexed { index, row ->
            val labelView = progressLabels.getOrNull(index) ?: return@forEachIndexed
            val durationView = progressDurations.getOrNull(index) ?: return@forEachIndexed
            labelView.text = "${row.index}/$total  ${row.label}"
            durationView.text = formatDuration(row.durationMs)
            val color = when {
                row.isCurrent -> CURRENT_COLOR
                row.durationMs == null -> PENDING_COLOR
                else -> Color.WHITE
            }
            labelView.setTextColor(color)
            durationView.setTextColor(color)
        }
        progressTotal?.text = formatDuration(timeline.totalDurationMs(now))
    }

    private fun formatDuration(ms: Long?): String {
        if (ms == null) return "—"
        return when {
            ms < 1_000 -> "${ms}ms"
            ms < 60_000 -> String.format(Locale.ROOT, "%.1fs", ms / 1_000.0)
            else -> String.format(
                Locale.ROOT,
                "%dm %02ds",
                ms / 60_000,
                (ms % 60_000) / 1_000
            )
        }
    }

    private fun removeProgressView() {
        progressView?.let { runCatching { windowManager.removeView(it) } }
        progressView = null
        progressTitleCount = null
        progressTotal = null
        progressMessage = null
        progressLabels.clear()
        progressDurations.clear()
    }

    private fun performAction() {
        val options = FloatingButtonController.currentOptions() ?: return
        val started = runCatching {
            if (options.startAsForegroundService) {
                ContextCompat.startForegroundService(this, options.action)
            } else {
                startService(options.action)
            }
        }.isSuccess
        if (!started) return

        // 先启动目标服务（此时悬浮窗仍可见，满足 Android 15+ 的 FGS 豁免），再隐藏按钮。
        setVisible(false)
        if (options.autoRestoreMs > 0) {
            handler.postDelayed(restoreRunnable, options.autoRestoreMs)
        }
    }

    private fun savePosition(x: Int, y: Int) {
        FloatingButtonSettings(this).apply {
            this.x = x
            this.y = y
        }
    }

    private fun snapToEdge(lp: WindowManager.LayoutParams) {
        val mode = FloatingButtonSettings(this).mode
        if (mode == FloatingButtonMode.OFF) return

        val size = buttonView?.width?.takeIf { it > 0 } ?: lp.width
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val maxX = (width - size).coerceAtLeast(0)
        val isLeft = lp.x + size / 2 < width / 2
        val targetX = if (isLeft) 0 else maxX

        lp.y = lp.y.coerceIn(0, (height - size).coerceAtLeast(0))
        savePosition(targetX, lp.y)
        cancelAutoHide()

        val onEnd = if (mode == FloatingButtonMode.EDGE_HIDE) {
            { scheduleAutoHide() }
        } else {
            null
        }

        if (lp.x == targetX) {
            buttonView?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
            onEnd?.invoke()
            return
        }
        animateToX(lp, targetX, EDGE_ANIM_MS, 4f, onEnd)
    }

    private fun hideToHalf() {
        if (FloatingButtonSettings(this).mode != FloatingButtonMode.EDGE_HIDE) return
        if (progressView != null) return

        val view = buttonView ?: return
        if (view.visibility != View.VISIBLE) return
        val lp = buttonParams ?: return

        val size = view.width.takeIf { it > 0 } ?: lp.width
        val width = resources.displayMetrics.widthPixels
        val isLeft = lp.x + size / 2 < width / 2
        val targetX = if (isLeft) -size / 2 else width - size / 2

        savePosition(targetX, lp.y)
        if (lp.x == targetX) return
        animateToX(lp, targetX, EDGE_ANIM_MS, 4f)
    }

    private fun revealIfNeeded(lp: WindowManager.LayoutParams) {
        if (FloatingButtonSettings(this).mode != FloatingButtonMode.EDGE_HIDE) return

        val size = buttonView?.width?.takeIf { it > 0 } ?: lp.width
        val width = resources.displayMetrics.widthPixels
        val maxX = (width - size).coerceAtLeast(0)
        val targetX = if (lp.x + size / 2 < width / 2) 0 else maxX
        if (lp.x == targetX) return

        cancelAutoHide()
        animateToX(lp, targetX, REVEAL_ANIM_MS, 1.5f)
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        if (FloatingButtonSettings(this).mode != FloatingButtonMode.EDGE_HIDE) return
        val view = buttonView ?: return
        if (view.visibility != View.VISIBLE) return
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS)
    }

    private fun cancelAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
    }

    private fun animateToX(
        lp: WindowManager.LayoutParams,
        targetX: Int,
        durationMs: Long = EDGE_ANIM_MS,
        tension: Float = 4f,
        onEnd: (() -> Unit)? = null
    ) {
        val view = buttonView ?: return
        cancelEdgeAnimation()
        val startX = lp.x
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = durationMs
            interpolator = OvershootInterpolator(tension)
            addUpdateListener { animation ->
                lp.x = animation.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, lp) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (edgeAnimator !== animation) return
                    lp.x = targetX
                    runCatching { windowManager.updateViewLayout(view, lp) }
                    edgeAnimator = null
                    onEnd?.invoke()
                }
            })
        }
        edgeAnimator = animator
        animator.start()
    }

    private fun cancelEdgeAnimation() {
        val animator = edgeAnimator
        edgeAnimator = null
        animator?.cancel()
    }

    private fun refreshButtonMode() {
        val mode = FloatingButtonSettings(this).mode
        if (mode == FloatingButtonMode.OFF) {
            stopSelf()
            return
        }
        if (buttonView == null) {
            addButton()
            return
        }
        buttonParams?.let { snapToEdge(it) }
    }

    private inner class DragTouchListener(
        private val view: View,
        private val lp: WindowManager.LayoutParams
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(this@FloatingButtonService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAutoHide()
                    cancelEdgeAnimation()
                    revealIfNeeded(lp)
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                        cancelEdgeAnimation()
                        startX = lp.x
                        startY = lp.y
                        downX = event.rawX
                        downY = event.rawY
                    }
                    if (moved) {
                        lp.x = startX + (event.rawX - downX).toInt()
                        lp.y = startY + (event.rawY - downY).toInt()
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        snapToEdge(lp)
                    } else {
                        cancelEdgeAnimation()
                        performAction()
                    }
                }

                MotionEvent.ACTION_CANCEL -> snapToEdge(lp)
                else -> return false
            }
            return true
        }
    }
}
