package org.caojun.traffic

import java.time.YearMonth

data class DayTraffic(
    val wifiBytes: Long = 0L,
    val mobileBytes: Long = 0L,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

data class MonthTraffic(
    val month: YearMonth,
    val days: Map<Int, DayTraffic>,
    val wifiBytes: Long,
    val mobileBytes: Long,
)

/** 今日 / 本周 / 本月汇总，不依赖每日 bucket。 */
data class TrafficTotals(
    val todayWifi: Long,
    val todayMobile: Long,
    val weekWifi: Long,
    val weekMobile: Long,
    val monthWifi: Long,
    val monthMobile: Long,
)
