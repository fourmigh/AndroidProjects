package org.caojun.webfilm.data

import android.content.Context
import android.net.Uri

/**
 * 轻量设置存储：网站域名与广告拦截开关。
 * 域名可改，方便站点换域名时继续使用。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, value).apply()
        }

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_AD_BLOCK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AD_BLOCK, value).apply()
        }

    /** 网页导航缓存（JSON 数组），避免启动时底部栏闪动。 */
    var navCacheJson: String
        get() = prefs.getString(KEY_NAV_JSON, "").orEmpty()
        private set(value) {
            prefs.edit().putString(KEY_NAV_JSON, value).apply()
        }

    var navCacheBase: String
        get() = prefs.getString(KEY_NAV_BASE, "").orEmpty()
        private set(value) {
            prefs.edit().putString(KEY_NAV_BASE, value).apply()
        }

    /** 底部导航栏透明度，0.3 ~ 1.0。 */
    var navAlpha: Float
        get() = prefs.getFloat(KEY_NAV_ALPHA, 0.82f)
        set(value) {
            prefs.edit().putFloat(KEY_NAV_ALPHA, value).apply()
        }

    /** 用户自定义的栏目顺序，路径以换行分隔。 */
    var navOrder: String
        get() = prefs.getString(KEY_NAV_ORDER, "").orEmpty()
        private set(value) {
            prefs.edit().putString(KEY_NAV_ORDER, value).apply()
        }

    /** 用户勾选的栏目路径，以换行分隔。 */
    var navSelected: String
        get() = prefs.getString(KEY_NAV_SELECTED, "").orEmpty()
        private set(value) {
            prefs.edit().putString(KEY_NAV_SELECTED, value).apply()
        }

    /** 用户是否手动调整过栏目（勾选/排序）；没调整过时按屏幕宽度自动截断。 */
    var navSelectionTouched: Boolean
        get() = prefs.getBoolean(KEY_NAV_TOUCHED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NAV_TOUCHED, value).apply()
        }

    fun saveNavConfig(order: List<String>, selected: Set<String>) {
        prefs.edit()
            .putString(KEY_NAV_ORDER, order.joinToString("\n"))
            .putString(KEY_NAV_SELECTED, selected.joinToString("\n"))
            .apply()
    }

    fun saveNav(baseUrl: String, json: String) {
        prefs.edit()
            .putString(KEY_NAV_JSON, json)
            .putString(KEY_NAV_BASE, baseUrl)
            .apply()
    }

    fun clearNav() {
        prefs.edit()
            .remove(KEY_NAV_JSON)
            .remove(KEY_NAV_BASE)
            .remove(KEY_NAV_ORDER)
            .remove(KEY_NAV_SELECTED)
            .remove(KEY_NAV_TOUCHED)
            .apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.1cbh.cc"

        private const val PREFS = "webfilm_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_AD_BLOCK = "ad_block"
        private const val KEY_NAV_JSON = "nav_json"
        private const val KEY_NAV_BASE = "nav_base"
        private const val KEY_NAV_ALPHA = "nav_alpha"
        private const val KEY_NAV_ORDER = "nav_order"
        private const val KEY_NAV_SELECTED = "nav_selected"
        private const val KEY_NAV_TOUCHED = "nav_touched"

        /** 规范化用户输入的域名；不合法时返回 null。 */
        fun normalize(raw: String): String? {
            var value = raw.trim()
            if (value.isEmpty()) return null
            if (!value.startsWith("http://", ignoreCase = true) &&
                !value.startsWith("https://", ignoreCase = true)
            ) {
                value = "https://$value"
            }
            value = value.trimEnd('/')
            val uri = Uri.parse(value)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host.isBlank() || !host.contains('.')) return null
            return value
        }

        /** 取根域名，例如 https://www.1cbh.cc/a -> 1cbh.cc */
        fun rootDomain(url: String): String =
            (Uri.parse(url).host ?: "").lowercase().removePrefix("www.")
    }
}
