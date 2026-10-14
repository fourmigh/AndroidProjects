package org.caojun.shotocr.prefs

/**
 * 与业务无关的键值存储抽象。上层业务只依赖此接口，
 * 便于替换实现（SharedPreferences / 内存 / 其他）与单元测试。
 */
interface KeyValueStore {

    fun getString(key: String, default: String? = null): String?

    fun putString(key: String, value: String?)

    fun getInt(key: String, default: Int = 0): Int

    fun putInt(key: String, value: Int)

    fun getLong(key: String, default: Long = 0L): Long

    fun putLong(key: String, value: Long)

    fun getBoolean(key: String, default: Boolean = false): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getFloat(key: String, default: Float = 0f): Float

    fun putFloat(key: String, value: Float)

    fun getStringList(key: String): List<String>

    fun putStringList(key: String, value: List<String>)

    fun contains(key: String): Boolean

    fun remove(key: String)

    fun clear()
}
