package org.caojun.shotocr.database.category

import org.caojun.shotocr.database.R
import org.caojun.shotocr.database.data.CategoryEntity
import org.caojun.shotocr.database.data.ReceiptEntity

/**
 * 默认分类体系（国际常见支出类型）。
 * 大类 id 形如 "life"，子类 id 形如 "life.utility"。
 * "uncategorized" 为内置兜底分类，对应 [ReceiptEntity.UNCATEGORIZED_ID]。
 *
 * 内置分类的对外名称不硬编码在此，而是存于 [R.string] 中英文资源，
 * 由 UI 层经 [nameResId] 解析；[toEntities] 写库时 [CategoryEntity.name] 仅存 id 占位。
 */
object DefaultCategories {

    const val UNCATEGORIZED_ID = ReceiptEntity.UNCATEGORIZED_ID

    /** [nameRes] 为内置大类名资源；children 为 子类短 id to 子类名资源。 */
    data class Group(val id: String, val nameRes: Int, val children: List<Pair<String, Int>>)

    val groups: List<Group> = listOf(
        Group(
            id = "life",
            nameRes = R.string.category_life,
            children = listOf(
                "utility" to R.string.category_life_utility,
                "rent" to R.string.category_life_rent,
                "property" to R.string.category_life_property,
                "telecom" to R.string.category_life_telecom,
                "beauty" to R.string.category_life_beauty,
                "errand" to R.string.category_life_errand,
                "housekeeping" to R.string.category_life_housekeeping,
                "repair" to R.string.category_life_repair,
                "laundry" to R.string.category_life_laundry,
                "moving" to R.string.category_life_moving
            )
        ),
        Group(
            id = "shopping",
            nameRes = R.string.category_shopping,
            children = listOf(
                "online" to R.string.category_shopping_online,
                "retail" to R.string.category_shopping_retail,
                "grocery" to R.string.category_shopping_grocery,
                "fuel" to R.string.category_shopping_fuel,
                "clothing" to R.string.category_shopping_clothing,
                "daily" to R.string.category_shopping_daily,
                "digital" to R.string.category_shopping_digital
            )
        ),
        Group(
            id = "transport",
            nameRes = R.string.category_transport,
            children = listOf(
                "public" to R.string.category_transport_public,
                "bike" to R.string.category_transport_bike,
                "taxi" to R.string.category_transport_taxi,
                "maintenance" to R.string.category_transport_maintenance,
                "parking" to R.string.category_transport_parking,
                "travel" to R.string.category_transport_travel
            )
        ),
        Group(
            id = "health",
            nameRes = R.string.category_health,
            children = listOf(
                "hospital" to R.string.category_health_hospital,
                "pharmacy" to R.string.category_health_pharmacy,
                "dental" to R.string.category_health_dental,
                "insurance" to R.string.category_health_insurance
            )
        ),
        Group(
            id = "entertainment",
            nameRes = R.string.category_entertainment,
            children = listOf(
                "dining" to R.string.category_entertainment_dining,
                "movie" to R.string.category_entertainment_movie,
                "game" to R.string.category_entertainment_game,
                "fitness" to R.string.category_entertainment_fitness,
                "parenting" to R.string.category_entertainment_parenting
            )
        ),
        Group(
            id = "growth",
            nameRes = R.string.category_growth,
            children = listOf(
                "education" to R.string.category_growth_education,
                "book" to R.string.category_growth_book,
                "finance" to R.string.category_growth_finance
            )
        ),
        Group(
            id = "digital_services",
            nameRes = R.string.category_digital_services,
            children = listOf(
                "ai" to R.string.category_digital_services_ai,
                "software" to R.string.category_digital_services_software,
                "cloud" to R.string.category_digital_services_cloud,
                "membership" to R.string.category_digital_services_membership
            )
        )
    )

    /** 分类 id → 内置名称资源 id。仅含内置分类，自定义分类不在其中。 */
    val nameResIds: Map<String, Int> = buildMap {
        put(UNCATEGORIZED_ID, R.string.category_uncategorized)
        groups.forEach { group ->
            put(group.id, group.nameRes)
            group.children.forEach { (childId, nameRes) ->
                put(childId(group.id, childId), nameRes)
            }
        }
    }

    /** 返回该内置分类的名称资源 id；自定义分类返回 null。 */
    fun nameResId(id: String): Int? = nameResIds[id]

    fun toEntities(): List<CategoryEntity> {
        val list = mutableListOf<CategoryEntity>()
        var order = 0
        list.add(
            CategoryEntity(
                id = UNCATEGORIZED_ID,
                name = UNCATEGORIZED_ID,
                namesJson = "{}",
                parentId = null,
                isDefault = true,
                sortOrder = order++
            )
        )
        groups.forEach { group ->
            list.add(
                CategoryEntity(
                    id = group.id,
                    name = group.id,
                    namesJson = "{}",
                    parentId = null,
                    isDefault = true,
                    sortOrder = order++
                )
            )
            group.children.forEach { (childId, _) ->
                val id = childId(group.id, childId)
                list.add(
                    CategoryEntity(
                        id = id,
                        name = id,
                        namesJson = "{}",
                        parentId = group.id,
                        isDefault = true,
                        sortOrder = order++
                    )
                )
            }
        }
        return list
    }

    fun childId(groupId: String, childId: String): String = "$groupId.$childId"
}
