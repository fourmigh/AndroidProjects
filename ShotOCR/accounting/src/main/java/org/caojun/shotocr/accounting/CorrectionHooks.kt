package org.caojun.shotocr.accounting

/**
 * 解耦钩子：确认页的"记住这次纠正"由上层（app，具备 llm-parser 依赖）注入实现，
 * 避免 accounting 模块直接依赖 llm-parser。
 */
object CorrectionHooks {

    /** (原始 OCR 文本, 纠正后的字段 JSON) -> Unit */
    var remember: ((rawText: String, correctedJson: String) -> Unit)? = null
}
