package org.caojun.shotocr.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.PointF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OcrEngine(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var isInitialized = false

    private val detInputSize = 640
    private val recImageHeight = 48
    private val recImageWidth = 320

    private val charset: Array<String> by lazy {
        context.assets.open("models/rec/ppocrv6_dict.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotEmpty() }.toList().toTypedArray()
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        if (!OpenCVLoader.initDebug()) {
            throw RuntimeException("OpenCV initialization failed")
        }

        ortEnv = OrtEnvironment.getEnvironment()

        val detModelBytes = context.assets.open("models/det/inference.onnx").use { it.readBytes() }
        detSession = ortEnv!!.createSession(detModelBytes)

        val recModelBytes = context.assets.open("models/rec/inference.onnx").use { it.readBytes() }
        recSession = ortEnv!!.createSession(recModelBytes)

        isInitialized = true
    }

    suspend fun detect(bitmap: Bitmap): List<OcrResult> = withContext(Dispatchers.IO) {
        if (!isInitialized) initialize()

        val detInput = preprocessDet(bitmap)
        val shape = longArrayOf(1, 3, detInputSize.toLong(), detInputSize.toLong())
        val floatBuffer = FloatBuffer.wrap(detInput)
        val detTensor = OnnxTensor.createTensor(ortEnv!!, floatBuffer, shape)

        val inputName = detSession!!.inputNames.first()
        val detOutput = detSession!!.run(mapOf(inputName to detTensor))
        @Suppress("UNCHECKED_CAST")
        val detResult = detOutput[0].value as Array<Array<Array<FloatArray>>>

        val boxes = postprocessDet(detResult, bitmap.width, bitmap.height)

        val results = mutableListOf<OcrResult>()
        for (box in boxes) {
            val cropped = cropBitmap(bitmap, box)
            val text = recognizeText(cropped) ?: continue
            val cx = (box.left + box.right) / 2f
            val cy = (box.top + box.bottom) / 2f
            results.add(
                OcrResult(
                    text = text.first,
                    score = text.second,
                    box = box,
                    center = PointF(cx, cy)
                )
            )
        }

        results.sortedBy { it.center.y }
    }

    private fun preprocessDet(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, detInputSize, detInputSize, true)
        val pixels = IntArray(detInputSize * detInputSize)
        resized.getPixels(pixels, 0, detInputSize, 0, 0, detInputSize, detInputSize)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val chw = FloatArray(3 * detInputSize * detInputSize)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]) / 255.0f
            val g = Color.green(pixels[i]) / 255.0f
            val b = Color.blue(pixels[i]) / 255.0f

            chw[i] = (r - mean[0]) / std[0]
            chw[pixels.size + i] = (g - mean[1]) / std[1]
            chw[2 * pixels.size + i] = (b - mean[2]) / std[2]
        }

        resized.recycle()
        return chw
    }

    private fun postprocessDet(
        output: Array<Array<Array<FloatArray>>>,
        origW: Int,
        origH: Int
    ): List<RectF> {
        val h = output[0][0].size
        val w = output[0][0][0].size

        val mask = Mat(h, w, CvType.CV_32FC1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val value = output[0][0][y][x].toDouble()
                mask.put(y, x, value)
            }
        }

        val binary = Mat()
        Core.compare(mask, Scalar(0.3), binary, Core.CMP_GT)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
        )

        val ratio = maxOf(origW, origH).toFloat() / detInputSize
        val boxes = mutableListOf<RectF>()

        for (contour in contours) {
            val rect = Imgproc.boundingRect(contour)
            if (rect.area() < 100) continue

            val x1 = (rect.x * ratio).coerceIn(0f, origW.toFloat())
            val y1 = (rect.y * ratio).coerceIn(0f, origH.toFloat())
            val x2 = ((rect.x + rect.width) * ratio).coerceIn(0f, origW.toFloat())
            val y2 = ((rect.y + rect.height) * ratio).coerceIn(0f, origH.toFloat())

            boxes.add(RectF(x1, y1, x2, y2))
            contour.release()
        }

        mask.release()
        binary.release()
        hierarchy.release()

        return boxes
    }

    private fun cropBitmap(bitmap: Bitmap, box: RectF): Bitmap {
        val x = box.left.toInt().coerceIn(0, bitmap.width - 1)
        val y = box.top.toInt().coerceIn(0, bitmap.height - 1)
        val w = (box.right - box.left).toInt().coerceIn(1, bitmap.width - x)
        val h = (box.bottom - box.top).toInt().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    private fun recognizeText(cropped: Bitmap): Pair<String, Float>? {
        val input = preprocessRec(cropped) ?: return null
        val shape = longArrayOf(1, 3, recImageHeight.toLong(), recImageWidth.toLong())
        val floatBuffer = FloatBuffer.wrap(input)
        val tensor = OnnxTensor.createTensor(ortEnv!!, floatBuffer, shape)

        val inputName = recSession!!.inputNames.first()
        val output = recSession!!.run(mapOf(inputName to tensor))
        @Suppress("UNCHECKED_CAST")
        val logits = output[0].value as Array<Array<FloatArray>>

        val decoded = ctcDecode(logits[0])
        tensor.close()

        return decoded
    }

    private fun preprocessRec(bitmap: Bitmap): FloatArray? {
        val resized = Bitmap.createScaledBitmap(bitmap, recImageWidth, recImageHeight, true)
        val pixels = IntArray(recImageWidth * recImageHeight)
        resized.getPixels(pixels, 0, recImageWidth, 0, 0, recImageWidth, recImageHeight)

        val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
        val std = floatArrayOf(0.5f, 0.5f, 0.5f)
        val chw = FloatArray(3 * recImageHeight * recImageWidth)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]) / 255.0f
            val g = Color.green(pixels[i]) / 255.0f
            val b = Color.blue(pixels[i]) / 255.0f

            chw[i] = (r - mean[0]) / std[0]
            chw[pixels.size + i] = (g - mean[1]) / std[1]
            chw[2 * pixels.size + i] = (b - mean[2]) / std[2]
        }

        resized.recycle()
        return chw
    }

    private fun ctcDecode(logits: Array<FloatArray>): Pair<String, Float>? {
        val sb = StringBuilder()
        var lastIdx = 0
        var totalScore = 0f
        var count = 0

        for (t in logits.indices) {
            val maxIdx = logits[t].indices.maxByOrNull { logits[t][it] } ?: continue
            val score = logits[t][maxIdx]

            if (maxIdx != 0 && maxIdx != lastIdx) {
                val charIdx = maxIdx - 1
                if (charIdx < charset.size) {
                    sb.append(charset[charIdx])
                    totalScore += score
                    count++
                }
            }
            lastIdx = maxIdx
        }

        if (sb.isEmpty()) return null
        return Pair(sb.toString(), if (count > 0) totalScore / count else 0f)
    }

    fun release() {
        detSession?.close()
        recSession?.close()
        ortEnv?.close()
        detSession = null
        recSession = null
        ortEnv = null
        isInitialized = false
    }
}
