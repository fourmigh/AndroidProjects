package org.caojun.shotocr.llm

data class ReceiptData(
    val amount: Double?,
    val discount: Double?,
    val originalAmount: Double?,
    val storeName: String?,
    val paymentTime: String?,
    val paymentMethod: String?,
    val orderNumber: String?,
    val rawText: String,
    val sourceApp: String? = null
)
