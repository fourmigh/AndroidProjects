package org.caojun.shotocr.util

import android.util.Log

object LogUtil {
    private const val TAG_PREFIX = "ShotOCR/"

    fun d(tag: String, message: String) {
        Log.d("$TAG_PREFIX$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w("$TAG_PREFIX$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", message)
        }
    }
}
