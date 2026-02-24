package com.atrainingtracker.trainingtracker.ui.tracking

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager

// Data class for holding tab info, can be moved to a more common location later.
data class TrackingViewInfo(val id: Int, val name: String)

/**
 * A singleton repository that acts as the single source of truth for all tracking-related data.
 * It connects to the BANALService and the local database to provide a clean data source
 * for all ViewModels.
 */
class TrackingRepository private constructor(private val application: Application) {

    // --- ActivityType Management ---
    private val _activityType = MutableLiveData<ActivityType>()
    val activityType: LiveData<ActivityType> = _activityType

    init {
        // Set a default value when the repository is created
        _activityType.postValue(ActivityType.getDefaultActivityType())
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