package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs


import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

// helper class to navigate the fragment container after adding or deletion of a tab
sealed class TabNavigationEvent {
    data class NavigateTo(val index: Int) : TabNavigationEvent()
}

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

    private val _navigationEvent = MutableSharedFlow<TabNavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

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
            // 1. Get current index to calculate new index
            val currentIndex = trackingViews.value?.indexOfFirst { it.tabViewId == tabViewId } ?: 0

            // 2. Perform the database update
            trackingRepository.addEmptyTabView(tabViewId, after)

            // 3. Calculate target: if 'after', target is current + 1. If 'before', target is current.
            val targetIndex = if (after) currentIndex + 1 else currentIndex

            Log.i("TrackingTabsViewModel", "Adding after=$after $currentIndex -> $targetIndex")

            _navigationEvent.emit(TabNavigationEvent.NavigateTo(targetIndex))
        }
    }

    fun onDeleteTab(tabViewId: Long) {
        viewModelScope.launch {
            // 1. Get current index and total count before deletion
            val currentViews = trackingViews.value ?: emptyList()
            val currentIndex = currentViews.indexOfFirst { it.tabViewId == tabViewId }

            // 2. Perform the database update
            trackingRepository.deleteTab(tabViewId)

            // 3. Calculate the target index for the Fragment
            // If the user deletes the last tab (currentIndex == currentViews.size - 1),
            // we move to the new last tab (newCount).
            // Otherwise, we stay at the same index (which is now the next tab).
            val newCount = currentViews.size - 1
            val targetIndex = if (currentIndex >= newCount && newCount > 0) {
                newCount
            } else {
                currentIndex
            }

            Log.i("TrackingTabsViewModel", "Deleting $currentIndex; newCount=$newCount.  -> Navigate to $targetIndex")

            // 4. Emit the event with the safe, pre-calculated index
            _navigationEvent.emit(TabNavigationEvent.NavigateTo(targetIndex))
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
