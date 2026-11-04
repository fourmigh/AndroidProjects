package org.caojun.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** 通过 Shizuku 绑定调用方提供的 UserService（以 shell/root 身份运行）。 */
class RikkaShizukuUserServiceHost(context: Context) : ShizukuUserServiceHost {

    private val appContext = context.applicationContext

    override suspend fun <T> withService(
        serviceClass: Class<*>,
        serviceVersion: Int,
        timeoutMs: Long,
        block: suspend (IBinder) -> T
    ): T? {
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, serviceClass.name)
        )
            .daemon(false)
            .processNameSuffix(serviceClass.simpleName?.lowercase()?.take(16) ?: "service")
            .version(serviceVersion)

        var continuation: kotlinx.coroutines.CancellableContinuation<IBinder?>? = null
        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "bind start: ${serviceClass.name} timeoutMs=$timeoutMs")

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i(TAG, "connected: ${serviceClass.simpleName}")
                continuation?.takeIf { it.isActive }?.resume(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "disconnected: ${serviceClass.simpleName}")
                continuation?.takeIf { it.isActive }?.resume(null)
            }
        }

        try {
            val binder = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<IBinder?> { cont ->
                    continuation = cont
                    try {
                        Shizuku.bindUserService(args, connection)
                    } catch (t: Throwable) {
                        Log.e(TAG, "bindUserService failed: ${serviceClass.simpleName}", t)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (binder == null) {
                Log.w(
                    TAG,
                    "bind failed or timeout: ${serviceClass.simpleName} elapsedMs=${SystemClock.elapsedRealtime() - started}"
                )
                return null
            }
            Log.i(
                TAG,
                "bind ready: ${serviceClass.simpleName} elapsedMs=${SystemClock.elapsedRealtime() - started}"
            )
            return block(binder)
        } finally {
            val unbind = runCatching { Shizuku.unbindUserService(args, connection, true) }
            unbind.exceptionOrNull()?.let {
                Log.w(TAG, "unbind failed: ${serviceClass.simpleName}", it)
            }
        }
    }

    companion object {
        private const val TAG = "ShizukuUserService"
    }
}
