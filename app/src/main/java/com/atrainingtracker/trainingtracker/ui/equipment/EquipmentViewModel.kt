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
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.SimpleSensorInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutStatsSummary
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsPeriodHelper
import com.atrainingtracker.trainingtracker.monetization.GarageLeagueDetector
import com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvent
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
 * @param ioDispatcher CoroutineDispatcher for offloading repository queries.
 * @param equipmentRepository Single source of truth for equipment and link management.
 * @param sportTypesRepository Single source of truth for sport types.
 * @param deviceDataRepository Single source of truth for paired devices and sensors.
 * @param workoutRepository Single source of truth for workout summaries and equipment stats.
 * @param syncStatusFlow StateFlow emitting boolean indicator of active Strava equipment synchronization.
 */
class EquipmentViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val equipmentRepository: EquipmentRepository? = null,
    private val sportTypesRepository: SportTypesRepository? = null,
    private val deviceDataRepository: DeviceDataRepository? = null,
    private val workoutRepository: WorkoutRepository? = null,
    private val syncStatusFlow: StateFlow<Boolean> = EquipmentRepository.isSyncing,
    private val garageLeagueDetector: GarageLeagueDetector = GarageLeagueDetector(application)
) : AndroidViewModel(application) {

    private val effectiveEquipmentRepo: EquipmentRepository by lazy {
        equipmentRepository ?: EquipmentRepository.getInstance(application)
    }
    private val effectiveSportRepo: SportTypesRepository by lazy {
        sportTypesRepository ?: SportTypesRepository.getInstance(application)
    }
    private val effectiveDeviceRepo: DeviceDataRepository by lazy {
        deviceDataRepository ?: DeviceDataRepository.getInstance(application)
    }
    private val effectiveWorkoutRepo: WorkoutRepository by lazy {
        workoutRepository ?: WorkoutRepository.getInstance(application)
    }

    private val _garageNudgeEvent = MutableStateFlow<GarageNudgeEvent?>(null)
    val garageNudgeEvent: StateFlow<GarageNudgeEvent?> = _garageNudgeEvent

    private val _bikes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val bikes: StateFlow<List<EquipmentItem>> = _bikes

    private val _shoes = MutableStateFlow<List<EquipmentItem>>(emptyList())
    val shoes: StateFlow<List<EquipmentItem>> = _shoes

    private val _allRemoteSensors = MutableStateFlow<List<SimpleSensorInfo>>(emptyList())
    val allRemoteSensors: StateFlow<List<SimpleSensorInfo>> = _allRemoteSensors

    private val _bikeSensors = MutableStateFlow<List<SimpleSensorInfo>>(emptyList())
    val bikeSensors: StateFlow<List<SimpleSensorInfo>> = _bikeSensors

    private val _shoeSensors = MutableStateFlow<List<SimpleSensorInfo>>(emptyList())
    val shoeSensors: StateFlow<List<SimpleSensorInfo>> = _shoeSensors

    val runSensors: StateFlow<List<SimpleSensorInfo>> = _shoeSensors

    val bikeSensorsList: List<SimpleSensorInfo>
        get() = _bikeSensors.value.ifEmpty { effectiveDeviceRepo.getSensorsForSportType(BSportType.BIKE) }

    val runSensorsList: List<SimpleSensorInfo>
        get() = _shoeSensors.value.ifEmpty { effectiveDeviceRepo.getSensorsForSportType(BSportType.RUN) }

    val bikeSportTypes by lazy { effectiveSportRepo.getSportTypes(BSportType.BIKE) }
    val runSportTypes by lazy { effectiveSportRepo.getSportTypes(BSportType.RUN) }

    @Volatile
    private var loadJob: Job? = null

    init {
        loadEquipment()
        observeSyncStatus()
        observeExternalEquipmentLinks()
    }

    /**
     * Observes external equipment link changes (e.g. from Sensor Settings / EditDeviceDialog)
     * and reactively reloads equipment data (REQ-UI-257).
     */
    private fun observeExternalEquipmentLinks() {
        viewModelScope.launch(ioDispatcher) {
            EquipmentRepository.equipmentLinksChanged.collect {
                loadEquipment()
            }
        }
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
                // Use repository to get full data objects
                val equipmentDataList = effectiveEquipmentRepo.getEquipmentItems(sportType)

                equipmentDataList.map { data ->
                    // Resolve linked sensors
                    val linkedDeviceIds = effectiveEquipmentRepo.getDeviceIdsForEquipment(data.id)
                    val sensorNames = linkedDeviceIds.mapNotNull { deviceId ->
                        effectiveDeviceRepo.getDeviceName(deviceId)
                    }.joinToString(", ")

                    // Resolve linked sport types
                    val linkedSportTypeIds = effectiveEquipmentRepo.getSportTypeIdsForEquipment(data.id)
                    val sportTypeNames = linkedSportTypeIds.mapNotNull { sportId ->
                        effectiveSportRepo.getUIName(sportId)
                    }.joinToString(", ")
                    Log.i("EquipmentViewModel", "${data.id} ${data.name}: $sportTypeNames")

                    val stats = effectiveWorkoutRepo.getEquipmentStats(data.id)

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
            val remoteSensors = effectiveDeviceRepo.allRemoteSensors
            _allRemoteSensors.value = remoteSensors
            _bikeSensors.value = EquipmentSensorOrdering.sortSensors(
                remoteSensors.filter {
                    DeviceDataRepository.isBikeSensor(it.deviceType) || DeviceDataRepository.isSharedSensor(it.deviceType)
                },
                BSportType.BIKE
            )
            _shoeSensors.value = EquipmentSensorOrdering.sortSensors(
                remoteSensors.filter {
                    DeviceDataRepository.isRunSensor(it.deviceType) || DeviceDataRepository.isSharedSensor(it.deviceType)
                },
                BSportType.RUN
            )
        }
    }

    /**
     * Atomically toggles a link between an equipment item and a remote sensor (REQ-UI-256).
     * Updates SQLite LINKS table immediately and reactively refreshes equipment state.
     *
     * @param equipmentId ID of the equipment (bike or shoe)
     * @param sensorId ID of the sensor device
     * @param isLinked True to establish link, false to unlink
     */
    fun setSensorLink(equipmentId: Long, sensorId: Long, isLinked: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            effectiveEquipmentRepo.setDeviceLink(equipmentId, sensorId, isLinked)
            loadEquipment()
        }
    }

    fun getDetailedStats(equipmentName: String, equipmentId: Long, firstUsageDate: String?): List<StatsData> {
        return StatsPeriodHelper.getDetailedStats(
            context = getApplication(),
            firstUsageDate = firstUsageDate,
            fetchPeriod = { title, startS, endS ->
                val raw = effectiveWorkoutRepo.getEquipmentStatsForPeriod(equipmentId, startS, endS)
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

            effectiveEquipmentRepo.updateEquipment(
                item.id, item.name, item.frameType, item.linkedDeviceIds, item.isRetired
            )

            // Update Sport Type links
            effectiveEquipmentRepo.updateLinksForEquipment(item.id, item.linkedSportTypeIds)

            loadEquipment() // Refresh the list for the UI
        }
    }

    fun toggleRetired(item: EquipmentItem) {
        viewModelScope.launch(ioDispatcher) {
            effectiveEquipmentRepo.setEquipmentRetired(item.id, !item.isRetired)
            loadEquipment()
        }
    }

    fun addEquipment(name: String, frameType: Int, linkedDeviceIds: List<Long>, linkedSportTypes: List<Long>) {
        viewModelScope.launch(ioDispatcher) {

            // Add the equipment and get the new ID
            val newEquipmentId = effectiveEquipmentRepo.addEquipment(name, frameType, linkedDeviceIds)

            // If the insertion was successful, update the link table
            if (newEquipmentId != -1L) {
                effectiveEquipmentRepo.updateLinksForEquipment(newEquipmentId, linkedSportTypes)
            }

            loadEquipment() // Refresh the list

            // Evaluate N+1 garage qualification post-save (REQ-SET-088.3)
            if (newEquipmentId != -1L) {
                checkGarageNudge()
            }
        }
    }

    /**
     * Evaluates garage league status after adding new equipment and emits a nudge event if qualified (REQ-SET-088.3).
     */
    fun checkGarageNudge() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val event = garageLeagueDetector.evaluateNudge()
                if (event != null) {
                    garageLeagueDetector.markNudgeShown(event.league)
                    _garageNudgeEvent.value = event
                }
            } catch (e: Throwable) {
                // Defensive guard against unmocked Android dependencies during unit testing
            }
        }
    }

    fun dismissGarageNudge() {
        _garageNudgeEvent.value = null
    }

    fun deleteEquipment(item: EquipmentItem) {
        viewModelScope.launch(Dispatchers.IO) {
            effectiveEquipmentRepo.deleteEquipment(item.id)
            loadEquipment() // Refresh the list
        }
    }
}
