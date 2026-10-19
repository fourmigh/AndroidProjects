package org.caojun.floatingbutton

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * 普通绑定服务：显示一个可拖动、可点击的悬浮按钮。
 * 不使用前台服务、不发送常驻通知。
 */
class FloatingButtonService : Service() {

    companion object {
        internal const val ACTION_HIDE = "org.caojun.floatingbutton.action.HIDE"
        internal const val ACTION_SHOW = "org.caojun.floatingbutton.action.SHOW"
    }

    private lateinit var windowManager: WindowManager
    private var buttonView: ImageView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val restoreRunnable = Runnable { setVisible(true) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_HIDE -> setVisible(false)
                ACTION_SHOW -> setVisible(true)
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
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addButton()
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
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
    }

    private fun removeButton() {
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        buttonView = null
    }

    private fun setVisible(visible: Boolean) {
        handler.removeCallbacks(restoreRunnable)
        if (visible && buttonView == null) addButton()
        val view = buttonView ?: return
        view.visibility = if (visible) View.VISIBLE else View.GONE
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
