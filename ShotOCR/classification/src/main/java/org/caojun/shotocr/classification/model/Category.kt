package org.caojun.shotocr.classification.model

import org.caojun.shotocr.database.data.CategoryEntity
import org.json.JSONObject

data class Category(
    /**
     * 兜底主名称。
     * 内置分类存的是 [id] 占位（显示时走资源，读不到本字段）；
     * 自定义分类存的是创建时按当前语言填入的名称。
     * 界面请统一使用 `Category.displayName()`，不要直接读本字段。
     */
    val id: String,
    val name: String,
    /** 各语言名称，键为 `Locale.getDefault().language`（当前仅 zh / en）。 */
    val names: Map<String, String> = emptyMap(),
    val parentId: String?,
    val isDefault: Boolean,
    val sortOrder: Int
) {
    val isTopLevel: Boolean get() = parentId == null
}

/** `namesJson` 形如 `{"zh":"水电煤","en":"Utilities"}`；空串或非法 JSON 视为空表。 */
fun parseNamesJson(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    return try {
        val obj = JSONObject(json)
        buildMap {
            obj.keys().forEach { key -> put(key, obj.getString(key)) }
        }
    } catch (e: Exception) {
        emptyMap()
    }
}

fun namesToJson(names: Map<String, String>): String =
    if (names.isEmpty()) "{}"
    else JSONObject().apply { names.forEach { (k, v) -> put(k, v) } }.toString()

/** 由对话框两栏组装语言表，空白栏不入 map。 */
fun buildNames(zh: String, en: String): Map<String, String> = buildMap {
    if (zh.isNotEmpty()) put("zh", zh)
    if (en.isNotEmpty()) put("en", en)
}

fun CategoryEntity.toCategory(): Category = Category(
    id = id,
    name = name,
    names = parseNamesJson(namesJson),
    parentId = parentId,
    isDefault = isDefault,
    sortOrder = sortOrder
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    namesJson = namesToJson(names),
    parentId = parentId,
    isDefault = isDefault,
    sortOrder = sortOrder
)

/** 大类 + 其子类，供 UI 直接渲染。 */
data class CategoryGroup(
    val category: Category,
    val children: List<Category>
)

fun List<Category>.toGroups(): List<CategoryGroup> {
    val byParent = this.filter { !it.isTopLevel }.groupBy { it.parentId }
    return this.filter { it.isTopLevel }
        .sortedBy { it.sortOrder }
        .map { parent ->
            CategoryGroup(
                category = parent,
                children = byParent[parent.id].orEmpty().sortedBy { it.sortOrder }
            )
        }
}
