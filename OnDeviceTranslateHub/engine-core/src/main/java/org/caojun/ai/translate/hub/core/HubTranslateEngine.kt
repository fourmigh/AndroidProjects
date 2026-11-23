package org.caojun.ai.translate.hub.core

import java.io.File

/**
 * 推理引擎抽象。
 *
 * 具体实现由 `engine-llama` 提供，`engine-core` 不依赖 native 代码，
 * 便于在没有 JNI 的环境（单测、UI 预览）里替换成假实现。
 */
interface HubTranslateEngine {

    /** 当前已加载的模型 id。 */
    val loadedModelId: String?

    /** 最近一次加载失败的原因（供 UI / 异常信息使用）。 */
    val lastError: String?

    /** 确保 [model] 已加载（不同模型会先卸载）。 */
    fun ensureLoaded(model: CatalogModel, modelFile: File): Boolean

    /** 同步翻译；调用方保证串行。 */
    fun translate(model: CatalogModel, text: String, from: String, to: String): String

    /** 释放资源。 */
    fun unload()

    /** 引擎自报版本，用于 `engineReq.minEngine` 门控。 */
    val engineVersion: Int
}
