package org.caojun.floatingbutton

import android.content.Context

internal class FloatingButtonSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 展示模式；旧版 enabled=true 迁移为常显，false/未设置迁移为关闭。 */
    var mode: FloatingButtonMode
        get() {
            val id = prefs.getString(KEY_MODE, null)
            if (id != null) return FloatingButtonMode.fromId(id)
            return if (prefs.getBoolean(KEY_ENABLED, false)) {
                FloatingButtonMode.ALWAYS_SHOW
            } else {
                FloatingButtonMode.OFF
            }
        }
        set(value) {
            prefs.edit().putString(KEY_MODE, value.id).apply()
        }

    var x: Int
        get() = prefs.getInt(KEY_X, UNSET)
        set(value) {
            prefs.edit().putInt(KEY_X, value).apply()
        }

    var y: Int
        get() = prefs.getInt(KEY_Y, UNSET)
        set(value) {
            prefs.edit().putInt(KEY_Y, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "floating_button"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "mode"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        internal const val UNSET = -1
    }
}
