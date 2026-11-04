package org.caojun.shotocr.llm

/**
 * 判断一次 OCR 结果是否真的是一张账单。
 *
 * 小模型即使面对非账单截图也会输出 JSON，因此不能仅凭解析结果进入确认页。
 * 这里用 OCR 文本中的账单特征做确定性校验：
 * 1) 必须存在金额信号；
 * 2) 至少命中 3 类账单特征（金额算一类）。
 */
data class ReceiptValidationResult(
    val valid: Boolean,
    val reason: String,
    val amountSignal: Boolean,
    val groups: Int,
    val title: Boolean,
    val payment: Boolean,
    val order: Boolean,
    val time: Boolean,
    val merchant: Boolean
)

object ReceiptValidator {

    private val TITLE_SIGNALS = listOf(
        "账单详情",
        "账单管理",
        "创建时间",
        "商品说明",
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
    private val PLAIN_DECIMAL = Regex("""(?<!\d)\d+(?:,\d{3})*\.\d{2}(?!\d)""")
    private val DETAIL_CONTEXT = listOf("账单详情", "账单管理", "创建时间", "订单号")

    fun isReceipt(rawText: String, data: ReceiptData): Boolean =
        validate(rawText, data).valid

    fun validate(rawText: String, data: ReceiptData): ReceiptValidationResult {
        val text = OcrTextNormalizer.normalize(rawText)
        if (text.isBlank()) {
            return ReceiptValidationResult(
                valid = false,
                reason = "EMPTY_TEXT",
                amountSignal = false,
                groups = 0,
                title = false,
                payment = false,
                order = false,
                time = false,
                merchant = false
            )
        }

        val detailAmount = DETAIL_CONTEXT.any(text::contains) &&
            PLAIN_DECIMAL.containsMatchIn(text)
        val amountSignal = detailAmount ||
            CURRENCY_AMOUNT.containsMatchIn(text) ||
            SIGNED_DECIMAL.containsMatchIn(text) ||
            AMOUNT_SIGNALS.any(text::contains)
        val title = TITLE_SIGNALS.any(text::contains) ||
            PlatformDetector.detectFromText(text) != null
        val payment = PAYMENT_SIGNALS.any(text::contains)
        val order = ORDER_SIGNALS.any(text::contains) ||
            containsOrderNumber(text, data.orderNumber)
        val time = TIME_SIGNALS.any(text::contains)
        val merchant = MERCHANT_SIGNALS.any(text::contains) ||
            containsStoreName(text, data.storeName)

        var groups = 1
        if (title) groups++
        if (payment) groups++
        if (order) groups++
        if (time) groups++
        if (merchant) groups++

        val valid = amountSignal && groups >= MIN_FEATURE_GROUPS
        val reason = when {
            !amountSignal -> "NO_AMOUNT_SIGNAL"
            groups < MIN_FEATURE_GROUPS -> "FEW_SIGNAL_GROUPS"
            else -> "OK"
        }
        return ReceiptValidationResult(
            valid = valid,
            reason = reason,
            amountSignal = amountSignal,
            groups = groups,
            title = title,
            payment = payment,
            order = order,
            time = time,
            merchant = merchant
        )
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
