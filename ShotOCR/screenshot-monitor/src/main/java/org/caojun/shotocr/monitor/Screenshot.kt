package org.caojun.shotocr.monitor

import android.net.Uri

data class Screenshot(
    val uri: Uri,
    val path: String,
    val displayName: String,
    val timestamp: Long
)
