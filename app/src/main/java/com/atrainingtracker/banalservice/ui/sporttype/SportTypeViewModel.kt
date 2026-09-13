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
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsPeriodHelper

data class SportTypeItem(
    val id: Long,
    val name: String,
    val bSportType: BSportType,
    val minSpeed: Double,
    val maxSpeed: Double,
    val stravaName: String?,
    val tcxName: String,
    val gcName: String,
    val linkedEquipmentIds: List<Long>,
    val linkedEquipmentNames: String,
    val isEditable: Boolean,
    val firstUsed: String?,
    val lastUsed: String?,
    val statsData: StatsData
)

class SportTypeViewModel(application: Application) : AndroidViewModel(application) {
    private val dbSportTypeManager = SportTypeDatabaseManager.getInstance(application)
    private val dbSummariesManager = WorkoutSummariesDatabaseManager.getInstance(application)
    private val dbLinksHelper = SportTypeEquipmentLinkManager.getInstance(application)
    private val dbEquipmentHelper = EquipmentDbHelper(application)


    private val _sportTypes = MutableStateFlow<List<SportTypeItem>>(emptyList())
    val sportTypes: StateFlow<List<SportTypeItem>> = _sportTypes.asStateFlow()

    fun availableEquipment(bSportType: BSportType): List<EquipmentDbHelper.EquipmentData> {
        return dbEquipmentHelper.getEquipmentItems(bSportType)
    }

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

                    val linkedEquipIds = dbLinksHelper.getEquipmentIdsForSport(id)
                    val linkedEquipmentNames = linkedEquipIds.filter { it > 0 }.mapNotNull { equipId ->
                        dbEquipmentHelper.getEquipmentNameFromId(equipId)
                    }.joinToString(", ")

                    val stats = dbSummariesManager.getSportTypeStats(id)
                    val sportTypeName = it.getString(it.getColumnIndexOrThrow(SportTypeDatabaseManager.SportType.UI_NAME))

                    list.add(SportTypeItem(
                        id = id,
                        name = sportTypeName,
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
                        linkedEquipmentIds = linkedEquipIds,
                        linkedEquipmentNames = linkedEquipmentNames,
                        isEditable = SportTypeDatabaseManager.canDelete(id),
                        firstUsed = stats.firstUsage?.substringBefore(" "),
                        lastUsed = stats.lastUsage?.substringBefore(" "),
                        statsData = StatsData.fromDatabase(
                            primaryTitle = sportTypeName,
                            secondaryTitle = getApplication<Application>().getString(R.string.stats_total),
                            stats = stats,
                            sportTypeId = id
                        )
                    ))
                }
            }
            _sportTypes.value = list
        }
    }

    fun getDetailedStats(sportTypeName: String, sportTypeId: Long, firstUsageDate: String?): List<StatsData> {
        return StatsPeriodHelper.getDetailedStats(
            context = getApplication(),
            firstUsageDate = firstUsageDate,
            fetchPeriod = { title, startS, endS ->
                val raw = dbSummariesManager.getSportTypeStatsForPeriod(sportTypeId, startS, endS)
                StatsData.fromDatabase(
                    primaryTitle = sportTypeName,
                    secondaryTitle = title,
                    stats = raw,
                    sportTypeId = sportTypeId,
                    startTimeS = startS,
                    endTimeS = endS
                )
            }
        )
    }
    
    
    fun saveSportType(item: SportTypeItem) {
        // forward to the database manager
        viewModelScope.launch(Dispatchers.IO) {
            val actualId = dbSportTypeManager.updateSportType(
                item.id,
                item.name,
                item.bSportType,
                item.minSpeed,
                item.maxSpeed,
                item.stravaName,
                item.tcxName,
                item.gcName
            )

            // also update the links
            if (actualId != -1L) {
                dbLinksHelper.updateLinksForSportType(actualId, item.linkedEquipmentIds)
            }

            // Refresh the list
            loadSportTypes()
        }
    }

    fun deleteSportType(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbSportTypeManager.delete(id)

            // also delete the links
            dbLinksHelper.updateLinksForSportType(id, emptyList())

            loadSportTypes()
        }
    }
}