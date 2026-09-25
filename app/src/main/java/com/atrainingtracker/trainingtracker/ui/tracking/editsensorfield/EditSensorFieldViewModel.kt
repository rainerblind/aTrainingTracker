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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Intuitive smoothing presets for athletic sensor configurations (ATT-1276, REQ-UI-167, TST-UI-119).
 */
enum class FilterPreset(val labelResId: Int) {
    DIRECT(R.string.filter_preset_direct),
    SMOOTH_3S(R.string.filter_preset_3s),
    SMOOTH_10S(R.string.filter_preset_10s),
    SMOOTH_30S(R.string.filter_preset_30s),
    SESSION_AVG(R.string.filter_preset_avg),
    SESSION_MAX(R.string.filter_preset_max),
    CUSTOM(R.string.filter_preset_custom);

    fun getDisplayName(context: Context): String = context.getString(labelResId)
}

/**
 * Resolves the corresponding quick preset from raw filter configuration parameters.
 */
fun resolveFilterPreset(
    filterType: FilterType,
    constant: Double,
    unit: String
): FilterPreset {
    return when (filterType) {
        FilterType.INSTANTANEOUS -> FilterPreset.DIRECT
        FilterType.AVERAGE -> FilterPreset.SESSION_AVG
        FilterType.MAX_VALUE -> FilterPreset.SESSION_MAX
        FilterType.MOVING_AVERAGE_TIME -> {
            if (unit == "sec") {
                when (constant) {
                    1.0 -> FilterPreset.DIRECT
                    3.0 -> FilterPreset.SMOOTH_3S
                    10.0 -> FilterPreset.SMOOTH_10S
                    30.0 -> FilterPreset.SMOOTH_30S
                    else -> FilterPreset.CUSTOM
                }
            } else {
                FilterPreset.CUSTOM
            }
        }
        FilterType.MOVING_AVERAGE_NUMBER -> FilterPreset.CUSTOM
        FilterType.EXPONENTIAL_SMOOTHING -> FilterPreset.CUSTOM
    }
}

// This class will hold all the state for our dialog
data class EditDialogUiState(
    val selectedSensorType: SensorType? = null,
    val availableSensorTypesForCurrentActivityType: List<SensorType> = emptyList(),
    val selectedDeviceId: Long = -1,
    val selectedDeviceName: String? = null,
    val availableDevices: List<Pair<Long, String>> = emptyList(),
    val selectedViewSize: ViewSize = ViewSize.NORMAL,
    val availableViewSizes: List<ViewSize> = ViewSize.values().toList(),
    val showFilterConfigDialog: Boolean = false,
    val filterSummary: String = "",
    val selectedFilterType: FilterType = FilterType.INSTANTANEOUS,
    val filterConstant: Double = 1.0,
    val movingAverageUnit: String = "sec", // "sec", "min", or "samples"
    val isCustomFilterExpanded: Boolean = false
) {
    val activePreset: FilterPreset
        get() = if (isCustomFilterExpanded && resolveFilterPreset(selectedFilterType, filterConstant, movingAverageUnit) != FilterPreset.CUSTOM) {
            FilterPreset.CUSTOM
        } else {
            resolveFilterPreset(selectedFilterType, filterConstant, movingAverageUnit)
        }
}

