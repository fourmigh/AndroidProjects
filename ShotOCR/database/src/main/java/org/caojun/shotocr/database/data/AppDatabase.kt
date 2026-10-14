package org.caojun.shotocr.database.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.caojun.shotocr.database.category.DefaultCategories
import org.caojun.shotocr.database.category.DefaultCategoryRules

@Database(
    entities = [ReceiptEntity::class, CategoryEntity::class, CategoryRuleEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shotocr.db"
                )
                    // 不维护迁移：结构变更时直接重建数据库（升级/降级都允许）。
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val appContext = context.applicationContext
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(appContext)
                                if (database.categoryDao().count() == 0) {
                                    database.categoryDao().insertAll(DefaultCategories.toEntities())
                                }
                                if (database.categoryRuleDao().count() == 0) {
                                    database.categoryRuleDao().upsertAll(DefaultCategoryRules.toEntities())
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            val appContext = context.applicationContext
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(appContext)
                                // 补齐缺失的内置分类（不覆盖用户改名/自定义项）
                                val categoryDao = database.categoryDao()
                                val existingCategoryIds = categoryDao.getAll().map { it.id }.toSet()
                                val missingCategories =
                                    DefaultCategories.toEntities().filter { it.id !in existingCategoryIds }
                                if (missingCategories.isNotEmpty()) {
                                    categoryDao.insertAll(missingCategories)
                                }
                                // 刷新内置关键词规则：缺失的补齐，仍是内置默认（未被用户编辑）的更新为最新定义。
                                // 用户编辑过的规则 isDefault=false，保留不动。
                                val categoryRuleDao = database.categoryRuleDao()
                                val existingRules = categoryRuleDao.getAll().associateBy { it.categoryId }
                                val rulesToUpsert = DefaultCategoryRules.toEntities().filter { default ->
                                    val existing = existingRules[default.categoryId]
                                    existing == null || existing.isDefault
                                }
                                if (rulesToUpsert.isNotEmpty()) {
                                    categoryRuleDao.upsertAll(rulesToUpsert)
                                }
                            }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
