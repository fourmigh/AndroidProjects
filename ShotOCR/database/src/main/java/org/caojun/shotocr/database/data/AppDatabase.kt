package org.caojun.shotocr.database.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.caojun.shotocr.database.category.DefaultCategories
import org.caojun.shotocr.database.category.DefaultCategoryRules
import org.caojun.shotocr.parser.ReceiptParseConfig

@Database(
    entities = [ReceiptEntity::class, ParserConfigEntity::class, CategoryEntity::class, CategoryRuleEntity::class],
    version = 10
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun parserConfigDao(): ParserConfigDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN screenshotImage BLOB DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS parser_configs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        originalAmountKeywordsJson TEXT NOT NULL,
                        originalAmountRegex TEXT NOT NULL,
                        amountKeywordsJson TEXT NOT NULL,
                        amountExcludePatternsJson TEXT NOT NULL,
                        discountKeywordsJson TEXT NOT NULL,
                        discountExcludeKeywordsJson TEXT NOT NULL,
                        storeNameExcludeKeywordsJson TEXT NOT NULL,
                        paymentTimeRegex TEXT NOT NULL,
                        paymentMethodKeywordsJson TEXT NOT NULL,
                        paymentMethodValuesJson TEXT NOT NULL,
                        orderNumberKeywordsJson TEXT NOT NULL,
                        orderNumberRegex TEXT NOT NULL,
                        itemRegex TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN originalAmountPatternPreset TEXT NOT NULL DEFAULT 'integer_or_decimal'")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN originalAmountRegexCustom TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN paymentTimePatternPreset TEXT NOT NULL DEFAULT 'datetime_full'")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN paymentTimeRegexCustom TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN orderNumberPatternPreset TEXT NOT NULL DEFAULT 'long_digit'")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN orderNumberRegexCustom TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN itemPatternPreset TEXT NOT NULL DEFAULT 'name_price_qty'")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN itemRegexCustom TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN amountPatternPreset TEXT NOT NULL DEFAULT 'integer_or_decimal'")
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN amountRegexCustom TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parser_configs ADD COLUMN paymentTimeKeywordsJson TEXT NOT NULL DEFAULT '[\"支付时间\",\"扣款时间\",\"付款时间\",\"交易时间\",\"成交时间\"]'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        parentId TEXT,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE receipts ADD COLUMN categoryId TEXT NOT NULL DEFAULT 'uncategorized'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE categories ADD COLUMN namesJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN sourceApp TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_rules (
                        categoryId TEXT NOT NULL,
                        keywordsJson TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(categoryId)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shotocr.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val appContext = context.applicationContext
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(appContext)
                                if (database.parserConfigDao().count() == 0) {
                                    database.parserConfigDao()
                                        .insert(ParserConfigEntity.fromConfig(ReceiptParseConfig.default()))
                                }
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
                                // 补齐缺失的内置关键词规则
                                val categoryRuleDao = database.categoryRuleDao()
                                val existingRuleIds = categoryRuleDao.getAll().map { it.categoryId }.toSet()
                                val missingRules =
                                    DefaultCategoryRules.toEntities().filter { it.categoryId !in existingRuleIds }
                                if (missingRules.isNotEmpty()) {
                                    categoryRuleDao.upsertAll(missingRules)
                                }
                            }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
