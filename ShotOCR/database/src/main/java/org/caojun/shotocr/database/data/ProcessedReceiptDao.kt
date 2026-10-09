package org.caojun.shotocr.database.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProcessedReceiptDao {

    @Query("SELECT EXISTS(SELECT 1 FROM processed_receipts WHERE orderNumber = :orderNumber AND sourceApp = :sourceApp)")
    suspend fun exists(orderNumber: String, sourceApp: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProcessedReceiptEntity)

    @Query("DELETE FROM processed_receipts WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