class EditSensorFieldViewModel(
    application: Application,
    private val trackingViewsRepository: TrackingViewsRepository,
    private val banalServiceRepository: BANALServiceRepository,
    private val activityType: ActivityType,
    private val sensorFieldId: Long, // use -1L to signal "New Mode
    private val tabViewId: Long,
    private val rowNr: Int,
    private val colNr: Int,          // -1 for new row
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditDialogUiState())
    val uiState: StateFlow<EditDialogUiState> = _uiState.asStateFlow()

    // This flow will ONLY be used to receive updates from the database.
    private val configFromRepoFlow = trackingViewsRepository.getSensorFieldConfig(sensorFieldId).filterNotNull()
    lateinit var initialConfig: SensorFieldConfig

    private val isNewField = sensorFieldId == -1L

    init {
        Log.i("EditSensorFieldViewModel", "init(): $sensorFieldId, $tabViewId, $rowNr, $colNr")
        if (isNewField) {
            setupDefaultState()
        } else {
            loadInitialState()
        }
    }

    private fun setupDefaultState() {
        val context = getApplication<Application>().applicationContext
        // Use a default sensor type (e.g., first available)
        val defaultSensor = SensorType.SPEED_mps
        val defaultFilterType = if (defaultSensor == SensorType.POWER) FilterType.MOVING_AVERAGE_TIME else FilterType.INSTANTANEOUS
        val defaultConstant = if (defaultSensor == SensorType.POWER) 3.0 else 1.0

        // Create a MOCK initialConfig for the "Add" scenario.
        // This ensures that functions like onFilterConfigDismissed don't crash.
        initialConfig = SensorFieldConfig(
            sensorFieldId = -1,
            rowNr = 0,
            colNr = 0,
            sensorType = defaultSensor,
            sourceDeviceId = -1,
            sourceDeviceName = context.getString(R.string.bestSensor),
            viewSize = ViewSize.NORMAL,
            filterType = defaultFilterType,
            filterConstant = defaultConstant
        )

        _uiState.update {
            it.copy(
                selectedSensorType = defaultSensor,
                availableSensorTypesForCurrentActivityType = ActivityType.getSensorTypeArray(activityType, context).toList(),
                selectedDeviceId = -1,
                selectedDeviceName = context.getString(R.string.bestSensor),
                availableDevices = emptyList(), // Will be updated by side-effect if needed
                selectedViewSize = ViewSize.NORMAL,
                selectedFilterType = defaultFilterType,
                filterConstant = defaultConstant,
                movingAverageUnit = "sec",
                filterSummary = defaultFilterType.getSummary(context, defaultConstant),
                isCustomFilterExpanded = false
            )
        }
    }

    fun loadInitialState() {
        viewModelScope.launch {
            // Fetch the config just once to populate the dialog initially.
            initialConfig = configFromRepoFlow.firstOrNull() ?: return@launch
            val context = getApplication<Application>().applicationContext

            var initialUnit = "sec"
            var displayConstant = initialConfig.filterConstant

            if (initialConfig.filterType == FilterType.MOVING_AVERAGE_TIME) {
                if (initialConfig.filterConstant >= 60 && initialConfig.filterConstant % 60 == 0.0) {
                    initialUnit = "min"
                    displayConstant = initialConfig.filterConstant / 60
                }
            } else if (initialConfig.filterType == FilterType.MOVING_AVERAGE_NUMBER) {
                initialUnit = "samples"
            } else if (initialConfig.filterType == FilterType.EXPONENTIAL_SMOOTHING) {
                if (displayConstant <= 0.0 || displayConstant > 1.0) {
                    displayConstant = 0.8
                }
            }

            val initialPreset = resolveFilterPreset(initialConfig.filterType, displayConstant, initialUnit)

            _uiState.value = EditDialogUiState(
                selectedSensorType = initialConfig.sensorType,
                availableSensorTypesForCurrentActivityType = ActivityType.getSensorTypeArray(activityType, context).toList(),
                selectedDeviceId = initialConfig.sourceDeviceId,
                selectedDeviceName = initialConfig.sourceDeviceName,
                availableDevices = getFullDeviceList(initialConfig.sensorType),
                selectedViewSize = initialConfig.viewSize,
                filterSummary = initialConfig.filterType.getSummary(context, initialConfig.filterConstant),
                selectedFilterType = initialConfig.filterType,
                filterConstant = displayConstant,
                movingAverageUnit = initialUnit,
                isCustomFilterExpanded = (initialPreset == FilterPreset.CUSTOM)
            )
        }
    }

    fun onSensorTypeChanged(newSensorType: SensorType) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val defaultFilterType = if (newSensorType == SensorType.POWER) FilterType.MOVING_AVERAGE_TIME else FilterType.INSTANTANEOUS
            val defaultConstant = if (newSensorType == SensorType.POWER) 3.0 else 1.0

            _uiState.update {
                it.copy(
                    // update the selected sensor type
                    selectedSensorType = newSensorType,
                    // but also:
                    // set 'Best' device as source
                    selectedDeviceId = -1,
                    selectedDeviceName = context.getString(R.string.bestSensor),
                    availableDevices = getFullDeviceList(newSensorType),
                    // set filter with smart defaults (3s for power, 1s direct for others)
                    filterSummary = defaultFilterType.getSummary(context, defaultConstant),
                    selectedFilterType = defaultFilterType,
                    filterConstant = defaultConstant,
                    movingAverageUnit = "sec",
                    isCustomFilterExpanded = false
                )
            }
        }
    }

    fun onDeviceChanged(newDeviceId: Long, newDeviceName: String) {
        val context = getApplication<Application>().applicationContext

        _uiState.update {
            it.copy(
                selectedDeviceId = newDeviceId,
                selectedDeviceName = newDeviceName
            )
        }
    }

    fun onViewSizeChanged(newViewSize: ViewSize) {
        _uiState.update { it.copy(selectedViewSize = newViewSize) }
    }

    fun onPresetSelected(preset: FilterPreset) {
        val context = getApplication<Application>().applicationContext
        when (preset) {
            FilterPreset.DIRECT -> {
                _uiState.update {
                    it.copy(
                        selectedFilterType = FilterType.INSTANTANEOUS,
                        filterConstant = 1.0,
                        movingAverageUnit = "sec",
                        filterSummary = FilterType.INSTANTANEOUS.getSummary(context, 1.0),
                        isCustomFilterExpanded = false
                    )
                }
            }
            FilterPreset.SMOOTH_3S -> {
                _uiState.update {
                    it.copy(
                        selectedFilterType = FilterType.MOVING_AVERAGE_TIME,
                        filterConstant = 3.0,
                        movingAverageUnit = "sec",
                        filterSummary = FilterType.MOVING_AVERAGE_TIME.getSummary(context, 3.0),
                        isCustomFilterExpanded = false
                    )
                }
            }
            FilterPreset.SMOOTH_10S -> {
                _uiState.update {
                    it.copy(
                        selectedFilterType = FilterType.MOVING_AVERAGE_TIME,
                        filterConstant = 10.0,
                        movingAverageUnit = "sec",
                        filterSummary = FilterType.MOVING_AVERAGE_TIME.getSummary(context, 10.0),
                        isCustomFilterExpanded = false
                    )
                }
            }
            FilterPreset.SMOOTH_30S -> {
                _uiState.update {
                    it.copy(
                        selectedFilterType = FilterType.MOVING_AVERAGE_TIME,
                        filterConstant = 30.0,
                        movingAverageUnit = "sec",
                        filterSummary = FilterType.MOVING_AVERAGE_TIME.getSummary(context, 30.0),
                        isCustomFilterExpanded = false
                    )
                }
            }
            FilterPreset.SESSION_AVG -> {
                _uiState.update {
                    it.copy(
                        selectedFilterType = FilterType.AVERAGE,
                        filterConstant = 1.0,
                        movingAverageUnit = "sec",
                        filterSummary = FilterType.AVERAGE.getSummary(context, 1.0),
                        isCustomFilterExpanded = false
                    )
                }
            }
            FilterPreset.SESSION_MAX -> {
                _uiState.update {
                    it.copy(
                        selectedFilterType = FilterType.MAX_VALUE,
                        filterConstant = 1.0,
                        movingAverageUnit = "sec",
                        filterSummary = FilterType.MAX_VALUE.getSummary(context, 1.0),
                        isCustomFilterExpanded = false
                    )
                }
            }
            FilterPreset.CUSTOM -> {
                _uiState.update {
                    it.copy(isCustomFilterExpanded = true)
                }
            }
        }
    }

    fun onCustomFilterExpandedChanged(isExpanded: Boolean) {
        _uiState.update { it.copy(isCustomFilterExpanded = isExpanded) }
    }

    fun onFilterTypeChanged(newFilterType: FilterType) {
        _uiState.update {
            val adjustedConstant = if (newFilterType == FilterType.EXPONENTIAL_SMOOTHING) {
                if (it.filterConstant <= 0.0 || it.filterConstant > 1.0) 0.8 else it.filterConstant
            } else if (it.selectedFilterType == FilterType.EXPONENTIAL_SMOOTHING &&
                (newFilterType == FilterType.MOVING_AVERAGE_TIME || newFilterType == FilterType.MOVING_AVERAGE_NUMBER)
            ) {
                if (it.filterConstant <= 1.0) 3.0 else it.filterConstant
            } else {
                it.filterConstant
            }
            it.copy(
                selectedFilterType = newFilterType,
                filterConstant = adjustedConstant,
                filterSummary = newFilterType.getSummary(getApplication<Application>().applicationContext, adjustedConstant),
                isCustomFilterExpanded = true
            )
        }
    }

    fun onFilterConstantChanged(newConstant: Double) {
        _uiState.update {
            val validConstant = if (it.selectedFilterType == FilterType.EXPONENTIAL_SMOOTHING) {
                newConstant.coerceIn(0.01, 1.0)
            } else {
                newConstant
            }
            it.copy(
                filterConstant = validConstant,
                filterSummary = it.selectedFilterType.getSummary(getApplication<Application>().applicationContext, validConstant),
                isCustomFilterExpanded = true
            )
        }
    }

    fun onUnitChanged(newUnit: String) {
        _uiState.update {
            it.copy(
                movingAverageUnit = newUnit,
                filterSummary = it.selectedFilterType.getSummary(getApplication<Application>().applicationContext, it.filterConstant),
                isCustomFilterExpanded = true
            )
        }
    }

    fun onConfigureFilterClicked() {
        _uiState.update { it.copy(showFilterConfigDialog = true) }
    }

    fun onFilterConfigDismissed() {
        val context = getApplication<Application>().applicationContext

        _uiState.update {
            // when the sensor type and the source device is unchanged
            if (it.selectedSensorType == initialConfig.sensorType &&
                it.selectedDeviceId == initialConfig.sourceDeviceId) {
                // then copy the filter stuff from the initial config
                var initialUnit = "sec"
                var displayConstant = initialConfig.filterConstant
                if (initialConfig.filterType == FilterType.MOVING_AVERAGE_TIME) {
                    if (initialConfig.filterConstant >= 60 && initialConfig.filterConstant % 60 == 0.0) {
                        initialUnit = "min"
                        displayConstant = initialConfig.filterConstant / 60
                    }
                } else if (initialConfig.filterType == FilterType.MOVING_AVERAGE_NUMBER) {
                    initialUnit = "samples"
                } else if (initialConfig.filterType == FilterType.EXPONENTIAL_SMOOTHING) {
                    if (displayConstant <= 0.0 || displayConstant > 1.0) {
                        displayConstant = 0.8
                    }
                }
                val initialPreset = resolveFilterPreset(initialConfig.filterType, displayConstant, initialUnit)
                it.copy(
                    showFilterConfigDialog = false,
                    filterSummary = initialConfig.filterType.getSummary(context, initialConfig.filterConstant),
                    selectedFilterType = initialConfig.filterType,
                    filterConstant = displayConstant,
                    movingAverageUnit = initialUnit,
                    isCustomFilterExpanded = (initialPreset == FilterPreset.CUSTOM)
                )

            }
            else {
                // otherwise, set it to the default for this sensor type
                val defaultFilterType = if (it.selectedSensorType == SensorType.POWER) FilterType.MOVING_AVERAGE_TIME else FilterType.INSTANTANEOUS
                val defaultConstant = if (it.selectedSensorType == SensorType.POWER) 3.0 else 1.0
                it.copy(
                    showFilterConfigDialog = false,
                    filterSummary = defaultFilterType.getSummary(context, defaultConstant),
                    selectedFilterType = defaultFilterType,
                    filterConstant = defaultConstant,
                    movingAverageUnit = "sec",
                    isCustomFilterExpanded = false
                )
            }
        }
    }

    fun onSaveFilterConfig() {
        // nothing to do here.
        // except for removing the ConfigureFilterDialog.
        _uiState.update { it.copy(showFilterConfigDialog = false) }
    }

    private fun getFinalFilterConstant(): Double {
        val state = _uiState.value
        return when (state.selectedFilterType) {
            FilterType.MOVING_AVERAGE_TIME -> {
                if (state.movingAverageUnit == "min") state.filterConstant * 60 else state.filterConstant
            }
            FilterType.EXPONENTIAL_SMOOTHING -> {
                state.filterConstant.coerceIn(0.01, 1.0)
            }
            else -> state.filterConstant
        }
    }

    private fun getFinalFilterType(): FilterType {
        val state = _uiState.value
        return if (state.selectedFilterType == FilterType.MOVING_AVERAGE_TIME && state.movingAverageUnit == "samples") {
            FilterType.MOVING_AVERAGE_NUMBER
        } else {
            state.selectedFilterType
        }
    }

    fun saveChanges() {
        Log.i("EditSensorFieldViewModel", "saveChanges(): $tabViewId, $rowNr, $colNr")
        val currentState = _uiState.value
        val sensorType = currentState.selectedSensorType ?: return

        val newSourceDeviceName = currentState.selectedDeviceName
        val newSensorType = sensorType
        val newFilterType = getFinalFilterType()
        val newFilterConstant = getFinalFilterConstant()

        // when the filter has changed, the BANALService must create this filter.
        val filterData = FilterData(newSourceDeviceName, newSensorType, newFilterType, newFilterConstant)
        banalServiceRepository.createFilter(filterData)

        viewModelScope.launch {
            if (isNewField) {
                trackingViewsRepository.insertSensorFieldConfig(
                    tabViewId = tabViewId,
                    rowNr = rowNr,
                    colNr = colNr,
                    newSensorType = newSensorType,
                    newViewSize = currentState.selectedViewSize,
                    newSourceDeviceId = currentState.selectedDeviceId,
                    newSourceDeviceName = newSourceDeviceName,
                    newFilterType = newFilterType,
                    newFilterConstant = newFilterConstant
                )
            } else {
                trackingViewsRepository.updateSensorFieldConfig(
                    sensorFieldId = sensorFieldId,
                    newSensorType = newSensorType,
                    newViewSize = currentState.selectedViewSize,
                    newSourceDeviceId = currentState.selectedDeviceId,
                    newSourceDeviceName = newSourceDeviceName,
                    newFilterType = newFilterType,
                    newFilterConstant = newFilterConstant
                )
            }
        }
    }

    private suspend fun getFullDeviceList(sensorType: SensorType): List<Pair<Long, String>> {
        val context = getApplication<Application>().applicationContext
        val defaultDevice = listOf(-1L to context.getString(R.string.bestSensor))
        val deviceLists = trackingViewsRepository.getDeviceLists(sensorType) ?: return defaultDevice
        val ids = deviceLists.deviceIds ?: return defaultDevice
        val names = deviceLists.names ?: return defaultDevice
        val devices = ids.zip(names).toMutableList()
        devices.add(0, -1L to context.getString(R.string.bestSensor))
        return devices
    }
}

class EditSensorFieldViewModelFactory(
    private val application: Application,
    private val trackingViewsRepository: TrackingViewsRepository,
    private val banalServiceRepository: BANALServiceRepository,
    private val activityType: ActivityType,
    private val sensorFieldId: Long,  // -1 means "New Mode"
    private val tabViewId: Long,
    private val rowNr: Int,
    private val colNr: Int  // -1 means new row
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditSensorFieldViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditSensorFieldViewModel(application, trackingViewsRepository, banalServiceRepository,
                activityType, sensorFieldId, tabViewId, rowNr, colNr) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

