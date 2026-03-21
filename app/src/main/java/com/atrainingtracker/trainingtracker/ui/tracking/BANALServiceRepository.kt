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
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
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


    // TODO: change to StateFlow?
    private val _activityType = MutableLiveData<ActivityType>(ActivityType.getDefaultActivityType())
    val activityType: LiveData<ActivityType> = _activityType
    // Note that we get the LiveData by observing the BANALServiceComm.activityType

    private val _allFilteredSensorData = MutableStateFlow<List<FilteredSensorData<*>>>(emptyList())
    val allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>> = _allFilteredSensorData.asStateFlow()
    // Note that we get the filtered sensor data from the BANALServiceComm

    // The name of the device the BANALService is currently searching for.
    private val _searchingForDevice = MutableStateFlow<String?>(null)
    val searchingForDevice: StateFlow<String?> = _searchingForDevice.asStateFlow()

    private val _bSportType = MutableStateFlow<BSportType>(BSportType.UNKNOWN)
    val bSportType: StateFlow<BSportType> = _bSportType.asStateFlow()

    private val _foundDeviceIds = MutableStateFlow<List<Long>>(emptyList())
    val foundDeviceIds: StateFlow<List<Long>> = _foundDeviceIds.asStateFlow()

    private val _activeSensors = MutableStateFlow<Set<SensorType>>(emptySet())
    val activeSensors: StateFlow<Set<SensorType>> = _activeSensors.asStateFlow()

    private val _activeDevicesForUI = MutableLiveData<List<MyDevice>>(emptyList())
    val activeDevicesForUI: LiveData<List<MyDevice>> = _activeDevicesForUI

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
        _activityType.postValue(ActivityType.getDefaultActivityType())

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
            // This is called when the service connection is lost unexpectedly
            isBoundToBanalService = false
            banalServiceComm = null
        }
    }

    // Methods to bind and unbind to the BANALService
    fun bindToBANALService() {
        if (!isBoundToBanalService) {
            val intent = Intent(context, BANALService::class.java)
            // BIND_AUTO_CREATE ensures the service is created if not already running
            context.bindService(intent, banalServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindFromBANALService() {
        if (isBoundToBanalService) {
            context.unbindService(banalServiceConnection)
            isBoundToBanalService = false
            banalServiceComm = null
        }
    }

    // Observing the BANALService: This function will be called once the service is connected.
    private fun startObservingBANALService() {
        repositoryScope.launch {
            flow {
                while (true) {
                    emit(Unit)
                    delay(1000)
                }
            }.collect {
                if (banalServiceComm == null) return@collect // Service not bound yet

                // --- ACTIVITY TYPE ---
                val newActivityType = banalServiceComm?.activityType
                if (_activityType.value != newActivityType) {
                    _activityType.postValue(newActivityType!!)
                }

                // -- filtered sensor data --
                val newSensorData = banalServiceComm?.allFilteredSensorData
                if (newSensorData != null) {
                    // Update the StateFlow using the .value property
                    _allFilteredSensorData.value = newSensorData
                }

                _searchingForDevice.value = banalServiceComm?.nameOfSearchingDevice
                _bSportType.value = banalServiceComm?.bSportType!!
                _foundDeviceIds.value = banalServiceComm?.databaseIdsOfActiveDevices!!
                _activeSensors.value = banalServiceComm?.availableSensorTypeSet?.toSet() ?: emptySet()

                _activeDevicesForUI.postValue(banalServiceComm?.activeDevicesForUI ?: emptyList())

                if (DEBUG) Log.i(TAG, "BANALService:\n _searchingForDevice.value: ${_searchingForDevice.value},\n _bSportType.value: ${_bSportType.value},\n _foundDeviceIds.value: ${_foundDeviceIds.value}},\n _activeSensors.value: ${_activeSensors.value}")
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