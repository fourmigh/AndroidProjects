package org.caojun.shotocr.classification.classifier

import org.caojun.shotocr.classification.repository.CategoryRuleRepository

/**
 * 从 OCR 文本推断账单分类。
 * 匹配优先级：商户名 → 商品名 → 全文；命中即返回对应分类 id，否则返回 null。
 */
object CategoryClassifier {

    suspend fun suggest(storeName: String?, rawText: String?): String? =
        classify(storeName, rawText, CategoryRuleRepository.loadRules())

    fun classify(
        storeName: String?,
        rawText: String?,
        rules: List<CategoryRule>
    ): String? {
        matchIn(storeName, rules)?.let { return it }
        matchIn(rawText, rules)?.let { return it }
        return null
    }

    private fun matchIn(text: String?, rules: List<CategoryRule>): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        for (rule in rules) {
            if (rule.keywords.any { it.isNotBlank() && lower.contains(it.lowercase()) }) {
                return rule.categoryId
            }
        }
        return null
    }
}
