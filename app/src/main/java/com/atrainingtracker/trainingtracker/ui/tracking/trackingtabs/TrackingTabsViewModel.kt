/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs


import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.LapEvent
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// helper class to navigate the fragment container after adding or deletion of a tab
sealed class TabNavigationEvent {
    data class NavigateTo(val index: Int) : TabNavigationEvent()
    data class EditDevice(val deviceId: Long) : TabNavigationEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingTabsViewModel(
    application: Application,
    private val trackingViewsRepository: TrackingViewsRepository,
    private val banalServiceRepository: BANALServiceRepository,
    private val devicesRepository: DeviceDataRepository
) : AndroidViewModel(application) {

    // State to hold the explicitly selected ActivityType
    private val _explicitActivityType = MutableStateFlow<ActivityType?>(null)

    // activityType prefers the explicit type over the repository's live type.
    val activityType: StateFlow<ActivityType> = _explicitActivityType
        .flatMapLatest { explicit ->
            if (explicit != null) {
                // If the user selected a type (e.g. in Config Activity), stay on it.
                kotlinx.coroutines.flow.flowOf(explicit)
            } else {
                // Otherwise, follow the live sensor service (Classic Tracking mode)
                banalServiceRepository.activityType
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ActivityType.getDefaultActivityType()
        )

    val trackingMode: LiveData<TrackingMode> = banalServiceRepository.trackingMode
    val activeSensors = banalServiceRepository.activeSensors
    val sensorSourceMapping = banalServiceRepository.sensorSourceDeviceIds
    val allTelemetry = banalServiceRepository.allActiveDevicesTelemetry
    val allDevices = devicesRepository.allDevices

    val lapEvent: LiveData<LapEvent?> = banalServiceRepository.lapEvent
    fun clearLapEvent() = banalServiceRepository.clearLapEvent()

    // Screen mode is now local to the ViewModel to prevent background state leakage (ATT-245)
    private val _screenMode = MutableStateFlow(ScreenMode.TRACKING)
    val screenMode: StateFlow<ScreenMode> = _screenMode.asStateFlow()

    fun onResume() {
        viewModelScope.launch {
            devicesRepository.loadAllDevices()
        }
    }

    fun onEditDevice(deviceId: Long) {
        viewModelScope.launch {
            _navigationEvent.emit(TabNavigationEvent.EditDevice(deviceId))
        }
    }

    private val _navigationEvent = MutableSharedFlow<TabNavigationEvent>()
    val navigationEvent: SharedFlow<TabNavigationEvent> = _navigationEvent.asSharedFlow()

    val trackingViews: StateFlow<List<TrackingViewInfo>> = combine(
        activityType,
        _screenMode // Re-trigger when screen mode changes if needed
    ) { type, _ ->
        trackingViewsRepository.getTrackingViewsFlow(type)
    }.flatMapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val navigateToTrackingTab = SingleLiveEvent<Unit>()

    init {
        // ensure the repository is bound to the BANALService
        banalServiceRepository.bindToBANALService()

        // Observe the tracking mode from the repository
        viewModelScope.launch {
            banalServiceRepository.trackingMode.asFlow().collect { mode ->
                if (mode == TrackingMode.TRACKING) {
                    Log.i("TrackingTabsViewModel", "Tracking started...")
                    navigateToTrackingTab.call()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // unbind from the BANALService
        banalServiceRepository.unbindFromBANALService()
    }

    // Method for the Fragment to set the explicit ActivityType
    fun setExplicitActivityType(type: ActivityType) {
        _explicitActivityType.value = type
    }

    fun setScreenMode(mode: ScreenMode) {
        _screenMode.value = mode
    }

    fun toggleScreenMode() {
        when (_screenMode.value) {
            ScreenMode.TRACKING -> _screenMode.value = ScreenMode.CONFIGURATION
            ScreenMode.CONFIGURATION -> _screenMode.value = ScreenMode.PREVIEW
            ScreenMode.PREVIEW -> _screenMode.value = ScreenMode.CONFIGURATION
        }
    }

    /**
     * Exits the configuration context entirely and returns to standard tracking mode.
     */
    fun exitConfiguration() {
        _screenMode.value = ScreenMode.TRACKING
        _explicitActivityType.value = null
    }

    /**
     * Handles back-press navigation for the tracking tab configuration flow (ATT-245).
     * Transitions from CONFIGURATION to PREVIEW. 
     * Higher-level exit (PREVIEW -> TRACKING/FINISH) is handled by the Activity.
     */
    fun handleBackPressToPreview() {
        if (_screenMode.value == ScreenMode.CONFIGURATION) {
            _screenMode.value = ScreenMode.PREVIEW
        }
    }

    fun onUpdateTabName(tabViewId: Long, newName: String) {
        // Only update if the name actually changed to prevent loop cycles
        viewModelScope.launch {
            val currentViews = trackingViews.value
            val existing = currentViews.find { it.tabViewId == tabViewId }
            if (existing != null && existing.name != newName) {
                trackingViewsRepository.updateTabName(tabViewId, newName)
            }
        }
    }

    fun onUpdateShowLapButton(tabViewId: Long, show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowLapButton(tabViewId, show)
        }
    }

    fun onUpdateShowLiveSegments(tabViewId: Long, show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowLiveSegments(tabViewId, show)
        }
    }

    fun onUpdateShowMap(tabViewId: Long, show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowMap(tabViewId, show)
        }
    }

    fun onUpdateShowElevationProfile(tabViewId: Long, show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowElevationProfile(tabViewId, show)
        }
    }

    fun onAddTabRelative(tabViewId: Long, addAfter: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.addEmptyTabView(tabViewId, addAfter)
        }
    }

    fun onDeleteTab(tabViewId: Long) {
        viewModelScope.launch {
            val currentList = trackingViews.value
            val indexToDelete = currentList.indexOfFirst { it.tabViewId == tabViewId }
            if (indexToDelete != -1) {
                trackingViewsRepository.deleteTab(tabViewId)
                // If we deleted the current tab or something after it, adjust navigation
                val newTarget = if (indexToDelete >= currentList.size - 1) {
                    (currentList.size - 2).coerceAtLeast(0)
                } else {
                    indexToDelete
                }
                _navigationEvent.emit(TabNavigationEvent.NavigateTo(newTarget))
            }
        }
    }


    fun onLapButtonClick() {
        trackingViewsRepository.requestNewLap()
    }
}

class TrackingTabsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackingTabsViewModel::class.java)) {
            val trackingViewsRepository = TrackingViewsRepository.getInstance(application)
            val banalServiceRepository = BANALServiceRepository.Companion.getInstance(application)
            val devicesRepository = DeviceDataRepository.getInstance(application)

            @Suppress("UNCHECKED_CAST")
            return TrackingTabsViewModel(
                application,
                trackingViewsRepository,
                banalServiceRepository,
                devicesRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private fun <T1, T2, R> combine(
    flow: kotlinx.coroutines.flow.Flow<T1>,
    flow2: kotlinx.coroutines.flow.Flow<T2>,
    transform: suspend (T1, T2) -> R
): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(flow, flow2, transform)
