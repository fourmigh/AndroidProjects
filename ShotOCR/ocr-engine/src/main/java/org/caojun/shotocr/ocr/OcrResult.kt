package org.caojun.shotocr.ocr

import android.graphics.RectF
import android.graphics.PointF

data class OcrResult(
    val text: String,
    val score: Float,
    val box: RectF,
    val center: PointF
)
