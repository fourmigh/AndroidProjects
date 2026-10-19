package org.caojun.shotocr

import org.caojun.shizuku.ShizukuGateway
import org.caojun.shotocr.capture.CaptureMode
import org.caojun.shotocr.capture.CaptureModeManager
import org.caojun.shotocr.capture.CapturePreference
import org.caojun.shotocr.prefs.MemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CaptureModeManagerTest {

    private class FakeGateway(
        var running: Boolean,
        var granted: Boolean,
        var installed: Boolean = true
    ) : ShizukuGateway {
        override fun isInstalled(): Boolean = installed
        override fun isRunning(): Boolean = running
        override fun isPermissionGranted(): Boolean = granted
        override fun requestPermission(code: Int) = Unit
        override fun addPermissionResultListener(
            listener: (code: Int, granted: Boolean) -> Unit
        ): () -> Unit = {}
        override fun addBinderDeadListener(listener: () -> Unit): () -> Unit = {}
        override fun addBinderReceivedListenerSticky(listener: () -> Unit): () -> Unit = {}
    }

    @Test
    fun auto_usesShizukuWhenUsable() {
        val manager = CaptureModeManager(FakeGateway(running = true, granted = true), MemoryKeyValueStore())

        assertEquals(CaptureMode.SHIZUKU, manager.resolve())
    }

    @Test
    fun auto_fallsBackWhenNotRunning() {
        val manager = CaptureModeManager(FakeGateway(running = false, granted = true), MemoryKeyValueStore())

        assertEquals(CaptureMode.FALLBACK, manager.resolve())
    }

    @Test
    fun auto_fallsBackWhenNotGranted() {
        val manager = CaptureModeManager(FakeGateway(running = true, granted = false), MemoryKeyValueStore())

        assertEquals(CaptureMode.FALLBACK, manager.resolve())
    }

    @Test
    fun deniedPermission_fallsBackUntilReGranted() {
        val gateway = FakeGateway(running = true, granted = true)
        val manager = CaptureModeManager(gateway, MemoryKeyValueStore())

        manager.onPermissionResult(granted = false)
        gateway.granted = false
        assertEquals(CaptureMode.FALLBACK, manager.resolve())

        manager.onPermissionResult(granted = true)
        gateway.granted = true
        assertEquals(CaptureMode.SHIZUKU, manager.resolve())
    }

    @Test
    fun staleDenial_isClearedWhenActuallyGranted() {
        val gateway = FakeGateway(running = true, granted = true)
        val manager = CaptureModeManager(gateway, MemoryKeyValueStore())
        manager.markShizukuDenied()

        assertEquals(CaptureMode.SHIZUKU, manager.resolve())
        assertFalse(manager.isShizukuDenied())
    }

    @Test
    fun fallbackPreference_alwaysFallsBack() {
        val manager = CaptureModeManager(FakeGateway(running = true, granted = true), MemoryKeyValueStore())
        manager.setPreference(CapturePreference.FALLBACK)

        assertEquals(CaptureMode.FALLBACK, manager.resolve())
    }

    @Test
    fun binderDead_later_fallsBack() {
        val gateway = FakeGateway(running = true, granted = true)
        val manager = CaptureModeManager(gateway, MemoryKeyValueStore())
        assertEquals(CaptureMode.SHIZUKU, manager.resolve())

        gateway.running = false
        assertEquals(CaptureMode.FALLBACK, manager.resolve())
    }
}
