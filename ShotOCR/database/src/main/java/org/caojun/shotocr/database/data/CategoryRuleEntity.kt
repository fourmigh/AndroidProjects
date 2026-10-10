package org.caojun.shotocr.database.data

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
    val updatedAt: Long = System.currentTimeMillis()
)
