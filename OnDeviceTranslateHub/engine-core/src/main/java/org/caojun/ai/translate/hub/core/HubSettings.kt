package org.caojun.ai.translate.hub.core

import android.content.Context

/** 轻量偏好设置（不引入 DataStore，保持 APK 最小）。 */
class HubSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hub_settings", Context.MODE_PRIVATE)

    var allowMetered: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_METERED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_METERED, value).apply()

    /** 默认模型：Router 在 AUTO 策略下优先使用它。 */
    var defaultModelId: String?
        get() = prefs.getString(KEY_DEFAULT_MODEL, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODEL, value).apply()

    var lastFrom: String
        get() = prefs.getString(KEY_LAST_FROM, "zh-Hans") ?: "zh-Hans"
        set(value) = prefs.edit().putString(KEY_LAST_FROM, value).apply()

    var lastTo: String
        get() = prefs.getString(KEY_LAST_TO, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LAST_TO, value).apply()

    // ---------------------------------------------------------------- 下载任务

    /** 记住进行中的 DownloadManager 任务，进程被杀后可以恢复轮询。 */
    fun setActiveDownload(modelId: String, downloadId: Long) {
        prefs.edit().putLong(KEY_DOWNLOAD_PREFIX + modelId, downloadId).apply()
    }

    fun clearActiveDownload(modelId: String) {
        prefs.edit().remove(KEY_DOWNLOAD_PREFIX + modelId).apply()
    }

    fun activeDownloads(): Map<String, Long> =
        prefs.all
            .filterKeys { it.startsWith(KEY_DOWNLOAD_PREFIX) }
            .mapNotNull { (key, value) ->
                val id = value as? Long ?: return@mapNotNull null
                key.removePrefix(KEY_DOWNLOAD_PREFIX) to id
            }
            .toMap()

    private companion object {
        const val KEY_ALLOW_METERED = "allow_metered"
        const val KEY_DEFAULT_MODEL = "default_model"
        const val KEY_LAST_FROM = "last_from"
        const val KEY_LAST_TO = "last_to"
        const val KEY_DOWNLOAD_PREFIX = "download_"
    }
}
