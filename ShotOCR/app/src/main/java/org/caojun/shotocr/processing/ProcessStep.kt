package org.caojun.shotocr.processing

import androidx.annotation.StringRes
import org.caojun.shotocr.R

/**
 * 截图处理流程的步骤，用于进度展示。
 * [weight] 为已完成的步骤数（用于进度条比例）。
 */
enum class ProcessStep(@StringRes val labelRes: Int, val weight: Int) {
    IDLE(R.string.step_idle, 0),
    RESOLVING_SOURCE(R.string.step_resolving_source, 1),
    LOADING_IMAGE(R.string.step_loading_image, 2),
    OCR_INIT(R.string.step_ocr_init, 3),
    OCR_RECOGNIZE(R.string.step_ocr_recognize, 4),
    AI_LOADING(R.string.step_ai_loading, 5),
    AI_PARSING(R.string.step_ai_parsing, 6),
    DEDUP(R.string.step_dedup, 7),
    DONE(R.string.step_done, 8),
    FAILED(R.string.step_failed, 8);

    companion object {
        /** 进度条总量（完成时的 weight 值）。 */
        const val TOTAL = 8
    }
}
