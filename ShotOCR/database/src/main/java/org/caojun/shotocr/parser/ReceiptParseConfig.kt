package org.caojun.shotocr.parser

data class ReceiptParseConfig(
    val id: Long = 0,
    val name: String = "默认配置",
    val originalAmountKeywords: List<String> = listOf("订单金额", "原价", "原价金额", "商品总价", "总价", "合计金额"),
    val originalAmountPatternPreset: String = "integer_or_decimal",
    val originalAmountRegexCustom: String = "",
    val amountKeywords: List<String> = listOf("实付", "实收", "应付", "支付金额"),
    val amountExcludePatterns: List<String> = listOf("支付时间", "支付方式"),
    val amountPatternPreset: String = "integer_or_decimal",
    val amountRegexCustom: String = "",
    val discountKeywords: List<String> = listOf("优惠", "尤惠", "减免", "折扣", "立减", "满减", "抵扣", "福利", "财神", "红包", "补贴", "返现"),
    val discountExcludeKeywords: List<String> = listOf("领", "就可用", "去查看", "推荐", "服务"),
    val storeNameExcludeKeywords: List<String> = listOf("账单", "详情", "支付", "订单", "交易", "时间", "方式", "说明", "奖励", "收款", "推荐", "服务", "分类", "管理", "贴纸", "解锁"),
    val paymentTimePatternPreset: String = "datetime_full",
    val paymentTimeRegexCustom: String = "",
    val paymentTimeKeywords: List<String> = listOf("支付时间", "扣款时间", "付款时间", "交易时间", "成交时间"),
    val paymentMethodKeywords: List<String> = listOf("付款方式", "寸款方式", "支付方式"),
    val paymentMethodValues: List<String> = listOf("银行卡", "储蓄卡", "信用卡", "花呗", "余额宝", "微信", "支付宝", "网商银行", "微信支付"),
    val orderNumberKeywords: List<String> = listOf("订单号", "交易号", "流水号"),
    val orderNumberPatternPreset: String = "long_digit",
    val orderNumberRegexCustom: String = "",
    val itemPatternPreset: String = "name_price_qty",
    val itemRegexCustom: String = "",
    val isDefault: Boolean = false
) {
    companion object {
        fun default() = ReceiptParseConfig(isDefault = true)
    }
}
