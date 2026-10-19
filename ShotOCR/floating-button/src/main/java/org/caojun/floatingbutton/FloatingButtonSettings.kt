package org.caojun.floatingbutton

import android.content.Context

internal class FloatingButtonSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
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
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val UNSET = -1
    }
}
