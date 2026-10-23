package org.caojun.floatingbutton

import android.content.Intent

/** 悬浮按钮配置；点击时启动 [action] 指定的组件。 */
data class FloatingButtonOptions(
    val action: Intent,
    val contentDescription: String? = null,
    val startAsForegroundService: Boolean = true
)
