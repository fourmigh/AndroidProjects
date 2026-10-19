package org.caojun.shotocr

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import org.caojun.shotocr.capture.CaptureMode
import org.caojun.shotocr.capture.CaptureModeStore

/**
 * 快捷设置磁贴：点击触发一次截屏识别。
 * Shizuku 可用时走 Shizuku 截屏，否则启动现有监听服务（FALLBACK）。
 */
class QuickCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val mode = CaptureModeStore.create(this).resolve()
        val service = if (mode == CaptureMode.SHIZUKU) {
            CaptureProcessService::class.java
        } else {
            ScreenshotService::class.java
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, service))
        } catch (_: Exception) {
        }
        refreshTile()
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
