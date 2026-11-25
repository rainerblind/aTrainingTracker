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
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.AttFusedAltitudePreferences
import com.atrainingtracker.banalservice.devices.AttFusedPreferences
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
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
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
 * Staged in-memory draft toggles for ATT Odometry Fusion (`REQ-UI-2799.2`, `ATT-8125`).
 * Persisted to [AttFusedPreferences] strictly on Save and discarded on Cancel.
 */
data class AttFusedDraftConfig(
    val gnssEnabled: Boolean = true,
    val wheelEnabled: Boolean = true,
    val gyroEnabled: Boolean = true,
    val autoCalibEnabled: Boolean = false
)

/**
 * Staged in-memory draft toggles for ATT Altitude & Slope Fusion (`REQ-UI-2799.2`, `ATT-8125`).
 * Persisted to [AttFusedAltitudePreferences] strictly on Save and discarded on Cancel.
 */
data class AttFusedAltitudeDraftConfig(
    val barometerEnabled: Boolean = true,
    val demEnabled: Boolean = false,
    val gpsEnabled: Boolean = false,
    val speedEnabled: Boolean = true
)

/**
 * Staged in-memory draft radar tail light selection (`REQ-UI-2799.2`, `ATT-8125`).
 * Applied to [RadarLightController] strictly on Save and discarded on Cancel.
 */
