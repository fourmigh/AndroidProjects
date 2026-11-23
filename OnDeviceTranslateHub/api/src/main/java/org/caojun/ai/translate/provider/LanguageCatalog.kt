package org.caojun.ai.translate.provider

/**
 * 语种目录：内部一律使用 BCP-47 规范码，provider 差异在这里归一化。
 *
 * - [en] 用于拼 prompt（Hy-MT 系列要求英文语言名，如 `Cantonese`）
 * - [native] 用于 UI
 * - [ocrReadable] 表示现有 OCR（中英模型）能否直接识别该文字；
 *   为 false 的语种只能手动输入/粘贴
 */
data class Language(
    val code: String,
    val en: String,
    val native: String,
    val script: String,
    val ocrReadable: Boolean = false,
    val dialect: Boolean = false,
)

object LanguageCatalog {

    /** 常用语种 + 主要方言/民汉，按 code 索引。 */
    val all: List<Language> = listOf(
        Language("zh-Hans", "Chinese", "简体中文", "Hans", ocrReadable = true),
        Language("zh-Hant", "Traditional Chinese", "繁體中文", "Hant", ocrReadable = true),
        Language("yue", "Cantonese", "粤语", "Hans", ocrReadable = true, dialect = true),
        Language("bo", "Tibetan", "藏语", "Tibt", dialect = true),
        Language("ug", "Uyghur", "维吾尔语", "Arab", dialect = true),
        Language("mn", "Mongolian", "蒙古语", "Cyrl", dialect = true),
        Language("kk", "Kazakh", "哈萨克语", "Cyrl", dialect = true),
        Language("en", "English", "英语", "Latn", ocrReadable = true),
        Language("ja", "Japanese", "日语", "Jpan"),
        Language("ko", "Korean", "韩语", "Kore"),
        Language("fr", "French", "法语", "Latn"),
        Language("de", "German", "德语", "Latn"),
        Language("es", "Spanish", "西班牙语", "Latn"),
        Language("pt", "Portuguese", "葡萄牙语", "Latn"),
        Language("it", "Italian", "意大利语", "Latn"),
        Language("ru", "Russian", "俄语", "Cyrl"),
        Language("uk", "Ukrainian", "乌克兰语", "Cyrl"),
        Language("pl", "Polish", "波兰语", "Latn"),
        Language("cs", "Czech", "捷克语", "Latn"),
        Language("sk", "Slovak", "斯洛伐克语", "Latn"),
        Language("sl", "Slovenian", "斯洛文尼亚语", "Latn"),
        Language("hr", "Croatian", "克罗地亚语", "Latn"),
        Language("sr", "Serbian", "塞尔维亚语", "Cyrl"),
        Language("bs", "Bosnian", "波斯尼亚语", "Latn"),
        Language("bg", "Bulgarian", "保加利亚语", "Cyrl"),
        Language("ro", "Romanian", "罗马尼亚语", "Latn"),
        Language("hu", "Hungarian", "匈牙利语", "Latn"),
        Language("el", "Greek", "希腊语", "Grek"),
        Language("nl", "Dutch", "荷兰语", "Latn"),
        Language("da", "Danish", "丹麦语", "Latn"),
        Language("sv", "Swedish", "瑞典语", "Latn"),
        Language("nb", "Norwegian", "挪威语", "Latn"),
        Language("fi", "Finnish", "芬兰语", "Latn"),
        Language("is", "Icelandic", "冰岛语", "Latn"),
        Language("et", "Estonian", "爱沙尼亚语", "Latn"),
        Language("lv", "Latvian", "拉脱维亚语", "Latn"),
        Language("lt", "Lithuanian", "立陶宛语", "Latn"),
        Language("tr", "Turkish", "土耳其语", "Latn"),
        Language("ar", "Arabic", "阿拉伯语", "Arab"),
        Language("fa", "Persian", "波斯语", "Arab"),
        Language("he", "Hebrew", "希伯来语", "Hebr"),
        Language("ur", "Urdu", "乌尔都语", "Arab"),
        Language("hi", "Hindi", "印地语", "Deva"),
        Language("bn", "Bengali", "孟加拉语", "Beng"),
        Language("gu", "Gujarati", "古吉拉特语", "Gujr"),
        Language("mr", "Marathi", "马拉地语", "Deva"),
        Language("te", "Telugu", "泰卢固语", "Telu"),
        Language("ta", "Tamil", "泰米尔语", "Taml"),
        Language("kn", "Kannada", "卡纳达语", "Knda"),
        Language("ml", "Malayalam", "马拉雅拉姆语", "Mlym"),
        Language("pa", "Punjabi", "旁遮普语", "Guru"),
        Language("ne", "Nepali", "尼泊尔语", "Deva"),
        Language("si", "Sinhala", "僧伽罗语", "Sinh"),
        Language("th", "Thai", "泰语", "Thai"),
        Language("vi", "Vietnamese", "越南语", "Latn"),
        Language("id", "Indonesian", "印尼语", "Latn"),
        Language("ms", "Malay", "马来语", "Latn"),
        Language("fil", "Filipino", "菲律宾语", "Latn"),
        Language("ceb", "Cebuano", "宿务语", "Latn"),
        Language("jv", "Javanese", "爪哇语", "Latn"),
        Language("uz", "Uzbek", "乌兹别克语", "Latn"),
        Language("km", "Khmer", "高棉语", "Khmr"),
        Language("my", "Burmese", "缅甸语", "Mymr"),
        Language("lo", "Lao", "老挝语", "Laoo"),
        Language("sw", "Swahili", "斯瓦希里语", "Latn"),
        Language("am", "Amharic", "阿姆哈拉语", "Ethi"),
        Language("yo", "Yoruba", "约鲁巴语", "Latn"),
        Language("ig", "Igbo", "伊博语", "Latn"),
        Language("ha", "Hausa", "豪萨语", "Latn"),
        Language("zu", "Zulu", "祖鲁语", "Latn"),
        Language("af", "Afrikaans", "南非荷兰语", "Latn"),
        Language("sq", "Albanian", "阿尔巴尼亚语", "Latn"),
        Language("hy", "Armenian", "亚美尼亚语", "Armn"),
        Language("az", "Azerbaijani", "阿塞拜疆语", "Latn"),
        Language("be", "Belarusian", "白俄罗斯语", "Cyrl"),
        Language("ka", "Georgian", "格鲁吉亚语", "Geor"),
        Language("mk", "Macedonian", "马其顿语", "Cyrl"),
        Language("mt", "Maltese", "马耳他语", "Latn"),
        Language("cy", "Welsh", "威尔士语", "Latn"),
        Language("ga", "Irish", "爱尔兰语", "Latn"),
        Language("gl", "Galician", "加利西亚语", "Latn"),
        Language("eu", "Basque", "巴斯克语", "Latn"),
        Language("ca", "Catalan", "加泰罗尼亚语", "Latn"),
        Language("la", "Latin", "拉丁语", "Latn"),
        Language("eo", "Esperanto", "世界语", "Latn"),
    )

