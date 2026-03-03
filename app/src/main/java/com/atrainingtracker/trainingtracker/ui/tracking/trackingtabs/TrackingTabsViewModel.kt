package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData

import androidx.lifecycle.switchMap
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.tracking.LapEvent
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

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

    val screenMode: StateFlow<ScreenMode> = trackingRepository.screenMode

    // When the activityType from the repository changes, this switchMap will automatically
    // re-fetch the list of tracking views from the repository.
    val trackingViews: LiveData<List<TrackingViewInfo>> = activityType.switchMap { currentActivityType ->
        liveData(Dispatchers.IO) {
            val views = trackingRepository.getTrackingViews(currentActivityType)
            // Once the data is fetched, emit() posts the value to the LiveData on the main thread
            emit(views)
        }
    }

    fun toggleScreenMode() {
        trackingRepository.toggleScreenMode()
    }


    fun onLapButtonClick() {
        // -> request a new lap.
        trackingRepository.requestNewLap()
    }
}

class TrackingTabsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackingTabsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrackingTabsViewModel(application, TrackingRepository.getInstance(application)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
