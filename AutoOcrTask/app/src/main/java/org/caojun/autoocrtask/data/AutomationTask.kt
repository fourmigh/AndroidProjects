package org.caojun.autoocrtask.data

import org.json.JSONArray
import org.json.JSONObject

enum class LaunchType(val id: String) {
    HOME("home"),
    ACTIVITY("activity"),
    URI("uri");

    companion object {
        fun fromId(id: String?): LaunchType = entries.firstOrNull { it.id == id } ?: HOME
    }
}

data class LaunchSpec(
    val type: LaunchType = LaunchType.HOME,
    val activity: String = "",
    val uri: String = ""
)

enum class StepType(val id: String) {
    WAIT_TEXT("wait_text"),
    TAP_TEXT("tap_text"),
    WAIT_PACKAGE("wait_package"),
    TAP_POINT("tap_point"),
    SWIPE("swipe"),
    BACK("back"),
    DELAY("delay");

    companion object {
        fun fromId(id: String?): StepType = entries.firstOrNull { it.id == id } ?: DELAY
    }
}

data class AutomationStep(
    val type: StepType,
    val text: String = "",
    val packageName: String = "",
    val timeoutMs: Long = 10_000L,
    val exact: Boolean = false,
    val useOcr: Boolean = true,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val x2: Float = 0.5f,
    val y2: Float = 0.5f,
    val durationMs: Int = 500,
    val delayMs: Long = 1_000L
)

data class AutomationTask(
    val id: String,
    val name: String,
    val packageName: String,
    val launch: LaunchSpec = LaunchSpec(),
    val steps: List<AutomationStep> = emptyList()
)

fun AutomationTask.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("packageName", packageName)
    put("launch", launch.toJson())
    put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
}

fun automationTaskFromJson(obj: JSONObject): AutomationTask {
    val stepsArray = obj.optJSONArray("steps")
    val steps = if (stepsArray != null) {
        (0 until stepsArray.length()).mapNotNull { index ->
            stepsArray.optJSONObject(index)?.let(::automationStepFromJson)
        }
    } else {
        emptyList()
    }
    return AutomationTask(
        id = obj.optString("id", ""),
        name = obj.optString("name", ""),
        packageName = obj.optString("packageName", ""),
        launch = obj.optJSONObject("launch")?.let(::launchSpecFromJson) ?: LaunchSpec(),
        steps = steps
    )
}

internal fun LaunchSpec.toJson(): JSONObject = JSONObject().apply {
    put("type", type.id)
    put("activity", activity)
    put("uri", uri)
}

internal fun launchSpecFromJson(obj: JSONObject): LaunchSpec = LaunchSpec(
    type = LaunchType.fromId(obj.optString("type")),
    activity = obj.optString("activity", ""),
    uri = obj.optString("uri", "")
)

internal fun AutomationStep.toJson(): JSONObject = JSONObject().apply {
    put("type", type.id)
    put("text", text)
    put("packageName", packageName)
    put("timeoutMs", timeoutMs)
    put("exact", exact)
    put("useOcr", useOcr)
    put("x", x.toDouble())
    put("y", y.toDouble())
    put("x2", x2.toDouble())
    put("y2", y2.toDouble())
    put("durationMs", durationMs)
    put("delayMs", delayMs)
}

internal fun automationStepFromJson(obj: JSONObject): AutomationStep = AutomationStep(
    type = StepType.fromId(obj.optString("type")),
    text = obj.optString("text", ""),
    packageName = obj.optString("packageName", ""),
    timeoutMs = obj.optLong("timeoutMs", 10_000L),
    exact = obj.optBoolean("exact", false),
    useOcr = obj.optBoolean("useOcr", true),
    x = obj.optDouble("x", 0.5).toFloat(),
    y = obj.optDouble("y", 0.5).toFloat(),
    x2 = obj.optDouble("x2", 0.5).toFloat(),
    y2 = obj.optDouble("y2", 0.5).toFloat(),
    durationMs = obj.optInt("durationMs", 500),
    delayMs = obj.optLong("delayMs", 1_000L)
)
