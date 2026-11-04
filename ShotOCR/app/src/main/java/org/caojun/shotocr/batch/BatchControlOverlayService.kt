package org.caojun.shotocr.batch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.caojun.shotocr.R

/** 批量识别期间显示在屏幕顶部的控制条；截图前会临时隐藏。 */
class BatchControlOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var controlView: LinearLayout? = null
    private lateinit var statusView: TextView
    private lateinit var pauseView: TextView
    private lateinit var stopView: TextView

    private var current = 0
    private var success = 0
    private var failed = 0
    private var duplicate = 0
    private var income = 0
    private var skipped = 0
    private var paused = false
    private var orderWaiting = false
    private var hiddenForCapture = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BatchControlOverlayController.ACTION_START,
                BatchControlOverlayController.ACTION_UPDATE -> {
                    applyState(intent)
                }
                BatchControlOverlayController.ACTION_HIDE_FOR_CAPTURE -> {
                    hiddenForCapture = true
                    setVisible(false)
                }
                BatchControlOverlayController.ACTION_SHOW_AFTER_CAPTURE -> {
                    hiddenForCapture = false
                    setVisible(true)
                }
                BatchControlOverlayController.ACTION_REMOVE -> stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createView()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(BatchControlOverlayController.ACTION_START)
                addAction(BatchControlOverlayController.ACTION_UPDATE)
                addAction(BatchControlOverlayController.ACTION_HIDE_FOR_CAPTURE)
                addAction(BatchControlOverlayController.ACTION_SHOW_AFTER_CAPTURE)
                addAction(BatchControlOverlayController.ACTION_REMOVE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let(::applyState)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controlView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, layoutParams()) }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        controlView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        controlView = null
        super.onDestroy()
    }

    private fun createView() {
        val density = resources.displayMetrics.density
        val padding = (10 * density).toInt()
        val buttonPaddingH = (8 * density).toInt()
        val buttonPaddingV = (3 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * density
                setColor(0xDD212121.toInt())
            }
            elevation = 6 * density
        }

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, (2 * density).toInt())
        }
        pauseView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            isClickable = true
            setOnClickListener {
                sendAction(
                    when {
                        orderWaiting -> BatchCaptureService.ACTION_START_ORDER
                        paused -> BatchCaptureService.ACTION_RESUME
                        else -> BatchCaptureService.ACTION_PAUSE
                    }
                )
            }
        }
        stopView = TextView(this).apply {
            setTextColor(0xFFFF8A80.toInt())
            textSize = 12f
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            isClickable = true
            text = getString(R.string.batch_control_stop)
            setOnClickListener { sendAction(BatchCaptureService.ACTION_STOP) }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        buttonRow.addView(
            pauseView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        buttonRow.addView(
            stopView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        controlView = root
        runCatching { windowManager.addView(root, layoutParams()) }
        updateText()
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val statusBarHeight = resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        ).let { id ->
            if (id > 0) resources.getDimensionPixelSize(id) else (24 * density).toInt()
        }
        val width = (resources.displayMetrics.widthPixels * 0.96f).toInt()
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight + (8 * density).toInt()
        }
    }

    private fun applyState(intent: Intent?) {
        if (intent == null) return
        current = intent.getIntExtra(BatchControlOverlayController.EXTRA_CURRENT, current)
        success = intent.getIntExtra(BatchControlOverlayController.EXTRA_SUCCESS, success)
        failed = intent.getIntExtra(BatchControlOverlayController.EXTRA_FAILED, failed)
        duplicate = intent.getIntExtra(BatchControlOverlayController.EXTRA_DUPLICATE, duplicate)
        income = intent.getIntExtra(BatchControlOverlayController.EXTRA_INCOME, income)
        skipped = intent.getIntExtra(BatchControlOverlayController.EXTRA_SKIPPED, skipped)
        paused = intent.getBooleanExtra(BatchControlOverlayController.EXTRA_PAUSED, paused)
        orderWaiting = intent.getBooleanExtra(
            BatchControlOverlayController.EXTRA_ORDER_WAITING,
            orderWaiting
        )
        updateText()
    }

    private fun updateText() {
        statusView.text = when {
            orderWaiting -> getString(R.string.batch_control_waiting_order)
            paused -> getString(
                R.string.batch_control_paused,
                current,
                success,
                duplicate,
                income,
                skipped,
                failed
            )
            else -> getString(
                R.string.batch_control_status,
                current,
                success,
                duplicate,
                income,
                skipped,
                failed
            )
        }
        pauseView.text = getString(
            when {
                orderWaiting -> R.string.batch_control_start
                paused -> R.string.batch_control_resume
                else -> R.string.batch_control_pause
            }
        )
        setVisible(!hiddenForCapture)
    }

    private fun setVisible(visible: Boolean) {
        controlView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun sendAction(action: String) {
        runCatching {
            startService(
                Intent(this, BatchCaptureService::class.java).setAction(action)
            )
        }
    }
}
