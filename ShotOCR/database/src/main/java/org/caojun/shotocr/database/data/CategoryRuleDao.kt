package org.caojun.shotocr.database.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY priority ASC, categoryId ASC")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules ORDER BY priority ASC, categoryId ASC")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules WHERE categoryId = :categoryId")
    suspend fun getByCategory(categoryId: String): CategoryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CategoryRuleEntity>)

    @Query("DELETE FROM category_rules WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: String)

    @Query("SELECT COUNT(*) FROM category_rules")
    suspend fun count(): Int

    @Query("SELECT MAX(priority) FROM category_rules")
    suspend fun maxPriority(): Int?
}
