package org.caojun.shotocr

import org.caojun.shotocr.classification.classifier.CategoryClassifier
import org.caojun.shotocr.classification.classifier.CategoryRule
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
}
