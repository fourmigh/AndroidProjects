package org.caojun.autoocrtask.data

import android.content.Context
import org.caojun.shotocr.prefs.SharedPrefsStore

/** 跨进程/重建传递“任务结束后打开任务列表”的意图，消费一次即清除。 */
object AppNavigation {

    private const val PREFS = "app_navigation"
    private const val KEY_OPEN_TASKS = "open_tasks"

    private fun store(context: Context) =
        SharedPrefsStore(context.applicationContext, PREFS)

    fun requestTasks(context: Context) {
        store(context).putBoolean(KEY_OPEN_TASKS, true)
    }

    fun consumeTasks(context: Context): Boolean {
        val store = store(context)
        val value = store.getBoolean(KEY_OPEN_TASKS, false)
        if (value) store.putBoolean(KEY_OPEN_TASKS, false)
        return value
    }
}
