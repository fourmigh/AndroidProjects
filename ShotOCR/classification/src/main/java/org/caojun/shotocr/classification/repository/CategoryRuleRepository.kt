package org.caojun.shotocr.classification.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.caojun.shotocr.classification.classifier.CategoryRule
import org.caojun.shotocr.database.category.DefaultCategoryRules
import org.caojun.shotocr.database.data.AppDatabase
import org.caojun.shotocr.database.data.CategoryRuleDao
import org.caojun.shotocr.database.data.CategoryRuleEntity
import org.json.JSONArray

/**
 * 分类关键词规则的读取与维护。规则从数据库加载并缓存，供 [org.caojun.shotocr.classification.classifier.CategoryClassifier] 匹配。
 */
object CategoryRuleRepository {

    @Volatile
    private var dao: CategoryRuleDao? = null

    @Volatile
    private var cached: List<CategoryRule> = emptyList()

    fun init(context: Context) {
        if (dao == null) {
            synchronized(this) {
                if (dao == null) {
                    val categoryRuleDao =
                        AppDatabase.getInstance(context.applicationContext).categoryRuleDao()
                    dao = categoryRuleDao
                    CoroutineScope(Dispatchers.IO).launch {
                        categoryRuleDao.observeAll().collect { entities ->
                            cached = entities.map { toRule(it) }
                        }
                    }
                }
            }
        }
    }

    fun getRules(): List<CategoryRule> = cached

    /**
     * 确保规则可用：缓存非空直接返回；否则查库；库为空时用内置默认规则兜底。
     * 用于消除"异步缓存未就绪"导致的首次识别失败。
     */
    suspend fun loadRules(): List<CategoryRule> {
        cached.takeIf { it.isNotEmpty() }?.let { return it }
        val entities = dao?.getAll().orEmpty()
        cached = if (entities.isEmpty()) {
            DefaultCategoryRules.rules.map { CategoryRule(it.categoryId, it.keywords, true) }
        } else {
            entities.map { toRule(it) }
        }
        return cached
    }

    suspend fun getByCategory(categoryId: String): CategoryRule? =
        dao?.getByCategory(categoryId)?.let { toRule(it) }

    suspend fun upsert(categoryId: String, keywords: List<String>) {
        dao?.upsert(
            CategoryRuleEntity(
                categoryId = categoryId,
                keywordsJson = keywordsToJson(keywords),
                isDefault = false
            )
        )
    }

    /** 恢复为内置默认关键词（仅内置分类有效）。 */
    suspend fun restoreDefault(categoryId: String) {
        val defaults = DefaultCategoryRules.keywordsFor(categoryId) ?: return
        dao?.upsert(
            CategoryRuleEntity(
                categoryId = categoryId,
                keywordsJson = keywordsToJson(defaults),
                isDefault = true
            )
        )
    }

    private fun toRule(entity: CategoryRuleEntity): CategoryRule =
        CategoryRule(
            categoryId = entity.categoryId,
            keywords = parseKeywords(entity.keywordsJson),
            isDefault = entity.isDefault
        )

    private fun parseKeywords(json: String): List<String> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun keywordsToJson(keywords: List<String>): String {
        val array = JSONArray()
        keywords.forEach { array.put(it) }
        return array.toString()
    }
}
