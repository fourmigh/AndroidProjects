package org.caojun.floatingbutton

/** 悬浮按钮展示模式：关闭 / 贴边隐藏 / 常显。 */
enum class FloatingButtonMode(val id: String) {
    OFF("off"),
    EDGE_HIDE("edge_hide"),
    ALWAYS_SHOW("always_show");

    companion object {
        fun fromId(id: String?): FloatingButtonMode =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}
