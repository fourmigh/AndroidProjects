package org.caojun.shotocr.accounting

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import org.caojun.shotocr.parser.ReceiptData
import org.caojun.shotocr.parser.ReceiptItem

data class EditableReceiptItem(
    val name: String = "",
    val price: String = "",
    val quantity: String = "",
    val subtotal: String = ""
)

data class EditableReceipt(
    val screenshotUri: Uri,
    val rawText: String,
    val amount: String = "",
    val discount: String = "",
    val originalAmount: String = "",
    val items: List<EditableReceiptItem> = emptyList()
) {
    companion object {
        fun from(receiptData: ReceiptData, screenshotUri: Uri): EditableReceipt {
            return EditableReceipt(
                screenshotUri = screenshotUri,
                rawText = receiptData.rawText,
                amount = receiptData.amount?.toString() ?: "",
                discount = receiptData.discount?.toString() ?: "",
                originalAmount = receiptData.originalAmount?.toString() ?: "",
                items = receiptData.items.map { item ->
                    EditableReceiptItem(
                        name = item.name,
                        price = item.price?.toString() ?: "",
                        quantity = item.quantity?.toString() ?: "",
                        subtotal = item.subtotal?.toString() ?: ""
                    )
                }
            )
        }

        fun empty(): EditableReceipt {
            return EditableReceipt(
                screenshotUri = Uri.EMPTY,
                rawText = ""
            )
        }
    }
}
