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
import org.caojun.shotocr.parser.ReceiptParseConfig

@Database(entities = [ReceiptEntity::class, ParserConfigEntity::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun parserConfigDao(): ParserConfigDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shotocr.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).parserConfigDao().let { dao ->
                                    if (dao.count() == 0) {
                                        val defaultConfig = ReceiptParseConfig.default()
                                        dao.insert(ParserConfigEntity.fromConfig(defaultConfig))
                                    }
                                }
                            }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
