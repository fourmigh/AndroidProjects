package org.caojun.shotocr.llm

/**
 * 判断一次 OCR 结果是否真的是一张账单。
 *
 * 小模型即使面对非账单截图也会输出 JSON，因此不能仅凭解析结果进入确认页。
 * 这里用 OCR 文本中的账单特征做确定性校验：
 * 1) 必须存在金额信号；
 * 2) 至少命中 3 类账单特征（金额算一类）。
 */
object ReceiptValidator {

    private val TITLE_SIGNALS = listOf(
        "账单详情",
        "全部账单",
        "云闪付交易详情",
        "交易详情",
        "订单详情",
        "订单信息",
        "充值成功",
        "支付成功",
        "支付结果",
        "交易成功"
    )

    private val AMOUNT_SIGNALS = listOf(
        "实付",
        "实付款",
        "实付价",
        "订单金额",
        "商品金额",
        "订单总额",
        "原价",
        "付款金额",
        "支付金额",
        "优惠",
        "立减",
        "红包",
        "补贴",
        "共减",
        "共省"
    )

    private val PAYMENT_SIGNALS = listOf("付款方式", "支付方式")

    private val ORDER_SIGNALS = listOf(
        "订单号",
        "订单编号",
        "交易单号",
        "交易流水号",
        "商户单号",
        "支付宝交易号"
    )

    private val TIME_SIGNALS = listOf("支付时间", "付款时间", "交易时间", "创建时间")

    private val MERCHANT_SIGNALS = listOf("商户名称", "商户名", "收款方全称", "商家名称")

    /** 金额符号或“-8.88”这类带两位小数的负数；避免把 2026-10-23 的 -10 当成金额。 */
    private val CURRENCY_AMOUNT = Regex("""[¥￥]\s*\d""")
    private val SIGNED_DECIMAL = Regex("""-\s*[¥￥]?\s*\d+\.\d{2}""")

    fun isReceipt(rawText: String, data: ReceiptData): Boolean {
        val text = OcrTextNormalizer.normalize(rawText)
        if (text.isBlank()) return false

        val amountSignal = CURRENCY_AMOUNT.containsMatchIn(text) ||
            SIGNED_DECIMAL.containsMatchIn(text) ||
            AMOUNT_SIGNALS.any(text::contains)
        if (!amountSignal) return false

        var groups = 1
        if (TITLE_SIGNALS.any(text::contains) || PlatformDetector.detectFromText(text) != null) {
            groups++
        }
        if (PAYMENT_SIGNALS.any(text::contains)) groups++
        if (ORDER_SIGNALS.any(text::contains) || containsOrderNumber(text, data.orderNumber)) {
            groups++
        }
        if (TIME_SIGNALS.any(text::contains)) groups++
        if (MERCHANT_SIGNALS.any(text::contains) || containsStoreName(text, data.storeName)) {
            groups++
        }
        return groups >= MIN_FEATURE_GROUPS
    }

    private fun containsStoreName(text: String, storeName: String?): Boolean {
        val name = storeName?.trim()?.takeIf { it.length >= 2 } ?: return false
        return text.contains(name)
    }

    private fun containsOrderNumber(text: String, orderNumber: String?): Boolean {
        val number = orderNumber?.trim()?.takeIf { it.length >= 6 } ?: return false
        return text.replace(" ", "").contains(number.replace(" ", ""))
    }

    private const val MIN_FEATURE_GROUPS = 3
}
