package org.caojun.autoocrtask.automation

import android.content.Context
import java.util.Locale
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.StepType

object StepDescriptions {

    fun typeRes(type: StepType): Int = when (type) {
        StepType.WAIT_TEXT -> R.string.step_type_wait_text
        StepType.TAP_TEXT -> R.string.step_type_tap_text
        StepType.WAIT_PACKAGE -> R.string.step_type_wait_package
        StepType.TAP_POINT -> R.string.step_type_tap_point
        StepType.SWIPE -> R.string.step_type_swipe
        StepType.BACK -> R.string.step_type_back
        StepType.DELAY -> R.string.step_type_delay
    }

    fun typeLabel(context: Context, type: StepType): String =
        context.getString(typeRes(type))

    fun describe(context: Context, step: AutomationStep): String = when (step.type) {
        StepType.WAIT_TEXT ->
            context.getString(R.string.step_desc_wait_text, step.text)

        StepType.TAP_TEXT ->
            context.getString(R.string.step_desc_tap_text, step.text)

        StepType.WAIT_PACKAGE ->
            context.getString(R.string.step_desc_wait_package, step.packageName)

        StepType.TAP_POINT -> context.getString(
            R.string.step_desc_tap_point,
            ratio(step.x),
            ratio(step.y)
        )

        StepType.SWIPE -> context.getString(
            R.string.step_desc_swipe,
            ratio(step.x),
            ratio(step.y),
            ratio(step.x2),
            ratio(step.y2)
        )

        StepType.BACK -> context.getString(R.string.step_desc_back)

        StepType.DELAY ->
            context.getString(R.string.step_desc_delay, step.delayMs)
    }

    private fun ratio(value: Float): String =
        String.format(Locale.US, "%.2f", value)
}
