package org.caojun.shotocr.capture

import android.content.Context
import org.caojun.shizuku.RikkaShizukuGateway
import org.caojun.shizuku.ShizukuGateway
import org.caojun.shotocr.prefs.KeyValueStore
import org.caojun.shotocr.prefs.SharedPrefsStore

/**
 * 截屏方式选择：优先 Shizuku；不可用/被拒绝/binder 失效时自动回退到现有监听方案。
 */
class CaptureModeManager(
    private val gateway: ShizukuGateway,
    private val store: KeyValueStore
) {

    fun preference(): CapturePreference = CapturePreference.fromId(store.getString(KEY_MODE))

    fun setPreference(preference: CapturePreference) {
        store.putString(KEY_MODE, preference.id)
    }

    fun isShizukuDenied(): Boolean = store.getBoolean(KEY_DENIED, false)

    fun markShizukuDenied() = store.putBoolean(KEY_DENIED, true)

    fun clearShizukuDenied() = store.putBoolean(KEY_DENIED, false)

    /** Shizuku 是否可用：binder 存活且已授权（实际授权优先于历史拒绝标记）。 */
    fun isShizukuUsable(): Boolean {
        val usable = gateway.isRunning() && gateway.isPermissionGranted()
        if (usable && isShizukuDenied()) clearShizukuDenied()
        return usable
    }

    fun resolve(): CaptureMode = when (preference()) {
        CapturePreference.FALLBACK -> CaptureMode.FALLBACK
        CapturePreference.SHIZUKU ->
            if (isShizukuUsable()) CaptureMode.SHIZUKU else CaptureMode.UNAVAILABLE
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) clearShizukuDenied() else markShizukuDenied()
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4210
        private const val KEY_MODE = "capture_mode"
        private const val KEY_DENIED = "shizuku_denied"
    }
}

object CaptureModeStore {
    private const val PREFS = "capture_settings"

    fun create(
        context: Context,
        gateway: ShizukuGateway = RikkaShizukuGateway(context.applicationContext)
    ): CaptureModeManager = CaptureModeManager(
        gateway,
        SharedPrefsStore(context.applicationContext, PREFS)
    )
}
