package org.caojun.traffic

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.telephony.TelephonyManager
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * 通过系统 NetworkStatsManager 统计本 App 的流量。
 *
 * Android 只区分 WiFi / 移动数据，4G、5G 的历史用量无法拆分，
 * 所以这里把移动网络统一记为「移动数据」。
 */
class TrafficStore(private val context: Context) {

    private val cache = mutableMapOf<YearMonth, MonthTraffic>()
    private var dailySupportCache: Boolean? = null

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            @Suppress("DEPRECATION")
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun currentNetworkLabel(hasPhonePermission: Boolean): String {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
                ?: return context.getString(R.string.traffic_network_unknown)
            val network = manager.activeNetwork
                ?: return context.getString(R.string.traffic_network_none)
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return context.getString(R.string.traffic_network_none)
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
                    context.getString(R.string.traffic_network_vpn)
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    context.getString(R.string.traffic_network_wifi)
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    if (hasPhonePermission) {
                        cellularLabel()
                    } else {
                        context.getString(R.string.traffic_network_mobile)
                    }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    context.getString(R.string.traffic_network_ethernet)
                else -> context.getString(R.string.traffic_network_other)
            }
        } catch (e: SecurityException) {
            context.getString(R.string.traffic_network_no_permission)
        } catch (e: Exception) {
            context.getString(R.string.traffic_network_unknown)
        }
    }

    /** 读取当前移动网络制式；没有 READ_PHONE_STATE 时调用方会先走「移动数据」。 */
    private fun cellularLabel(): String {
        return try {
            val manager = context.getSystemService(TelephonyManager::class.java)
                ?: return context.getString(R.string.traffic_network_mobile)
            when (manager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> context.getString(R.string.traffic_network_5g)
                TelephonyManager.NETWORK_TYPE_LTE -> context.getString(R.string.traffic_network_4g)
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> context.getString(R.string.traffic_network_3g)
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_GSM -> context.getString(R.string.traffic_network_2g)
                else -> context.getString(R.string.traffic_network_mobile)
            }
        } catch (e: SecurityException) {
            context.getString(R.string.traffic_network_mobile)
        } catch (e: Exception) {
            context.getString(R.string.traffic_network_mobile)
        }
    }

    fun clearCache() {
        cache.clear()
        dailySupportCache = null
    }

    /**
     * 探测本 App 是否有按天的 bucket。
     *
     * 用 queryDetailsForUid 查最近 14 天（WiFi + 移动）的原始 bucket，
     * 如果 bucket 的起始时间落在这 14 天里的至少两个不同日期，就认为可以按月历展示；
     * 如果只有一个日期或只有一个大桶，则只显示汇总。
     */
    fun supportsDailyBreakdown(): Boolean {
        dailySupportCache?.let { return it }
        if (!hasUsageAccess()) {
            dailySupportCache = false
            return false
        }

        val now = System.currentTimeMillis()
        val start = now - 14L * 24L * 60L * 60L * 1000L
        val zone = ZoneId.systemDefault()
        val days = mutableSetOf<Long>()

        fun collect(networkType: Int) {
            try {
                val manager = context.getSystemService(NetworkStatsManager::class.java)
                val stats = manager.queryDetailsForUid(
                    networkType,
                    null,
                    start,
                    now,
                    Process.myUid(),
                )
                val bucket = NetworkStats.Bucket()
                var count = 0
                while (stats.hasNextBucket() && count < 3000 && days.size < 2) {
                    stats.getNextBucket(bucket)
                    val date = Instant.ofEpochMilli(bucket.startTimeStamp)
                        .atZone(zone)
                        .toLocalDate()
                    days.add(date.toEpochDay())
                    count++
                }
            } catch (e: Exception) {
                // 部分系统不提供 queryDetailsForUid，交给下面的兜底
            }
        }

        collect(ConnectivityManager.TYPE_WIFI)
        if (days.size < 2) collect(ConnectivityManager.TYPE_MOBILE)

        val result = days.size >= 2
        dailySupportCache = result
        return result
    }

    /** 今日 / 本周 / 本月汇总。 */
    fun queryTotals(): TrafficTotals {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()

        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = today
            .minusDays((today.dayOfWeek.value - 1).toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val monthStart = today
            .withDayOfMonth(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        return TrafficTotals(
            todayWifi = queryRange(ConnectivityManager.TYPE_WIFI, todayStart, now),
            todayMobile = queryRange(ConnectivityManager.TYPE_MOBILE, todayStart, now),
            weekWifi = queryRange(ConnectivityManager.TYPE_WIFI, weekStart, now),
            weekMobile = queryRange(ConnectivityManager.TYPE_MOBILE, weekStart, now),
            monthWifi = queryRange(ConnectivityManager.TYPE_WIFI, monthStart, now),
            monthMobile = queryRange(ConnectivityManager.TYPE_MOBILE, monthStart, now),
        )
    }

    fun queryMonth(month: YearMonth): MonthTraffic {
        cache[month]?.let { return it }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val todayMonth = YearMonth.from(today)
        val lastDay = when {
            month.isAfter(todayMonth) -> 0
            month == todayMonth -> today.dayOfMonth
            else -> month.lengthOfMonth()
        }

        val days = mutableMapOf<Int, DayTraffic>()
        var wifiTotal = 0L
        var mobileTotal = 0L

        // 按天查询：部分 ROM 在跨天查询时会返回覆盖整个区间的单个 bucket，
        // 只用桶的起始时间归并会把整月流量都算到 1 号。
        for (day in 1..lastDay) {
            val date = month.atDay(day)
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
                System.currentTimeMillis(),
            )
            if (end <= start) continue

            val wifi = queryRange(ConnectivityManager.TYPE_WIFI, start, end)
            val mobile = queryRange(ConnectivityManager.TYPE_MOBILE, start, end)
            if (wifi > 0L || mobile > 0L) {
                days[day] = DayTraffic(wifiBytes = wifi, mobileBytes = mobile)
            }
            wifiTotal += wifi
            mobileTotal += mobile
        }

        val result = MonthTraffic(
            month = month,
            days = days,
            wifiBytes = wifiTotal,
            mobileBytes = mobileTotal,
        )
        cache[month] = result
        return result
    }

    /** 查询一个时间范围内本 App 的流量。 */
    private fun queryRange(networkType: Int, start: Long, end: Long): Long {
        if (!hasUsageAccess()) return 0L

        return try {
            val manager = context.getSystemService(NetworkStatsManager::class.java)
            val stats = manager.querySummary(networkType, null, start, end)
            val bucket = NetworkStats.Bucket()
            var total = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (bucket.uid != Process.myUid()) continue
                total += bucket.rxBytes.coerceAtLeast(0L) + bucket.txBytes.coerceAtLeast(0L)
            }
            total
        } catch (e: Exception) {
            // 未授权、厂商限制或系统不提供统计时返回 0
            0L
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
                bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
                bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
                else -> "$bytes B"
            }
        }

        /** 月历格子用的短格式，例如 12M、1.2G。 */
        fun formatShort(bytes: Long): String {
            if (bytes <= 0L) return "0"
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> String.format(Locale.getDefault(), "%.1fG", bytes / gb)
                bytes >= mb -> String.format(Locale.getDefault(), "%.0fM", bytes / mb)
                else -> String.format(Locale.getDefault(), "%.0fK", bytes / kb)
            }
        }
    }
}
