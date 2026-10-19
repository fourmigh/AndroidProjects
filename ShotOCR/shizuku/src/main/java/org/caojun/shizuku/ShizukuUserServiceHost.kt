package org.caojun.shizuku

import android.os.IBinder

/**
 * 以 shell/root 身份绑定调用方提供的 UserService。
 * 库本身不提供命令执行接口，提权行为完全由调用方的 UserService 决定。
 */
interface ShizukuUserServiceHost {

    /**
     * 绑定 [serviceClass] 对应的 UserService，在 [block] 中执行操作后自动解绑。
     * 绑定失败或超时返回 null。
     */
    suspend fun <T> withService(
        serviceClass: Class<*>,
        serviceVersion: Int = 1,
        timeoutMs: Long = 10_000L,
        block: suspend (IBinder) -> T
    ): T?
}
