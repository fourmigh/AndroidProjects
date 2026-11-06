package org.caojun.shotocr.translate

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import java.io.ByteArrayInputStream

/**
 * 翻译模型使用的分词器封装（基于 DJL `HuggingFaceTokenizer`）。
 *
 * 分词规则来自模型自带的 `tokenizer.json`（空白切分 + SentencePiece `▁` + Unigram）；
 * 但 onnx-community 导出的该文件中 `normalizer.precompiled_charsmap` 为 `null`
 * （二进制 charsmap 被删），底层 Rust 解析器会直接 panic，因此加载前先做修补。
 *
 * 非线程安全，调用方需保证串行调用。
 */
internal class TranslateTokenizer private constructor(
    private val tokenizer: HuggingFaceTokenizer,
) : AutoCloseable {

    fun encode(text: String): IntArray {
        val ids = tokenizer.encode(text).ids
        return IntArray(ids.size) { ids[it].toInt() }
    }

    fun decode(ids: IntArray): String {
        val longIds = LongArray(ids.size) { ids[it].toLong() }
        return tokenizer.decode(longIds, true)
    }

    override fun close() {
        tokenizer.close()
    }

    companion object {

        private val OPTIONS = mapOf(
            "addSpecialTokens" to "true",
            "padding" to "false",
            "truncation" to "false",
        )

        /** 从 `tokenizer.json` 文本构造；会自动修补损坏的 normalizer。 */
        fun fromJson(json: String): TranslateTokenizer {
            val patched = TokenizerJsonSanitizer.sanitize(json)
            val stream = ByteArrayInputStream(patched.toByteArray(Charsets.UTF_8))
            val tokenizer = HuggingFaceTokenizer.newInstance(stream, OPTIONS)
            return TranslateTokenizer(tokenizer)
        }
    }
}

/**
 * 修补 onnx-community 导出的 `tokenizer.json`：
 * 其中 `normalizer` 为 `{"type":"Precompiled","precompiled_charsmap":null}`，
 * 直接把该节点替换为 `null`（等价于不做 normalizer），交给分词器的空白切分 + Metaspace 处理。
 */
internal object TokenizerJsonSanitizer {

    private val BROKEN_PRECOMPILED = Regex(
        """"normalizer"\s*:\s*\{[^{}]*"precompiled_charsmap"\s*:\s*null[^{}]*\}"""
    )

    fun sanitize(json: String): String {
        if (!json.contains("precompiled_charsmap")) return json
        return BROKEN_PRECOMPILED.replace(json, "\"normalizer\":null")
    }
}
