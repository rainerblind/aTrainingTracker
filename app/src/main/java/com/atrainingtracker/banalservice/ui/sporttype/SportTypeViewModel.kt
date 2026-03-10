package com.atrainingtracker.banalservice.ui.sporttype

import android.app.Application
import android.icu.util.Calendar
import android.content.ContentValues
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.atrainingtracker.R

class SportTypeViewModel(application: Application) : AndroidViewModel(application) {
    private val dbSportTypeManager = SportTypeDatabaseManager.getInstance(application)
    private val dbSummariesManager = WorkoutSummariesDatabaseManager.getInstance(application)

    private val _sportTypes = MutableStateFlow<List<SportTypeItem>>(emptyList())
    val sportTypes: StateFlow<List<SportTypeItem>> = _sportTypes.asStateFlow()

    init {
        loadSportTypes()
    }

    fun loadSportTypes() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<SportTypeItem>()
            val db = dbSportTypeManager.database
            val cursor = db.query(
                SportTypeDatabaseManager.SportType.TABLE,
                null, null, null, null, null,
                "${SportTypeDatabaseManager.SportType.MIN_AVG_SPEED} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.C_ID))
                    val stats = dbSummariesManager.getSportTypeStats(id)

                    list.add(SportTypeItem(
                        id = id,
                        name = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.UI_NAME)),
                        minSpeed = it.getDouble(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED)),
                        maxSpeed = it.getDouble(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED)),
                        stravaName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.STRAVA_NAME)),
                        tcxName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.TCX_NAME)),
                        gcName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME)),
                        isEditable = SportTypeDatabaseManager.canDelete(id),
                        firstUsed = stats.firstUsage?.substringBefore(" "),
                        lastUsed = stats.lastUsage?.substringBefore(" "),
                        statsData = StatsData.fromDatabase(
                            title = getApplication<Application>().getString(R.string.stats_total),
                            stats = stats
                        )
                    ))
                }
            }
            _sportTypes.value = list
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
        val raw = dbSummariesManager.getSportTypeStatsForPeriod(id, startS, endS)
        return StatsData.fromDatabase(title, raw)
    }
    
    
    // TODO: we don't do this here. This must be done by the databaseManger / Helper.
    fun saveSportType(item: SportTypeItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = dbSportTypeManager.database

            val values = ContentValues().apply {
                put(SportTypeDatabaseManager.SportType.UI_NAME, item.name)
                put(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED, item.minSpeed)
                put(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED, item.maxSpeed)
                put(SportTypeDatabaseManager.SportType.STRAVA_NAME, item.stravaName)
                put(SportTypeDatabaseManager.SportType.TCX_NAME, item.tcxName)
                put(SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME, item.gcName)
            }

            if (item.id == -1L) {
                db.insert(SportTypeDatabaseManager.SportType.TABLE, null, values)
            } else {
                db.update(
                    SportTypeDatabaseManager.SportType.TABLE,
                    values,
                    "${SportTypeDatabaseManager.SportType.C_ID}=?",
                    arrayOf(item.id.toString())
                )
            }
            loadSportTypes() // Refresh the list
        }
    }

    fun deleteSportType(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbSportTypeManager.delete(id)
            loadSportTypes()
        }
    }
}