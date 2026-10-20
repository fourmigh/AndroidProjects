package org.caojun.shotocr.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextNormalizerTest {

    @Test
    fun trimsNoiseBeforeBillAnchor() {
        val raw = """
            13:00:59144H支天福
            日为90
            账单详情
            胡记锅贴
        """.trimIndent()

        val normalized = OcrTextNormalizer.normalize(raw)

        assertTrue(normalized.startsWith("账单详情"))
    }

    @Test
    fun insertsSpaceBetweenDateAndTime() {
        assertEquals(
            "支付时间\n2026-10-20 07:07:57",
            OcrTextNormalizer.normalize("支付时间\n2026-10-2007:07:57")
        )
    }

    @Test
    fun handlesIsoSeparator() {
        assertEquals(
            "2026-09-14 10:00:00",
            OcrTextNormalizer.normalize("2026-09-14T10:00:00")
        )
    }

    @Test
    fun handlesChineseDateTime() {
        assertEquals(
            "2026年9月5日 10:00:00",
            OcrTextNormalizer.normalize("2026年9月5日10:00:00")
        )
    }
}
