package org.caojun.shotocr.database.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 分类关键词规则：把 OCR 文本中的关键词映射到账单分类。
 * 每个分类一条记录，关键词以 JSON 数组存储。
 */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey val categoryId: String,
    val keywordsJson: String,
    val isDefault: Boolean = false,
    /** 匹配优先级，越小越先命中；内置分类按其定义顺序编号，自定义分类排在其后。 */
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
