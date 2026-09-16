package org.caojun.shotocr.accounting

import android.net.Uri
import org.caojun.shotocr.parser.ReceiptData
import org.caojun.shotocr.parser.ReceiptItem

data class EditableReceiptItem(
    val name: String = "",
    val price: String = "",
    val quantity: String = "",
    val subtotal: String = ""
)

data class EditableReceipt(
    val id: Long = 0,
    val screenshotUri: Uri,
    val screenshotImage: ByteArray? = null,
    val rawText: String,
    val amount: String = "",
    val discount: String = "",
    val originalAmount: String = "",
    val storeName: String = "",
    val paymentTime: String = "",
    val paymentMethod: String = "",
    val orderNumber: String = "",
    val items: List<EditableReceiptItem> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditableReceipt) return false
        return id == other.id &&
                screenshotUri == other.screenshotUri &&
                screenshotImage.contentEquals(other.screenshotImage) &&
                rawText == other.rawText &&
                amount == other.amount &&
                discount == other.discount &&
                originalAmount == other.originalAmount &&
                storeName == other.storeName &&
                paymentTime == other.paymentTime &&
                paymentMethod == other.paymentMethod &&
                orderNumber == other.orderNumber &&
                items == other.items
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + screenshotUri.hashCode()
        result = 31 * result + (screenshotImage?.contentHashCode() ?: 0)
        result = 31 * result + rawText.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + discount.hashCode()
        result = 31 * result + originalAmount.hashCode()
        result = 31 * result + storeName.hashCode()
        result = 31 * result + paymentTime.hashCode()
        result = 31 * result + paymentMethod.hashCode()
        result = 31 * result + orderNumber.hashCode()
        result = 31 * result + items.hashCode()
        return result
    }

    companion object {
        fun from(receiptData: ReceiptData, screenshotUri: Uri): EditableReceipt {
            return EditableReceipt(
                screenshotUri = screenshotUri,
                rawText = receiptData.rawText,
                amount = receiptData.amount?.toString() ?: "",
                discount = receiptData.discount?.toString() ?: "",
                originalAmount = receiptData.originalAmount?.toString() ?: "",
                storeName = receiptData.storeName ?: "",
                paymentTime = receiptData.paymentTime ?: "",
                paymentMethod = receiptData.paymentMethod ?: "",
                orderNumber = receiptData.orderNumber ?: "",
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
