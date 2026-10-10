package org.caojun.shotocr.database.category

import org.caojun.shotocr.database.data.CategoryRuleEntity
import org.json.JSONArray

/**
 * 内置默认分类关键词规则。
 * categoryId 与 [DefaultCategories] 的子类 id 一致，形如 "entertainment.dining"。
 * 首次建库时写入 category_rules 表；用户可编辑或恢复默认。
 */
object DefaultCategoryRules {

    data class Rule(val categoryId: String, val keywords: List<String>)

    val rules: List<Rule> = listOf(
        Rule(
            "entertainment.dining",
            listOf("肯德基", "KFC", "麦当劳", "星巴克", "餐厅", "饭店", "咖啡", "奶茶", "外卖", "饿了么", "美团外卖", "海底捞", "必胜客", "汉堡", "火锅", "烧烤", "快餐")
        ),
        Rule(
            "entertainment.movie",
            listOf("电影", "影城", "影院", "万达影城", "CGV", "横店", "购票", "电影票")
        ),
        Rule(
            "entertainment.game",
            listOf("游戏", "点券", "Steam", "腾讯游戏", "网易游戏")
        ),
        Rule(
            "entertainment.fitness",
            listOf("健身", "健身房", "游泳", "瑜伽", "球馆")
        ),
        Rule(
            "transport.public",
            listOf("地铁", "公交", "公共交通", "乘车", "出行", "一卡通", "轨道交通", "轮渡")
        ),
        Rule(
            "transport.taxi",
            listOf("打车", "滴滴", "出租车", "网约车", "曹操出行", "高德打车")
        ),
        Rule(
            "transport.parking",
            listOf("停车", "停车场", "泊车", "亭车")
        ),
        Rule(
            "transport.maintenance",
            listOf("汽车保养", "汽车维修", "洗车", "4S店", "汽车服务")
        ),
        Rule(
            "shopping.fuel",
            listOf("加油", "中石化", "中石油", "壳牌", "加油站", "汽油", "柴油")
        ),
        Rule(
            "shopping.grocery",
            listOf("超市", "便利店", "罗森", "全家", "永辉", "盒马", "沃尔玛", "家乐福", "7-11", "711", "菜市场", "生鲜")
        ),
        Rule(
            "shopping.online",
            listOf("淘宝", "天猫", "京东", "拼多多", "唯品会", "网购", "抖音商城")
        ),
        Rule(
            "shopping.retail",
            listOf("商场", "百货", "优衣库", "专卖店", "门店", "屈臣氏")
        ),
        Rule(
            "life.utility",
            listOf("电费", "水费", "燃气", "天然气", "煤气", "水电", "供电", "取暖", "物业费")
        ),
        Rule(
            "life.rent",
            listOf("房租", "租金", "租房")
        ),
        Rule(
            "life.property",
            listOf("物业管理", "物业")
        ),
        Rule(
            "life.telecom",
            listOf("话费", "流量", "中国移动", "中国联通", "中国电信", "宽带", "手机充值", "充值")
        ),
        Rule(
            "health.hospital",
            listOf("医院", "门诊", "挂号", "体检", "诊所", "卫生院")
        ),
        Rule(
            "health.pharmacy",
            listOf("药店", "药房", "大药房", "药品", "医药")
        ),
        Rule(
            "health.dental",
            listOf("牙科", "口腔", "牙齿", "口腔诊所")
        ),
        Rule(
            "health.insurance",
            listOf("保险", "医保")
        ),
        Rule(
            "growth.education",
            listOf("学费", "培训", "教育", "学校", "课程", "辅导", "补习")
        ),
        Rule(
            "growth.book",
            listOf("书店", "图书", "当当", "书籍", "书城")
        ),
        Rule(
            "growth.finance",
            listOf("理财", "基金", "证券", "投资")
        )
    )

    /** categoryId → 内置关键词列表；自定义分类返回 null。 */
    fun keywordsFor(categoryId: String): List<String>? =
        rules.firstOrNull { it.categoryId == categoryId }?.keywords

    fun toEntities(): List<CategoryRuleEntity> = rules.map { rule ->
        CategoryRuleEntity(
            categoryId = rule.categoryId,
            keywordsJson = toJson(rule.keywords),
            isDefault = true
        )
    }

    private fun toJson(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }
}
