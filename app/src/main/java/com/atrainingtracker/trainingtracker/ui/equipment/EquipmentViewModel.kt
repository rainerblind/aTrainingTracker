/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.equipment

import android.app.Application
import android.content.Context
import android.icu.util.Calendar
import androidx.compose.foundation.layout.add
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class EquipmentItem(
    val id: Long,
    val name: String,
    val sensors: String,
    val frameType: Int,
    val stravaName: String?,
    val stravaId: String?,
    val firstUsed: String?,
    val lastUsed: String?,
    val statsData: StatsData
)

class EquipmentViewModel(application: Application) : AndroidViewModel(application) {
    private val dbEquipmentHelper = EquipmentDbHelper(application)
    private val dbDevicesHelper = DevicesDatabaseManager.getInstance(application)
    private val dbSummariesManager = WorkoutSummariesDatabaseManager.getInstance(application)


    private val _bikes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val bikes: StateFlow<List<EquipmentItem>> = _bikes

    private val _shoes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val shoes: StateFlow<List<EquipmentItem>> = _shoes

    fun refreshEquipment() {
        viewModelScope.launch(Dispatchers.IO) {
            val fetchItems = { sportType: BSportType ->
                // Use the new method to get full data objects
                val equipmentDataList = dbEquipmentHelper.getEquipmentItems(sportType)

                equipmentDataList.map { data ->
                    // Resolve linked sensors
                    val linkedDeviceIds = dbEquipmentHelper.getDeviceIdsForEquipment(data.id)
                    val sensorNames = linkedDeviceIds.mapNotNull { deviceId ->
                        dbDevicesHelper.getDeviceName(deviceId)
                    }.joinToString(", ")

                    val stats = dbSummariesManager.getEquipmentStats(data.id)

                    // You can now access data.stravaName, data.frameType, etc.
                    EquipmentItem(
                        id = data.id,
                        name = data.name,
                        sensors = sensorNames,
                        frameType = data.frameType,
                        stravaName = data.stravaName,
                        stravaId = data.stravaId,
                        firstUsed = stats.firstUsage?.substringBefore(" "),
                        lastUsed = stats.lastUsage?.substringBefore(" "),
                        statsData = StatsData.fromDatabase(
                            title = getApplication<Application>().getString(R.string.stats_total),
                            equipmentStats = stats
                        )
                    )
                }
            }

            _bikes.value = fetchItems(BSportType.BIKE)
            _shoes.value = fetchItems(BSportType.RUN)
        }
    }

    fun getDetailedStats(equipmentId: Long, firstUsageDate: String?): List<StatsData> {
        val context = getApplication<Application>()
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
        statsList.add(fetchPeriod(context.getString(R.string.stats_this_week), equipmentId, thisWeekStart, System.currentTimeMillis() / 1000))

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
        statsList.add(fetchPeriod(context.getString(R.string.stats_last_week), equipmentId, lastWeekRange.first, lastWeekRange.second))

        // --- 3. THIS MONTH (1st to Now) ---
        val thisMonthStart = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            toStartOfDay()
        }.timeInMillis / 1000
        statsList.add(fetchPeriod(context.getString(R.string.stats_this_month), equipmentId, thisMonthStart, System.currentTimeMillis() / 1000))

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
        statsList.add(fetchPeriod(context.getString(R.string.stats_last_month), equipmentId, lastMonthRange.first, lastMonthRange.second))

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

            val title = if (year == currentYear) context.getString(R.string.stats_this_year) else year.toString()
            statsList.add(fetchPeriod(title, equipmentId, yearStart, yearEnd))
        }

        // Return only periods with actual workouts.
        return statsList.distinctBy { it.title }.filter { it.totalWorkouts > 0 }
    }

    private fun fetchPeriod(title: String, id: Long, startS: Long, endS: Long): StatsData {
        val raw = dbSummariesManager.getEquipmentStatsForPeriod(id, startS, endS)
        return StatsData.fromDatabase(title, raw)
    }


    fun deleteEquipment(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // dbHelper.deleteEquipment(name) // TODO: implement this.
            refreshEquipment()
        }
    }
}
