package org.caojun.quotamonitor.data

import android.content.Context
import org.caojun.quotamonitor.model.MetricMode
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 平台配置的持久化存储：SharedPreferences 保存 JSON 数组。
 * 首次启动从 assets/default_platforms.json 写入默认配置。
 */
class PlatformStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    /** 读取全部配置；首次运行以默认配置初始化。 */
    fun loadAll(): List<PlatformConfig> {
        val raw = prefs.getString(KEY_PLATFORMS, null)
        if (raw.isNullOrBlank()) {
            val defaults = readDefaults()
            saveAll(defaults)
            return defaults
        }
        return runCatching { parseConfigs(JSONObject(raw).getJSONArray("platforms")) }
            .getOrElse { emptyList() }
    }

    fun saveAll(configs: List<PlatformConfig>) {
        val json = JSONObject().apply {
            put("version", VERSION)
            put("platforms", JSONArray().apply { configs.forEach { put(it.toJson()) } })
        }
        prefs.edit().putString(KEY_PLATFORMS, json.toString()).apply()
    }

    /** 恢复内置默认配置（覆盖用户配置）。 */
    fun restoreDefaults(): List<PlatformConfig> {
        val defaults = readDefaults()
        saveAll(defaults)
        return defaults
    }

    /** 按 URL 主机名查找内置模板（用于自动识别时继承套餐名/页面特征等高级规则）。 */
    fun templateForUrl(url: String): PlatformConfig? = matchTemplate(readDefaults(), url)

    fun newId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- 解析 / 序列化

    private fun readDefaults(): List<PlatformConfig> = runCatching {
        val text = appContext.assets.open("default_platforms.json")
            .bufferedReader()
            .use { it.readText() }
            .trimStart('\uFEFF')
        parseConfigs(JSONObject(text).getJSONArray("platforms"))
    }.getOrElse { emptyList() }

    companion object {
        private const val PREFS = "platform_store"
        private const val KEY_PLATFORMS = "platforms"
        private const val VERSION = 1

        /** 在内置模板里按主机名匹配（纯函数，便于测试）。 */
        fun matchTemplate(defaults: List<PlatformConfig>, url: String): PlatformConfig? {
            val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return null
            return defaults.firstOrNull { def ->
                val defHost = runCatching { java.net.URI(def.url).host }.getOrNull()?.lowercase()
                defHost != null && (defHost == host || defHost.endsWith(".$host") || host.endsWith(".$defHost"))
            }
        }

        fun parseConfigs(arr: JSONArray): List<PlatformConfig> {
            val list = mutableListOf<PlatformConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    PlatformConfig(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = o.optString("name"),
                        url = o.optString("url"),
                        enabled = o.optBoolean("enabled", true),
                        loginKeywords = o.optStringList("loginKeywords"),
                        marketingKeywords = o.optStringList("marketingKeywords"),
                        pageMarkerRegex = o.optStringOrNull("pageMarkerRegex"),
                        planNameRegex = o.optStringOrNull("planNameRegex"),
                        planLineRegex = o.optStringOrNull("planLineRegex"),
                        remainKey = o.optStringOrNull("remainKey"),
                        endKey = o.optStringOrNull("endKey"),
                        metrics = parseMetrics(o.optJSONArray("metrics") ?: JSONArray()),
                    )
                )
            }
            return list
        }

        private fun parseMetrics(arr: JSONArray): List<MetricRule> {
            val list = mutableListOf<MetricRule>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    MetricRule(
                        label = o.optString("label"),
                        mode = MetricMode.fromJson(o.optString("mode")),
                        valueRegex = o.optStringOrNull("valueRegex"),
                        resetRegex = o.optStringOrNull("resetRegex"),
                        lookAhead = o.optInt("lookAhead", 6).coerceIn(1, 20),
                    )
                )
            }
            return list
        }

        private fun PlatformConfig.toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("url", url)
            put("enabled", enabled)
            put("loginKeywords", JSONArray(loginKeywords))
            put("marketingKeywords", JSONArray(marketingKeywords))
            put("pageMarkerRegex", pageMarkerRegex ?: JSONObject.NULL)
            put("planNameRegex", planNameRegex ?: JSONObject.NULL)
            put("planLineRegex", planLineRegex ?: JSONObject.NULL)
            put("remainKey", remainKey ?: JSONObject.NULL)
            put("endKey", endKey ?: JSONObject.NULL)
            put("metrics", JSONArray().apply {
                metrics.forEach { m ->
                    put(JSONObject().apply {
                        put("label", m.label)
                        put("mode", m.mode.jsonName)
                        put("valueRegex", m.valueRegex ?: JSONObject.NULL)
                        put("resetRegex", m.resetRegex ?: JSONObject.NULL)
                        put("lookAhead", m.lookAhead)
                    })
                }
            })
        }

        fun JSONObject.optStringList(key: String): List<String> {
            val arr = optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }

        fun JSONObject.optStringOrNull(key: String): String? =
            optString(key).takeIf { it.isNotBlank() && it != "null" }

        /** 各模式的默认取值正则（用户不填时使用）。 */
        fun defaultValueRegex(mode: MetricMode): String? = when (mode) {
            MetricMode.PERCENT_INLINE -> """(\d+(?:\.\d+)?)\s*%"""
            MetricMode.PERCENT_NEXT_NUMBER -> """^\s*(\d+(?:\.\d+)?)\s*%?\s*$"""
            MetricMode.USED_TOTAL -> """(\d[\d,.]*(?:\.\d+)?)\s*(?:/|of|／)\s*(\d[\d,.]*(?:\.\d+)?)"""
        }

        /** 校验：URL 合法、至少一个指标、正则可编译。 */
        fun validate(config: PlatformConfig): ValidationError? {
            if (config.url.isBlank() || !config.url.startsWith("http")) {
                return ValidationError.INVALID_URL
            }
            if (config.name.isBlank()) return ValidationError.EMPTY_NAME
            if (config.metrics.isEmpty()) return ValidationError.NO_METRICS
            val regexes = buildList {
                config.pageMarkerRegex?.let { add(it) }
                config.planNameRegex?.let { add(it) }
                config.planLineRegex?.let { add(it) }
                config.metrics.forEach { m ->
                    m.valueRegex?.let { add(it) }
                    m.resetRegex?.let { add(it) }
                }
            }
            for (r in regexes) {
                if (runCatching { Regex(r) }.isFailure) return ValidationError.INVALID_REGEX
            }
            config.metrics.forEach { m ->
                if (m.label.isBlank()) return ValidationError.EMPTY_METRIC_LABEL
                if ((m.valueRegex ?: defaultValueRegex(m.mode)) == null) {
                    return ValidationError.INVALID_METRIC_MODE
                }
            }
            return null
        }
    }

    enum class ValidationError {
        INVALID_URL,
        EMPTY_NAME,
        NO_METRICS,
        INVALID_REGEX,
        EMPTY_METRIC_LABEL,
        INVALID_METRIC_MODE,
    }
}

private val MetricMode.jsonName: String
    get() = when (this) {
        MetricMode.PERCENT_INLINE -> "percent_inline"
        MetricMode.PERCENT_NEXT_NUMBER -> "percent_next_number"
        MetricMode.USED_TOTAL -> "used_total"
    }
