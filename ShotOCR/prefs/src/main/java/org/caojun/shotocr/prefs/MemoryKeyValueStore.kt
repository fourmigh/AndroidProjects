package org.caojun.shotocr.prefs

/** [KeyValueStore] 的内存实现，主要用于单元测试。 */
class MemoryKeyValueStore : KeyValueStore {

    private val map = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    override fun getString(key: String, default: String?): String? = map[key] as? String ?: default

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default

    override fun putInt(key: String, value: Int) {
        map[key] = value
    }

    override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default

    override fun putLong(key: String, value: Long) {
        map[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    override fun getFloat(key: String, default: Float): Float = map[key] as? Float ?: default

    override fun putFloat(key: String, value: Float) {
        map[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringList(key: String): List<String> = map[key] as? List<String> ?: emptyList()

    override fun putStringList(key: String, value: List<String>) {
        map[key] = value
    }

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }
}
