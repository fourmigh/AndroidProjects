package org.caojun.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
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

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                continuation?.takeIf { it.isActive }?.resume(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
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
                        Log.e(TAG, "bindUserService failed", t)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } ?: return null

            return block(binder)
        } finally {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }

    companion object {
        private const val TAG = "ShizukuUserService"
    }
}
