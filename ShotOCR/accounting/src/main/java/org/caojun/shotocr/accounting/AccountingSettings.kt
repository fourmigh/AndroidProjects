package org.caojun.shotocr.accounting

import android.content.Context
import java.time.DayOfWeek

object AccountingSettings {

    private const val PREFS_NAME = "accounting_settings"
    private const val KEY_WEEK_START = "week_start"

    private fun prefs(context: Context): android.content.SharedPreferences {
        return context.applicationContext.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
        )
    }

    fun getWeekStart(context: Context): DayOfWeek {
        val name = prefs(context).getString(KEY_WEEK_START, DayOfWeek.MONDAY.name)
        return runCatching { DayOfWeek.valueOf(name!!) }.getOrDefault(DayOfWeek.MONDAY)
    }

    fun setWeekStart(context: Context, day: DayOfWeek) {
        prefs(context).edit().putString(KEY_WEEK_START, day.name).apply()
    }
}