data class RadarLightDraftSelection(
    val isAutoMode: Boolean,
    val mode: RadarLightMode
)

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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tuningPreferencesDataStore: TuningPreferencesDataStore? = null
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        devicesRepository = DeviceDataRepository.getInstance(application),
        banalServiceRepository = BANALServiceRepository.getInstance(application),
        discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(application),
        equipmentRepository = EquipmentRepository.getInstance(application),
        ioDispatcher = Dispatchers.IO,
        tuningPreferencesDataStore = runCatching { TuningPreferencesDataStore.getInstance(application) }.getOrNull()
    )

    private fun resolveTuningDataStore(): TuningPreferencesDataStore? =
        tuningPreferencesDataStore ?: runCatching { TuningPreferencesDataStore.getInstance(application) }.getOrNull()

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

    // Stable persisted pre-edit device identity used for Polar capability evaluation (REQ-CON-049.4)
    private val _persistedDeviceIdentity = MutableStateFlow<DeviceUiData?>(null)
    val persistedDeviceIdentity: StateFlow<DeviceUiData?> = _persistedDeviceIdentity.asStateFlow()

    // Longitudinal wheel circumference history scoped to this sensor's linked bikes & positions (REQ-SEN-033.2)
    private val _wheelCircumferenceHistory = MutableStateFlow<List<WheelCircumferenceRecord>>(emptyList())
    val wheelCircumferenceHistory: StateFlow<List<WheelCircumferenceRecord>> = _wheelCircumferenceHistory.asStateFlow()

    // Indicates if the device could not be found/loaded from the database
    private val _deviceNotFound = MutableStateFlow(false)
    val deviceNotFound: StateFlow<Boolean> = _deviceNotFound.asStateFlow()

    // Draft-only staged preferences & radar mode persisted strictly on Save (REQ-UI-2799.2, ATT-8125)
    private val _attFusedDraft = MutableStateFlow<AttFusedDraftConfig?>(null)
    val attFusedDraft: StateFlow<AttFusedDraftConfig?> = _attFusedDraft.asStateFlow()

    private val _attFusedAltitudeDraft = MutableStateFlow<AttFusedAltitudeDraftConfig?>(null)
    val attFusedAltitudeDraft: StateFlow<AttFusedAltitudeDraftConfig?> = _attFusedAltitudeDraft.asStateFlow()

    private val _radarLightDraft = MutableStateFlow<RadarLightDraftSelection?>(null)
    val radarLightDraft: StateFlow<RadarLightDraftSelection?> = _radarLightDraft.asStateFlow()

    // Persisted & draft-staged Post-Workout ERTS smoothing preference for Fused sensors (REQ-FIL-017.5, REQ-UI-2799.2, ATT-8113)
    private val _postWorkoutErtsPersisted = MutableStateFlow(
        TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED
    )
    val postWorkoutErtsPersisted: StateFlow<Boolean> = _postWorkoutErtsPersisted.asStateFlow()

    private val _postWorkoutErtsDraft = MutableStateFlow<Boolean?>(null)
    val postWorkoutErtsDraft: StateFlow<Boolean?> = _postWorkoutErtsDraft.asStateFlow()

    private var ertsObserverJob: Job? = null

    private var hasUnsavedChanges: Boolean = false
    private var hasPendingBaselineReset: Boolean = false
    private var pendingBaselineResetCircumferenceMm: Int? = null

    /**
     * Loads the initial device data from the repository and populates the initial UI state.
     * Preserves dirty unsaved draft edits across configuration changes (rotation) for the same
     * [deviceId] while still reloading fresh persisted snapshots when clean or reopened
     * (`REQ-UI-2799.2`, `TST-UI-216.3`).
     */
    fun loadInitialDeviceData(deviceId: Long) {
        if (_editingId.value == deviceId && _deviceSnapshot.value != null && hasUnsavedChanges) {
            return
        }
        ertsObserverJob?.cancel()
        ertsObserverJob = null
        hasUnsavedChanges = false
        hasPendingBaselineReset = false
        pendingBaselineResetCircumferenceMm = null
        _attFusedDraft.value = null
        _attFusedAltitudeDraft.value = null
        _radarLightDraft.value = null
        _postWorkoutErtsDraft.value = null
        _postWorkoutErtsPersisted.value = TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED
        _wheelCircumferenceHistory.value = emptyList()
        val snapshot = devicesRepository.getDeviceSnapshotById(deviceId)
        if (snapshot == null) {
            _editingId.value = null
            _deviceSnapshot.value = null
            _persistedDeviceIdentity.value = null
            _deviceNotFound.value = true
        } else {
            _deviceNotFound.value = false
            _persistedDeviceIdentity.value = snapshot
            _deviceSnapshot.value = snapshot
            _editingId.value = deviceId
            if (snapshot.deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED) {
                _attFusedDraft.value = runCatching {
                    AttFusedDraftConfig(
                        gnssEnabled = AttFusedPreferences.isGnssEnabled(application),
                        wheelEnabled = AttFusedPreferences.isWheelEnabled(application),
                        gyroEnabled = AttFusedPreferences.isGyroEnabled(application),
                        autoCalibEnabled = AttFusedPreferences.isAutoCalibEnabled(application)
                    )
                }.getOrElse { AttFusedDraftConfig() }
            }
            if (snapshot.deviceType == DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED) {
                _attFusedAltitudeDraft.value = runCatching {
                    AttFusedAltitudeDraftConfig(
                        barometerEnabled = AttFusedAltitudePreferences.isBarometerEnabled(application),
                        demEnabled = AttFusedAltitudePreferences.isDemEnabled(application),
                        gpsEnabled = AttFusedAltitudePreferences.isGpsEnabled(application),
                        speedEnabled = AttFusedAltitudePreferences.isSpeedEnabled(application)
                    )
                }.getOrElse { AttFusedAltitudeDraftConfig() }
            }
            if (shouldShowPostWorkoutErtsToggle(snapshot.deviceType)) {
                val dataStore = resolveTuningDataStore()
                _postWorkoutErtsPersisted.value = dataStore?.getTuningConfig()?.postWorkoutErtsSmoothingEnabled
                    ?: TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED
                if (dataStore != null) {
                    ertsObserverJob = viewModelScope.launch {
                        runCatching {
                            dataStore.postWorkoutErtsSmoothingFlow.collect { persisted ->
                                if (_editingId.value == deviceId) {
                                    _postWorkoutErtsPersisted.value = persisted
                                }
                            }
                        }
                    }
                }
            }
            if (snapshot.protocol == Protocol.BLUETOOTH_LE && snapshot.deviceType == DeviceType.HRM) {
                runCatching { devicesRepository.backfillLegacyPolarIdentityIfNeeded(snapshot) }
            }
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
     * Stages a draft toggle change for ATT Odometry Fusion without writing to [AttFusedPreferences]
     * until [saveChanges] is invoked (`REQ-UI-2799.2`, `ATT-8125`).
     */
    fun onAttFusedConfigChanged(update: (AttFusedDraftConfig) -> AttFusedDraftConfig) {
        hasUnsavedChanges = true
        _attFusedDraft.update { current -> update(current ?: AttFusedDraftConfig()) }
    }

    /**
     * Stages a draft toggle change for ATT Altitude & Slope Fusion without writing to
     * [AttFusedAltitudePreferences] until [saveChanges] is invoked (`REQ-UI-2799.2`, `ATT-8125`).
     */
    fun onAttFusedAltitudeConfigChanged(update: (AttFusedAltitudeDraftConfig) -> AttFusedAltitudeDraftConfig) {
        hasUnsavedChanges = true
        _attFusedAltitudeDraft.update { current -> update(current ?: AttFusedAltitudeDraftConfig()) }
    }

    /**
     * Stages a draft toggle change for Post-Workout ERTS smoothing without writing to
     * [TuningPreferencesDataStore] until [saveChanges] is invoked (`REQ-FIL-017.5`, `REQ-UI-2799.2`, `ATT-8113`).
     */
    fun onPostWorkoutErtsSmoothingChanged(enabled: Boolean) {
        hasUnsavedChanges = true
        _postWorkoutErtsDraft.value = enabled
    }

    /**
     * Stages a draft radar tail light selection inside EditDeviceDialog without mutating
     * [RadarLightController] or `TuningPreferencesDataStore` until [saveChanges] is invoked
     * (`REQ-UI-2799.2`, `ATT-8125`).
     */
    fun onRadarLightSelectionChanged(isAutoMode: Boolean, mode: RadarLightMode) {
        hasUnsavedChanges = true
        _radarLightDraft.value = RadarLightDraftSelection(isAutoMode = isAutoMode, mode = mode)
    }

    /**
     * Stages a tire pressure baseline (iTPMS) reset to the currently selected wheel circumference
     * in draft state without writing to `Devices.db` or `Equipment.db` until [saveChanges] is called
     * (`REQ-TRK-050`, `REQ-SEN-033.2`, `REQ-UI-2799.2`, `ATT-8125`).
     */
    fun resetTirePressureBaseline() {
        if (_editingId.value == null && deviceSnapshot.value == null) return
        val currentCircumference = deviceSnapshot.value?.wheelCircumference
        val currentBaselineMeters = currentCircumference?.let { it / 1000.0 } ?: 0.0
        pendingBaselineResetCircumferenceMm = currentCircumference
        hasPendingBaselineReset = true
        updateState { it.copy(baselineCircumference = currentBaselineMeters) }
    }

    /**
     * Discards all staged in-memory draft changes without writing to SQLite, SharedPreferences,
     * or DataStore (`REQ-UI-2799.2`, `ATT-8125`). Called on dialog Cancel / dismiss so reopening
     * the same device reloads fresh persisted values.
     */
    fun discardDraft() {
        ertsObserverJob?.cancel()
        ertsObserverJob = null
        hasUnsavedChanges = false
        hasPendingBaselineReset = false
        pendingBaselineResetCircumferenceMm = null
        _attFusedDraft.value = null
        _attFusedAltitudeDraft.value = null
        _radarLightDraft.value = null
        _postWorkoutErtsDraft.value = null
        _postWorkoutErtsPersisted.value = TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED
        _editingId.value = null
        _deviceSnapshot.value = null
        _persistedDeviceIdentity.value = null
        _wheelCircumferenceHistory.value = emptyList()
        _deviceNotFound.value = false
    }

    /**
     * A generic helper function to safely update the state.
     * It ensures we always work with a non-null state and posts the result.
     */
    private fun updateState(updateAction: (currentState: DeviceUiData) -> DeviceUiData) {
        _deviceSnapshot.update { currentState ->
            if (currentState != null) {
                hasUnsavedChanges = true
                updateAction(currentState)
            } else {
                null
            }
        }
    }

    /**
     * Persists all staged device, preference, TPMS baseline, and radar draft edits permanently
     * (`REQ-UI-2799.2`, `ATT-8125`).
     */
    fun saveChanges() {
        val finalState = _deviceSnapshot.value ?: return
        val id = _editingId.value ?: finalState.id
        val fusedDraft = _attFusedDraft.value
        val fusedAltDraft = _attFusedAltitudeDraft.value
        val radarDraft = _radarLightDraft.value
        val ertsDraft = _postWorkoutErtsDraft.value
        val shouldResetBaseline = hasPendingBaselineReset
        val baselineCircumferenceMm = pendingBaselineResetCircumferenceMm ?: finalState.wheelCircumference

        hasUnsavedChanges = false
        hasPendingBaselineReset = false
        pendingBaselineResetCircumferenceMm = null
        _radarLightDraft.value = null
        _postWorkoutErtsDraft.value = null

        if (finalState.deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED && fusedDraft != null) {
            AttFusedPreferences.setGnssEnabled(application, fusedDraft.gnssEnabled)
            AttFusedPreferences.setWheelEnabled(application, fusedDraft.wheelEnabled)
            AttFusedPreferences.setGyroEnabled(application, fusedDraft.gyroEnabled)
            AttFusedPreferences.setAutoCalibEnabled(application, fusedDraft.autoCalibEnabled)
        }
        if (finalState.deviceType == DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED && fusedAltDraft != null) {
            AttFusedAltitudePreferences.setBarometerEnabled(application, fusedAltDraft.barometerEnabled)
            AttFusedAltitudePreferences.setDemEnabled(application, fusedAltDraft.demEnabled)
            AttFusedAltitudePreferences.setGpsEnabled(application, fusedAltDraft.gpsEnabled)
            AttFusedAltitudePreferences.setSpeedEnabled(application, fusedAltDraft.speedEnabled)
        }
        if (finalState.deviceType == DeviceType.BIKE_RADAR && radarDraft != null) {
            if (radarDraft.isAutoMode) {
                RadarLightController.setAutoMode()
            } else {
                RadarLightController.setManualLightMode(radarDraft.mode)
            }
        }
        val persistedErtsUpdate = ertsDraft?.takeIf { shouldShowPostWorkoutErtsToggle(finalState.deviceType) }
        if (persistedErtsUpdate != null) {
            _postWorkoutErtsPersisted.value = persistedErtsUpdate
        }

        viewModelScope.launch {
            if (persistedErtsUpdate != null) {
                withContext(NonCancellable) {
                    resolveTuningDataStore()?.updatePostWorkoutErtsSmoothingEnabled(persistedErtsUpdate)
                }
            }
            devicesRepository.updateDevice(finalState)
            if (shouldResetBaseline) {
                devicesRepository.resetTirePressureBaseline(id, baselineCircumferenceMm)
                val refreshedHistory = withContext(ioDispatcher) {
                    if (baselineCircumferenceMm != null && baselineCircumferenceMm > 0) {
                        equipmentRepository.recordManualBaselineResetForDevice(id, baselineCircumferenceMm)
                    }
                    equipmentRepository.getCircumferenceHistoryForDevice(id)
                }
                if (_editingId.value == id) {
                    _wheelCircumferenceHistory.value = refreshedHistory
                }
            }
        }
    }

    /** Returns the persisted Bluetooth MAC address for [deviceId] via the repository layer. */
    fun getBluetoothMacAddress(deviceId: Long): String? =
        devicesRepository.getBluetoothMACAddress(deviceId)

}