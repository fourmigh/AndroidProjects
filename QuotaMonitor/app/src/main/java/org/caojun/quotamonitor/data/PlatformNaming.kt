package org.caojun.quotamonitor.data

import java.net.URI

/**
 * 平台名称自动推导：直接使用完整网页标题（不做裁剪），其次用 URL 主机名。
 * 用户无需手动填写名称，且能在识别结果页修改。
 */
object PlatformNaming {

    /** 正常标题不会超过该长度；仅对极端超长标题做保护性截断。 */
    const val MAX_LEN = 80

    /**
     * @param title 网页标题（document.title），可为空
     * @param url   平台页面 URL，可为空
     */
    fun derive(title: String?, url: String?): String {
        val cleaned = cleanTitle(title.orEmpty())
        if (cleaned.isNotBlank()) return cleaned
        val host = hostOf(url)
        if (!host.isNullOrBlank()) return host.removePrefix("www.")
        return (url ?: "").trim()
    }

    /** 完整保留网页标题，仅清理首尾空白；超长时做安全截断。 */
    fun cleanTitle(title: String): String {
        val t = title.trim()
        if (t.isEmpty()) return t
        return if (t.length > MAX_LEN) t.take(MAX_LEN).trim() else t
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
