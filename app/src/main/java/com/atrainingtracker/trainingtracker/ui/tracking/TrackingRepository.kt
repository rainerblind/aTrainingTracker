package com.atrainingtracker.trainingtracker.ui.tracking

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent

// Data class for holding tab info, can be moved to a more common location later.
data class TrackingViewInfo(val id: Int, val name: String)


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
 * A singleton repository that acts as the single source of truth for all tracking-related data.
 * It connects to the BANALService and the local database to provide a clean data source
 * for all ViewModels.
 */
class TrackingRepository private constructor(private val application: Application) {

    private val _activityType = MutableLiveData<ActivityType>()
    val activityType: LiveData<ActivityType> = _activityType

    // -- Tracking mode
    private val _trackingMode = MutableLiveData<TrackingMode>()
    val trackingMode: LiveData<TrackingMode> = _trackingMode

    // The receiver now lives in the repository
    private val trackingStateReceiver = object : BroadcastReceiver() {
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

    // --- NEW: Receiver for Lap Summary ---
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
            trackingStateReceiver,
            IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED // Specify that it only receives broadcasts from this app
        )

        application.registerReceiver(
            lapSummaryReceiver,
            IntentFilter(BANALService.LAP_SUMMARY),
            Context.RECEIVER_NOT_EXPORTED)
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
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME
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
                    val id = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID))
                    val name = it.getString(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME))
                    viewList.add(TrackingViewInfo(id, name))
                } while (it.moveToNext())
            }
        }
        return viewList
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