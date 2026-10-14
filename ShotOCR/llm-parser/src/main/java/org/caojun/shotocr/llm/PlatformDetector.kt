package org.caojun.shotocr.llm

/**
 * 平台检测：优先用前台应用名，其次用 OCR 文本签名。均不匹配返回 null（由上层回退到通用/自定义）。
 */
object PlatformDetector {

    private val labelSignatures = listOf(
        Platform.TAOBAO to listOf("淘宝", "天猫"),
        Platform.ALIPAY to listOf("支付宝"),
        Platform.WECHAT to listOf("微信"),
        Platform.UNIONPAY to listOf("云闪付"),
        Platform.JD to listOf("京东"),
        Platform.DOUYIN to listOf("抖音")
    )

    fun detectFromLabel(foregroundLabel: String?): Platform? {
        if (foregroundLabel.isNullOrBlank()) return null
        return labelSignatures.firstOrNull { (_, keywords) ->
            keywords.any { foregroundLabel.contains(it) }
        }?.first
    }

    fun detectFromText(ocrText: String): Platform? {
        if (ocrText.isBlank()) return null
        return when {
            ocrText.contains("云闪付交易详情") -> Platform.UNIONPAY
            ocrText.contains("收单机构") && ocrText.contains("清算机构") -> Platform.UNIONPAY
            ocrText.contains("财付通") || ocrText.contains("微信支付") ||
                ocrText.contains("零钱通") ||
                (ocrText.contains("商户单号") && ocrText.contains("交易单号")) -> Platform.WECHAT
            ocrText.contains("淘宝") || ocrText.contains("天猫") || ocrText.contains("进店逛逛") -> Platform.TAOBAO
            ocrText.contains("京东") || ocrText.contains("京豆") -> Platform.JD
            ocrText.contains("抖音支付") || ocrText.contains("抖音月付") -> Platform.DOUYIN
            ocrText.contains("支付宝交易号") || ocrText.contains("花呗") ||
                ocrText.contains("余额宝") || ocrText.contains("账单详情") -> Platform.ALIPAY
            else -> null
        }
    }

    /** 先前台、再文本；均不匹配返回 null。 */
    fun detect(foregroundLabel: String?, ocrText: String): Platform? =
        detectFromLabel(foregroundLabel) ?: detectFromText(ocrText)
}
