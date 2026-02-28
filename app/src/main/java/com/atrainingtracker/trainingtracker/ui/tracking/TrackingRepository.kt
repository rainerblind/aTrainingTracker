package com.atrainingtracker.trainingtracker.ui.tracking

import android.app.Application
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
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class for holding tab info, can be moved to a more common location later.
data class TrackingViewInfo(
    val tabViewId: Long,
    val name: String,
    val showMap: Boolean,
    val showLapButton: Boolean
)

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
 * A data class to hold the complete configuration for a single sensor field,
 * as loaded from the TrackingViewsDatabaseManager.
 */
data class SensorFieldConfig(
    val sensorFieldId: Long,
    val rowNr: Int,
    val colNr: Int,
    val viewSize: ViewSize,
    val sensorType: SensorType,
    val filterType: FilterType,
    val filterConstant: Double,
    val sourceDeviceId: Long,
    val sourceDeviceName: String? = null
)

/**
 * A singleton repository that acts as the single source of truth for all tracking-related data.
 * It connects to the BANALService and the local database to provide a clean data source
 * for all ViewModels.
 */
class TrackingRepository private constructor(private val application: Application) {

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val viewsDbManager = TrackingViewsDatabaseManager.getInstance(application)
    // Add a member for the DevicesDatabaseManager
    private val devicesDbManager = DevicesDatabaseManager.getInstance(application)

    // This StateFlow acts as a signal. Changing its value will trigger a refresh of the sensor field data
    private val configUpdateTrigger = MutableStateFlow(0)


    // access to the BANALService
    private var banalServiceComm: BANALService.BANALServiceComm? = null
    private var isBoundToBanalService = false


    private val _activityType = MutableLiveData<ActivityType>()
    val activityType: LiveData<ActivityType> = _activityType
    // Note that we get the LiveData by observing the BANALServiceComm.activityType

    private val _allFilteredSensorData = MutableStateFlow<List<FilteredSensorData<*>>>(emptyList())
    val allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>> = _allFilteredSensorData.asStateFlow()
    // Note that we get the filtered sensor data from the BANALServiceComm


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

        _trackingMode.postValue(TrackingMode.WAITING_FOR_BANAL_SERVICE)

        // Register the receiver to listen for changes from the TrainingApplication
        application.registerReceiver(
            trackingModeReceiver,
            IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED // Specify that it only receives broadcasts from this app
        )

        // Register the receiver to listen for changes from the BANALService
        application.registerReceiver(
            lapSummaryReceiver,
            IntentFilter(BANALService.LAP_SUMMARY),
            Context.RECEIVER_NOT_EXPORTED)


