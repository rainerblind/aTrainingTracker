package com.atrainingtracker.trainingtracker.ui.components.stats

import android.content.Context
import com.atrainingtracker.R
import java.util.Calendar

object StatsPeriodHelper {
    fun getDetailedStats(
        context: Context,
        firstUsageDate: String?,
        fetchPeriod: (title: String, startS: Long, endS: Long) -> StatsData
    ): List<StatsData> {
        val statsList = mutableListOf<StatsData>()

        // Base calendar for calculations
        val cal = Calendar.getInstance()

        fun Calendar.toStartOfDay() {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun Calendar.toEndOfDay() {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        // --- 1. THIS WEEK (Monday to Now) ---
        val thisWeekStart = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            toStartOfDay()
        }.timeInMillis / 1000
        statsList.add(
            fetchPeriod(
                context.getString(R.string.stats_this_week),
                thisWeekStart,
                System.currentTimeMillis() / 1000
            )
        )

        // --- 2. LAST WEEK (Previous full Mon-Sun) ---
        val lastWeekRange = (cal.clone() as Calendar).run {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            add(Calendar.WEEK_OF_YEAR, -1)
            toStartOfDay()
            val start = timeInMillis / 1000

            add(Calendar.DAY_OF_WEEK, 6)
            toEndOfDay()
            val end = timeInMillis / 1000
            Pair(start, end)
        }
        statsList.add(
            fetchPeriod(
                context.getString(R.string.stats_last_week),
                lastWeekRange.first,
                lastWeekRange.second
            )
        )

        // --- 3. THIS MONTH (1st to Now) ---
        val thisMonthStart = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            toStartOfDay()
        }.timeInMillis / 1000
        statsList.add(
            fetchPeriod(
                context.getString(R.string.stats_this_month),
                thisMonthStart,
                System.currentTimeMillis() / 1000
            )
        )

        // --- 4. LAST MONTH (Previous full 1st to End of Month) ---
        val lastMonthRange = (cal.clone() as Calendar).run {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            toStartOfDay()
            val start = timeInMillis / 1000

            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            toEndOfDay()
            val end = timeInMillis / 1000
            Pair(start, end)
        }
        statsList.add(
            fetchPeriod(
                context.getString(R.string.stats_last_month),
                lastMonthRange.first,
                lastMonthRange.second
            )
        )

        // --- 5. YEARLY STATS (Current Year + Previous Years) ---
        val currentYear = cal.get(Calendar.YEAR)
        // Extract year from "yyyy-MM-dd HH:mm:ss"
        val startYear = firstUsageDate?.substringBefore("-")?.toIntOrNull() ?: currentYear

        for (year in currentYear downTo startYear) {
            val yearStart = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.DAY_OF_YEAR, 1)
                toStartOfDay()
            }.timeInMillis / 1000

            val yearEnd = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 31)
                toEndOfDay()
            }.timeInMillis / 1000

            val title =
                if (year == currentYear) context.getString(R.string.stats_this_year) else year.toString()
            statsList.add(fetchPeriod(title, yearStart, yearEnd))
        }

        // Return only periods with actual workouts.
        return statsList.distinctBy { it.title }.filter { it.totalWorkouts > 0 }
    }
}