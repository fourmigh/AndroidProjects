package org.caojun.ai.translate.provider

import org.json.JSONObject

/** 已安装的单个模型能力。 */
data class InstalledModel(
    val modelId: String,
    val displayName: String,
    val languages: List<String>,
    val dialects: List<String>,
    val quality: Int,
    val speedClass: String,
    val longText: Boolean,
    val maxInputChars: Int,
)

/** provider 能力描述。 */
data class TranslateCapability(
    val apiVersion: Int,
    val providerId: String,
    val displayName: String,
    val modelReady: Boolean,
    val directionMode: String,
    val languages: List<String>,
    val dialects: List<String>,
    val textOnlyLangs: List<String>,
    val installedModels: List<InstalledModel>,
) {

    fun supports(from: String, to: String): Boolean =
        from in languages && to in languages && from != to

    /** 覆盖该方向的最佳模型。 */
    fun bestModelFor(from: String, to: String, policy: TranslateProviderApi.Policy): InstalledModel? {
        val candidates = installedModels.filter { from in it.languages && to in it.languages }
        if (candidates.isEmpty()) return null
        return when (policy) {
            TranslateProviderApi.Policy.QUALITY -> candidates.maxBy { it.quality }
            TranslateProviderApi.Policy.SPEED -> candidates.minBy { speedRank(it.speedClass) }
            TranslateProviderApi.Policy.AUTO -> candidates.maxBy { it.quality }
        }
    }

    companion object {

        fun speedRank(speedClass: String): Int = when (speedClass) {
            "fast" -> 0
            "medium" -> 1
            else -> 2
        }

        /** 解析 provider 返回的 JSON；失败返回 null（视为不可用）。 */
        fun parse(json: String?): TranslateCapability? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(json)
                val api = root.optInt("apiVersion", 1)
                val models = root.optJSONArray("installedModels")
                val installed = buildList {
                    if (models != null) {
                        for (i in 0 until models.length()) {
                            val m = models.optJSONObject(i) ?: continue
                            val langs = m.optJSONArray("languages").toStringList()
                            add(
                                InstalledModel(
                                    modelId = m.optString("modelId"),
                                    displayName = m.optString("displayName"),
                                    languages = langs,
                                    dialects = m.optJSONArray("dialects").toStringList(),
                                    quality = m.optInt("quality", 0),
                                    speedClass = m.optString("speedClass", "medium"),
                                    longText = m.optBoolean("longText", false),
                                    maxInputChars = m.optInt("maxInputChars", 1000),
                                )
                            )
                        }
                    }
                }
                TranslateCapability(
                    apiVersion = api,
                    providerId = root.optString("providerId"),
                    displayName = root.optString("displayName"),
                    modelReady = root.optBoolean("modelReady", false),
                    directionMode = root.optString("directionMode", "any"),
                    languages = root.optJSONArray("languages").toStringList(),
                    dialects = root.optJSONArray("dialects").toStringList(),
                    textOnlyLangs = root.optJSONArray("textOnlyLangs").toStringList(),
                    installedModels = installed,
                )
            }.getOrNull()
        }

        private fun org.json.JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (i in 0 until length()) {
                    val raw = optString(i, "")
                    LanguageCatalog.normalize(raw)?.let { if (it !in this) add(it) }
                }
            }
        }
    }
}

/** 一个已发现且可用的 provider。 */
data class DiscoveredProvider(
    val packageName: String,
    val capability: TranslateCapability,
)
