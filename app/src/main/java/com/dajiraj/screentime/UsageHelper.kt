package com.dajiraj.screentime

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.*

object UsageHelper {

    // System packages to exclude from tracking
    private val EXCLUDE_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.spage",
        "com.samsung.android.incallui",
        "com.android.phone",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "android",
        "com.android.settings",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.server.telecom"
    )

    /**
     * Returns total screen-on usage in minutes for today (from midnight to now).
     * Uses Android's UsageStatsManager — requires PACKAGE_USAGE_STATS permission.
     */
    fun getTodayTotalMinutes(context: Context): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis

        // Set to midnight today
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime, endTime
        )

        if (stats.isNullOrEmpty()) return 0

        val totalMs = stats
            .filter { it.packageName !in EXCLUDE_PACKAGES }
            .filter { it.packageName != context.packageName } // exclude self
            .sumOf { it.totalTimeInForeground }

        return (totalMs / 60000).toInt() // convert ms to minutes
    }

    /**
     * Returns hourly breakdown for today: array of 24 ints (minutes per hour).
     * Uses INTERVAL_BEST which gives ~hour-level granularity.
     */
    fun getHourlyBreakdownToday(context: Context): List<Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val result = IntArray(24) { 0 }

        val nowCal = Calendar.getInstance()
        val nowHour = nowCal.get(Calendar.HOUR_OF_DAY)

        // Query each hour from midnight to now
        for (hour in 0..nowHour) {
            val startCal = Calendar.getInstance()
            startCal.set(Calendar.HOUR_OF_DAY, hour)
            startCal.set(Calendar.MINUTE, 0)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)

            val endCal = Calendar.getInstance()
            if (hour == nowHour) {
                // Current hour — use current time as end
            } else {
                endCal.set(Calendar.HOUR_OF_DAY, hour + 1)
                endCal.set(Calendar.MINUTE, 0)
                endCal.set(Calendar.SECOND, 0)
                endCal.set(Calendar.MILLISECOND, 0)
            }

            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startCal.timeInMillis,
                endCal.timeInMillis
            )

            val hourMs = stats
                ?.filter { it.packageName !in EXCLUDE_PACKAGES }
                ?.filter { it.packageName != context.packageName }
                ?.sumOf { it.totalTimeInForeground } ?: 0L

            result[hour] = (hourMs / 60000).toInt()
        }

        return result.toList()
    }

    /**
     * Returns top N apps by usage today, as list of (appName, minutes).
     */
    fun getTopAppsToday(context: Context, limit: Int = 5): List<Pair<String, Int>> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            ?: return emptyList()

        return stats
            .filter { it.packageName !in EXCLUDE_PACKAGES }
            .filter { it.packageName != context.packageName }
            .filter { it.totalTimeInForeground > 60_000L } // at least 1 minute
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { stat ->
                val name = try {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(stat.packageName, 0)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    stat.packageName.split(".").last().replaceFirstChar { it.uppercase() }
                }
                Pair(name, (stat.totalTimeInForeground / 60000).toInt())
            }
    }

    /**
     * Returns last 30 days of daily usage: list of (dateKey, minutes).
     * dateKey format: "yyyy-MM-dd"
     */
    fun getLast30DaysUsage(context: Context): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (i in 0 until 30) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cal.time)

            val dayStart = cal.clone() as Calendar
            dayStart.set(Calendar.HOUR_OF_DAY, 0)
            dayStart.set(Calendar.MINUTE, 0)
            dayStart.set(Calendar.SECOND, 0)
            dayStart.set(Calendar.MILLISECOND, 0)

            val dayEnd = dayStart.clone() as Calendar
            dayEnd.add(Calendar.DAY_OF_YEAR, 1)

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                dayStart.timeInMillis,
                dayEnd.timeInMillis
            )

            val totalMs = stats
                ?.filter { it.packageName !in EXCLUDE_PACKAGES }
                ?.sumOf { it.totalTimeInForeground } ?: 0L

            result.add(Pair(dateStr, (totalMs / 60000).toInt()))
        }
        return result.reversed() // oldest first
    }
}
