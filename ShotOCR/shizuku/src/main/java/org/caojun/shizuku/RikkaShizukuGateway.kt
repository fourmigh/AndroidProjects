package org.caojun.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/** 基于 Rikka Shizuku API 的状态/权限实现。 */
class RikkaShizukuGateway(context: Context) : ShizukuGateway {

    private val appContext = context.applicationContext

    override fun isInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }.isSuccess

    override fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    override fun isPermissionGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun requestPermission(code: Int) {
        runCatching { Shizuku.requestPermission(code) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    override fun addPermissionResultListener(
        listener: (code: Int, granted: Boolean) -> Unit
    ): () -> Unit {
        val real = Shizuku.OnRequestPermissionResultListener { code, grantResult ->
            listener(code, grantResult == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(real)
        return { runCatching { Shizuku.removeRequestPermissionResultListener(real) } }
    }

    override fun addBinderDeadListener(listener: () -> Unit): () -> Unit {
        val real = Shizuku.OnBinderDeadListener { listener() }
        Shizuku.addBinderDeadListener(real)
        return { runCatching { Shizuku.removeBinderDeadListener(real) } }
    }

    override fun addBinderReceivedListenerSticky(listener: () -> Unit): () -> Unit {
        val real = Shizuku.OnBinderReceivedListener { listener() }
        Shizuku.addBinderReceivedListenerSticky(real)
        return { runCatching { Shizuku.removeBinderReceivedListener(real) } }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val TAG = "ShizukuGateway"
    }
}
