package org.caojun.shotocr.database.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ParserConfigDao {
    @Query("SELECT * FROM parser_configs ORDER BY isDefault DESC, createdAt DESC")
    fun getAll(): Flow<List<ParserConfigEntity>>

    @Query("SELECT * FROM parser_configs WHERE id = :id")
    suspend fun getById(id: Long): ParserConfigEntity?

    @Query("SELECT * FROM parser_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ParserConfigEntity?

    @Query("SELECT * FROM parser_configs WHERE isDefault = 1 LIMIT 1")
    fun getDefaultFlow(): Flow<ParserConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ParserConfigEntity): Long

    @Update
    suspend fun update(config: ParserConfigEntity)

    @Delete
    suspend fun delete(config: ParserConfigEntity)

    @Query("DELETE FROM parser_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE parser_configs SET isDefault = 0")
    suspend fun clearDefault()

    @Query("SELECT COUNT(*) FROM parser_configs")
    suspend fun count(): Int
}
