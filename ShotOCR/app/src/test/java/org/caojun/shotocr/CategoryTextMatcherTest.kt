package org.caojun.shotocr

import org.caojun.shotocr.classification.classifier.CategoryTextMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTextMatcherTest {

    @Test
    fun nameScore_substringHits() {
        assertEquals(1.0, CategoryTextMatcher.nameScore("餐饮", "外卖/餐饮"), 0.0001)
        assertEquals(1.0, CategoryTextMatcher.nameScore("旅行", "酒店旅行"), 0.0001)
    }

    @Test
    fun nameScore_typoStillMatches() {
        // 餐钦 vs 餐饮：编辑距离 1，maxLen 2 -> 0.5
        assertTrue(CategoryTextMatcher.nameScore("餐钦", "餐饮") >= 0.4)
    }

    @Test
    fun levenshtein_basic() {
        assertEquals(1, CategoryTextMatcher.levenshtein("餐钦", "餐饮"))
        assertEquals(0, CategoryTextMatcher.levenshtein("餐饮", "餐饮"))
    }

    @Test
    fun jaccard_partial() {
        // {旅,行} vs {酒,店,旅,行} -> 2/4 = 0.5
        assertEquals(0.5, CategoryTextMatcher.jaccard("旅行", "酒店旅行"), 0.0001)
    }

    @Test
    fun nameScore_unrelatedIsLow() {
        assertTrue(CategoryTextMatcher.nameScore("abc", "餐饮") < 0.4)
    }
}
