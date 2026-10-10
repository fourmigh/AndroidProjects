package org.caojun.shotocr

import org.caojun.shotocr.classification.classifier.CategoryClassifier
import org.caojun.shotocr.classification.classifier.CategoryRule
import org.caojun.shotocr.database.category.DefaultCategoryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryClassifierTest {

    private val rules = listOf(
        CategoryRule("entertainment.dining", listOf("肯德基", "KFC", "麦当劳", "餐厅")),
        CategoryRule("transport.public", listOf("地铁", "公交")),
        CategoryRule("shopping.fuel", listOf("加油", "中石化")),
        CategoryRule("health.pharmacy", listOf("药店", "药房"))
    )

    @Test
    fun classify_matchesStoreName() {
        assertEquals(
            "entertainment.dining",
            CategoryClassifier.classify("肯德基(上海塘桥餐厅", emptyList(), null, rules)
        )
    }

    @Test
    fun classify_matchesItemName() {
        assertEquals(
            "transport.public",
            CategoryClassifier.classify("某商户", listOf("地铁出行"), null, rules)
        )
    }

    @Test
    fun classify_matchesRawText() {
        assertEquals(
            "shopping.fuel",
            CategoryClassifier.classify(null, emptyList(), "中石化加油站", rules)
        )
    }

    @Test
    fun classify_storeNameHasPriorityOverRawText() {
        assertEquals(
            "health.pharmacy",
            CategoryClassifier.classify("大药房", emptyList(), "地铁站附近", rules)
        )
    }

    @Test
    fun classify_returnsNullWhenNoMatch() {
        assertNull(CategoryClassifier.classify("某某公司", listOf("服务费"), "无关键词", rules))
    }

    private val defaultRules: List<CategoryRule> =
        DefaultCategoryRules.rules.map { CategoryRule(it.categoryId, it.keywords) }

    @Test
    fun classify_defaultRules_newCategories() {
        assertEquals("shopping.clothing", CategoryClassifier.classify("优衣库", emptyList(), null, defaultRules))
        assertEquals("shopping.daily", CategoryClassifier.classify("名创优品", emptyList(), null, defaultRules))
        assertEquals("shopping.digital", CategoryClassifier.classify("小米之家", emptyList(), null, defaultRules))
        assertEquals("life.beauty", CategoryClassifier.classify("理发店", emptyList(), null, defaultRules))
        assertEquals("life.housekeeping", CategoryClassifier.classify(null, emptyList(), "家政保洁服务", defaultRules))
        assertEquals("life.laundry", CategoryClassifier.classify(null, emptyList(), "干洗店", defaultRules))
        assertEquals("transport.travel", CategoryClassifier.classify("携程酒店", emptyList(), null, defaultRules))
        assertEquals("entertainment.parenting", CategoryClassifier.classify(null, emptyList(), "亲子游乐", defaultRules))
        assertEquals("digital_services.ai", CategoryClassifier.classify(null, emptyList(), "ChatGPT 订阅续费", defaultRules))
        assertEquals("digital_services.cloud", CategoryClassifier.classify("阿里云", emptyList(), null, defaultRules))
    }
}
