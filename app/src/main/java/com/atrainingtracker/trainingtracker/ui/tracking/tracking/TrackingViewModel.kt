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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.google.android.gms.maps.model.LatLng
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Objects

/**
 * The state for the entire tracking screen, containing a list of all sensor fields.
 */
data class TrackingScreenState(
    val showMap: Boolean = false,
    val showLiveSegments: Boolean = false,
    val showElevationProfile: Boolean = false,
    val showLiveClimbs: Boolean = true,
    val showNavigationHints: Boolean = true,
    val showLapButton: Boolean = true,
    val fields: List<SensorFieldState> = emptyList(),
    val pathPoints: List<com.atrainingtracker.trainingtracker.ui.map.PathPoint> = emptyList(),
    val mapState: TrackingMapState = TrackingMapState(showMap = showMap),
    
    // Map specific state (backward-compatible)
    val zoomFocus: MapZoomFocus = mapState.zoomFocus,
    val userBearing: Float = mapState.userBearing,
    val userSpeed: Float = mapState.userSpeed,
    val bSportType: BSportType = mapState.bSportType,
    val currentTrack: List<LatLng> = mapState.currentTrack,
    val mapTracks: List<MapTrack> = mapState.mapTracks,
    val mapSegments: List<MapSegment> = mapState.mapSegments,
    val activeLiveSegmentIds: Set<Long> = mapState.activeLiveSegmentIds,
    val mapRoutes: List<MapRoute> = mapState.mapRoutes,
    val mapMarkers: List<LocationMarker> = mapState.mapMarkers
)

/**
 * ViewModel for a single tracking tab (a TrackingFragment instance).
 * It is responsible for fetching the configuration for its viewId, subscribing to live sensor data,
 * and mapping that data into a UI-ready state for the composables to render.
 */
