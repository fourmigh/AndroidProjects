package org.caojun.shotocr.database.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: Long): ReceiptEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM receipts WHERE orderNumber = :orderNumber AND sourceApp = :sourceApp)")
    suspend fun existsByOrderNumber(orderNumber: String, sourceApp: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM receipts WHERE paymentTime = :paymentTime AND sourceApp = :sourceApp)")
    suspend fun existsByPaymentTime(paymentTime: String, sourceApp: String): Boolean

    @Query("SELECT * FROM receipts WHERE orderNumber = :orderNumber AND sourceApp = :sourceApp LIMIT 1")
    suspend fun getByOrderNumber(orderNumber: String, sourceApp: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE paymentTime = :paymentTime AND sourceApp = :sourceApp LIMIT 1")
    suspend fun getByPaymentTime(paymentTime: String, sourceApp: String): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: ReceiptEntity): Long

    @Update
    suspend fun update(receipt: ReceiptEntity)

    @Delete
    suspend fun delete(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()
}
