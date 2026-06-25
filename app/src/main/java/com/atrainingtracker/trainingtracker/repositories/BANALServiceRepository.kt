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
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.google.android.gms.maps.model.LatLng
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
 * A singleton repository that acts as the single source of truth for all tracking-related data from the sensors.
 * It connects to the BANALService to provide a clean data source for all ViewModels.
 */
class BANALServiceRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --- Service Connection State ---
    private val _serviceBinder = MutableStateFlow<BANALService.BANALServiceComm?>(null)
    private var isBoundToBanalService = false
    // Guard to prevent multiple simultaneous bind attempts during app startup
    private var isBinding = false


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

    private val _bSportType = MutableStateFlow<BSportType>(BSportType.UNKNOWN)
    val bSportType: StateFlow<BSportType> = _bSportType.asStateFlow()

    private val _activeRemoteDevicesIds = MutableStateFlow<List<Long>>(emptyList())
    val activeRemoteDevicesIds: StateFlow<List<Long>> = _activeRemoteDevicesIds.asStateFlow()

    // he newly found devices
    private val _newlyFoundDevicesIds = MutableStateFlow<List<Long>>(emptyList())
    val newlyFoundDevicesIds: StateFlow<List<Long>> = _newlyFoundDevicesIds.asStateFlow()

    private val _activeSensors = MutableStateFlow<Set<SensorType>>(emptySet())
    val activeSensors: StateFlow<Set<SensorType>> = _activeSensors.asStateFlow()

    // all active devices (including the smartphones speed and location devices)
    private val _allActiveDevices = MutableLiveData<List<MyDevice>>(emptyList())
    val allActiveDevices: LiveData<List<MyDevice>> = _allActiveDevices

    // StateFlow for the current location
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    // Current speed (mps)
    private val _currentSpeed = MutableStateFlow<Double?>(null)
    val currentSpeed: StateFlow<Double?> = _currentSpeed.asStateFlow()

    // Total distance accumulated in current workout
    private val _currentDistance = MutableStateFlow<Double?>(null)
    val currentDistance: StateFlow<Double?> = _currentDistance.asStateFlow()

    // Current movement bearing
    private val _currentBearing = MutableStateFlow<Double?>(null)
    val currentBearing: StateFlow<Double?> = _currentBearing.asStateFlow()

    // --- Current Track (Breadcrumbs for the Map Polyline) ---
    private val _currentTrack = MutableStateFlow<List<LatLng>>(emptyList())
    val currentTrack: StateFlow<List<LatLng>> = _currentTrack.asStateFlow()

    // --- Current Path Points (Distance + LatLng + Altitude) for Elevation Profile ---
    private val _currentPathPoints = MutableStateFlow<List<com.atrainingtracker.trainingtracker.ui.map.PathPoint>>(emptyList())
    val currentPathPoints: StateFlow<List<com.atrainingtracker.trainingtracker.ui.map.PathPoint>> = _currentPathPoints.asStateFlow()

    // --- Tracking Mode and Lifecycle Events ---
    
    private val _trackingMode = MutableLiveData<TrackingMode>()
    val trackingMode: LiveData<TrackingMode> = _trackingMode

    // Listens for tracking state changes (READY, TRACKING, PAUSED) from TrainingApplication
    private val trackingModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val newTrackingMode = TrainingApplication.getTrackingMode()
            if (_trackingMode.value != newTrackingMode) {
                _trackingMode.postValue(newTrackingMode)
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


    init {
        _trackingMode.postValue(TrackingMode.READY)

        appContext.registerReceiver(
            trackingModeReceiver,
            IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )

        appContext.registerReceiver(
            lapSummaryReceiver,
            IntentFilter(BANALService.LAP_SUMMARY),
            Context.RECEIVER_NOT_EXPORTED)

        // Start the long-lived observation loop that reacts to the binder state.
        // It will automatically start/stop based on whether we are connected to the service.
        startObservingBANALService()
    }

    // Handles the low-level Android Service Connection
    private val banalServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (DEBUG) Log.i(TAG, "onServiceConnected")
            val binder = service as? BANALService.BANALServiceComm
            _serviceBinder.value = binder // This "wakes up" the observation loop
            isBoundToBanalService = true
            isBinding = false
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
        if (DEBUG) Log.i(TAG, "bindToBANALService()")
        if (!isBoundToBanalService && !isBinding) {
            isBinding = true
            val intent = Intent(appContext, BANALService::class.java)
            appContext.startService(intent)
            appContext.bindService(intent, banalServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbinds from the service. The observation loop will automatically stop.
     */
    fun unbindFromBANALService() {
        if (DEBUG) Log.i(TAG, "unbindFromBANALService()")
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
                            _allFilteredSensorData.value = newSensorData
                        }

                        // --- DEVICE AND SENSOR STATUS ---
                        _searchingForDevice.value = binder.nameOfSearchingDevice
                        _bSportType.value = binder.bSportType
                        _activeRemoteDevicesIds.value = binder.databaseIdsOfActiveRemoteDevices
                        _newlyFoundDevicesIds.value = binder.getIdsOfNewlyFoundDevices()
                        _activeSensors.value = binder.availableSensorTypeSet?.toSet() ?: emptySet()

                        _allActiveDevices.postValue(binder.activeDevicesIncludingSpeedAndLocationDevices ?: emptyList())

                        // --- LOCATION AND NAVIGATION ---
                        val latData = binder.getBestSensorData(SensorType.LATITUDE)
                        val lonData = binder.getBestSensorData(SensorType.LONGITUDE)

                        if (latData?.value is Double && lonData?.value is Double) {
                            val newLocation = LatLng(latData.value as Double, lonData.value as Double)
                            if (_currentLocation.value != newLocation) {
                                _currentLocation.value = newLocation
                                if (TrainingApplication.isTracking()) {
                                    _currentTrack.value = currentTrack.value + newLocation
                                }
                            }
                        }

                        // --- PRIMARY METRICS ---
                        val currentSpeed = binder.getBestSensorData(SensorType.SPEED_mps)?.value as Double?
                        val currentAltitude = binder.getBestSensorData(SensorType.ALTITUDE)?.value as Double?
                        val currentDistance = binder.getBestSensorData(SensorType.DISTANCE_m)?.value as Double?

                        _currentSpeed.value = currentSpeed
                        _currentBearing.value = binder.getBestSensorData(SensorType.BEARING)?.value as Double?
                        _currentDistance.value = currentDistance

                        if (TrainingApplication.isTracking()) {
                            val newPathPoint = com.atrainingtracker.trainingtracker.ui.map.PathPoint(
                                distance = currentDistance ?: 0.0,
                                latLng = _currentLocation.value ?: LatLng(0.0, 0.0),
                                altitude = currentAltitude ?: 0.0
                            )
                            _currentPathPoints.value = _currentPathPoints.value + newPathPoint
                        }

                        if (DEBUG) Log.i(TAG, "BANALService update:\n _searchingForDevice: ${_searchingForDevice.value},\n _bSportType: ${_bSportType.value},\n _foundDeviceIds: ${_activeRemoteDevicesIds.value},\n _activeSensors: ${_activeSensors.value}")

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
    }

    // --- Helper methods to communicate with BANALService via Binder ---

    fun createFilter(filterData: FilterData) {
        _serviceBinder.value?.createFilter(filterData)
    }

    fun setUserSelectedSportType(bSportType: BSportType) {
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
     * Simple internal helper to send a broadcast within the application package
     */
    private fun sendBroadcast(action: String) {
        val intent = Intent(action).apply {
            `package` = appContext.packageName
        }
        appContext.sendBroadcast(intent)
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