class TrackingViewModel(
    private val application: Application,
    val trackingViewsRepository: TrackingViewsRepository,
    val banalServiceRepository: BANALServiceRepository,
    val liveSegmentsRepository: LiveSegmentsRepository,
    val routesRepository: RoutesRepository,
    private val viewId: Long
) : ViewModel() {

    // --- The StateFlow to hold and expose the UI state ---
    private val _uiState = MutableStateFlow(TrackingScreenState())
    val uiState: StateFlow<TrackingScreenState> = _uiState.asStateFlow()

    // Holds the Map-specific state, decoupled from high-frequency sensor telemetry (REQ-UI-326 / ATT-2944)
    private val _mapState = MutableStateFlow(TrackingMapState())
    val mapState: StateFlow<TrackingMapState> = _mapState.asStateFlow()

    private val _activityType = MutableStateFlow<ActivityType>(ActivityType.getDefaultActivityType())
    val activityType: StateFlow<ActivityType> = _activityType.asStateFlow()

    // Holds the ID of the field being edited. Null means no dialog is shown.
    private val _editingFieldId = MutableStateFlow<Long?>(null)
    val editingFieldId: StateFlow<Long?> = _editingFieldId.asStateFlow()

    // Holds the field currently selected for Pick & Place move/swap (REQ-UI-200)
    private val _selectedFieldForMove = MutableStateFlow<SensorFieldState?>(null)
    val selectedFieldForMove: StateFlow<SensorFieldState?> = _selectedFieldForMove.asStateFlow()

    data class AdditionParams(val row: Int, val col: Int)
    private val _pendingAddition = MutableStateFlow<AdditionParams?>(null)
    val pendingAddition = _pendingAddition.asStateFlow()

    // Screen mode is now local and driven by the parent (ATT-245)
    private val _screenMode = MutableStateFlow(ScreenMode.TRACKING)
    val screenMode: StateFlow<ScreenMode> = _screenMode.asStateFlow()

    fun updateScreenMode(mode: ScreenMode) {
        if (mode != ScreenMode.CONFIGURATION) {
            _selectedFieldForMove.value = null
        }
        _screenMode.value = mode
    }

    // Filter and sort the segments from the repository:
    // TODO: also filter for activity type.
    val activeLiveSegments: StateFlow<List<LiveSegment>> = combine(
        liveSegmentsRepository.liveSegments,
        banalServiceRepository.bSportType
    ) { allLiveSegments, currentBSportType ->
        allLiveSegments.filter { segment ->
            ( segment.staticData.summary.bSportType == currentBSportType
                    || currentBSportType == BSportType.UNKNOWN)
                    && segment.liveData.segmentStatus != LiveSegmentStatus.FAR_FAR_AWAY
        }
            .sortedWith(
                compareByDescending<LiveSegment> {
                    // Priority 1: Status
                    when(it.liveData.segmentStatus) {
                        LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> 4
                        LiveSegmentStatus.ON_SEGMENT -> 3
                        LiveSegmentStatus.APPROACHING -> 2
                        LiveSegmentStatus.FINISHED -> 1
                        else -> 0
                    }
                }.thenBy {
                    // Priority 2: Conditional tie-breaker
                    when(it.liveData.segmentStatus) {
                        // If we are ON the segment, prioritize by remaining distance (finish line)
                        LiveSegmentStatus.ON_SEGMENT,
                        LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> it.liveData.remainingDistance

                        // If we are APPROACHING, prioritize by distance to the start line
                        LiveSegmentStatus.APPROACHING -> it.liveData.distanceToStart

                        // For the other cases, it is not clear what to do.  Thus, we try the segment offset. I.e., the closest segment (although this might jump).
                        else -> it.liveData.segmentOffset
                    }
                }
            )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val settingsDataStore = SettingsDataStore(application)
    private val zoneDisplayOptionsMap = mutableMapOf<SettingsDataStore.ZoneType, ZoneDisplayOptions>()
    private val defaultZoneColor = Color.Transparent

    // Pre-load the zone colors into a list for efficient access. The order is important.
    private val zoneColors: List<Color> = listOf(
        Color(ContextCompat.getColor(application, R.color.zone_1)),
        Color(ContextCompat.getColor(application, R.color.zone_2)),
        Color(ContextCompat.getColor(application, R.color.zone_3)),
        Color(ContextCompat.getColor(application, R.color.zone_4)),
        Color(ContextCompat.getColor(application, R.color.zone_5))
    )

    init {
        observeZoneDisplayOptions()
        observeMapState()
        // Load both the main UI state and the activity type
        loadSensorFieldStates()
        loadActivityType()
    }

    private fun observeZoneDisplayOptions() {
        viewModelScope.launch {
            combine(
                settingsDataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_RUN),
                settingsDataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.HR_BIKE),
                settingsDataStore.getZoneDisplayOptionsFlow(SettingsDataStore.ZoneType.PWR_BIKE)
            ) { hrRun, hrBike, pwrBike ->
                zoneDisplayOptionsMap[SettingsDataStore.ZoneType.HR_RUN] = hrRun
                zoneDisplayOptionsMap[SettingsDataStore.ZoneType.HR_BIKE] = hrBike
                zoneDisplayOptionsMap[SettingsDataStore.ZoneType.PWR_BIKE] = pwrBike
            }.collect()
        }
    }

    private fun loadActivityType() {
        viewModelScope.launch {
            // Use the new repository function to get the activity type
            _activityType.value = trackingViewsRepository.getActivityTypeForView(viewId)
        }
    }

    private data class MapData<T1, T2, T3, T4>(
        val allLiveSegments: T1,
        val activeLiveSegments: T2,
        val allRoutes: T3,
        val activeNavigatedRouteId: T4
    )

    private fun observeMapState() {
        viewModelScope.launch {
            val mapDataFlow = combine(
                liveSegmentsRepository.liveSegments,
                activeLiveSegments,
                routesRepository.allRoutes,
                routesRepository.activeNavigatedRouteId
            ) { allSegments, activeSegments, allRoutes, activeNavigatedRouteId ->
                MapData(allSegments, activeSegments, allRoutes, activeNavigatedRouteId)
            }

            combine(
                trackingViewsRepository.getTrackingViewInfoFlow(viewId),
                mapDataFlow,
                banalServiceRepository.currentTrack,
                banalServiceRepository.bSportType
            ) { viewInfo, mapData, currentTrack, bSportType ->
                val (allLiveSegments, activeLiveSegments, allRoutes, activeNavigatedRouteId) = mapData
                val markerList = mutableListOf<LocationMarker>()
                if (currentTrack.isNotEmpty()) {
                    markerList.add(
                        LocationMarker(
                            position = currentTrack.first(),
                            iconResId = R.drawable.start_logo_map,
                            title = application.getString(R.string.Start)
                        )
                    )
                }

                // Convert LiveSegments into MapSegments
                val mapSegments = allLiveSegments.map { live ->
                    MapSegment(
                        stravaId = live.staticData.summary.stravaId,
                        name = live.staticData.summary.name,
                        bSportType = live.staticData.summary.bSportType,
                        path = live.staticData.path
                    )
                }

                val activeIds = activeLiveSegments.map { it.staticData.summary.stravaId }.toSet()

                TrackingMapState(
                    showMap = viewInfo?.showMap ?: false,
                    zoomFocus = MapZoomFocus.FOLLOW_ME,
                    userSpeed = banalServiceRepository.currentSpeed.value?.toFloat() ?: 0f,
                    userBearing = banalServiceRepository.currentBearing.value?.toFloat() ?: 0f,
                    bSportType = bSportType,
                    currentTrack = currentTrack,
                    mapSegments = mapSegments,
                    mapRoutes = allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeNavigatedRouteId)) },
                    activeLiveSegmentIds = activeIds,
                    mapMarkers = markerList
                )
            }.collect { newMapState ->
                _mapState.value = newMapState
                _uiState.update { current ->
                    current.copy(
                        showMap = newMapState.showMap,
                        mapState = newMapState,
                        zoomFocus = newMapState.zoomFocus,
                        userBearing = newMapState.userBearing,
                        userSpeed = newMapState.userSpeed,
                        bSportType = newMapState.bSportType,
                        currentTrack = newMapState.currentTrack,
                        mapTracks = newMapState.mapTracks,
                        mapSegments = newMapState.mapSegments,
                        activeLiveSegmentIds = newMapState.activeLiveSegmentIds,
                        mapRoutes = newMapState.mapRoutes,
                        mapMarkers = newMapState.mapMarkers
                    )
                }
            }
        }
    }

    private fun loadSensorFieldStates() {
        viewModelScope.launch {
            combine(
                trackingViewsRepository.getSensorFieldConfigsForView(viewId),
                banalServiceRepository.allFilteredSensorData,
                trackingViewsRepository.getTrackingViewInfoFlow(viewId),
                banalServiceRepository.currentPathPoints
            ) { configs, allSensorData, viewInfo, livePathPoints ->
                // --- Step 1: Create the base state from the latest configurations ---
                val currentActivity = banalServiceRepository.activityType.value
                val baseFields = configs.map { config ->
                    val uniqueHash = Objects.hash(config.sensorType, config.filterType, config.filterConstant, config.sourceDeviceName)
                    var filterDescription = config.filterType.getShortSummary(application, config.filterConstant)
                    if (config.sourceDeviceName != null) {
                        filterDescription = if (filterDescription.isNotEmpty()) {
                            "${config.sourceDeviceName}: $filterDescription"
                        } else {
                            config.sourceDeviceName
                        }
                    }

                    val zoneType = getZoneType(config.sensorType, currentActivity.sportType)
                    val displayOptions = zoneType?.let { zoneDisplayOptionsMap[it] } ?: ZoneDisplayOptions()

                    SensorFieldState(
                        configHash = uniqueHash,
                        sensorFieldId = config.sensorFieldId,
                        rowNr = config.rowNr,
                        colNr = config.colNr,
                        viewSize = config.viewSize,
                        label = application.getString(config.sensorType.shortNameId),
                        filterDescription = filterDescription,
                        value = "--",
                        units = application.getString(MyHelper.getShortUnitsId(config.sensorType)),
                        zoneColor = defaultZoneColor,
                        zoneDisplayOptions = displayOptions
                    )
                }

                // --- Step 2: Apply live sensor data to the base state ---
                val finalFields = applySensorData(baseFields, allSensorData, currentActivity)

                // --- Step 3: Package into TrackingScreenState reusing the decoupled mapState ---
                val currentMap = _mapState.value
                TrackingScreenState(
                    fields = finalFields,
                    showMap = viewInfo?.showMap ?: false,
                    showLiveSegments = viewInfo?.showLiveSegments ?: false,
                    showElevationProfile = viewInfo?.showElevationProfile ?: false,
                    showLiveClimbs = viewInfo?.showLiveClimbs ?: true,
                    showNavigationHints = viewInfo?.showNavigationHints ?: true,
                    showLapButton = viewInfo?.showLapButton ?: true,
                    pathPoints = livePathPoints,
                    mapState = currentMap,
                    zoomFocus = currentMap.zoomFocus,
                    userSpeed = currentMap.userSpeed,
                    userBearing = currentMap.userBearing,
                    bSportType = currentMap.bSportType,
                    currentTrack = currentMap.currentTrack,
                    mapTracks = currentMap.mapTracks,
                    mapSegments = currentMap.mapSegments,
                    mapRoutes = currentMap.mapRoutes,
                    activeLiveSegmentIds = currentMap.activeLiveSegmentIds,
                    mapMarkers = currentMap.mapMarkers
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // Helper function to keep the logic clean
    private fun applySensorData(
        currentFields: List<SensorFieldState>,
        allSensorData: List<FilteredSensorData<*>>,
        activityType: ActivityType
    ): List<SensorFieldState> {
        // 1. Group fields by their hash. Now we have a map of Hash -> List<SensorFieldState>.
        val fieldsByHash = currentFields.groupBy { it.configHash }
        val updatedFields = currentFields.toMutableList() // Start with a mutable copy of the original list
        var hasChanged = false

        // Iterate through all the live data coming from the service
        for (sensorData in allSensorData) {
            val uniqueHash = Objects.hash(sensorData.sensorType, sensorData.filterType, sensorData.filterConstant, sensorData.deviceName)

            // 2. Find all fields that match this hash.
            val fieldsToUpdate = fieldsByHash[uniqueHash]

            if (fieldsToUpdate != null) {
                val newFormattedValue = sensorData.stringValue
                val newZoneColor = calculateZoneColor(sensorData, activityType)
                val zoneType = getZoneType(sensorData.sensorType, activityType.sportType)
                val displayOptions = zoneType?.let { zoneDisplayOptionsMap[it] } ?: ZoneDisplayOptions()

                // 3. Iterate through every field that needs this update.
                for (fieldToUpdate in fieldsToUpdate) {
                    // Check if this specific instance needs an update to avoid unnecessary changes.
                    if (fieldToUpdate.value != newFormattedValue ||
                        fieldToUpdate.zoneColor != newZoneColor ||
                        fieldToUpdate.zoneDisplayOptions != displayOptions) {
                        val index = updatedFields.indexOf(fieldToUpdate)
                        if (index != -1) {
                            updatedFields[index] = fieldToUpdate.copy(
                                value = newFormattedValue,
                                zoneColor = newZoneColor,
                                zoneDisplayOptions = displayOptions
                            )
                            hasChanged = true
                        }
                    }
                }
            }
        }

        // Only return a new list if something actually changed to avoid unnecessary recompositions
        return if (hasChanged) updatedFields else currentFields
    }

    // Helper function for zone color calculation
    private fun calculateZoneColor(
        sensorData: FilteredSensorData<*>,
        activityType: ActivityType
    ): Color {
        val currentValue = sensorData.value
        val zoneType = getZoneType(sensorData.sensorType, activityType.sportType)

        if (zoneType != null && currentValue is Number) {
            val z1Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1)
            val z2Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2)
            val z3Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3)
            val z4Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4)
            val numericValue = currentValue.toDouble()

            return when {
                numericValue <= z1Max -> zoneColors[0]
                numericValue <= z2Max -> zoneColors[1]
                numericValue <= z3Max -> zoneColors[2]
                numericValue <= z4Max -> zoneColors[3]
                else -> zoneColors[4]
            }
        }
        return defaultZoneColor
    }

    /**
     * Simple helper function to determines which zone configuration to use based on the sensor type and the current sport.
     */
    private fun getZoneType(sensorType: SensorType, sportType: BSportType): SettingsDataStore.ZoneType? {
        return when (sensorType) {
            SensorType.HR -> when (sportType) {
                BSportType.RUN -> SettingsDataStore.ZoneType.HR_RUN
                BSportType.BIKE -> SettingsDataStore.ZoneType.HR_BIKE
                else -> null
            }
            SensorType.POWER -> when (sportType) {
                BSportType.BIKE -> SettingsDataStore.ZoneType.PWR_BIKE
                else -> null
            }
            else -> null
        }
    }

    /***********************************************************************************************
     * Functions for configuration
     **********************************************************************************************/
    fun onEditField(fieldState: SensorFieldState) {
        _editingFieldId.value = fieldState.sensorFieldId
    }

    fun onDismissEditDialog() {
        _editingFieldId.value = null
    }

    fun onAddRow(atRow: Int) {
        _pendingAddition.value = AdditionParams(row = atRow, col = -1)
    }

    fun onAddCol(atRow: Int, atCol: Int) {
        _pendingAddition.value = AdditionParams(row = atRow, col = atCol)
    }

    fun onDismissAddition() {
        _pendingAddition.value = null
    }

    fun onSelectFieldForMove(fieldState: SensorFieldState) {
        _selectedFieldForMove.value = fieldState
    }

    fun onCancelMove() {
        _selectedFieldForMove.value = null
    }

    fun onSwapFields(sourceFieldId: Long, targetFieldId: Long) {
        _selectedFieldForMove.value = null
        viewModelScope.launch {
            trackingViewsRepository.swapSensorFields(sourceFieldId, targetFieldId)
        }
    }

    fun onMoveField(sourceFieldId: Long, targetRow: Int, targetCol: Int) {
        _selectedFieldForMove.value = null
        viewModelScope.launch {
            trackingViewsRepository.moveSensorField(sourceFieldId, targetRow, targetCol)
        }
    }

    fun onDeleteSensorField(sensorFieldId: Long) {
        if (_selectedFieldForMove.value?.sensorFieldId == sensorFieldId) {
            _selectedFieldForMove.value = null
        }
        viewModelScope.launch {
            trackingViewsRepository.deleteSensorField(sensorFieldId)
        }
    }

    fun onUpdateShowMap(show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowMap(viewId, show)
        }
    }

    fun onUpdateShowElevationProfile(show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowElevationProfile(viewId, show)
        }
    }

    fun onUpdateShowLiveSegments(show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowLiveSegments(viewId, show)
        }
    }

    fun onUpdateShowLiveClimbs(show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowLiveClimbs(viewId, show)
        }
    }

    fun onUpdateShowNavigationHints(show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowNavigationHints(viewId, show)
        }
    }

    fun onUpdateShowLapButton(show: Boolean) {
        viewModelScope.launch {
            trackingViewsRepository.updateShowLapButton(viewId, show)
        }
    }

}

/**
 * Factory for creating a TrackingViewModel with a constructor that takes a repository and a viewId.
 */
class TrackingViewModelFactory(
    private val application: Application,
    private val viewId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
            val trackingViewsRepo = TrackingViewsRepository.getInstance(application)
            val banalServiceRepo = BANALServiceRepository.getInstance(application)
            val liveSegmentsRepository = LiveSegmentsRepository.getInstance(application)
            val routesRepository = RoutesRepository.getInstance(application)
            return TrackingViewModel(application, trackingViewsRepo, banalServiceRepo, liveSegmentsRepository, routesRepository, viewId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}