package org.caojun.hanzi

import org.caojun.hanzi.data.LetterLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterLayoutTest {
    @Test
    fun `cols follow size and gap`() {
        assertEquals(6, LetterLayout.colsOf(95, 8))
        assertEquals(14, LetterLayout.colsOf(45, -12))
        assertEquals(3, LetterLayout.colsOf(200, 24))
        assertEquals(2, LetterLayout.colsOf(700, 0))
    }

    @Test
    fun `parse lines wraps by columns and keeps empty paragraphs`() {
        assertEquals(
            listOf(listOf("枯", "藤"), listOf("老")),
            LetterLayout.parseLines("枯藤老", 2),
        )
        assertEquals(
            listOf(listOf("a", "b"), emptyList(), listOf("c", "d")),
            LetterLayout.parseLines("ab\n\ncd", 2),
        )
        assertEquals(emptyList<List<String>>(), LetterLayout.parseLines("", 6).filter { it.isNotEmpty() })
    }

    @Test
    fun `blank cells occupy slots but are not written`() {
        val lines = LetterLayout.parseLines("你好 世界\u3000再见", 4)
        assertEquals(4, LetterLayout.countWritable(LetterLayout.parseLines("你好世界", 2)))
        assertEquals(6, LetterLayout.countWritable(lines))
        assertTrue(LetterLayout.isBlank(" "))
        assertTrue(LetterLayout.isBlank("\u3000"))
        assertFalse(LetterLayout.isBlank("字"))
    }

    @Test
    fun `input limit follows code points`() {
        val text = "字\uD840\uDC00".repeat(4) // 4 个汉字 + 4 个扩展 B 区字
        assertEquals(8, text.codePointCount(0, text.length))
        val limited = LetterLayout.takeCodePoints(text, 3)
        assertEquals(3, limited.codePointCount(0, limited.length))
        assertEquals("字\uD840\uDC00字", limited)
    }
}
