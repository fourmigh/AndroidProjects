package org.caojun.floatingbutton

import android.content.Context

internal class FloatingButtonSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun config(): FloatingButtonConfig = FloatingButtonConfig(
        sizeDp = prefs.getInt(KEY_SIZE_DP, FloatingButtonConfig.DEFAULT_SIZE_DP),
        edgeTapAction = FloatingButtonEdgeTapAction.fromId(
            prefs.getString(KEY_EDGE_TAP_ACTION, null)
        ),
        edgeAutoHideMs = prefs.getLong(
            KEY_EDGE_AUTO_HIDE_MS,
            FloatingButtonConfig.DEFAULT_EDGE_AUTO_HIDE_MS
        ),
        shape = FloatingButtonShape.fromId(prefs.getString(KEY_SHAPE, null)),
        backgroundColor = prefs.getInt(
            KEY_BACKGROUND_COLOR,
            FloatingButtonConfig.DEFAULT_BACKGROUND_COLOR
        ),
        opacity = prefs.getFloat(KEY_OPACITY, FloatingButtonConfig.DEFAULT_OPACITY),
        icon = FloatingButtonIcon.fromId(prefs.getString(KEY_ICON, null))
    ).normalized()

    fun updateConfig(config: FloatingButtonConfig) {
        val normalized = config.normalized()
        prefs.edit()
            .putInt(KEY_SIZE_DP, normalized.sizeDp)
            .putString(KEY_EDGE_TAP_ACTION, normalized.edgeTapAction.id)
            .putLong(KEY_EDGE_AUTO_HIDE_MS, normalized.edgeAutoHideMs)
            .putString(KEY_SHAPE, normalized.shape.id)
            .putInt(KEY_BACKGROUND_COLOR, normalized.backgroundColor)
            .putFloat(KEY_OPACITY, normalized.opacity)
            .putString(KEY_ICON, normalized.icon.id)
            .apply()
    }

    fun resetPosition() {
        prefs.edit().remove(KEY_X).remove(KEY_Y).apply()
    }

    fun resetConfig() {
        prefs.edit()
            .remove(KEY_SIZE_DP)
            .remove(KEY_EDGE_TAP_ACTION)
            .remove(KEY_EDGE_AUTO_HIDE_MS)
            .remove(KEY_SHAPE)
            .remove(KEY_BACKGROUND_COLOR)
            .remove(KEY_OPACITY)
            .remove(KEY_ICON)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "floating_button"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MODE = "mode"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_SIZE_DP = "size_dp"
        private const val KEY_EDGE_TAP_ACTION = "edge_tap_action"
        private const val KEY_EDGE_AUTO_HIDE_MS = "edge_auto_hide_ms"
        private const val KEY_SHAPE = "shape"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_ICON = "icon"
        internal const val UNSET = -1
    }
}
