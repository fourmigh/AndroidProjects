package org.caojun.ai.translate.hub.core

import org.caojun.ai.translate.provider.LanguageCatalog

/**
 * 按模型风格拼装 prompt。
 *
 * 两类模型：
 * 1. **走 chat template**（Hy-MT2 / Index 等）：这里只产出「用户消息正文」，
 *    由引擎调用模型内置模板包装；模板不可用时退化为 ChatML。
 * 2. **自绘 prompt**（PLaMo 3）：模型使用专有的 PFML3 格式，`llama_chat_apply_template`
 *    无法渲染，必须由 [buildSelfContained] 直接给出完整字符串。
 */
object PromptBuilder {

    /**
     * 返回完整 prompt；null 表示该模型应走 chat template 路径。
     *
     * 返回的字符串包含 BOS（PLaMo 的 tokenizer 不会自动加 BOS）。
     */
    fun buildSelfContained(model: CatalogModel, text: String, from: String, to: String): String? =
        when (model.prompt.style) {
            "plamo3" -> plamo3(text, from, to)
            else -> null
        }

    /** 供 chat template 路径使用的用户消息正文。 */
    fun build(model: CatalogModel, text: String, from: String, to: String): String =
        when (model.prompt.style) {
            "index_zh" -> {
                val src = LanguageCatalog.of(from)?.native ?: from
                val dst = LanguageCatalog.of(to)?.native ?: to
                "请将以下${src}文本翻译为${dst}，直接输出翻译结果，不要进行任何解释。\n\n$text"
            }
            // hymt / plain：官方推荐英文语言名
            else -> {
                val target = LanguageCatalog.englishName(to)
                "Translate the following segment into $target, without additional explanation.\n\n$text"
            }
        }

    /** 引擎不支持模型内置模板时的兜底（ChatML）。 */
    fun chatMl(userContent: String): String =
        "<|im_start|>user\n$userContent<|im_end|>\n<|im_start|>assistant\n"

    /**
     * 纯文本兜底 prompt（不带任何特殊结构标记）。
     *
     * 当模型的专有格式拼装失败时（例如分词器版本不匹配），用一个最简单的指令再试一次，
     * 至少能拿到结果；同时把诊断信息记进日志。
     */
    fun buildPlainBootstrap(text: String, to: String): String {
        val target = LanguageCatalog.englishName(to)
        return "<|plamo:bos|>Translate the following segment into $target, " +
            "without additional explanation.\n\n$text"
    }

    // ------------------------------------------------------------------ PLaMo 3

    /**
     * PLaMo 3 的 PFML3 格式（来自 GGUF 的 tokenizer.chat_template）：
     *
     * ```
     * <|plamo:bos|><|plamo:tag|>dataset<|plamo:msg|>translation
     * <|plamo:tag|>input[<|plamo:key|>lang<|plamo:val|>en]<|plamo:msg|>{text}
     * <|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>{target}<|plamo:msg|>
     * ```
     *
     * - 模板要求 `target_lang/source_lang` 用小写语言码（`zh`、`en`、`ja`）
     * - BOS 由这里显式给出：PLAMO2 的 tokenize 分支不处理 `add_bos`，不会自动加
     */
    private fun plamo3(text: String, from: String, to: String): String = buildString {
        append("<|plamo:bos|>")
        append("<|plamo:tag|>dataset<|plamo:msg|>translation")
        append("<|plamo:tag|>input")
        plamoCode(from)?.let { append("<|plamo:key|>lang<|plamo:val|>").append(it) }
        append("<|plamo:msg|>").append(text)
        append("<|plamo:tag|>output")
        append("<|plamo:key|>lang<|plamo:val|>").append(plamoCode(to) ?: to.lowercase())
        append("<|plamo:msg|>")
    }

    /** 规范语种码 → PLaMo 的语言码（其语种表用 `tl` 而不是 `fil`、`zh` 而不是 `zh-Hans`）。 */
    fun plamoCode(code: String): String? = when (code) {
        "zh-Hans" -> "zh"
        "zh-Hant" -> "zh-hant"
        "fil" -> "tl"
        else -> code.lowercase()
    }
}
