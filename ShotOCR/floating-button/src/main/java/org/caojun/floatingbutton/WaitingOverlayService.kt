package org.caojun.floatingbutton

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Binder
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Shizuku 启动期间显示一个极小的可见悬浮点。
 * 目的是让 App 在后台时仍有一个可见悬浮窗，满足 Android 15+
 * “SYSTEM_ALERT_WINDOW + 当前可见悬浮窗”的后台启动 Activity 豁免。
 */
class WaitingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var dotView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addDot()
    }

    override fun onBind(intent: Intent?): IBinder = Binder()

    override fun onDestroy() {
        removeDot()
        super.onDestroy()
    }

    private fun addDot() {
        if (dotView != null) return
        val density = resources.displayMetrics.density
        val size = (10 * density).toInt().coerceAtLeast(1)

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x99FFFFFF.toInt())
            }
        }

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (12 * density).toInt()
        }

        if (runCatching { windowManager.addView(dot, lp) }.isFailure) return
        dotView = dot
    }

    private fun removeDot() {
        dotView?.let { runCatching { windowManager.removeView(it) } }
        dotView = null
    }
}
