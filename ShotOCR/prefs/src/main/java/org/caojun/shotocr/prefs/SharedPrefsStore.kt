package org.caojun.shotocr.prefs

import android.content.Context
import android.content.SharedPreferences

/** [KeyValueStore] 的 SharedPreferences 实现。 */
class SharedPrefsStore(context: Context, name: String) : KeyValueStore {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = sp.getString(key, default)

    override fun putString(key: String, value: String?) {
        val editor = sp.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    override fun getInt(key: String, default: Int): Int = sp.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, default: Long): Long = sp.getLong(key, default)

    override fun putLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    override fun getFloat(key: String, default: Float): Float = sp.getFloat(key, default)

    override fun putFloat(key: String, value: Float) {
        sp.edit().putFloat(key, value).apply()
    }

    override fun getStringList(key: String): List<String> =
        sp.getStringSet(key, emptySet())?.toList().orEmpty()

    override fun putStringList(key: String, value: List<String>) {
        sp.edit().putStringSet(key, value.toSet()).apply()
    }

    override fun contains(key: String): Boolean = sp.contains(key)

    override fun remove(key: String) {
        sp.edit().remove(key).apply()
    }

    override fun clear() {
        sp.edit().clear().apply()
    }
}
