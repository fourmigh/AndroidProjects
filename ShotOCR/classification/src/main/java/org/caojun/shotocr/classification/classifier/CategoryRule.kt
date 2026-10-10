package org.caojun.shotocr.classification.classifier

/** 单条分类规则：分类 id 与命中关键词列表。 */
data class CategoryRule(
    val categoryId: String,
    val keywords: List<String>,
    val isDefault: Boolean = false
)
