package org.caojun.shotocr.classification.ui

import android.content.Context
import org.caojun.shotocr.classification.classifier.CategoryRule
import org.caojun.shotocr.classification.classifier.CategoryTextMatcher
import org.caojun.shotocr.classification.model.Category
import org.caojun.shotocr.database.category.DefaultCategories
import java.util.Locale

/** 分类搜索默认阈值。 */
const val CATEGORY_SEARCH_THRESHOLD = 0.4f

/** 分类的名称（非 Composable，供搜索匹配使用）：用户按语言填的名称 → 任一语言名 → 内置资源 → 兜底。 */
fun categoryName(context: Context, category: Category): String {
    val lang = Locale.getDefault().language
    category.names[lang]?.let { return it }
    category.names.values.firstOrNull()?.let { return it }
    if (category.isDefault) {
        DefaultCategories.nameResId(category.id)?.let { return context.getString(it) }
    }
    return category.name
}

/** 分类与搜索词的匹配分：名称相似度 与 关键词别名 取较大者。 */
fun categoryScore(
    context: Context,
    category: Category,
    query: String,
    rules: List<CategoryRule>
): Double {
    val nameScore = CategoryTextMatcher.nameScore(query, categoryName(context, category))
    val aliasHit = rules.any { rule ->
        rule.categoryId == category.id && rule.keywords.any { keyword ->
            keyword.isNotBlank() &&
                (keyword.contains(query, ignoreCase = true) || query.contains(keyword, ignoreCase = true))
        }
    }
    return maxOf(nameScore, if (aliasHit) 1.0 else 0.0)
}
