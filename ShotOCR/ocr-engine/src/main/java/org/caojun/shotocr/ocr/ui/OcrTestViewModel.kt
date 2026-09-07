package org.caojun.shotocr.ocr.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.caojun.shotocr.ocr.OcrEngine
import org.caojun.shotocr.ocr.OcrResult

data class OcrUiState(
    val isInitialized: Boolean = false,
    val isLoading: Boolean = false,
    val results: List<OcrResult> = emptyList(),
    val fullText: String = "",
    val error: String? = null
)

class OcrTestViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private var ocrEngine: OcrEngine? = null

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: throw Exception("Failed to load image")

                if (ocrEngine == null) {
                    ocrEngine = OcrEngine(context).also { it.initialize() }
                    _uiState.value = _uiState.value.copy(isInitialized = true)
                }

                val results = ocrEngine!!.detect(bitmap)
                val fullText = results.joinToString("\n") { it.text }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    results = results,
                    fullText = fullText
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrEngine?.release()
    }
}
