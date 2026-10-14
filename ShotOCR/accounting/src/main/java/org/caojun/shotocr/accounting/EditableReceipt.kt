package org.caojun.shotocr.accounting

import android.net.Uri

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
    val sourceApp: String = "",
    val categoryId: String = DEFAULT_CATEGORY_ID
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
                sourceApp == other.sourceApp &&
                categoryId == other.categoryId
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
        result = 31 * result + sourceApp.hashCode()
        result = 31 * result + categoryId.hashCode()
        return result
    }

    companion object {
        const val DEFAULT_CATEGORY_ID = "uncategorized"

        fun empty(): EditableReceipt {
            return EditableReceipt(
                screenshotUri = Uri.EMPTY,
                rawText = ""
            )
        }
    }
}
