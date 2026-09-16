package org.caojun.shotocr.patternpicker

enum class PresetType { AMOUNT, TIME, ORDER_NUMBER, ITEM }

object RegexPreset {

    data class Preset(
        val name: String,
        val displayName: String,
        val regex: String,
        val example: String
    )

    val amountPatterns = listOf(
        Preset("integer_or_decimal", "整数或小数", """(\d+\.?\d*)""", "12 或 12.50"),
        Preset("decimal", "小数 (必须带小数点)", """(\d+\.\d+)""", "12.50"),
        Preset("with_sign", "带正负号", """(-?\d+\.?\d+)""", "-5.00"),
        Preset("currency_symbol", "带货币符号", """[¥￥]?\s*(\d+\.?\d+)""", "¥12.50"),
    )

    val timePatterns = listOf(
        Preset("datetime_full", "完整日期时间", """(\d{3,4}[-/年]\d{1,2}[-/月]\d{1,2}[日]?\s*\d{1,2}:\d{2}:\d{2})""", "2024-01-15 14:30:00 或 2024年1月15日14:30:00"),
        Preset("date_only", "仅日期", """(\d{4}[-/]\d{1,2}[-/]\d{1,2})""", "2024-01-15"),
        Preset("time_only", "仅时间", """(\d{1,2}:\d{2}:\d{2})""", "14:30:00"),
        Preset("datetime_compact", "紧凑格式", """(\d{14})""", "20240115143000"),
    )

    val orderNumberPatterns = listOf(
        Preset("long_digit", "长数字串 (≥16位)", """(\d{16,})""", "6222021234567890123"),
        Preset("medium_digit", "数字串 (≥8位)", """(\d{8,})""", "12345678"),
        Preset("alphanumeric", "字母数字混合", """([A-Za-z0-9]{8,})""", "ABC12345"),
    )

    val itemPatterns = listOf(
        Preset("name_price_qty", "商品名 + 单价 × 数量", """(.+?)\s+[¥￥]?\s*(\d+\.?\d*)\s*[xX×]\s*(\d+)""", "苹果 ¥5.00 x 3"),
        Preset("name_qty_price", "商品名 + 数量 × 单价", """(.+?)\s+(\d+)\s*[xX×]\s*[¥￥]?\s*(\d+\.?\d*)""", "苹果 3 x ¥5.00"),
        Preset("name_total", "商品名 + 金额", """(.+?)\s+[¥￥]?\s*(\d+\.?\d+)""", "苹果 ¥15.00"),
    )

    fun getPatterns(type: PresetType): List<Preset> = when (type) {
        PresetType.AMOUNT -> amountPatterns
        PresetType.TIME -> timePatterns
        PresetType.ORDER_NUMBER -> orderNumberPatterns
        PresetType.ITEM -> itemPatterns
    }

    fun resolveRegex(type: PresetType, presetName: String, customRegex: String): String {
        if (customRegex.isNotEmpty()) return customRegex
        return getPatterns(type).find { it.name == presetName }?.regex ?: ""
    }
}
