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

// Data class for holding tab info, can be moved to a more common location later.
data class TrackingViewInfo(val id: Int, val name: String)

/**
 * A singleton repository that acts as the single source of truth for all tracking-related data.
 * It connects to the BANALService and the local database to provide a clean data source
 * for all ViewModels.
 */
class TrackingRepository private constructor(private val application: Application) {

    private val _activityType = MutableLiveData<ActivityType>()
    val activityType: LiveData<ActivityType> = _activityType

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