package org.caojun.ai.ocr.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF

/** 与迁移前的 OcrResult 字段保持一致，方便业务代码平滑切换。 */
data class OcrResult(
    val text: String,
    val score: Float,
    val box: RectF,
    val center: PointF
)

fun OcrLine.toOcrResult(): OcrResult = OcrResult(
    text = text,
    score = score,
    box = RectF(left, top, right, bottom),
    center = PointF(centerX, centerY)
)

/**
 * 兼容旧本地 OcrEngine 的客户端门面。
 *
 * 方法签名与迁移前一致，内部通过 AIDL 调用 OnDeviceOcr provider。
 */
class OcrEngine(context: Context) {

    private val client = OcrClient(context)

    suspend fun initialize() = client.preload()

    suspend fun detect(bitmap: Bitmap): List<OcrResult> =
        client.recognize(bitmap).map { it.toOcrResult() }

    /** 跨进程调用，没有本地资源需要释放。 */
    fun release() {
    }
}
