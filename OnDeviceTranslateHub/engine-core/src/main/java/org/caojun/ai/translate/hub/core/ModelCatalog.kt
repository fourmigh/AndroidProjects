package org.caojun.ai.translate.hub.core

import android.content.Context
import org.caojun.ai.translate.provider.LanguageCatalog
import org.json.JSONObject

/** 模型许可信息。 */
data class LicenseInfo(
    val name: String,
    val summary: String,
    val requiresAccept: Boolean,
    val url: String,
)

/** 一个待下载文件（多数模型只有一个 gguf）。 */
data class CatalogFile(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
    val urls: List<String>,
)

/** prompt 风格。 */
data class PromptSpec(
    val style: String,
)

/** 采样参数。 */
data class SamplingSpec(
    val temp: Float,
    val topK: Int,
    val topP: Float,
    val repPenalty: Float,
)

/** 引擎要求（用于门控「这个模型当前引擎能不能跑」）。 */
data class EngineReq(
    val llamaArch: List<String>,
    val minEngine: Int,
)

/** 目录中的一个模型。 */
data class CatalogModel(
    val id: String,
    val name: String,
    val vendor: String,
    val params: String,
    val summary: String,
    val langs: List<String>,
    val dialects: List<String>,
    val textOnlyLangs: List<String>,
    val sizeBytes: Long,
    val minRamMb: Int,
    val quality: Int,
    val speedClass: String,
    val longText: Boolean,
    val maxInputChars: Int,
    val recommended: Boolean,
    val license: LicenseInfo,
    val files: List<CatalogFile>,
    val prompt: PromptSpec,
    val sampling: SamplingSpec,
    val postprocess: List<String>,
    val stopTokens: List<String>,
    val engineReq: EngineReq,
    val langsSource: String,
    val verifiedAt: String,
    val available: Boolean,
) {

    /** 该模型是否支持某个规范语种码。 */
    fun supports(code: String): Boolean = code in langs

    /** OCR 能否直接识别（否则只能手动输入）。 */
    fun isTextOnly(code: String): Boolean = code in textOnlyLangs
}

object ModelCatalog {

    private const val ASSET_NAME = "catalog.json"

    @Volatile
    private var cached: List<CatalogModel>? = null

    fun load(context: Context): List<CatalogModel> {
        cached?.let { return it }
        val json = context.assets.open(ASSET_NAME).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        val list = parse(json)
        cached = list
        return list
    }

    fun parse(json: String): List<CatalogModel> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("models") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                runCatching { add(parseModel(o)) }
            }
        }
    }

    private fun parseModel(o: JSONObject): CatalogModel {
        val license = o.optJSONObject("license") ?: JSONObject()
        val prompt = o.optJSONObject("prompt") ?: JSONObject()
        val sampling = o.optJSONObject("sampling") ?: JSONObject()
        val req = o.optJSONObject("engineReq") ?: JSONObject()

        val files = buildList {
            val fa = o.optJSONArray("files") ?: return@buildList
            for (i in 0 until fa.length()) {
                val f = fa.optJSONObject(i) ?: continue
                add(
                    CatalogFile(
                        name = f.optString("name", "model.gguf"),
                        sizeBytes = f.optLong("sizeBytes", 0L),
                        sha256 = f.optString("sha256", ""),
                        urls = buildList {
                            val ua = f.optJSONArray("urls") ?: return@buildList
                            for (j in 0 until ua.length()) add(ua.optString(j))
                        },
                    )
                )
            }
        }

        return CatalogModel(
            id = o.optString("id"),
            name = o.optString("name"),
            vendor = o.optString("vendor"),
            params = o.optString("params"),
            summary = o.optString("summary"),
            langs = o.optJSONArray("langs").normalized(),
            dialects = o.optJSONArray("dialects").normalized(),
            textOnlyLangs = o.optJSONArray("textOnlyLangs").normalized(),
            sizeBytes = o.optLong("sizeBytes", 0L),
            minRamMb = o.optInt("minRamMb", 2048),
            quality = o.optInt("quality", 0),
            speedClass = o.optString("speedClass", "medium"),
            longText = o.optBoolean("longText", false),
            maxInputChars = o.optInt("maxInputChars", 1000),
            recommended = o.optBoolean("recommended", false),
            license = LicenseInfo(
                name = license.optString("name"),
                summary = license.optString("summary"),
                requiresAccept = license.optBoolean("requiresAccept", false),
                url = license.optString("url"),
            ),
            files = files,
            prompt = PromptSpec(style = prompt.optString("style", "plain")),
            sampling = SamplingSpec(
                temp = sampling.optDouble("temp", 0.0).toFloat(),
                topK = sampling.optInt("topK", 1),
                topP = sampling.optDouble("topP", 1.0).toFloat(),
                repPenalty = sampling.optDouble("repPenalty", 1.0).toFloat(),
            ),
            postprocess = o.optJSONArray("postprocess").toStringList(),
            stopTokens = o.optJSONArray("stopTokens").toStringList(),
            engineReq = EngineReq(
                llamaArch = req.optJSONArray("llamaArch").toStringList(),
                minEngine = req.optInt("minEngine", 1),
            ),
            langsSource = o.optString("langsSource"),
            verifiedAt = o.optString("verifiedAt"),
            available = o.optBoolean("available", true),
        )
    }

    private fun org.json.JSONArray?.normalized(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val code = LanguageCatalog.normalize(optString(i, "")) ?: continue
                if (code !in this) add(code)
            }
        }
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(optString(i, ""))
        }
    }
}
