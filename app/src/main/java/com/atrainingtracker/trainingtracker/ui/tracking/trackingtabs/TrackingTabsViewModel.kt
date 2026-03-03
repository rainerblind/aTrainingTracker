package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs


import android.app.Application
import androidx.activity.result.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData

import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.tracking.LapEvent
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

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

    // 1. Combine ActivityType and Trigger into a single reactive LiveData
    // This ensures that tabs refresh whenever:
    // a) The user changes the activity type (Running -> Cycling)
    // b) The user renames/adds/deletes a tab (configUpdateTrigger increments)
    @OptIn(ExperimentalCoroutinesApi::class)
    val trackingViews: LiveData<List<TrackingViewInfo>> = activityType.asFlow()
        .flatMapLatest { currentActivityType ->
            // Use the flow from the repository which already listens to configUpdateTrigger
            trackingRepository.getTrackingViewsFlow(currentActivityType)
        }
        .asLiveData(viewModelScope.coroutineContext + Dispatchers.Default)

    fun toggleScreenMode() {
        trackingRepository.toggleScreenMode()
    }

    fun onUpdateTabName(tabViewId: Long, newName: String) {
        // Only update if the name actually changed to prevent loop cycles
        viewModelScope.launch {
            val currentViews = trackingViews.value
            val currentName = currentViews?.find { it.tabViewId == tabViewId }?.name

            if (newName != currentName) {
                trackingRepository.updateTabName(tabViewId, newName)
            }
        }
    }

    fun onUpdateShowLapButton(tabViewId: Long, showLapButton: Boolean) {
        viewModelScope.launch {
            trackingRepository.updateShowLapButton(tabViewId,showLapButton)
        }
    }

    fun onUpdateShowMap(tabViewId: Long, showMap: Boolean) {
        viewModelScope.launch {
            trackingRepository.updateShowMap(tabViewId,showMap)
        }
    }

    fun onAddTabRelative(tabViewId: Long, after: Boolean) {
        viewModelScope.launch {
            trackingRepository.addEmptyTabView(tabViewId, after)
        }
    }

    fun onDeleteTab(tabViewId: Long) {
        viewModelScope.launch {
            trackingRepository.deleteTab(tabViewId)
        }
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
