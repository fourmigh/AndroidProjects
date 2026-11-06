package org.caojun.shotocr.llm

/** 本地模型文件未随 APK 打包（assets 中缺失）时抛出。 */
class LlmModelMissingException(val assetName: String) : Exception(assetName)
