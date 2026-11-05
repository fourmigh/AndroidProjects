package org.caojun.autoocrtask.data

import android.content.Context
import org.caojun.shotocr.prefs.JsonStore
import org.caojun.shotocr.prefs.SharedPrefsStore

class AutomationTaskStore(context: Context) {

    private val json = JsonStore(
        SharedPrefsStore(context.applicationContext, PREFS_NAME)
    )

    fun list(): List<AutomationTask> = json.getList(KEY_TASKS, ::automationTaskFromJson)

    fun find(id: String): AutomationTask? = list().firstOrNull { it.id == id }

    fun save(task: AutomationTask) {
        val current = list().toMutableList()
        val index = current.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            current[index] = task
        } else {
            current.add(task)
        }
        json.putList(KEY_TASKS, current) { it.toJson() }
    }

    fun remove(id: String) {
        val current = list().toMutableList()
        current.removeAll { it.id == id }
        json.putList(KEY_TASKS, current) { it.toJson() }
    }

    companion object {
        private const val PREFS_NAME = "automation_tasks"
        private const val KEY_TASKS = "tasks"

        fun newId(): String = "task_" + System.currentTimeMillis()
    }
}
