package org.caojun.autoocrtask.recorder

import android.content.Context
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.LaunchSpec
import org.caojun.autoocrtask.data.automationStepFromJson
import org.caojun.autoocrtask.data.launchSpecFromJson
import org.caojun.autoocrtask.data.toJson
import org.caojun.shotocr.prefs.SharedPrefsStore
import org.json.JSONArray
import org.json.JSONObject

data class RecorderDraft(
    val taskId: String?,
    val name: String,
    val packageName: String,
    val launch: LaunchSpec,
    val existingSteps: List<AutomationStep>,
    val recordedSteps: List<AutomationStep>,
    val pending: Boolean
) {
    val allSteps: List<AutomationStep> get() = existingSteps + recordedSteps
}

/**
 * 录制草稿与录制结果的本地持久化。录制期间 AutoOcrTask 可能被回收，
 * 因此所有状态立即写入 SharedPreferences。
 */
object RecorderSession {

    private const val PREFS = "recorder_session"
    private const val KEY = "session"

    private fun store(context: Context) =
        SharedPrefsStore(context.applicationContext, PREFS)

    fun begin(
        context: Context,
        taskId: String?,
        name: String,
        packageName: String,
        launch: LaunchSpec,
        existingSteps: List<AutomationStep>
    ): RecorderDraft {
        val draft = RecorderDraft(
            taskId = taskId,
            name = name,
            packageName = packageName,
            launch = launch,
            existingSteps = existingSteps,
            recordedSteps = emptyList(),
            pending = false
        )
        save(context, draft)
        return draft
    }

    fun current(context: Context): RecorderDraft? = read(context)

    fun append(context: Context, step: AutomationStep): RecorderDraft? {
        val draft = current(context) ?: return null
        val updated = draft.copy(recordedSteps = draft.recordedSteps + step)
        save(context, updated)
        return updated
    }

    fun undo(context: Context): RecorderDraft? {
        val draft = current(context) ?: return null
        if (draft.recordedSteps.isEmpty()) return draft
        val updated = draft.copy(recordedSteps = draft.recordedSteps.dropLast(1))
        save(context, updated)
        return updated
    }

    fun finish(context: Context) {
        val draft = current(context) ?: return
        save(context, draft.copy(pending = true))
    }

    fun cancel(context: Context) {
        store(context).remove(KEY)
    }

    fun hasPending(context: Context): Boolean = current(context)?.pending == true

    fun pendingTaskId(context: Context): String? =
        current(context)?.takeIf { it.pending }?.taskId

    /** 读取并清除录制结果；编辑器只消费一次。 */
    fun consume(context: Context): RecorderDraft? {
        val draft = current(context) ?: return null
        store(context).remove(KEY)
        return draft
    }

    private fun save(context: Context, draft: RecorderDraft) {
        store(context).putString(KEY, draft.toJson().toString())
    }

    private fun read(context: Context): RecorderDraft? {
        val raw = store(context).getString(KEY) ?: return null
        return runCatching { draftFromJson(JSONObject(raw)) }.getOrNull()
    }
}

private fun RecorderDraft.toJson(): JSONObject = JSONObject().apply {
    put("taskId", taskId ?: JSONObject.NULL)
    put("name", name)
    put("packageName", packageName)
    put("launch", launch.toJson())
    put("existingSteps", stepsToJson(existingSteps))
    put("recordedSteps", stepsToJson(recordedSteps))
    put("pending", pending)
}

private fun draftFromJson(obj: JSONObject): RecorderDraft = RecorderDraft(
    taskId = if (obj.isNull("taskId")) null else obj.optString("taskId"),
    name = obj.optString("name", ""),
    packageName = obj.optString("packageName", ""),
    launch = obj.optJSONObject("launch")?.let(::launchSpecFromJson) ?: LaunchSpec(),
    existingSteps = stepsFromJson(obj.optJSONArray("existingSteps")),
    recordedSteps = stepsFromJson(obj.optJSONArray("recordedSteps")),
    pending = obj.optBoolean("pending", false)
)

private fun stepsToJson(steps: List<AutomationStep>): JSONArray = JSONArray().apply {
    steps.forEach { put(it.toJson()) }
}

private fun stepsFromJson(array: JSONArray?): List<AutomationStep> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(::automationStepFromJson)
    }
}
