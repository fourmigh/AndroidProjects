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
    val categoryId: String = DEFAULT_CATEGORY_ID,
    val origin: String = ORIGIN_REVIEWED
) {
    val isAutoBatch: Boolean get() = origin == ORIGIN_AUTO_BATCH

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
                categoryId == other.categoryId &&
                origin == other.origin
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
        result = 31 * result + origin.hashCode()
        return result
    }

    companion object {
        const val DEFAULT_CATEGORY_ID = "uncategorized"
        const val ORIGIN_MANUAL = "manual"
        const val ORIGIN_AUTO_BATCH = "auto_batch"
        const val ORIGIN_REVIEWED = ""

        fun empty(): EditableReceipt {
            return EditableReceipt(
                screenshotUri = Uri.EMPTY,
                rawText = ""
            )
        }
    }
}
