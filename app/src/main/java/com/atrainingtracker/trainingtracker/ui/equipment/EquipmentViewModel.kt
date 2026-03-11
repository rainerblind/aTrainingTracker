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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsPeriodHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class EquipmentItem(
    val id: Long,
    val name: String,
    val linkedDeviceIds: List<Long>,
    val linkedDeviceNames: String,
    val linkedSportTypeIds: List<Long>,
    val linkedSportTypeNames: String,
    val frameType: Int,
    val stravaName: String?,
    val stravaId: String?,
    val firstUsed: String?,
    val lastUsed: String?,
    val statsData: StatsData,
)

class EquipmentViewModel(application: Application) : AndroidViewModel(application) {
    private val dbEquipmentHelper = EquipmentDbHelper(application)
    private val dbLinksHelper = SportTypeEquipmentLinkManager.getInstance(application)
    private val dbSportHelper = SportTypeDatabaseManager.getInstance(application)
    private val dbDevicesHelper = DevicesDatabaseManager.getInstance(application)
    private val dbSummariesManager = WorkoutSummariesDatabaseManager.getInstance(application)


    private val _bikes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val bikes: StateFlow<List<EquipmentItem>> = _bikes

    private val _shoes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val shoes: StateFlow<List<EquipmentItem>> = _shoes

    val bikeSensors = dbDevicesHelper.getSensorsForSportType(BSportType.BIKE)
    val runSensors = dbDevicesHelper.getSensorsForSportType(BSportType.RUN)

    val bikeSportTypes = dbSportHelper.getSportTypes(BSportType.BIKE)
    val runSportTypes = dbSportHelper.getSportTypes(BSportType.RUN)

    fun loadEquipment() {
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

                    // Resolve linked sport types
                    val linkedSportTypeIds = dbLinksHelper.getSportTypeIdsForEquipment(data.id)
                    val sportTypeNames = linkedSportTypeIds.mapNotNull { sportId ->
                        dbSportHelper.getUIName(sportId)
                    }.joinToString(", ")
                    Log.i("EquipmentViewModel", "${data.id} ${data.name}: $sportTypeNames")

                    val stats = dbSummariesManager.getEquipmentStats(data.id)

                    // You can now access data.stravaName, data.frameType, etc.
                    EquipmentItem(
                        id = data.id,
                        name = data.name,
                        linkedDeviceIds = linkedDeviceIds,
                        linkedDeviceNames = sensorNames,
                        linkedSportTypeIds = linkedSportTypeIds,
                        linkedSportTypeNames = sportTypeNames,
                        frameType = data.frameType,
                        stravaName = data.stravaName,
                        stravaId = data.stravaId,
                        firstUsed = stats.firstUsage?.substringBefore(" "),
                        lastUsed = stats.lastUsage?.substringBefore(" "),
                        statsData = StatsData.fromDatabase(
                            title = getApplication<Application>().getString(R.string.stats_total),
                            stats = stats
                        )
                    )
                }
            }

            _bikes.value = fetchItems(BSportType.BIKE)
            _shoes.value = fetchItems(BSportType.RUN)
        }
    }

    fun getDetailedStats(equipmentId: Long, firstUsageDate: String?): List<StatsData> {
        return StatsPeriodHelper.getDetailedStats(
            context = getApplication(),
            firstUsageDate = firstUsageDate,
            fetchPeriod = { title, startS, endS ->
                val raw = dbSummariesManager.getEquipmentStatsForPeriod(equipmentId, startS, endS)
                StatsData.fromDatabase(title, raw)
            }
        )
    }

    fun updateEquipment(item: EquipmentItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: when the strava frame type is changed, we should also update strava.

            dbEquipmentHelper.updateEquipment(
                item.id, item.name, item.frameType, item.linkedDeviceIds
            )

            // Update Sport Type links
            dbLinksHelper.updateLinksForEquipment(item.id, item.linkedSportTypeIds)

            loadEquipment() // Refresh the list for the UI
        }
    }

    fun addEquipment(name: String, frameType: Int, linkedDeviceIds: List<Long>, linkedSportTypes: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {

            // Add the equipment and get the new ID
            val newEquipmentId = dbEquipmentHelper.addEquipment(name, frameType, linkedDeviceIds)

            // If the insertion was successful, update the link table
            if (newEquipmentId != -1L) {
                dbLinksHelper.updateLinksForEquipment(newEquipmentId, linkedSportTypes)
            }

            loadEquipment() // Refresh the list
        }
    }

    fun deleteEquipment(item: EquipmentItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dbEquipmentHelper.deleteEquipment(item.id)

            // Clean up links
            dbLinksHelper.updateLinksForEquipment(item.id, emptyList())

            loadEquipment() // Refresh the list
        }
    }
}
