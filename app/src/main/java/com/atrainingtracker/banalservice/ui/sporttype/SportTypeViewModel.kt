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
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsPeriodHelper

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

    fun getDetailedStats(sportTypeId: Long, firstUsageDate: String?): List<StatsData> {
        return StatsPeriodHelper.getDetailedStats(
            context = getApplication(),
            firstUsageDate = firstUsageDate,
            fetchPeriod = { title, startS, endS ->
                val raw = dbSummariesManager.getSportTypeStatsForPeriod(sportTypeId, startS, endS)
                StatsData.fromDatabase(title, raw)
            }
        )
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