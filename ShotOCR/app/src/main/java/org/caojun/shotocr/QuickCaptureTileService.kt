package org.caojun.shotocr

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.caojun.shotocr.capture.CaptureMode
import org.caojun.shotocr.capture.CaptureModeStore
import org.caojun.shotocr.capture.hasStoragePermission

/**
 * 快捷设置磁贴：点击触发一次截屏识别。
 * 仅当用户明确选择“监听截屏”时才启动监听服务。
 */
class QuickCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        when (CaptureModeStore.create(this).resolve()) {
            CaptureMode.SHIZUKU -> startService(CaptureProcessService::class.java)
            CaptureMode.FALLBACK -> {
                if (hasStoragePermission(this)) {
                    startService(ScreenshotService::class.java)
                } else {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.capture_storage_permission_toast,
                            getString(R.string.capture_mode_fallback)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            CaptureMode.UNAVAILABLE -> Toast.makeText(
                this,
                R.string.capture_unavailable_toast,
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshTile()
    }

    private fun startService(service: Class<*>) {
        try {
            ContextCompat.startForegroundService(this, Intent(this, service))
        } catch (_: Exception) {
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val mode = CaptureModeStore.create(this).resolve()
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_camera)
        tile.state = if (mode == CaptureMode.SHIZUKU) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
