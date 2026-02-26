package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.text.color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Objects
import kotlin.collections.find

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
    private val trackingRepository: TrackingRepository,
    private val viewId: Int
) : ViewModel() {

    // --- The StateFlow to hold and expose the UI state ---
    private val _uiState = MutableStateFlow(TrackingScreenState())
    val uiState: StateFlow<TrackingScreenState> = _uiState.asStateFlow()

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
        viewModelScope.launch {
            // --- Step 1: Create the initial, static state for all fields ---
            val initialFieldStates = withContext(Dispatchers.IO) {
                val fieldConfigurations = trackingRepository.getSensorFieldConfigsForView(viewId)

                fieldConfigurations.map { config ->
                    val uniqueHash =
                        Objects.hash(
                            config.sensorType,
                            config.filterType,
                            config.filterConstant,
                            config.sourceDeviceName
                        )

                    // calculate the 'filter description' (including the device name when available.  When not, it represents the best sensor)
                    var filterDescription = config.filterType.getShortSummary(application, config.filterConstant)
                    if (config.sourceDeviceName != null) {
                        filterDescription = if (filterDescription.isNotEmpty()) {
                            config.sourceDeviceName + ": " + filterDescription
                        } else {
                            config.sourceDeviceName
                        }
                    }

                    SensorFieldState(
                        configHash = uniqueHash, // Store the unique ID
                        sensorFieldId = config.sensorFieldId,
                        rowNr = config.rowNr,
                        colNr = config.colNr,
                        label = application.getString(config.sensorType.shortNameId),
                        filterDescription = filterDescription,
                        value = "--", // Default value
                        units = application.getString(MyHelper.getShortUnitsId(config.sensorType)),
                        zoneColor = defaultZoneColor // Default color
                    )
                }
            }
            _uiState.value = TrackingScreenState(fields = initialFieldStates)


            // --- Step 2: Reactively update ONLY the value and color ---
            trackingRepository.allFilteredSensorData.combine(trackingRepository.activityType.asFlow()) { allSensorData, activityType ->

                if (activityType == null) return@combine

                val currentFields = _uiState.value.fields
                // Use a map for O(1) lookups, which is much faster than indexOfFirst (O(n))
                val updatedFields = currentFields.associateBy { it.configHash }.toMutableMap()
                var hasChanged = false

                for (sensorData in allSensorData) {
                    val uniqueHash = Objects.hash(
                        sensorData.sensorType,
                        sensorData.filterType,
                        sensorData.filterConstant,
                        sensorData.deviceName
                    )

                    val fieldToUpdate = updatedFields[uniqueHash]
                    if (fieldToUpdate != null) {
                        val currentValue = sensorData.value
                        val newFormattedValue = sensorData.stringValue
                        var newZoneColor = fieldToUpdate.zoneColor

                        val zoneType = getZoneType(sensorData.sensorType, activityType.sportType)
                        if (zoneType != null && currentValue is Number) {
                            val z1Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1)
                            val z2Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2)
                            val z3Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3)
                            val z4Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4)

                            val numericValue = currentValue.toDouble()

                            newZoneColor = when {
                                numericValue <= z1Max -> zoneColors[0]
                                numericValue <= z2Max -> zoneColors[1]
                                numericValue <= z3Max -> zoneColors[2]
                                numericValue <= z4Max -> zoneColors[3]
                                else -> zoneColors[4]
                            }
                        } else {
                            newZoneColor = defaultZoneColor
                        }

                        if (fieldToUpdate.value != newFormattedValue || fieldToUpdate.zoneColor != newZoneColor) {
                            // Update the entry in our temporary map
                            updatedFields[uniqueHash] = fieldToUpdate.copy(
                                value = newFormattedValue,
                                zoneColor = newZoneColor
                            )
                            hasChanged = true
                        }
                    }
                }

                if (hasChanged) {
                    // Convert the map's values back to a list
                    _uiState.value = TrackingScreenState(fields = updatedFields.values.toList())
                }

            }.collect {} // Keep the flow active by collecting with an empty lambda
        }
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
    private val viewId: Int
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