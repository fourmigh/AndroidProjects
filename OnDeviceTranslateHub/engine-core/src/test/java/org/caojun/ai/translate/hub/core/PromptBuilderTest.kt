package org.caojun.ai.translate.hub.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * prompt 拼装的格式校验。
 *
 * PLaMo 3 的 PFML3 结构一旦写错，模型会直接输出 EOS（表现为「没有有效结果」），
 * 所以这里把格式钉死。
 */
class PromptBuilderTest {

    private fun model(style: String): CatalogModel = ModelCatalog.parse(
        """
        {"models":[{
          "id":"t","name":"t","langs":["en","zh-Hans"],
          "sizeBytes":1,
          "files":[{"name":"m","sizeBytes":1,"urls":["https://example.com/m"]}],
          "prompt":{"style":"$style"},
          "sampling":{}
        }]}
        """.trimIndent()
    ).first()

    @Test
    fun plamo3UsesPfml3Structure() {
        val prompt = PromptBuilder.buildSelfContained(model("plamo3"), "Hello", "en", "zh-Hans")
        assertEquals(
            "<|plamo:bos|>" +
                "<|plamo:tag|>dataset<|plamo:msg|>translation" +
                "<|plamo:tag|>input<|plamo:key|>lang<|plamo:val|>en" +
                "<|plamo:msg|>Hello" +
                "<|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>zh<|plamo:msg|>",
            prompt,
        )
    }

    @Test
    fun plamo3MapsFilipinoToTl() {
        val prompt = PromptBuilder.buildSelfContained(model("plamo3"), "Hi", "en", "fil")!!
        assertTrue("expected tl language code", prompt.contains("<|plamo:val|>tl<|plamo:msg|>"))
    }

    @Test
    fun otherStylesUseChatTemplatePath() {
        assertNull(PromptBuilder.buildSelfContained(model("hymt"), "Hello", "en", "zh-Hans"))
        assertNull(PromptBuilder.buildSelfContained(model("index_zh"), "Hello", "en", "zh-Hans"))
        assertNull(PromptBuilder.buildSelfContained(model("plain"), "Hello", "en", "zh-Hans"))
    }

    @Test
    fun hymtPromptUsesEnglishLanguageName() {
        val text = PromptBuilder.build(model("hymt"), "Hello", "en", "zh-Hans")
        assertTrue(text.contains("Chinese"))
        assertTrue(text.endsWith("Hello"))
    }

    @Test
    fun indexPromptUsesChineseNames() {
        val text = PromptBuilder.build(model("index_zh"), "Hello", "en", "zh-Hans")
        assertTrue(text.contains("简体中文"))
        assertTrue(text.contains("英语"))
    }
}
