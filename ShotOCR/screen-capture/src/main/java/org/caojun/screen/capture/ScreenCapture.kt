package org.caojun.screen.capture

import android.graphics.Bitmap

/** 获取当前屏幕位图；失败返回 null。 */
interface ScreenCapture {
    suspend fun capture(): Bitmap?
}