    private val byCode: Map<String, Language> = all.associateBy { it.code }

    /** 原始语种码 → 规范码的别名表。 */
    private val aliases: Map<String, String> = buildMap {
        put("zh", "zh-Hans")
        put("zho", "zh-Hans")
        put("cmn", "zh-Hans")
        put("zh-hans", "zh-Hans")
        put("zh_cn", "zh-Hans")
        put("zh-sg", "zh-Hans")
        put("zh-hant", "zh-Hant")
        put("zh_hant", "zh-Hant")
        put("zho_hant", "zh-Hant")
        put("zh-tw", "zh-Hant")
        put("zh-hk", "zh-Hant")
        put("zh-mo", "zh-Hant")
        put("tl", "fil")
        put("filipino", "fil")
        put("pes", "fa")
        put("fas", "fa")
        put("heb", "he")
        put("mya", "my")
        put("zho_hans", "zh-Hans")
        put("yue_hant", "yue")
        put("uig", "ug")
        put("bod", "bo")
        put("mon", "mn")
        put("kaz", "kk")
        put("nob", "nb")
        put("nor", "nb")
        put("ell", "el")
        put("deu", "de")
        put("fra", "fr")
        put("spa", "es")
        put("por", "pt")
        put("ita", "it")
        put("rus", "ru")
        put("ukr", "uk")
        put("pol", "pl")
        put("ces", "cs")
        put("nld", "nl")
        put("tur", "tr")
        put("ara", "ar")
        put("hin", "hi")
        put("ben", "bn")
        put("guj", "gu")
        put("mar", "mr")
        put("tel", "te")
        put("tam", "ta")
        put("kan", "kn")
        put("mal", "ml")
        put("pan", "pa")
        put("nep", "ne")
        put("sin", "si")
        put("tha", "th")
        put("vie", "vi")
        put("ind", "id")
        put("msa", "ms")
        put("khm", "km")
        put("swa", "sw")
        put("amh", "am")
        put("yor", "yo")
        put("ibo", "ig")
        put("hau", "ha")
        put("zul", "zu")
        put("afr", "af")
        put("sqi", "sq")
        put("hye", "hy")
        put("aze", "az")
        put("bel", "be")
        put("kat", "ka")
        put("mkd", "mk")
        put("mlt", "mt")
        put("cym", "cy")
        put("gle", "ga")
        put("glg", "gl")
        put("eus", "eu")
        put("cat", "ca")
        put("lat", "la")
        put("epo", "eo")
    }

    /**
     * 把 provider 返回的原始码归一化到规范码；无法识别时返回 null（fail-closed：
     * 未知语种宁可不显示，也不要把用户路由到不支持的方向）。
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val key = raw.trim()
        byCode[key]?.let { return it.code }
        aliases[key.lowercase()]?.let { return it }
        // 处理 zh-Hant-TW 这类带后缀的
        val base = key.substringBefore('-').lowercase()
        aliases[base]?.let { return it }
        val hit = all.firstOrNull { it.code.equals(key, ignoreCase = true) }
        return hit?.code
    }

    fun of(code: String?): Language? = code?.let { byCode[it] ?: byCode[normalize(it) ?: ""] }

    fun isDialect(code: String?): Boolean = of(code)?.dialect == true

    fun ocrReadable(code: String?): Boolean = of(code)?.ocrReadable == true

    /** prompt 用的英文名；找不到时回退为 code 本身。 */
    fun englishName(code: String?): String = of(code)?.en ?: code.orEmpty()
}
