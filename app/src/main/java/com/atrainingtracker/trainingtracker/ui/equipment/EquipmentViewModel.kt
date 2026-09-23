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
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val isRetired: Boolean = false,
    val firstUsed: String?,
    val lastUsed: String?,
    val statsData: StatsData,
)

/**
 * ViewModel for managing equipment items (Bikes and Shoes), their linked sensors and sport types,
 * and associated usage statistics (REQ-UI-158, REQ-UI-160, ATT-1309).
 *
 * Implements self-initialization upon construction and reactive observation of Strava
 * equipment synchronization completion, ensuring UI views remain up-to-date without
 * requiring manual polling or external fragment triggers.
 *
 * @param application The Android Application instance.
 * @param ioDispatcher CoroutineDispatcher for offloading SQLite database queries.
 * @param dbEquipmentHelper SQLite database helper for equipment CRUD.
 * @param dbLinksHelper Manager for mapping equipment to sport types.
 * @param dbSportHelper Manager for sport type metadata.
 * @param dbDevicesHelper Manager for paired sensor devices.
 * @param dbSummariesManager Manager for workout summaries and equipment stats.
 * @param syncStatusFlow StateFlow emitting boolean indicator of active Strava equipment synchronization.
 */
class EquipmentViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dbEquipmentHelper: EquipmentDbHelper = EquipmentDbHelper(application),
    private val dbLinksHelper: SportTypeEquipmentLinkManager = SportTypeEquipmentLinkManager.getInstance(application),
    private val dbSportHelper: SportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application),
    private val dbDevicesHelper: DevicesDatabaseManager = DevicesDatabaseManager.getInstance(application),
    private val dbSummariesManager: WorkoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(application),
    private val syncStatusFlow: StateFlow<Boolean> = EquipmentRepository.isSyncing
) : AndroidViewModel(application) {

    private val _bikes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val bikes: StateFlow<List<EquipmentItem>> = _bikes

    private val _shoes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val shoes: StateFlow<List<EquipmentItem>> = _shoes

    val bikeSensors = dbDevicesHelper.getSensorsForSportType(BSportType.BIKE)
    val runSensors = dbDevicesHelper.getSensorsForSportType(BSportType.RUN)

    val bikeSportTypes = dbSportHelper.getSportTypes(BSportType.BIKE)
    val runSportTypes = dbSportHelper.getSportTypes(BSportType.RUN)

    @Volatile
    private var loadJob: Job? = null

    init {
        loadEquipment()
        observeSyncStatus()
    }

    /**
     * Observes the equipment synchronization state flow in [viewModelScope].
     * Triggers a reload if and only if the sync status transitions from true to false
     * (falling edge), indicating a background Strava sync has finished.
     */
    private fun observeSyncStatus() {
        viewModelScope.launch(ioDispatcher) {
            var wasSyncing = false
            syncStatusFlow.collect { syncing ->
                if (wasSyncing && !syncing) {
                    loadEquipment()
                }
                wasSyncing = syncing
            }
        }
    }

    /**
     * Loads equipment items asynchronously from SQLite and updates [_bikes] and [_shoes] StateFlows.
     * Cancels any pending prior job to prevent overlapping or stale writes during rapid successive trigger events.
     */
    @Synchronized
    fun loadEquipment() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(ioDispatcher) {
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
                        isRetired = data.isRetired,
                        firstUsed = stats.firstUsage?.substringBefore(" "),
                        lastUsed = stats.lastUsage?.substringBefore(" "),
                        statsData = StatsData.fromDatabase(
                            primaryTitle = data.name,
                            secondaryTitle = getApplication<Application>().getString(R.string.stats_total),
                            stats = stats,
                            equipmentId = data.id
                        )
                    )
                }
            }

            _bikes.value = fetchItems(BSportType.BIKE)
            _shoes.value = fetchItems(BSportType.RUN)
        }
    }

    fun getDetailedStats(equipmentName: String, equipmentId: Long, firstUsageDate: String?): List<StatsData> {
        return StatsPeriodHelper.getDetailedStats(
            context = getApplication(),
            firstUsageDate = firstUsageDate,
            fetchPeriod = { title, startS, endS ->
                val raw = dbSummariesManager.getEquipmentStatsForPeriod(equipmentId, startS, endS)
                StatsData.fromDatabase(
                    primaryTitle = equipmentName,
                    secondaryTitle = title,
                    stats = raw,
                    equipmentId = equipmentId,
                    startTimeS = startS,
                    endTimeS = endS
                )
            }
        )
    }

    fun updateEquipment(item: EquipmentItem) {
        viewModelScope.launch(ioDispatcher) {
            // TODO: when the strava frame type is changed, we should also update strava.

            dbEquipmentHelper.updateEquipment(
                item.id, item.name, item.frameType, item.linkedDeviceIds, item.isRetired
            )

            // Update Sport Type links
            dbLinksHelper.updateLinksForEquipment(item.id, item.linkedSportTypeIds)

            loadEquipment() // Refresh the list for the UI
        }
    }

    fun toggleRetired(item: EquipmentItem) {
        viewModelScope.launch(ioDispatcher) {
            dbEquipmentHelper.setEquipmentRetired(item.id, !item.isRetired)
            loadEquipment()
        }
    }

    fun addEquipment(name: String, frameType: Int, linkedDeviceIds: List<Long>, linkedSportTypes: List<Long>) {
        viewModelScope.launch(ioDispatcher) {

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