        repositoryScope.launch {
            // connect to the BANALService
            bindToBANALService()
        }
    }

    /**
     * Retrieves the ActivityType associated with a specific view definition.
     */
    suspend fun getActivityTypeForView(tabViewId: Long): ActivityType? {
        return withContext(Dispatchers.IO) {
            viewsDbManager.getActivityTypeForTab(tabViewId)
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
            val intent = Intent(application, BANALService::class.java)
            // BIND_AUTO_CREATE ensures the service is created if not already running
            application.bindService(intent, banalServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindFromBANALService() {
        if (isBoundToBanalService) {
            application.unbindService(banalServiceConnection)
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
            }
        }
    }



    // --- Tracking Views ---
    /**
     * Loads the list of available tracking views for a given activity type from the database.
     */
    fun getTrackingViews(activityType: ActivityType): List<TrackingViewInfo> {
        val dbManager = TrackingViewsDatabaseManager.getInstance(application)
        val viewList = mutableListOf<TrackingViewInfo>()

        val cursor = dbManager.database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON
            ),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=?",
            arrayOf(activityType.name),
            null,
            null,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR} ASC"
        )

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getLong(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID))
                    val name = it.getString(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME))
                    val showMap = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP)) == 1
                    val showLapButton = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LAP_BUTTON)) == 1

                    viewList.add(TrackingViewInfo(id, name, showMap, showLapButton))
                } while (it.moveToNext())
            }
        }
        return viewList
    }

    /**
     * Retrieves a flow of sensor configurations for a given view.
     * This flow will automatically re-emit the latest data whenever the configUpdateTrigger changes.
     */
    fun getSensorFieldConfigsForView(tabViewId: Long): Flow<List<SensorFieldConfig>> {
        // The flow now collects from the trigger.
        return configUpdateTrigger.map {
            // When the trigger changes, this 'map' block re-executes.
            Log.d("TrackingRepository", "Config update triggered. Refetching configs for tab: $tabViewId")
            fetchSensorFieldConfigs(tabViewId)
        }.flowOn(Dispatchers.IO)
    }

    private fun fetchSensorFieldConfigs(tabViewId: Long): List<SensorFieldConfig> {
        Log.i("TrackingRepository", "getSensorFieldConfigsForView called for tabViewId: $tabViewId")

        val dbManager = TrackingViewsDatabaseManager.getInstance(application)
        val devicesDbManager = DevicesDatabaseManager.getInstance(application)

        val fieldList = mutableListOf<SensorFieldConfig>()


        // Query the ROWS_TABLE for all rows belonging to the given viewId
        val cursor = dbManager.database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
            null, // Get all columns
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
            arrayOf(tabViewId.toString()),
            null,
            null,
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR} ASC, ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR} ASC"
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                // Pre-fetch column indices for efficiency inside the loop
                val sensorFieldIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID)
                val rowNrIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR)
                val colNrIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR)
                val sensorTypeIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE)
                val filterTypeIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE)
                val filterConstantIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT)
                val viewSizeIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE)
                val deviceIdIndex = c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID)

                do {
                    val sizeString = c.getString(viewSizeIndex)
                    val viewSize = try {
                        ViewSize.valueOf(sizeString)
                    } catch (e: IllegalArgumentException) {
                        ViewSize.NORMAL // Fallback for invalid or null data
                    }

                    val sourceDeviceId = c.getLong(deviceIdIndex)
                    val deviceName = if (sourceDeviceId > 0) {
                        devicesDbManager.getDeviceName(sourceDeviceId)
                    }
                    else {
                        null
                    }
                    Log.i("TrackingRepository", "Source device ID: $sourceDeviceId, Name: $deviceName")

                    fieldList.add(
                        SensorFieldConfig(
                            sensorFieldId = c.getLong(sensorFieldIndex),
                            rowNr = c.getInt(rowNrIndex),
                            colNr = c.getInt(colNrIndex),
                            viewSize = viewSize, // Use the directly parsed enum value
                            sensorType = SensorType.valueOf(c.getString(sensorTypeIndex)),
                            filterType = FilterType.valueOf(c.getString(filterTypeIndex)),
                            filterConstant = c.getDouble(filterConstantIndex),
                            sourceDeviceId = sourceDeviceId,
                            sourceDeviceName = deviceName
                        )
                    )
                } while (c.moveToNext())
            }
        }
        return fieldList
    }

    fun getSensorFieldConfig(sensorFieldId: Long): Flow<SensorFieldConfig?> {
        // This flow is now driven by the same trigger as the list-based flow.
        return configUpdateTrigger.map {
            // When the trigger changes, this block re-executes.
            fetchSingleSensorFieldConfig(sensorFieldId)
        }.flowOn(Dispatchers.IO)
    }

    // --- Renamed the original suspend function to be a private helper ---
    private suspend fun fetchSingleSensorFieldConfig(sensorFieldId: Long): SensorFieldConfig? {
        return withContext(Dispatchers.IO) {
            val cursor = viewsDbManager.database.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf(sensorFieldId.toString()), null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val sizeString = c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE))
                    val viewSize = try { ViewSize.valueOf(sizeString) } catch (e: IllegalArgumentException) { ViewSize.NORMAL }
                    val sourceDeviceId = c.getLong(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID))
                    val deviceName = if (sourceDeviceId > 0) devicesDbManager.getDeviceName(sourceDeviceId) else null
                    return@withContext SensorFieldConfig(
                        sensorFieldId = sensorFieldId,
                        rowNr = c.getInt(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR)),
                        colNr = c.getInt(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR)),
                        viewSize = viewSize,
                        sensorType = SensorType.valueOf(c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE))),
                        filterType = FilterType.valueOf(c.getString(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE))),
                        filterConstant = c.getDouble(c.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT)),
                        sourceDeviceId = sourceDeviceId,
                        sourceDeviceName = deviceName
                    )
                } else {
                    return@withContext null // Return null if not found
                }
            }
        }
    }

    suspend fun getDeviceLists(sensorType: SensorType): DevicesDatabaseManager.DeviceIdAndNameLists? {
        return withContext(Dispatchers.IO) {
            devicesDbManager.getDeviceIdAndNameLists(sensorType)
        }
    }

    fun requestNewLap() {
        application.sendBroadcast(
            Intent(TrainingApplication.REQUEST_NEW_LAP)
                .setPackage(application.packageName)
        )
    }


    /**
     * Updates the configuration of a specific sensor field in the database.
     */
    suspend fun updateSensorFieldConfig(
        sensorFieldId: Long,
        newSensorType: SensorType,
        newViewSize: ViewSize,
        newSourceDeviceId: Long?,
        newSourceDeviceName: String?,
        newFilterType: FilterType,
        newFilterConstant: Double
    ) {
        withContext(Dispatchers.IO) {

            // write the new config to the database
            viewsDbManager.updateSensorView(
                sensorFieldId,
                newSensorType,
                newViewSize,
                newSourceDeviceId,
                newFilterType,
                newFilterConstant
            )
        }

        // request the BANALService to create this new filter
        val filterData = FilterData(newSourceDeviceName, newSensorType, newFilterType, newFilterConstant)
        if (banalServiceComm != null) banalServiceComm?.createFilter(filterData)

        // notify collectors that the data has changed by incrementing the value
        withContext(Dispatchers.Main) {
            configUpdateTrigger.value++
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TrackingRepository? = null

        fun getInstance(application: Application): TrackingRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TrackingRepository(application)
                INSTANCE = instance
                instance
            }
        }
    }
}