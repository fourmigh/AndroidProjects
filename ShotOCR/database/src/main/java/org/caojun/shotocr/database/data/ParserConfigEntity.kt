package org.caojun.shotocr.database.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.caojun.shotocr.parser.ReceiptParseConfig
import org.json.JSONArray

@Entity(tableName = "parser_configs")
data class ParserConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originalAmountKeywordsJson: String,
    val originalAmountPatternPreset: String,
    val originalAmountRegexCustom: String,
    val amountKeywordsJson: String,
    val amountExcludePatternsJson: String,
    val amountPatternPreset: String,
    val amountRegexCustom: String,
    val discountKeywordsJson: String,
    val discountExcludeKeywordsJson: String,
    val storeNameExcludeKeywordsJson: String,
    val paymentTimePatternPreset: String,
    val paymentTimeRegexCustom: String,
    val paymentTimeKeywordsJson: String,
    val paymentMethodKeywordsJson: String,
    val paymentMethodValuesJson: String,
    val orderNumberKeywordsJson: String,
    val orderNumberPatternPreset: String,
    val orderNumberRegexCustom: String,
    val itemPatternPreset: String,
    val itemRegexCustom: String,
    val isDefault: Boolean,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toConfig(): ReceiptParseConfig {
        return ReceiptParseConfig(
            id = id,
            name = name,
            originalAmountKeywords = parseJsonStringList(originalAmountKeywordsJson),
            originalAmountPatternPreset = originalAmountPatternPreset,
            originalAmountRegexCustom = originalAmountRegexCustom,
            amountKeywords = parseJsonStringList(amountKeywordsJson),
            amountExcludePatterns = parseJsonStringList(amountExcludePatternsJson),
            amountPatternPreset = amountPatternPreset,
            amountRegexCustom = amountRegexCustom,
            discountKeywords = parseJsonStringList(discountKeywordsJson),
            discountExcludeKeywords = parseJsonStringList(discountExcludeKeywordsJson),
            storeNameExcludeKeywords = parseJsonStringList(storeNameExcludeKeywordsJson),
            paymentTimePatternPreset = paymentTimePatternPreset,
            paymentTimeRegexCustom = paymentTimeRegexCustom,
            paymentTimeKeywords = parseJsonStringList(paymentTimeKeywordsJson),
            paymentMethodKeywords = parseJsonStringList(paymentMethodKeywordsJson),
            paymentMethodValues = parseJsonStringList(paymentMethodValuesJson),
            orderNumberKeywords = parseJsonStringList(orderNumberKeywordsJson),
            orderNumberPatternPreset = orderNumberPatternPreset,
            orderNumberRegexCustom = orderNumberRegexCustom,
            itemPatternPreset = itemPatternPreset,
            itemRegexCustom = itemRegexCustom,
            isDefault = isDefault
        )
    }

    companion object {
        fun fromConfig(config: ReceiptParseConfig): ParserConfigEntity {
            return ParserConfigEntity(
                id = config.id,
                name = config.name,
                originalAmountKeywordsJson = toJsonString(config.originalAmountKeywords),
                originalAmountPatternPreset = config.originalAmountPatternPreset,
                originalAmountRegexCustom = config.originalAmountRegexCustom,
                amountKeywordsJson = toJsonString(config.amountKeywords),
                amountExcludePatternsJson = toJsonString(config.amountExcludePatterns),
                amountPatternPreset = config.amountPatternPreset,
                amountRegexCustom = config.amountRegexCustom,
                discountKeywordsJson = toJsonString(config.discountKeywords),
                discountExcludeKeywordsJson = toJsonString(config.discountExcludeKeywords),
                storeNameExcludeKeywordsJson = toJsonString(config.storeNameExcludeKeywords),
                paymentTimePatternPreset = config.paymentTimePatternPreset,
                paymentTimeRegexCustom = config.paymentTimeRegexCustom,
                paymentTimeKeywordsJson = toJsonString(config.paymentTimeKeywords),
                paymentMethodKeywordsJson = toJsonString(config.paymentMethodKeywords),
                paymentMethodValuesJson = toJsonString(config.paymentMethodValues),
                orderNumberKeywordsJson = toJsonString(config.orderNumberKeywords),
                orderNumberPatternPreset = config.orderNumberPatternPreset,
                orderNumberRegexCustom = config.orderNumberRegexCustom,
                itemPatternPreset = config.itemPatternPreset,
                itemRegexCustom = config.itemRegexCustom,
                isDefault = config.isDefault
            )
        }

        fun toJsonString(list: List<String>): String {
            val jsonArray = JSONArray()
            list.forEach { jsonArray.put(it) }
            return jsonArray.toString()
        }

        fun parseJsonStringList(json: String): List<String> {
            return try {
                val jsonArray = JSONArray(json)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
