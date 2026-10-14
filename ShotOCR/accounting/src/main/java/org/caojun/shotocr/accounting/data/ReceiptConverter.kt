package org.caojun.shotocr.accounting.data

import android.net.Uri
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.database.data.ReceiptEntity

fun ReceiptEntity.toEditableReceipt(): EditableReceipt {
    return EditableReceipt(
        id = id,
        screenshotUri = if (screenshotUri.isNotEmpty()) Uri.parse(screenshotUri) else Uri.EMPTY,
        screenshotImage = screenshotImage,
        rawText = rawText,
        amount = amount,
        discount = discount,
        originalAmount = originalAmount,
        storeName = storeName,
        paymentTime = paymentTime,
        paymentMethod = paymentMethod,
        orderNumber = orderNumber,
        sourceApp = sourceApp,
        categoryId = categoryId
    )
}

fun EditableReceipt.toEntity(): ReceiptEntity {
    return ReceiptEntity(
        id = id,
        screenshotUri = screenshotUri.toString(),
        screenshotImage = screenshotImage,
        rawText = rawText,
        amount = amount,
        discount = discount,
        originalAmount = originalAmount,
        storeName = storeName,
        paymentTime = paymentTime,
        paymentMethod = paymentMethod,
        orderNumber = orderNumber,
        sourceApp = sourceApp,
        categoryId = categoryId
    )
}
