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

package com.atrainingtracker.trainingtracker.repositories

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Location
import com.atrainingtracker.util.safeStartService
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.tpms.IndirectTpmsEstimator
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.atrainingtracker.trainingtracker.ui.tracking.LiveSparklineBuffer
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.atrainingtracker.trainingtracker.util.SnapshotChunkedList
import com.google.android.gms.maps.model.LatLng
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch


/**
 * Represents a single lap event, holding the data needed for the summary dialog.
 * Using a data class makes the event self-contained and easy to pass around.
 */
data class LapEvent(
    val lapNumber: Int,
    val lapTime: String?,
    val lapDistance: String?,
    val lapSpeed: String?
)


/**
 * Represents the real-time telemetry state of a device.
 * Using data classes ensures that StateFlow correctly detects value changes.
 */
data class DeviceTelemetry(
    val deviceId: Long,
    val mainValue: com.atrainingtracker.banalservice.devices.SimpleSensorData?,
    val allValues: List<com.atrainingtracker.banalservice.devices.SimpleSensorData>,
    val batteryPercentage: Int = -1
)


/**
 * Registration record for a live sparkline buffer in the singleton repository (REQ-TRK-059.6, ATT-6710).
 */
data class SparklineRegistration(
    val sensorFieldId: Long,
    val horizonSeconds: Int,
    val sensorType: SensorType,
    val filterType: FilterType,
    val filterConstant: Double,
    val sourceDeviceName: String?,
    val configHash: Int,
    val buffer: LiveSparklineBuffer
)


/**
 * A singleton repository that acts as the single source of truth for all tracking-related data from the sensors.
 * It connects to the BANALService to provide a clean data source for all ViewModels.
 */
class BANALServiceRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --- Live Cockpit Sparkline Buffers (REQ-TRK-059.6, ATT-6710) ---
    private val sparklineBuffers = ConcurrentHashMap<Long, SparklineRegistration>()

    // --- Service Connection State ---
    private val _serviceBinder = MutableStateFlow<BANALService.BANALServiceComm?>(null)
    private var isBoundToBanalService = false
    // Guard to prevent multiple simultaneous bind attempts during app startup
    private var isBinding = false
    // Reference count to manage multiple ViewModels binding/unbinding
    private var bindReferenceCount = 0
    private val registeredRrBeatListeners = java.util.concurrent.CopyOnWriteArraySet<RrBeatListener>()


    // --- Reactive Data Streams (StateFlows for UI) ---
    
    private val _activityType = MutableStateFlow<ActivityType>(ActivityType.getDefaultActivityType())
    val activityType: StateFlow<ActivityType> = _activityType
    // Note that we get the data by observing the BANALServiceComm.activityType

    private val _allFilteredSensorData = MutableStateFlow<List<FilteredSensorData<*>>>(emptyList())
    val allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>> = _allFilteredSensorData.asStateFlow()
    // Note that we get the filtered sensor data from the BANALServiceComm

    // The name of the device the BANALService is currently searching for (e.g., "Heart Rate Monitor")
    private val _searchingForDevice = MutableStateFlow<String?>(null)
    val searchingForDevice: StateFlow<String?> = _searchingForDevice.asStateFlow()

    // Status whether the service is actively searching for NEW devices
    private val _isSearchingForNewDevices = MutableStateFlow(false)
    val isSearchingForNewDevices: StateFlow<Boolean> = _isSearchingForNewDevices.asStateFlow()

    private val _bSportType = MutableStateFlow<BSportType>(BSportType.UNKNOWN)
    val bSportType: StateFlow<BSportType> = _bSportType.asStateFlow()

    private val _activeRemoteDevicesIds = MutableStateFlow<List<Long>>(emptyList())
    val activeRemoteDevicesIds: StateFlow<List<Long>> = _activeRemoteDevicesIds.asStateFlow()

    // he newly found devices
    private val _newlyFoundDevicesIds = MutableStateFlow<List<Long>>(emptyList())
    val newlyFoundDevicesIds: StateFlow<List<Long>> = _newlyFoundDevicesIds.asStateFlow()

    private val _activeSensors = MutableStateFlow<Set<SensorType>>(emptySet())
    val activeSensors: StateFlow<Set<SensorType>> = _activeSensors.asStateFlow()

    /**
     * Whether a speed source (SPEED_mps) is currently registered or available in BANALService (REQ-TRK-044, ATT-4925).
     */
    val hasSpeedSource: Boolean
        get() = _serviceBinder.value?.availableSensorTypeSet?.contains(SensorType.SPEED_mps)
            ?: _activeSensors.value.contains(SensorType.SPEED_mps)

    // Mapping from SensorType to the ID of the device currently providing the "best" value
    private val _sensorSourceDeviceIds = MutableStateFlow<Map<SensorType, Long>>(emptyMap())
    val sensorSourceDeviceIds: StateFlow<Map<SensorType, Long>> = _sensorSourceDeviceIds.asStateFlow()

    // all active devices telemetry (synchronized every second)
    private val _allActiveDevicesTelemetry = MutableStateFlow<List<DeviceTelemetry>>(emptyList())
    val allActiveDevicesTelemetry: StateFlow<List<DeviceTelemetry>> = _allActiveDevicesTelemetry.asStateFlow()

    // StateFlow for the current location
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    // Current speed (mps)
    private val _currentSpeed = MutableStateFlow<Double?>(null)
    val currentSpeed: StateFlow<Double?> = _currentSpeed.asStateFlow()

    // Total distance accumulated in current workout
    private val _currentDistance = MutableStateFlow<Double?>(null)
    val currentDistance: StateFlow<Double?> = _currentDistance.asStateFlow()

    val itpmsEstimator = IndirectTpmsEstimator()

    // Current movement bearing
    private val _currentBearing = MutableStateFlow<Double?>(null)
    val currentBearing: StateFlow<Double?> = _currentBearing.asStateFlow()

    // Current altitude in meters (REQ-TRK-047)
    private val _currentAltitude = MutableStateFlow<Double?>(null)
    val currentAltitude: StateFlow<Double?> = _currentAltitude.asStateFlow()

    // --- Current Track (Breadcrumbs for the Map Polyline) ---
    private val _currentTrack = MutableStateFlow<List<LatLng>>(SnapshotChunkedList.empty())
    val currentTrack: StateFlow<List<LatLng>> = _currentTrack.asStateFlow()

    // --- Current Path Points (Distance + LatLng + Altitude) for Elevation Profile ---
    private val _currentPathPoints = MutableStateFlow<List<com.atrainingtracker.trainingtracker.ui.map.PathPoint>>(SnapshotChunkedList.empty())
    val currentPathPoints: StateFlow<List<com.atrainingtracker.trainingtracker.ui.map.PathPoint>> = _currentPathPoints.asStateFlow()

    // Altimeter calibration status (REQ-UI-199)
    private val _isAltimeterCalibrated = MutableStateFlow(false)
    val isAltimeterCalibrated: StateFlow<Boolean> = _isAltimeterCalibrated.asStateFlow()

    // --- Tracking Mode and Lifecycle Events ---
    
    private val _wasAutoPaused = MutableStateFlow(false)
    val wasAutoPaused: StateFlow<Boolean> = _wasAutoPaused.asStateFlow()

    private val _trackingMode = MutableLiveData<TrackingMode>()
    val trackingMode: LiveData<TrackingMode> = _trackingMode

    // Listens for tracking state changes (READY, TRACKING, PAUSED) from TrainingApplication
    private val trackingModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val wasAuto = intent?.getBooleanExtra(TrainingApplication.EXTRA_WAS_AUTO_PAUSED, false)
                ?: TrainingApplication.wasAutoPaused()
            _wasAutoPaused.value = wasAuto

            val newTrackingMode = TrainingApplication.getTrackingMode()
            if (_trackingMode.value != newTrackingMode) {
                _trackingMode.postValue(newTrackingMode)
            }
            if (newTrackingMode == TrackingMode.READY) {
                clearBreadcrumbs()
            }
        }
    }

    private val _lapEvent = SingleLiveEvent<LapEvent?>()
    val lapEvent: LiveData<LapEvent?> = _lapEvent

    // Listens for lap completions from BANALService to trigger the UI summary dialog
    private val lapSummaryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                // If this lap was triggered by a "Pause", do not notify the UI
                if (it.getBooleanExtra(BANALService.IS_PAUSE, false)) {
                    if (DEBUG) Log.i(TAG, "Suppressing lap summary dialog due to pause")
                    return
                }

                val lapEvent = LapEvent(
                    lapNumber = it.getIntExtra(BANALService.PREV_LAP_NR, 0),
                    lapTime = it.getStringExtra(BANALService.PREV_LAP_TIME_STRING),
                    lapDistance = it.getStringExtra(BANALService.PREV_LAP_DISTANCE_STRING),
                    lapSpeed = it.getStringExtra(BANALService.PREV_LAP_SPEED_STRING)
                )
                _lapEvent.postValue(lapEvent)
            }
        }
    }

    fun clearLapEvent() {
        _lapEvent.value = null
    }

    // Listens for barometric altitude correction broadcasts to adjust live path points (REQ-TRK-047.2)
    private val altitudeCorrectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val delta = it.getDoubleExtra(com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0)
                if (Math.abs(delta) >= 0.1 && Math.abs(delta) <= 500.0) {
                    val t0 = it.getLongExtra(com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.PREV_CALIBRATION_TIME_MS_EXTRA, -1L)
                    val t1 = it.getLongExtra(com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.CALIBRATION_TIME_MS_EXTRA, System.currentTimeMillis())
                    applyRetroactiveAltitudeCorrection(delta, t0, t1)
                }
            }
        }
    }


    private fun registerInternalReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (android.os.Build.VERSION.SDK_INT == 0) {
            // Plain JVM unit test stub where Process.myPid() inside ContextCompat is not mocked
            return
        }
        androidx.core.content.ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    init {
        _wasAutoPaused.value = TrainingApplication.wasAutoPaused()
        _trackingMode.postValue(TrainingApplication.getTrackingMode())

        registerInternalReceiver(
            trackingModeReceiver,
            IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED)
        )

        registerInternalReceiver(
            lapSummaryReceiver,
            IntentFilter(BANALService.LAP_SUMMARY)
        )

        registerInternalReceiver(
            altitudeCorrectionReceiver,
            IntentFilter(com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT)
        )

        // Start the long-lived observation loop that reacts to the binder state.
        // It will automatically start/stop based on whether we are connected to the service.
        startObservingBANALService()
    }

    // Handles the low-level Android Service Connection
    private val banalServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BANALService.BANALServiceComm
            if (DEBUG) Log.i(TAG, "onServiceConnected - binder=$binder")
            _serviceBinder.value = binder // This "wakes up" the observation loop
            isBoundToBanalService = true
            isBinding = false
            if (binder != null) {
                registeredRrBeatListeners.forEach { binder.addRrBeatListener(it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (DEBUG) Log.i(TAG, "onServiceDisconnected")
            _serviceBinder.value = null // This suspends the observation loop
            isBoundToBanalService = false
            isBinding = false
        }
    }

    /**
     * Binds the repository to the BANALService. 
     * Uses startService first to ensure the service remains alive even if the Activity unbinds.
     */
    fun bindToBANALService() {
        bindReferenceCount++
        if (DEBUG) Log.i(TAG, "bindToBANALService() - count=$bindReferenceCount, isBound=$isBoundToBanalService")
        if (!isBoundToBanalService && !isBinding) {
            isBinding = true
            val intent = Intent(appContext, BANALService::class.java)
            appContext.safeStartService(intent, TAG)
            appContext.bindService(intent, banalServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbinds from the service. The observation loop will automatically stop.
     */
    fun unbindFromBANALService() {
        if (bindReferenceCount > 0) bindReferenceCount--
        if (DEBUG) Log.i(TAG, "unbindFromBANALService() - count=$bindReferenceCount")
        
        if (bindReferenceCount == 0 && isBoundToBanalService) {
            try {
                _serviceBinder.value = null
                appContext.unbindService(banalServiceConnection)
                isBoundToBanalService = false
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Service not registered or already unbound: ${e.message}")
            }
        }
    }

    /**
     * Forcibly unbinds the repository from BANALService, resetting bindReferenceCount to 0
     * and releasing the service connection when idle (REQ-TRK-049.2).
     */
    fun forceUnbindFromBANALService() {
        if (DEBUG) Log.i(TAG, "forceUnbindFromBANALService() - resetting count=$bindReferenceCount, isBound=$isBoundToBanalService")
        bindReferenceCount = 0
        if (isBoundToBanalService) {
            try {
                _serviceBinder.value = null
                appContext.unbindService(banalServiceConnection)
                isBoundToBanalService = false
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Service not registered or already unbound: ${e.message}")
            }
        }
    }

    /**
     * The main observation loop. Using collectLatest on the binder flow ensures that:
     * 1. A single continuous loop runs as long as we have a binder.
     * 2. The loop is automatically cancelled if the binder becomes null (unbinding).
     * 3. We avoid "zombie" loops logging in the background after app shutdown.
     */
    private fun startObservingBANALService() {
        if (DEBUG) Log.i(TAG, "startObservingBANALService()")

        repositoryScope.launch {
            _serviceBinder.collectLatest { binder ->
                if (binder != null) {
                    if (DEBUG) Log.i(TAG, "Starting observation loop for binder: $binder")
                    while (true) {
                        // --- ACTIVITY TYPE ---
                        val activityType = binder.activityType
                        if (_activityType.value != activityType) {
                            _activityType.value = activityType
                        }

                        // --- FILTERED SENSOR DATA ---
                        val newSensorData = binder.allFilteredSensorData
                        if (newSensorData != null) {
                            updateSparklineBuffers(newSensorData)
                            _allFilteredSensorData.value = newSensorData
                        }

                        // --- DEVICE AND SENSOR STATUS ---
                        _searchingForDevice.value = binder.nameOfSearchingDevice
                        _isSearchingForNewDevices.value = binder.isSearchingForNewRemoteDevices()
                        _bSportType.value = binder.bSportType
                        _activeRemoteDevicesIds.value = binder.databaseIdsOfActiveRemoteDevices
                        _newlyFoundDevicesIds.value = binder.getIdsOfNewlyFoundDevices()

                        val activeSensors = binder.availableSensorTypeSet?.toSet() ?: emptySet()
                        _activeSensors.value = activeSensors

                        // Update source device mapping for active sensors
                        val sourceMapping = mutableMapOf<SensorType, Long>()
                        activeSensors.forEach { type ->
                            sourceMapping[type] = binder.getSourceDeviceId(type)
                        }
                        _sensorSourceDeviceIds.value = sourceMapping

                        _allActiveDevicesTelemetry.value = binder.activeDevicesIncludingSpeedAndLocationDevices?.map { device ->
                            DeviceTelemetry(
                                deviceId = device.deviceId,
                                mainValue = device.mainSensorData,
                                allValues = device.allSensorData ?: emptyList(),
                                batteryPercentage = device.batteryPercentage
                            )
                        } ?: emptyList()

                        // --- LOCATION AND NAVIGATION ---
                        val latData = binder.getBestSensorData(SensorType.LATITUDE)
                        val lonData = binder.getBestSensorData(SensorType.LONGITUDE)

                        var locationChanged = false
                        if (latData?.value is Double && lonData?.value is Double) {
                            val newLocation = LatLng(latData.value as Double, lonData.value as Double)
                            if (_currentLocation.value != newLocation) {
                                _currentLocation.value = newLocation
                                locationChanged = true
                            }
                        }

                        // --- PRIMARY METRICS ---
                        val currentSpeed = binder.getBestSensorData(SensorType.SPEED_mps)?.value as? Double
                        val currentAltitude = binder.getBestSensorData(SensorType.ALTITUDE)?.value as? Double
                        val currentDistance = binder.getBestSensorData(SensorType.DISTANCE_m)?.value as? Double

                        val distanceChanged = (currentDistance != null && currentDistance != _currentDistance.value)

                        _currentSpeed.value = currentSpeed
                        _currentAltitude.value = currentAltitude
                        _currentBearing.value = binder.getBestSensorData(SensorType.BEARING)?.value as? Double
                        _currentDistance.value = currentDistance
                        _isAltimeterCalibrated.value = binder.isAltimeterCalibrated()

                        // REQ-TRK-048.5: Suppress appending redundant track breadcrumbs when stationary and within deadband (3.0m)
                        val location = _currentLocation.value
                        val isStationary = (currentSpeed ?: 0.0) <= SpeedAndLocationDevice.SPEED_ZERO_THRESHOLD
                        if (location != null && locationChanged) {
                            val lastTrackPoint = _currentTrack.value.lastOrNull()
                            var isSpiderJitter = false
                            if (isStationary && lastTrackPoint != null) {
                                val distanceResults = FloatArray(1)
                                Location.distanceBetween(
                                    lastTrackPoint.latitude, lastTrackPoint.longitude,
                                    location.latitude, location.longitude,
                                    distanceResults
                                )
                                if (distanceResults[0] <= 3.0f) {
                                    isSpiderJitter = true
                                }
                            }

                            if (!isSpiderJitter && TrainingApplication.getTrackingMode() == TrackingMode.TRACKING) {
                                _currentTrack.value = appendToTrack(_currentTrack.value, location)
                            }
                        }

                        // REQ-TRK-042.1 & REQ-TRK-048.5: Only append PathPoint when location is valid and not stationary spider jitter
                        if (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING && location != null && (locationChanged || distanceChanged)) {
                            val lastPathPoint = _currentPathPoints.value.lastOrNull()?.latLng
                            var isPathJitter = false
                            if (isStationary && !distanceChanged && lastPathPoint != null) {
                                val distanceResults = FloatArray(1)
                                Location.distanceBetween(
                                    lastPathPoint.latitude, lastPathPoint.longitude,
                                    location.latitude, location.longitude,
                                    distanceResults
                                )
                                if (distanceResults[0] <= 3.0f) {
                                    isPathJitter = true
                                }
                            }

                            if (!isPathJitter) {
                                val newPathPoint = com.atrainingtracker.trainingtracker.ui.map.PathPoint(
                                    distance = currentDistance ?: 0.0,
                                    latLng = location,
                                    altitude = currentAltitude ?: 0.0
                                )
                                _currentPathPoints.value = appendToTrack(_currentPathPoints.value, newPathPoint)
                            }
                        }

                        // REQ-TRK-050: Indirect Tire Pressure Monitoring (iTPMS)
                        if (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING && location != null) {
                            val activeDevices = binder.activeDevicesIncludingSpeedAndLocationDevices
                            val wheelDevice = activeDevices?.firstOrNull {
                                it.deviceType == DeviceType.BIKE_SPEED || it.deviceType == DeviceType.BIKE_SPEED_AND_CADENCE
                            }
                            if (wheelDevice != null) {
                                val wheelDist = (wheelDevice.getSensor(SensorType.DISTANCE_m)?.value as? Number)?.toDouble()
                                val calib = (wheelDevice as? MyRemoteDevice)?.calibrationFactor ?: 2.100
                                val acc = (binder.getBestSensorData(SensorType.ACCURACY)?.value as? Number)?.toDouble() ?: 5.0

                                if (wheelDist != null) {
                                    val alert = itpmsEstimator.processTelemetry(
                                        lat = location.latitude,
                                        lon = location.longitude,
                                        bearingDeg = _currentBearing.value ?: 0.0,
                                        accuracyMeters = acc,
                                        speedMps = currentSpeed ?: 0.0,
                                        elevationMeters = currentAltitude,
                                        wheelDistanceMeters = wheelDist,
                                        calibrationFactorMeters = calib
                                    )
                                    if (alert != null) {
                                        val (titleRes, msgRes, severity) = when (alert) {
                                            is IndirectTpmsEstimator.TpmsAlertResult.LowPressure -> {
                                                Triple(
                                                    R.string.itpms_low_pressure_reminder_title,
                                                    R.string.itpms_low_pressure_reminder_message,
                                                    WorkoutNavigationEvents.TpmsAlertSeverity.WARNING
                                                )
                                            }
                                            is IndirectTpmsEstimator.TpmsAlertResult.SlowPuncture -> {
                                                Triple(
                                                    R.string.itpms_slow_puncture_alert_title,
                                                    R.string.itpms_slow_puncture_alert_message,
                                                    WorkoutNavigationEvents.TpmsAlertSeverity.CRITICAL
                                                )
                                            }
                                        }
                                        val dropStr = String.format(java.util.Locale.US, "%.1f%%", alert.dropPercentage)
                                        WorkoutNavigationEvents.triggerTpmsAlert(
                                            WorkoutNavigationEvents.TpmsAlert(
                                                title = appContext.getString(titleRes),
                                                message = appContext.getString(msgRes, dropStr),
                                                severity = severity,
                                                dropPercentage = alert.dropPercentage
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (DEBUG) Log.i(TAG, "BANALService update loop - binder=$binder")

                        delay(1000) // Pulse every second
                    }
                } else {
                    if (DEBUG) Log.i(TAG, "Binder is null, observation loop is suspended")
                }
            }
        }
    }

    fun clearBreadcrumbs() {
        if (DEBUG) Log.i(TAG, "clearBreadcrumbs()")
        _currentTrack.value = emptyList()
        _currentPathPoints.value = emptyList()
        itpmsEstimator.resetSession()
        clearSparklineBuffers()
    }

    /**
     * Registers or retrieves a singleton-managed LiveSparklineBuffer for a cockpit tile (REQ-TRK-059.6, ATT-6710).
     */
    fun registerSparklineBuffer(
        sensorFieldId: Long,
        horizonSeconds: Int,
        sensorType: SensorType,
        filterType: FilterType,
        filterConstant: Double,
        sourceDeviceName: String?
    ): LiveSparklineBuffer {
        val configHash = Objects.hash(sensorType, filterType, filterConstant, sourceDeviceName)
        val existing = sparklineBuffers[sensorFieldId]
        if (existing != null && existing.horizonSeconds == horizonSeconds && existing.configHash == configHash) {
            return existing.buffer
        }
        val buffer = LiveSparklineBuffer(horizonSeconds)
        sparklineBuffers[sensorFieldId] = SparklineRegistration(
            sensorFieldId = sensorFieldId,
            horizonSeconds = horizonSeconds,
            sensorType = sensorType,
            filterType = filterType,
            filterConstant = filterConstant,
            sourceDeviceName = sourceDeviceName,
            configHash = configHash,
            buffer = buffer
        )
        return buffer
    }

    /**
     * Removes a sparkline buffer registration when a tile is reconfigured or deleted.
     */
    fun removeSparklineBuffer(sensorFieldId: Long) {
        sparklineBuffers.remove(sensorFieldId)
    }

    /**
     * Returns an immutable snapshot list of sparkline samples for a sensorFieldId, or emptyList() if not registered.
     */
    fun getSparklineSamples(sensorFieldId: Long): List<Float> {
        return sparklineBuffers[sensorFieldId]?.buffer?.getSamples() ?: emptyList()
    }

    /**
     * Returns the elapsed temporal span in seconds for a sensorFieldId, or 0 if not registered (REQ-TRK-059.9, ATT-6695).
     */
    fun getSparklineSpanSeconds(sensorFieldId: Long): Int {
        return sparklineBuffers[sensorFieldId]?.buffer?.currentSpanSeconds ?: 0
    }

    /**
     * Returns the LiveSparklineBuffer instance for a sensorFieldId, if registered.
     */
    fun getSparklineBuffer(sensorFieldId: Long): LiveSparklineBuffer? {
        return sparklineBuffers[sensorFieldId]?.buffer
    }

    /**
     * Resets all registered sparkline buffers (REQ-TRK-059.6, ATT-6710).
     */
    fun clearSparklineBuffers() {
        sparklineBuffers.values.forEach { it.buffer.clear() }
    }

    /**
     * Feeds incoming filtered sensor data to active sparkline buffers (REQ-TRK-059.6, ATT-6710).
     */
    fun updateSparklineBuffers(sensorDataList: List<FilteredSensorData<*>>) {
        if (sparklineBuffers.isEmpty() || sensorDataList.isEmpty()) return
        for (sensorData in sensorDataList) {
            val numericVal = (sensorData.value as? Number)?.toFloat() ?: continue
            val hash = Objects.hash(sensorData.sensorType, sensorData.filterType, sensorData.filterConstant, sensorData.deviceName)
            for (reg in sparklineBuffers.values) {
                if (reg.configHash == hash) {
                    reg.buffer.addSample(numericVal)
                }
            }
        }
    }

    /**
     * REQ-TRK-047.2: Retroactively adjusts recorded path points for the live cockpit elevation profile.
     * When t0Ms <= 0, applies uniform shift (Case A).
     * When t0Ms > 0, applies piecewise-linear drift interpolation across [t0, t1] (Case B).
     */
    fun applyRetroactiveAltitudeCorrection(deltaOffset: Double, t0Ms: Long = -1L, t1Ms: Long = -1L) {
        if (Math.abs(deltaOffset) < 0.001) return
        val currentPoints = _currentPathPoints.value
        if (currentPoints.isEmpty()) return

        val count = currentPoints.size
        val updatedPoints = if (t0Ms <= 0L || t1Ms <= t0Ms) {
            // Case A: Uniform shift
            currentPoints.map { point ->
                val alt = point.altitude?.let { it + deltaOffset }
                point.copy(altitude = alt)
            }
        } else {
            // Case B: Piecewise-linear drift across path points
            currentPoints.mapIndexed { index, point ->
                val fraction = if (count > 1) index.toDouble() / (count - 1) else 1.0
                val alt = point.altitude?.let { it + deltaOffset * fraction }
                point.copy(altitude = alt)
            }
        }
        _currentPathPoints.value = SnapshotChunkedList.from(updatedPoints)
    }

    private fun <T> appendToTrack(current: List<T>, element: T): SnapshotChunkedList<T> {
        return if (current is SnapshotChunkedList<T>) {
            current.append(element)
        } else {
            SnapshotChunkedList.from(current).append(element)
        }
    }

    // --- Helper methods to communicate with BANALService via Binder ---

    fun createFilter(filterData: FilterData) {
        _serviceBinder.value?.createFilter(filterData)
    }

    fun setUserSelectedSportType(bSportType: BSportType) {
        _bSportType.value = bSportType
        _serviceBinder.value?.setUserSelectedSportType(bSportType)
    }

    fun startSearchingForPairedDevices() {
        sendBroadcast(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
    }

    fun startSearchingForNewDevices(protocol: Protocol, deviceType: DeviceType) {
        val intent = Intent(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT).apply {
            putExtra(BANALService.PROTOCOL, protocol.name)
            putExtra(BANALService.DEVICE_TYPE, deviceType.name)
            `package` = appContext.packageName
        }
        appContext.sendBroadcast(intent)
    }

    fun stopSearchingForNewDevices() {
        sendBroadcast(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT)
    }

    /**
     * Dispatches altimeter calibration with the specified reference altitude (REQ-UI-199).
     *
     * @param altitude Target reference altitude in meters.
     * @return true if calibration was accepted/applied, false if service is unbound.
     */
    fun calibrateAltimeter(altitude: Double): Boolean {
        val binder = _serviceBinder.value ?: return false
        val success = binder.calibrateAltimeter(altitude)
        _isAltimeterCalibrated.value = binder.isAltimeterCalibrated()
        return success
    }

    /**
     * Simple internal helper to send a broadcast within the application package
     */
    private fun sendBroadcast(action: String) {
        val intent = Intent(action).apply {
            `package` = appContext.packageName
        }
        appContext.sendBroadcast(intent)
    }

    /**
     * Subscribes an [RrBeatListener] to the stream of raw RR beat events.
     * Re-attaches automatically when reconnecting to BANALService (REQ-PER-032.3).
     */
    fun addRrBeatListener(listener: RrBeatListener) {
        registeredRrBeatListeners.add(listener)
        _serviceBinder.value?.addRrBeatListener(listener)
    }

    /**
     * Unsubscribes an [RrBeatListener] from the stream of raw RR beat events.
     */
    fun removeRrBeatListener(listener: RrBeatListener) {
        registeredRrBeatListeners.remove(listener)
        _serviceBinder.value?.removeRrBeatListener(listener)
    }



    companion object {
        private const val TAG = "BANALServiceRepository"
        private val DEBUG = BANALService.getDebug(true)

        @Volatile
        private var INSTANCE: BANALServiceRepository? = null

        /**
         * Returns the thread-safe singleton instance of the repository.
         */
        fun getInstance(context: Context): BANALServiceRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = BANALServiceRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
