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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.GetMergedDevicesUseCase
import com.atrainingtracker.banalservice.ui.devices.devicedata.BikePowerFeatures
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicedata.PowerFeatureDisplay
import com.atrainingtracker.banalservice.ui.devices.devicedata.isWheelSpeedSensor
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the EditDeviceDialogFragment
 *
 * It provides the UI with device data and handles user actions like saving.
 * It survives configuration changes and acts as the bridge to the data layer (Repository).
 *
 * @param application The application context, needed to get the repository instance.
 */
class EditDeviceViewModel internal constructor(
    private val application: Application,
    private val devicesRepository: DeviceDataRepository,
    private val banalServiceRepository: BANALServiceRepository,
    private val discoveryManager: EquipmentAndSportTypeDiscoveryManager,
    private val equipmentRepository: EquipmentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        devicesRepository = DeviceDataRepository.getInstance(application),
        banalServiceRepository = BANALServiceRepository.getInstance(application),
        discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(application),
        equipmentRepository = EquipmentRepository.getInstance(application),
        ioDispatcher = Dispatchers.IO
    )

    private val useCase = GetMergedDevicesUseCase(
        devicesRepository,
        banalServiceRepository,
        application
    )

    // The ID of the device currently being edited.
    private val _editingId = MutableStateFlow<Long?>(null)

    // The device data with the data of its sensors.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val deviceLiveData: Flow<DeviceUiData?> = _editingId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else useCase.getMergedDeviceById(id)
        }

    // The single source of truth for the UI. This holds the CURRENT state of the device being edited.
    private val _deviceSnapshot = MutableStateFlow<DeviceUiData?>(null)
    val deviceSnapshot: StateFlow<DeviceUiData?> = _deviceSnapshot.asStateFlow()

    // Longitudinal wheel circumference history scoped to this sensor's linked bikes & positions (REQ-SEN-033.2)
    private val _wheelCircumferenceHistory = MutableStateFlow<List<WheelCircumferenceRecord>>(emptyList())
    val wheelCircumferenceHistory: StateFlow<List<WheelCircumferenceRecord>> = _wheelCircumferenceHistory.asStateFlow()

    // Indicates if the device could not be found/loaded from the database
    private val _deviceNotFound = MutableStateFlow(false)
    val deviceNotFound: StateFlow<Boolean> = _deviceNotFound.asStateFlow()

    /**
     * Loads the initial device data from the repository and populates the initial UI state.
     * This should be called once when the edit dialog is created.
     */
    fun loadInitialDeviceData(deviceId: Long) {
        _wheelCircumferenceHistory.value = emptyList()
        val snapshot = devicesRepository.getDeviceSnapshotById(deviceId)
        if (snapshot == null) {
            _deviceNotFound.value = true
        } else {
            _deviceNotFound.value = false
            _deviceSnapshot.value = snapshot
            _editingId.value = deviceId
            if (supportsWheelCircumference(snapshot)) {
                loadWheelCircumferenceHistory(deviceId)
            }
        }
    }

    private fun supportsWheelCircumference(data: DeviceUiData): Boolean {
        return isWheelSpeedSensor(data.deviceType) ||
            (data.deviceType == DeviceType.BIKE_POWER && (
                data.powerFeatures?.wheelRevolutionDataSupported == true ||
                    data.powerFeatures?.wheelSpeedDataSupported == true ||
                    data.powerFeatures?.wheelDistanceDataSupported == true
                ))
    }

    private fun loadWheelCircumferenceHistory(deviceId: Long) {
        viewModelScope.launch {
            val history = withContext(ioDispatcher) {
                equipmentRepository.getCircumferenceHistoryForDevice(deviceId)
            }
            if (_editingId.value == deviceId) {
                _wheelCircumferenceHistory.value = history
            }
        }
    }

    //--- dealing with wheel sizes
    val wheelSizeNames: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_names).toList()
    }
    // The corresponding integer values in millimeters (e.g., "2105")
    val wheelSizeValues: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_values).toList()
    }

    /**
     * Finds the index of a given circumference value in our master list.
     * @param circumference The circumference in mm (e.g., 2105).
     * @return The index in the list, or 0 if not found.
     */
    @Deprecated("Probably, we don't need this")
    fun getWheelSizePosition(circumference: Int?): Int {
        if (circumference == null) return 0
        val index = wheelSizeValues.indexOf(circumference.toString())
        return if (index != -1) index else 0
    }

    /**
     * Gets the circumference value in mm for a given spinner position.
     * @param position The selected index from the spinner.
     * @return The circumference as an Int, or null if invalid.
     */
    fun getWheelCircumferenceForPosition(position: Int): Int? {
        return wheelSizeValues.getOrNull(position)?.toIntOrNull()
    }


    /**
     * Converts a BikePowerFeatures object into a human-readable list of display-ready items,
     * including a flag to de-emphasize less common features. This keeps the conversion     * logic centralized in the repository.
     *
     * @param features The BikePowerFeatures object from the device data.
     * @return A list of [PowerFeatureDisplay] objects representing the supported features.
     */
    fun getPowerFeaturesForDisplay(features: BikePowerFeatures?): List<PowerFeatureDisplay> {
        if (features == null) {
            return emptyList()
        }

        val displayList = mutableListOf<PowerFeatureDisplay>()

        // --- Always add the default mandatory feature, not deemphasized ---
        displayList.add(
            PowerFeatureDisplay(
                application.getString(R.string.bike_power__instantaneous_power),
                false
            )
        )

        if (features.torqueDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__torque_data),
                    false
                )
            )
        }

        if (features.wheelRevolutionDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_revolution_data),
                    false
                )
            )
        }

        if (features.wheelSpeedDataSupported && features.wheelDistanceDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_speed_and_distance_data),
                    false
                )
            )
        } else if (features.wheelSpeedDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_speed_data),
                    false
                )
            )
        } else if (features.wheelDistanceDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_distance_data),
                    false
                )
            )
        }

        if (features.crankRevolutionDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__crank_revolution_data),
                    false
                )
            )
        }

        if (features.pedalPowerBalanceSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__pedal_power_balance),
                    false
                )
            )
        }

        if (features.extremaMagnitudesSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__extreme_magnitudes),
                    true
                )
            )
        }

        if (features.extremaAnglesSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__extreme_angles),
                    true
                )
            )
        }

        if (features.deadSpotAnglesSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__top_and_bottom_dead_sport_angles),
                    true
                )
            )
        }

        if (features.pedalSmoothnessSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__pedal_smoothness),
                    false
                )
            )
        }

        if (features.torqueEffectivenessSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__torque_effectiveness),
                    false
                )
            )
        }

        if (features.accumulatedTorqueSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__accumulated_torque),
                    true
                )
            )
        }

        if (features.accumulatedEnergySupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__accumulated_energy),
                    true
                )
            )
        }

        return displayList
    }

    // --- Event Handlers from the UI ---


    // called from edit device dialog fragment
    // -> update repository only onSave
    fun onDeviceNameChanged(newName: String) {
        updateState { it.copy(deviceName = newName) }
    }

    /**
     * Updates the in-memory draft equipment links and per-bike [WheelPosition] assignments
     * without mutating SQLite until [saveChanges] is called (`REQ-SEN-031.2`).
     */
    fun onEquipmentChanged(
        newEquipment: List<String>,
        newWheelPositions: Map<String, WheelPosition> = _deviceSnapshot.value?.linkedWheelPositions.orEmpty()
    ) {
        updateState { current ->
            val prunedPositions = if (isWheelSpeedSensor(current.deviceType)) {
                newEquipment.associateWith { eqName ->
                    newWheelPositions[eqName] ?: current.linkedWheelPositions[eqName] ?: WheelPosition.UNSPECIFIED
                }
            } else {
                emptyMap()
            }
            current.copy(
                linkedEquipment = newEquipment,
                linkedWheelPositions = prunedPositions,
                linkedSportTypes = discoveryManager.getSportNamesForEquipmentList(newEquipment)
            )
        }
    }


    fun onCalibrationFactorChanged(calibrationValue: Double) {
        // TODO: add validation before updating the state?
        updateState { it.copy(calibrationFactor = calibrationValue) }
    }

    fun onWheelCircumferenceChanged(wheelCircumference: Int) {
        updateState { it.copy(wheelCircumference = wheelCircumference) }
    }

    fun onPolarH10OfflineRecordingChanged(enabled: Boolean) {
        updateState { it.copy(polarH10OfflineRecording = enabled) }
    }

    /**
     * Updates the draft opt-in state for Polar live IMU streaming (`REQ-SET-112.1`, `ATT-8016`).
     */
    fun onPolarLiveImuEnabledChanged(enabled: Boolean) {
        updateState { it.copy(polarLiveImuEnabled = enabled) }
    }

    /**
     * Resets the persistent tire pressure baseline (iTPMS) to the currently selected wheel circumference,
     * synchronizing Devices.db and WheelCircumferenceHistory across linked bikes and refreshing the chart
     * (REQ-TRK-050, REQ-SEN-033.2).
     */
    fun resetTirePressureBaseline() {
        val id = _editingId.value ?: deviceSnapshot.value?.id ?: return
        val currentCircumference = deviceSnapshot.value?.wheelCircumference
        val currentBaselineMeters = currentCircumference?.let { it / 1000.0 } ?: 0.0
        updateState { it.copy(baselineCircumference = currentBaselineMeters) }
        viewModelScope.launch {
            devicesRepository.resetTirePressureBaseline(id, currentCircumference)
            val refreshedHistory = withContext(ioDispatcher) {
                if (currentCircumference != null && currentCircumference > 0) {
                    equipmentRepository.recordManualBaselineResetForDevice(id, currentCircumference)
                }
                equipmentRepository.getCircumferenceHistoryForDevice(id)
            }
            if (_editingId.value == id) {
                _wheelCircumferenceHistory.value = refreshedHistory
            }
        }
    }

    /**
     * A generic helper function to safely update the state.
     * It ensures we always work with a non-null state and posts the result.
     */
    private fun updateState(updateAction: (currentState: DeviceUiData) -> DeviceUiData) {
        _deviceSnapshot.update { currentState ->
            if (currentState != null) {
                updateAction(currentState)
            } else {
                null
            }
        }
    }


    /**
     * Takes the final state from _uiState and passes it to the repository to be saved permanently.
     */

    fun saveChanges() {
        val finalState = _deviceSnapshot.value ?: return

        viewModelScope.launch {
            devicesRepository.updateDevice(finalState)
        }
    }

    /** Returns the persisted Bluetooth MAC address for [deviceId] via the repository layer. */
    fun getBluetoothMacAddress(deviceId: Long): String? =
        devicesRepository.getBluetoothMACAddress(deviceId)

}