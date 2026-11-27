package org.caojun.player

import android.content.Context

/** 默认播放器设置，网页里点线路时使用。 */
class PlayerPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var defaultPlayer: PlayerType
        get() = PlayerType.fromStored(prefs.getString(KEY_DEFAULT, null))
        set(value) {
            prefs.edit().putString(KEY_DEFAULT, value.storedName).apply()
        }

    companion object {
        private const val PREFS = "player_settings"
        private const val KEY_DEFAULT = "default_player"
    }
}
