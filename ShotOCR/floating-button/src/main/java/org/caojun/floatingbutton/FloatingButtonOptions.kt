package org.caojun.floatingbutton

import android.content.Intent

/** 悬浮按钮配置；点击时启动 [action] 指定的组件。 */
data class FloatingButtonOptions(
    val action: Intent,
    val iconRes: Int = R.drawable.ic_floating_capture,
    val contentDescription: String? = null,
    val sizeDp: Int = 56,
    val autoRestoreMs: Long = 5_000L,
    val startAsForegroundService: Boolean = true
)
