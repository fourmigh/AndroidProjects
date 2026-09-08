package org.caojun.shotocr.database.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val screenshotUri: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val screenshotImage: ByteArray? = null,
    val rawText: String,
    val amount: String,
    val discount: String,
    val originalAmount: String,
    val storeName: String,
    val paymentTime: String,
    val paymentMethod: String,
    val orderNumber: String,
    val itemsJson: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceiptEntity) return false
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
                itemsJson == other.itemsJson &&
                createdAt == other.createdAt
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
        result = 31 * result + itemsJson.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
