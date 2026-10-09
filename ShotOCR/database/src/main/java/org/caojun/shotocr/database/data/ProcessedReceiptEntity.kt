package org.caojun.shotocr.database.data

import androidx.room.Entity

@Entity(tableName = "processed_receipts", primaryKeys = ["orderNumber", "sourceApp"])
data class ProcessedReceiptEntity(
    val orderNumber: String,
    val sourceApp: String,
    val createdAt: Long = System.currentTimeMillis()
)
