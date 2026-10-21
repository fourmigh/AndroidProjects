package org.caojun.shizuku.autoreturn

import android.content.Intent

/**
 * 配置“启动 Shizuku 后自动返回”的宿主信息。
 *
 * @param targetIntent 返回时启动的目标 Activity Intent（模块会自动补 NEW_TASK 等 flag）。
 * @param isTargetForeground 宿主是否已经在前台。
 * @param showWaitingOverlay 显示等待悬浮窗（用于 Android 15+ 后台启动 Activity 豁免），返回是否成功。
 * @param hideWaitingOverlay 隐藏等待悬浮窗。
 * @param notification 自动返回被拦截时的兜底通知；null 表示不发通知。
 * @param onReturned binder 到达且准备返回时回调（可用于返回后自动请求权限）。
 * @param timeoutMs pending 状态的超时时间。
 * @param fallbackDelayMs 尝试返回后等待多久仍在前台就发兜底通知。
 */
class ShizukuReturnConfig(
    val targetIntent: Intent,
    val isTargetForeground: () -> Boolean,
    val showWaitingOverlay: () -> Boolean = { false },
    val hideWaitingOverlay: () -> Unit = {},
    val notification: ReturnNotificationConfig? = null,
    val onReturned: () -> Unit = {},
    val timeoutMs: Long = 120_000L,
    val fallbackDelayMs: Long = 1_500L
)

/** 兜底通知配置；文案与图标由宿主提供。 */
class ReturnNotificationConfig(
    val channelId: String,
    val channelName: CharSequence,
    val channelDescription: CharSequence,
    val title: CharSequence,
    val text: CharSequence,
    val smallIconRes: Int,
    val notificationId: Int = 1008,
    val allowFullScreenIntent: Boolean = true
)
