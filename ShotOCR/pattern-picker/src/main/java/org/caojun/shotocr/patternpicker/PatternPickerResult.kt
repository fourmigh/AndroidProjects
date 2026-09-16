package org.caojun.shotocr.patternpicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

data class PatternPickerInput(
    val title: String = "",
    val presetType: PresetType,
    val currentPreset: String = "",
    val currentCustom: String = ""
)

data class PatternPickerResult(
    val presetName: String,
    val customRegex: String
)

class PatternPickerContract : ActivityResultContract<PatternPickerInput, PatternPickerResult?>() {

    override fun createIntent(context: Context, input: PatternPickerInput): Intent {
        return Intent(context, PatternPickerActivity::class.java).apply {
            putExtra(EXTRA_TITLE, input.title)
            putExtra(EXTRA_PRESET_TYPE, input.presetType.name)
            putExtra(EXTRA_CURRENT_PRESET, input.currentPreset)
            putExtra(EXTRA_CURRENT_CUSTOM, input.currentCustom)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PatternPickerResult? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return PatternPickerResult(
            presetName = intent.getStringExtra(EXTRA_PRESET_NAME) ?: "",
            customRegex = intent.getStringExtra(EXTRA_CUSTOM_REGEX) ?: ""
        )
    }

    companion object {
        const val EXTRA_TITLE = "pattern_picker_title"
        const val EXTRA_PRESET_TYPE = "pattern_picker_type"
        const val EXTRA_CURRENT_PRESET = "pattern_picker_current_preset"
        const val EXTRA_CURRENT_CUSTOM = "pattern_picker_current_custom"
        const val EXTRA_PRESET_NAME = "pattern_picker_result_name"
        const val EXTRA_CUSTOM_REGEX = "pattern_picker_result_regex"
    }
}
