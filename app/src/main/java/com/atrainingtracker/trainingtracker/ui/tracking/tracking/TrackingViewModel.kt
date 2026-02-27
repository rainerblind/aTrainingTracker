package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Objects

/**
 * The state for the entire tracking screen, containing a list of all sensor fields.
 */
data class TrackingScreenState(
    val fields: List<SensorFieldState> = emptyList()
)

/**
 * ViewModel for a single tracking tab (a TrackingFragment instance).
 * It is responsible for fetching the configuration for its viewId, subscribing to live sensor data,
 * and mapping that data into a UI-ready state for the composables to render.
 */
class TrackingViewModel(
    private val application: Application,
    val trackingRepository: TrackingRepository,
    private val viewId: Long
) : ViewModel() {

    // --- The StateFlow to hold and expose the UI state ---
    private val _uiState = MutableStateFlow(TrackingScreenState())
    val uiState: StateFlow<TrackingScreenState> = _uiState.asStateFlow()

    private val _activityType = MutableStateFlow<ActivityType?>(null)
    val activityType: StateFlow<ActivityType?> = _activityType.asStateFlow()

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val defaultZoneColor = Color(ContextCompat.getColor(application, R.color.color_background))

    // Pre-load the zone colors into a list for efficient access. The order is important.
    private val zoneColors: List<Color> = listOf(
        Color(ContextCompat.getColor(application, R.color.zone_1)),
        Color(ContextCompat.getColor(application, R.color.zone_2)),
        Color(ContextCompat.getColor(application, R.color.zone_3)),
        Color(ContextCompat.getColor(application, R.color.zone_4)),
        Color(ContextCompat.getColor(application, R.color.zone_5))
    )

    init {
        // Load both the main UI state and the activity type
        loadSensorFieldStates()
        loadActivityType()
    }

    private fun loadActivityType() {
        viewModelScope.launch {
            // Use the new repository function to get the activity type
            _activityType.value = trackingRepository.getActivityTypeForView(viewId)
        }
    }

    private fun loadSensorFieldStates() {
        viewModelScope.launch {
            // 1. COLLECT THE FLOW for configuration changes
            trackingRepository.getSensorFieldConfigsForView(viewId)
                .combine(trackingRepository.allFilteredSensorData) { configs, allSensorData ->
                    // This whole block will re-execute whenever configs OR sensor data change

                    // --- Step 1: Create the base state from the latest configurations ---
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
                            zoneColor = defaultZoneColor
                        )
                    }

                    // --- Step 2: Apply live sensor data to the base state ---
                    val activity = trackingRepository.activityType.value ?: return@combine baseFields // Use the LiveData value
                    val fieldsWithLiveData = applySensorData(baseFields, allSensorData, activity)

                    // Return the fully updated list
                    fieldsWithLiveData
                }
                .collect { updatedFields ->
                    // 2. EMIT the new state to the UI
                    _uiState.value = TrackingScreenState(fields = updatedFields)
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
            Log.i("TrackingViewModel", "Processing sensor data: ${sensorData.sensorType}, ${sensorData.filterConstant}, ${sensorData.deviceName}: ${sensorData.stringValue}")

            // 2. Find all fields that match this hash.
            val fieldsToUpdate = fieldsByHash[uniqueHash]

            if (fieldsToUpdate != null) {
                val newFormattedValue = sensorData.stringValue
                val newZoneColor = calculateZoneColor(sensorData, activityType)

                // 3. Iterate through every field that needs this update.
                for (fieldToUpdate in fieldsToUpdate) {
                    // Check if this specific instance needs an update to avoid unnecessary changes.
                    if (fieldToUpdate.value != newFormattedValue || fieldToUpdate.zoneColor != newZoneColor) {
                        val index = updatedFields.indexOf(fieldToUpdate)
                        if (index != -1) {
                            updatedFields[index] = fieldToUpdate.copy(value = newFormattedValue, zoneColor = newZoneColor)
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
     * Translates the getZoneType logic from TrackingFragmentClassic into a helper function.
     * Determines which zone configuration to use based on the sensor and the current sport.
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
            val repo = TrackingRepository.getInstance(application)
            return TrackingViewModel(application, repo, viewId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}