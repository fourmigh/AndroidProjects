package org.caojun.shotocr.translate

/**
 * 本地中英词典：用于已知词条的**精确直译**，命中时无需加载 ONNX 模型。
 *
 * 应用场景：内置分类名（水电煤 / Utilities 等）是固定的，查表比模型更快也更准
 * （模型对短词常给出「公用事业」这类不贴合分类语义的结果）。
 *
 * 未命中时返回 null，调用方应回退到 [TranslateEngine]。
 */
object TranslationDictionary {

    /**
     * 与内置分类名一一对应的中英对照表。
     *
     * 注意：新增/修改内置分类名时需同步维护这里（对应
     * `database/src/main/res/values{,-en}/strings.xml` 中的 `category_*`）。
     */
    private val dictionary: List<Pair<String, String>> = listOf(
        // 大类
        "基础生活" to "Daily Living",
        "购物消费" to "Shopping",
        "交通出行" to "Transport",
        "健康医疗" to "Health & Medical",
        "娱乐生活" to "Entertainment",
        "个人成长" to "Personal Growth",
        // 子类
        "水电煤" to "Utilities",
        "房租/押金" to "Rent & Deposit",
        "物业费" to "Property Fee",
        "网络通信" to "Internet & Phone",
        "线上购物" to "Online Shopping",
        "线下零售" to "In-store Retail",
        "超市/生鲜" to "Groceries",
        "燃油/加油" to "Fuel",
        "公共交通" to "Public Transit",
        "打车/网约车" to "Rideshare & Taxi",
        "车辆维护" to "Vehicle Maintenance",
        "停车费" to "Parking",
        "医院就诊" to "Hospital Visit",
        "药店" to "Pharmacy",
        "牙科/眼科" to "Dental & Vision",
        "保险费用" to "Insurance",
        "外卖/餐饮" to "Dining & Takeout",
        "电影/演出" to "Movies & Shows",
        "游戏/订阅" to "Games & Subscriptions",
        "运动健身" to "Fitness",
        "教育培训" to "Education & Training",
        "图书/学习" to "Books & Learning",
        "理财投资" to "Investments",
        // 新增大类
        "数字服务" to "Digital Services",
        // 新增子类
        "美容美发" to "Beauty & Hair",
        "跑腿代办" to "Errand",
        "家政" to "Housekeeping",
        "维修" to "Repair",
        "洗衣" to "Laundry",
        "搬家" to "Moving",
        "服饰装扮" to "Clothing",
        "日用百货" to "Daily Necessities",
        "数码电器" to "Digital & Appliances",
        "酒店旅行" to "Travel & Hotel",
        "亲子" to "Parenting",
        "AI 服务" to "AI Services",
        "软件订阅" to "Software Subscriptions",
        "云服务" to "Cloud Services",
        "会员充值" to "Memberships & Top-up",
        // 兜底
        "未分类" to "Uncategorized"
    )

    /** 中文 -> 英文。 */
    private val zhToEn: Map<String, String> = buildMap {
        dictionary.forEach { (zh, en) ->
            put(zh, en)
            // 兼容全角斜杠等常见变体
            put(zh.replace('/', '／'), en)
        }
    }

    /** 英文 -> 中文（大小写不敏感）。 */
    private val enToZh: Map<String, String> = buildMap {
        dictionary.forEach { (zh, en) -> put(en.lowercase(), zh) }
    }

    /**
     * 查词典。命中返回译文，未命中返回 null。
     *
     * @param text 源文本（会去除首尾空白）
     * @param from 源语言（[TranslateEngine.LANG_ZH] / [TranslateEngine.LANG_EN]）
     */
    fun lookup(text: String, from: String): String? {
        val key = text.trim()
        if (key.isEmpty()) return null
        return when (from) {
            TranslateEngine.LANG_ZH -> zhToEn[key]
            TranslateEngine.LANG_EN -> enToZh[key.lowercase()]
            else -> null
        }
    }
}
