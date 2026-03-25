package com.atrainingtracker.trainingtracker.ui.tracking

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

class BANALServiceRepository private constructor(private val context: Context) {

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // access to the BANALService
    private var banalServiceComm: BANALService.BANALServiceComm? = null
    private var isBoundToBanalService = false


    private val _activityType = MutableStateFlow<ActivityType>(ActivityType.getDefaultActivityType())
    val activityType: StateFlow<ActivityType> = _activityType
    // Note that we get the data by observing the BANALServiceComm.activityType

    private val _allFilteredSensorData = MutableStateFlow<List<FilteredSensorData<*>>>(emptyList())
    val allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>> = _allFilteredSensorData.asStateFlow()
    // Note that we get the filtered sensor data from the BANALServiceComm

    // The name of the device the BANALService is currently searching for.
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

    // --- Current Track (Breadcrumbs for the Map Polyline) ---
    private val _currentTrack = MutableStateFlow<List<LatLng>>(emptyList())
    val currentTrack: StateFlow<List<LatLng>> = _currentTrack.asStateFlow()

    // -- Tracking mode
    private val _trackingMode = MutableLiveData<TrackingMode>()
    val trackingMode: LiveData<TrackingMode> = _trackingMode

    // The receiver for the tracking mode
    private val trackingModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val newTrackingMode = TrainingApplication.getTrackingMode()
            if (_trackingMode.value != newTrackingMode) {
                _trackingMode.postValue(newTrackingMode)
            }
        }
    }

    // -- Lap Event
    private val _lapEvent = SingleLiveEvent<LapEvent>()
    val lapEvent: LiveData<LapEvent> = _lapEvent

    // the receiver for Lap Summary Event
    private val lapSummaryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val lapEvent = LapEvent(
                    lapNumber = it.getIntExtra(BANALService.PREV_LAP_NR, 0),
                    lapTime = it.getStringExtra(BANALService.PREV_LAP_TIME_STRING),
                    lapDistance = it.getStringExtra(BANALService.PREV_LAP_DISTANCE_STRING),
                    lapSpeed = it.getStringExtra(BANALService.PREV_LAP_SPEED_STRING)
                )
                // Post the new event to the LiveData
                _lapEvent.postValue(lapEvent)
            }
        }
    }


    init {
        // Set a default value when the repository is created
        _trackingMode.postValue(TrackingMode.READY)

        // Register the receiver to listen for changes from the TrainingApplication
        context.registerReceiver(
            trackingModeReceiver,
            IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED // Specify that it only receives broadcasts from this app
        )

        // Register the receiver to listen for changes from the BANALService
        context.registerReceiver(
            lapSummaryReceiver,
            IntentFilter(BANALService.LAP_SUMMARY),
            Context.RECEIVER_NOT_EXPORTED)


        repositoryScope.launch {
            // connect to the BANALService
            bindToBANALService()
        }

    }


    // Connection to BANALService and then observe it regularly.
    private val banalServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (DEBUG) Log.i(TAG, "onServiceConnected")

            // This is called when the connection to the service has been established.
            // We get the BANALServiceComm binder instance.
            banalServiceComm = service as? BANALService.BANALServiceComm
            isBoundToBanalService = banalServiceComm != null
            if (isBoundToBanalService) {
                // Once connected, start observing the service for data
                startObservingBANALService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (DEBUG) Log.i(TAG, "onServiceDisconnected")

            // This is called when the service connection is lost unexpectedly
            isBoundToBanalService = false
            banalServiceComm = null
        }
    }

    // Methods to bind and unbind to the BANALService
    fun bindToBANALService() {
        if (DEBUG) Log.i(TAG, "bindToBANALService()")

        if (!isBoundToBanalService) {
            val intent = Intent(context, BANALService::class.java)
            // BIND_AUTO_CREATE ensures the service is created if not already running
            context.bindService(intent, banalServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindFromBANALService() {
        if (DEBUG) Log.i(TAG, "unbindFromBANALService()")

        if (isBoundToBanalService) {
            context.unbindService(banalServiceConnection)
            isBoundToBanalService = false
            banalServiceComm = null
        }
    }

    // Observing the BANALService: This function will be called once the service is connected.
    private fun startObservingBANALService() {
        if (DEBUG) Log.i(TAG, "startObservingBANALService()")

        repositoryScope.launch {
            flow {
                while (true) {
                    emit(Unit)
                    delay(1000)
                }
            }.collect {
                if (DEBUG) Log.i(TAG, "collecting (banalServiceComm = ${banalServiceComm})")
                if (banalServiceComm == null) return@collect // Service not bound yet

                // --- ACTIVITY TYPE ---
                val newActivityType = banalServiceComm?.activityType
                if (_activityType.value != newActivityType) {
                    _activityType.value = newActivityType!!
                }

                // -- filtered sensor data --
                val newSensorData = banalServiceComm?.allFilteredSensorData
                if (newSensorData != null) {
                    // Update the StateFlow using the .value property
                    _allFilteredSensorData.value = newSensorData
                }

                Log.i(TAG, "getIdsOfFoundDevices: ${banalServiceComm?.getIdsOfNewlyFoundDevices()}")
                Log.i(TAG, "databaseIdsOfActiveDevices: ${banalServiceComm?.databaseIdsOfActiveRemoteDevices}")

                _searchingForDevice.value = banalServiceComm?.nameOfSearchingDevice
                _bSportType.value = banalServiceComm?.bSportType!!
                _activeRemoteDevicesIds.value = banalServiceComm?.databaseIdsOfActiveRemoteDevices!!
                _newlyFoundDevicesIds.value = banalServiceComm?.getIdsOfNewlyFoundDevices()!!
                _activeSensors.value = banalServiceComm?.availableSensorTypeSet?.toSet() ?: emptySet()

                _allActiveDevices.postValue(banalServiceComm?.activeDevicesIncludingSpeedAndLocationDevices ?: emptyList())

                // get the current location
                val latData = banalServiceComm?.getBestSensorData(SensorType.LATITUDE)
                val lonData = banalServiceComm?.getBestSensorData(SensorType.LONGITUDE)

                if (latData?.value is Double && lonData?.value is Double) {
                    Log.i(TAG, "got a location")

                    val newLocation = LatLng(
                        latData.value as Double,
                        lonData.value as Double
                    )
                    // Only update if the location actually changed to save UI re-compositions
                    if (_currentLocation.value != newLocation) {
                        _currentLocation.value = newLocation
                        if (TrainingApplication.isTracking()) {
                            _currentTrack.value = currentTrack.value + newLocation
                        }
                    }
                }

                if (DEBUG) Log.i(TAG, "BANALService:\n _searchingForDevice.value: ${_searchingForDevice.value},\n _bSportType.value: ${_bSportType.value},\n _foundDeviceIds.value: ${_activeRemoteDevicesIds.value},\n _activeSensors.value: ${_activeSensors.value}")
                if (DEBUG) Log.i(TAG, "trackingMode: ${_trackingMode.value}")
            }
        }
    }

    fun createFilter(filterData: FilterData) {
        if (banalServiceComm != null) banalServiceComm?.createFilter(filterData)
    }

    fun setUserSelectedSportType(bSportType: BSportType) {
        if (banalServiceComm != null) banalServiceComm?.setUserSelectedSportType(bSportType)
    }

    fun startSearchingForPairedDevices() {
        sendBroadcast(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
    }

    fun startSearchingForNewDevices(protocol: Protocol, deviceType: DeviceType) {
        val intent = Intent(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT).apply {
            putExtra(BANALService.PROTOCOL, protocol.name)
            putExtra(BANALService.DEVICE_TYPE, deviceType.name)
            setPackage(context.packageName)
        }
            context.sendBroadcast(intent)
    }

    fun stopSearchingForNewDevices() {
        sendBroadcast(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT)
    }

    /***********************************************************************************************
     * Simple helper to send a broadcast
     */
    private fun sendBroadcast(action: String) {
        val intent = Intent(action).apply {
            `package` = context.packageName
        }
        context.sendBroadcast(intent)
    }



    companion object {
        private const val TAG = "BANALServiceRepository"
        private val DEBUG = BANALService.getDebug(true)

        @Volatile
        private var INSTANCE: BANALServiceRepository? = null

        fun getInstance(context: Context): BANALServiceRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = BANALServiceRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }


}