package org.caojun.shotocr.classification.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.caojun.shotocr.classification.classifier.CategoryRule
import org.caojun.shotocr.classification.classifier.CategoryTextMatcher
import org.caojun.shotocr.classification.model.Category
import org.caojun.shotocr.classification.model.CategoryGroup
import org.caojun.shotocr.classification.model.toGroups
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.classification.repository.CategoryRuleRepository
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

/** 统一加载分类关键词规则。 */
@Composable
fun rememberCategoryRules(): List<CategoryRule> {
    var rules by remember { mutableStateOf<List<CategoryRule>>(emptyList()) }
    LaunchedEffect(Unit) { rules = CategoryRuleRepository.loadRules() }
    return rules
}

/** 订阅全部分类（扁平列表）。 */
@Composable
fun rememberCategories(): List<Category> =
    CategoryRepository.observeCategories()
        .collectAsStateWithLifecycle(initialValue = emptyList())
        .value

/** 订阅并按大类分组的分类列表。 */
@Composable
fun rememberCategoryGroups(): List<CategoryGroup> {
    val categories = rememberCategories()
    return remember(categories) { categories.toGroups() }
}

/** 按搜索词匹配分类，返回命中的分类 id 集合（query 为空时返回空集）。 */
fun matchedCategoryIds(
    context: Context,
    categories: List<Category>,
    query: String,
    rules: List<CategoryRule>,
    threshold: Float = CATEGORY_SEARCH_THRESHOLD
): Set<String> {
    val q = query.trim()
    if (q.isEmpty()) return emptySet()
    return categories
        .filter { categoryScore(context, it, q, rules) >= threshold }
        .map { it.id }
        .toSet()
}
