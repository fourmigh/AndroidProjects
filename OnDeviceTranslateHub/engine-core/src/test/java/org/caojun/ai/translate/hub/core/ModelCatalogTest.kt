package org.caojun.ai.translate.hub.core

import org.caojun.ai.translate.provider.LanguageCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * catalog.json 的数据校验。
 *
 * 这些约束是「配置即契约」的守门人：语种码写错、dialects 不在 langs 里、
 * textOnlyLangs 手填错了，都会在构建期失败，而不是等到真机上才发现。
 */
class ModelCatalogTest {

    private val catalogFile: File = File("../app/src/main/assets/catalog.json")

    private fun models(): List<CatalogModel> {
        assertTrue("catalog.json not found at ${catalogFile.absolutePath}", catalogFile.exists())
        return ModelCatalog.parse(catalogFile.readText())
    }

    @Test
    fun catalogParses() {
        val list = models()
        assertTrue("catalog should not be empty", list.isNotEmpty())
        list.forEach {
            assertTrue("id missing", it.id.isNotBlank())
            assertTrue("name missing for ${it.id}", it.name.isNotBlank())
            assertTrue("sizeBytes missing for ${it.id}", it.sizeBytes > 0)
            assertTrue("files missing for ${it.id}", it.files.isNotEmpty())
            assertTrue("urls missing for ${it.id}", it.files.first().urls.isNotEmpty())
        }
    }

    @Test
    fun languagesAreKnownCodes() {
        val known = LanguageCatalog.all.map { it.code }.toSet()
        models().forEach { model ->
            model.langs.forEach { code ->
                assertTrue("${model.id}: unknown language code $code", code in known)
            }
        }
    }

    @Test
    fun dialectsAndTextOnlyAreSubsetOfLangs() {
        models().forEach { model ->
            model.dialects.forEach {
                assertTrue("${model.id}: dialect $it not in langs", it in model.langs)
            }
            model.textOnlyLangs.forEach {
                assertTrue("${model.id}: textOnly $it not in langs", it in model.langs)
            }
        }
    }

    @Test
    fun noLanguageCodeIsSilentlyDropped() {
        val root = org.json.JSONObject(catalogFile.readText())
        val arr = root.getJSONArray("models")
        for (i in 0 until arr.length()) {
            val model = arr.getJSONObject(i)
            val id = model.getString("id")
            val raw = model.getJSONArray("langs")
            for (j in 0 until raw.length()) {
                val code = raw.getString(j)
                assertNotNull(
                    "$id: language '$code' is not in LanguageCatalog (it would be dropped)",
                    LanguageCatalog.normalize(code),
                )
            }
        }
    }

    @Test
    fun everyModelHasAtLeastTwoLanguages() {
        models().forEach {
            assertTrue("${it.id}: need >= 2 languages", it.langs.size >= 2)
        }
    }

    @Test
    fun promptStylesAreSupported() {
        val supported = setOf("plain", "hymt", "index_zh", "plamo3")
        models().forEach {
            assertTrue("${it.id}: unsupported prompt style ${it.prompt.style}", it.prompt.style in supported)
        }
    }

    @Test
    fun samplingIsSane() {
        models().forEach { model ->
            val s = model.sampling
            assertTrue("${model.id}: topK must be >= 1", s.topK >= 1)
            assertTrue("${model.id}: topP in (0, 1]", s.topP > 0f && s.topP <= 1f)
            assertTrue("${model.id}: temp >= 0", s.temp >= 0f)
            assertTrue("${model.id}: repPenalty >= 1", s.repPenalty >= 1f)
        }
    }

    @Test
    fun recommendedModelExists() {
        assertNotNull("at least one model should be recommended", models().firstOrNull { it.recommended })
    }

    @Test
    fun idsAreUnique() {
        val ids = models().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
