package org.caojun.shotocr.accounting

import android.content.Context
import org.caojun.shotocr.prefs.SharedPrefsStore
import java.time.DayOfWeek

object AccountingSettings {

    private const val PREFS_NAME = "accounting_settings"
    private const val KEY_WEEK_START = "week_start"

    private fun store(context: Context) =
        SharedPrefsStore(context.applicationContext, PREFS_NAME)

    fun getWeekStart(context: Context): DayOfWeek {
        val name = store(context).getString(KEY_WEEK_START, DayOfWeek.MONDAY.name)
        return runCatching { DayOfWeek.valueOf(name!!) }.getOrDefault(DayOfWeek.MONDAY)
    }

    fun setWeekStart(context: Context, day: DayOfWeek) {
        store(context).putString(KEY_WEEK_START, day.name)
    }
}
