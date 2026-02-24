package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager

// Data class to hold the necessary info for each tab/page
data class TrackingViewInfo(val id: Int, val name: String)

class TabbedContainerViewModel(application: Application) : AndroidViewModel(application) {

    // Holds the list of tracking views (e.g., "Page 1", "Page 2") loaded from the DB
    private val _trackingViews = MutableLiveData<List<TrackingViewInfo>>()
    val trackingViews: LiveData<List<TrackingViewInfo>> = _trackingViews

    // Holds the currently selected page index
    private val _selectedPage = MutableLiveData<Int>()
    val selectedPage: LiveData<Int> = _selectedPage

    /**
     * Loads the list of available tracking views for a given activity type from the database.
     */
    fun loadTrackingViews(activityType: ActivityType) {
        val context = getApplication<Application>().applicationContext
        val dbManager = TrackingViewsDatabaseManager.getInstance(context)
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
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID))
                val name = it.getString(it.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME))
                viewList.add(TrackingViewInfo(id, name))
            }
        }
        _trackingViews.postValue(viewList)
    }

    /**
     * Sets the currently selected page.
     */
    fun setSelectedPage(position: Int) {
        _selectedPage.value = position
    }
}