package org.caojun.shotocr.llm

import org.caojun.shotocr.prefs.MemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionStoreTest {

    @Test
    fun add_list_remove_clear() {
        val store = CorrectionStore(MemoryKeyValueStore())

        store.add(Correction("片段A", "{\"storeName\":\"A\"}"))
        store.add(Correction("片段B", "{\"storeName\":\"B\"}"))
        store.add(Correction("空改动", "{}"))

        assertEquals(2, store.list().size)
        assertEquals("片段A", store.list()[0].rawTextSnippet)

        store.removeAt(0)
        assertEquals(1, store.list().size)
        assertEquals("片段B", store.list()[0].rawTextSnippet)

        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun add_dedupesIdenticalEntries() {
        val store = CorrectionStore(MemoryKeyValueStore())
        val correction = Correction("片段", "{\"a\":1}")

        store.add(correction)
        store.add(correction)

        assertEquals(1, store.list().size)
    }

    @Test
    fun add_capsCountAndSnippetLength() {
        val store = CorrectionStore(MemoryKeyValueStore())

        repeat(CorrectionStore.MAX_COUNT + 3) { i ->
            store.add(Correction("片段$i", "{\"i\":$i}"))
        }
        assertEquals(CorrectionStore.MAX_COUNT, store.list().size)

        val longText = "x".repeat(CorrectionStore.MAX_SNIPPET + 500)
        store.clear()
        store.add(Correction(longText, "{\"storeName\":\"X\"}"))
        assertEquals(CorrectionStore.MAX_SNIPPET, store.list()[0].rawTextSnippet.length)
    }
}
