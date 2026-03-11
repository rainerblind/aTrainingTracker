package com.atrainingtracker.banalservice.ui.sporttype

import android.app.Application
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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsPeriodHelper

data class SportTypeItem(
    val id: Long,
    val name: String,
    val bSportType: BSportType,
    val minSpeed: Double,
    val maxSpeed: Double,
    val stravaName: String,
    val tcxName: String,
    val gcName: String,
    val isEditable: Boolean,
    val firstUsed: String?,
    val lastUsed: String?,
    val statsData: StatsData
)

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
                        bSportType = try {
                            BSportType.valueOf(it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE)))
                        } catch (e: Exception) {
                            BSportType.UNKNOWN
                        },
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
    
    
    fun saveSportType(item: SportTypeItem) {
        // forward to the database manager
        viewModelScope.launch(Dispatchers.IO) {
            dbSportTypeManager.updateSportType(
                item.id,
                item.name,
                item.bSportType,
                item.minSpeed,
                item.maxSpeed,
                item.stravaName,
                item.tcxName,
                item.gcName
            )

            // Refresh the list
            loadSportTypes()
        }
    }

    fun deleteSportType(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbSportTypeManager.delete(id)
            loadSportTypes()
        }
    }
}