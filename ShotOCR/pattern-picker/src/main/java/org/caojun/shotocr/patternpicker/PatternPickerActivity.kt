package org.caojun.shotocr.patternpicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

class PatternPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(PatternPickerContract.EXTRA_TITLE) ?: ""
        val presetType = try {
            PresetType.valueOf(intent.getStringExtra(PatternPickerContract.EXTRA_PRESET_TYPE) ?: PresetType.AMOUNT.name)
        } catch (_: Exception) {
            PresetType.AMOUNT
        }
        val currentPreset = intent.getStringExtra(PatternPickerContract.EXTRA_CURRENT_PRESET) ?: ""
        val currentCustom = intent.getStringExtra(PatternPickerContract.EXTRA_CURRENT_CUSTOM) ?: ""

        setContent {
            MaterialTheme {
                PatternPickerScreen(
                    title = title,
                    presetType = presetType,
                    currentPreset = currentPreset,
                    currentCustom = currentCustom,
                    onSelect = { presetName, customRegex ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                putExtra(PatternPickerContract.EXTRA_PRESET_NAME, presetName)
                                putExtra(PatternPickerContract.EXTRA_CUSTOM_REGEX, customRegex)
                            }
                        )
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
