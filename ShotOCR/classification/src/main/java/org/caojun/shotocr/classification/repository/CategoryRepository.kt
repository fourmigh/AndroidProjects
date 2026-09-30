package org.caojun.shotocr.classification.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.caojun.shotocr.classification.model.Category
import org.caojun.shotocr.classification.model.namesToJson
import org.caojun.shotocr.classification.model.toCategory
import org.caojun.shotocr.classification.model.toEntity
import org.caojun.shotocr.database.category.DefaultCategories
import org.caojun.shotocr.database.data.AppDatabase
import org.caojun.shotocr.database.data.CategoryDao
import org.caojun.shotocr.database.data.CategoryEntity
import java.util.Locale
import java.util.UUID

object CategoryRepository {

    @Volatile
    private var dao: CategoryDao? = null

    fun init(context: Context) {
        if (dao == null) {
            synchronized(this) {
                if (dao == null) {
                    dao = AppDatabase.getInstance(context.applicationContext).categoryDao()
                }
            }
        }
    }

    private fun requireDao(): CategoryDao =
        dao ?: throw IllegalStateException("CategoryRepository.init(context) must be called first")

    fun observeCategories(): Flow<List<Category>> =
        requireDao().observeAll().map { list -> list.map { it.toCategory() } }

    suspend fun getAll(): List<Category> =
        requireDao().getAll().map { it.toCategory() }

    suspend fun ensureDefaults() {
        val categoryDao = requireDao()
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.toEntities())
        }
    }

    suspend fun addTopCategory(names: Map<String, String>): String {
        val categoryDao = requireDao()
        val order = (categoryDao.maxSortOrder(null) ?: -1) + 1
        val id = newCustomId()
        categoryDao.insertAll(
            listOf(
                CategoryEntity(
                    id = id,
                    name = primaryName(names),
                    namesJson = namesToJson(names),
                    parentId = null,
                    isDefault = false,
                    sortOrder = order
                )
            )
        )
        return id
    }

    suspend fun addSubCategory(parentId: String, names: Map<String, String>): String {
        val categoryDao = requireDao()
        val order = (categoryDao.maxSortOrder(parentId) ?: -1) + 1
        val id = DefaultCategories.childId(parentId, newCustomId())
        categoryDao.insertAll(
            listOf(
                CategoryEntity(
                    id = id,
                    name = primaryName(names),
                    namesJson = namesToJson(names),
                    parentId = parentId,
                    isDefault = false,
                    sortOrder = order
                )
            )
        )
        return id
    }

    /**
     * 按各语言名称重命名。
     * 内置分类的 [CategoryEntity.name] 保持 id 占位不变，仅更新 [names]。
     */
    suspend fun rename(category: Category, names: Map<String, String>) {
        val updated = if (category.isDefault) {
            category.copy(names = names)
        } else {
            category.copy(names = names, name = primaryName(names))
        }
        requireDao().update(updated.toEntity())
    }

    /**
     * 清除自定义名称，使内置分类回到 strings.xml 原值。
     * 仅对内置分类生效。
     */
    suspend fun restoreDefaultName(category: Category) {
        if (!category.isDefault) return
        requireDao().update(category.copy(names = emptyMap()).toEntity())
    }

    /** 自定义分类的兜底主名称：当前语言优先，其次任一已填语言。 */
    private fun primaryName(names: Map<String, String>): String =
        names[Locale.getDefault().language] ?: names.values.firstOrNull() ?: ""

    /**
     * 仅允许删除自定义分类。
     * 大类下仍有任何子类时拒绝删除，避免默认子类变成孤儿。
     */
    suspend fun deleteCustom(category: Category): Boolean {
        if (category.isDefault) return false
        val categoryDao = requireDao()
        val hasChildren = categoryDao.getAll().any { it.parentId == category.id }
        if (category.isTopLevel && hasChildren) return false
        categoryDao.deleteCustomById(category.id)
        return true
    }

    private fun newCustomId(): String = "c" + UUID.randomUUID().toString().replace("-", "").take(12)
}
