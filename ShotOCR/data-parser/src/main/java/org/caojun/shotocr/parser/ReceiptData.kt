package org.caojun.shotocr.parser

data class ReceiptData(
    val amount: Double?,
    val discount: Double?,
    val originalAmount: Double?,
    val storeName: String?,
    val paymentTime: String?,
    val paymentMethod: String?,
    val orderNumber: String?,
    val items: List<ReceiptItem>,
    val rawText: String
)

data class ReceiptItem(
    val name: String,
    val price: Double?,
    val quantity: Int?,
    val subtotal: Double?
)
