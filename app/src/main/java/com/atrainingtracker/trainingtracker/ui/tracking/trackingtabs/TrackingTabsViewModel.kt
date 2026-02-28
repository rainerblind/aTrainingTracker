package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData

import androidx.lifecycle.switchMap
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.tracking.LapEvent
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import kotlinx.coroutines.Dispatchers

/**
 * ViewModel for the tabbed container of tracking views.
  */
class TrackingTabsViewModel(
    application: Application,
    private val trackingRepository: TrackingRepository
) : AndroidViewModel(application) {

    // Simply expose the ActivityType and TrackingMode from the repository
    val activityType: LiveData<ActivityType> = trackingRepository.activityType
    val trackingMode: LiveData<TrackingMode> = trackingRepository.trackingMode
    val lapEvent: LiveData<LapEvent> = trackingRepository.lapEvent

    // When the activityType from the repository changes, this switchMap will automatically
    // re-fetch the list of tracking views from the repository.
    val trackingViews: LiveData<List<TrackingViewInfo>> = activityType.switchMap { currentActivityType ->
        liveData(Dispatchers.IO) {
            val views = trackingRepository.getTrackingViews(currentActivityType)
            // Once the data is fetched, emit() posts the value to the LiveData on the main thread
            emit(views)
        }
    }

    fun onLapButtonClick() {
        // -> request a new lap.
        trackingRepository.requestNewLap()
    }
}
