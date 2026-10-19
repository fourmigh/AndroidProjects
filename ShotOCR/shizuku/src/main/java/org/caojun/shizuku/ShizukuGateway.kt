package org.caojun.shizuku

/**
 * Shizuku 状态与权限访问抽象，便于在单测中注入假实现。
 * 不包含任何命令执行能力。
 */
interface ShizukuGateway {

    /** Shizuku 应用是否已安装（不代表已启动）。 */
    fun isInstalled(): Boolean

    /** Shizuku 是否正在运行（binder 可用）。 */
    fun isRunning(): Boolean

    /** 是否已授予本应用 Shizuku 权限。 */
    fun isPermissionGranted(): Boolean

    fun requestPermission(code: Int)

    /** 注册权限结果监听，返回移除函数。 */
    fun addPermissionResultListener(listener: (code: Int, granted: Boolean) -> Unit): () -> Unit

    fun addBinderDeadListener(listener: () -> Unit): () -> Unit

    fun addBinderReceivedListenerSticky(listener: () -> Unit): () -> Unit
}
