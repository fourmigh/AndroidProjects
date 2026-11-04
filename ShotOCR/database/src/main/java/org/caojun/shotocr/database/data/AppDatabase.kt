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

        private const val DATABASE_NAME = "shotocr.db"
        private const val RESET_PREFS = "shotocr_db_reset"
        private const val RESET_KEY = "origin_field_reset_v1"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                resetLegacyDatabaseOnce(appContext)
                Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
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

        /** origin 字段引入时保持 version=1，只在首次运行时删除旧库。 */
        private fun resetLegacyDatabaseOnce(context: Context) {
            val prefs = context.getSharedPreferences(RESET_PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(RESET_KEY, false)) return
            context.deleteDatabase(DATABASE_NAME)
            prefs.edit().putBoolean(RESET_KEY, true).apply()
        }
    }
}
