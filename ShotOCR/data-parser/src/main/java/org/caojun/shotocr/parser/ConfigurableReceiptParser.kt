package org.caojun.shotocr.parser

import org.caojun.shotocr.patternpicker.PresetType
import org.caojun.shotocr.patternpicker.RegexPreset

/**
 * 可配置解析器：只覆盖"数据来源"（关键词、正则、货币等），
 * 提取算法统一由基类 [ReceiptParser] 提供，避免与基类重复实现。
 */
class ConfigurableReceiptParser(
    private val config: ReceiptParseConfig
) : ReceiptParser() {

    private fun resolveAmount(preset: String, custom: String): Regex =
        Regex(RegexPreset.resolveRegex(PresetType.AMOUNT, preset, custom))

    override fun originalAmountKeywords(): List<String> = config.originalAmountKeywords

    override fun originalAmountPattern(): Regex =
        resolveAmount(config.originalAmountPatternPreset, config.originalAmountRegexCustom)

    override fun amountKeywords(): List<String> = config.amountKeywords

    override fun amountExcludePatterns(): List<String> = config.amountExcludePatterns

    override fun amountPattern(): Regex =
        resolveAmount(config.amountPatternPreset, config.amountRegexCustom)

    override fun discountKeywords(): List<String> =
        (ReceiptParseConfig.default().discountKeywords + config.discountKeywords).distinct()

    override fun discountExcludeKeywords(): List<String> = config.discountExcludeKeywords

    override fun storeNameExcludeKeywords(): List<String> = config.storeNameExcludeKeywords

    override fun paymentTimeRegex(): Regex =
        Regex(RegexPreset.resolveRegex(PresetType.TIME, config.paymentTimePatternPreset, config.paymentTimeRegexCustom))

    override fun paymentTimeKeywords(): List<String> =
        (super.paymentTimeKeywords() + config.paymentTimeKeywords).distinct()

    override fun paymentMethodKeywords(): List<String> = config.paymentMethodKeywords

    override fun paymentMethodValues(): List<String> = config.paymentMethodValues

    override fun orderNumberKeywords(): List<String> = config.orderNumberKeywords

    override fun orderNumberPattern(): Regex =
        Regex(RegexPreset.resolveRegex(PresetType.ORDER_NUMBER, config.orderNumberPatternPreset, config.orderNumberRegexCustom))

    override fun itemPattern(): Regex =
        Regex(RegexPreset.resolveRegex(PresetType.ITEM, config.itemPatternPreset, config.itemRegexCustom))
}
