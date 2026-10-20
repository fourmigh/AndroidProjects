package org.caojun.floatingbutton

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        internal const val ACTION_PROGRESS_SHOW = "org.caojun.floatingbutton.action.PROGRESS_SHOW"
        internal const val ACTION_PROGRESS_UPDATE = "org.caojun.floatingbutton.action.PROGRESS_UPDATE"
        internal const val ACTION_PROGRESS_FINISH = "org.caojun.floatingbutton.action.PROGRESS_FINISH"
        internal const val ACTION_PROGRESS_CLEAR = "org.caojun.floatingbutton.action.PROGRESS_CLEAR"
        internal const val EXTRA_STEPS = "org.caojun.floatingbutton.extra.STEPS"
        internal const val EXTRA_CURRENT_INDEX = "org.caojun.floatingbutton.extra.CURRENT_INDEX"
        internal const val EXTRA_FRACTION = "org.caojun.floatingbutton.extra.FRACTION"

        private const val PROGRESS_TICK_MS = 150L
        private const val PROGRESS_LINGER_MS = 3_000L
        private val PENDING_COLOR = 0x80FFFFFF.toInt()
        private val CURRENT_COLOR = 0xFFFFD54F.toInt()
    }

    private lateinit var windowManager: WindowManager
    private var buttonView: ImageView? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    private var progressView: LinearLayout? = null
    private var progressTitleCount: TextView? = null
    private var progressTotal: TextView? = null
    private val progressLabels = mutableListOf<TextView>()
    private val progressDurations = mutableListOf<TextView>()
    private val timeline = ProgressTimeline()

    private val handler = Handler(Looper.getMainLooper())
    private val restoreRunnable = Runnable { setVisible(true) }
    private val finishHideRunnable = Runnable { clearProgressInternal() }
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
                addAction(ACTION_PROGRESS_SHOW)
                addAction(ACTION_PROGRESS_UPDATE)
                addAction(ACTION_PROGRESS_FINISH)
                addAction(ACTION_PROGRESS_CLEAR)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addButton()
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
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

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val defaultX = maxX - (16 * density).toInt()
            x = (if (settings.x >= 0) settings.x else defaultX).coerceIn(0, maxX)
            y = (if (settings.y >= 0) settings.y else (maxY * 0.4f).toInt()).coerceIn(0, maxY)
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
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showProgress(steps: List<String>, currentIndex: Int) {
        handler.removeCallbacks(restoreRunnable)
        handler.removeCallbacks(finishHideRunnable)
        handler.removeCallbacks(progressTicker)
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

        progressTitleCount = count
        progressTotal = totalValue

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

    private fun savePosition(lp: WindowManager.LayoutParams) {
        FloatingButtonSettings(this).apply {
            x = lp.x
            y = lp.y
        }
    }

    private fun snapToEdge(lp: WindowManager.LayoutParams) {
        val size = buttonView?.width?.takeIf { it > 0 } ?: lp.width
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        lp.x = if (lp.x + size / 2 < width / 2) 0 else (width - size).coerceAtLeast(0)
        lp.y = lp.y.coerceIn(0, (height - size).coerceAtLeast(0))
        buttonView?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
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
                    }
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        snapToEdge(lp)
                        savePosition(lp)
                    } else {
                        performAction()
                    }
                }

                MotionEvent.ACTION_CANCEL -> Unit
                else -> return false
            }
            return true
        }
    }
}
