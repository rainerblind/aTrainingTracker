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
    val movingAverageUnit: String = "sec" // "sec", "min", or "samples"
)

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
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0
        )

        _uiState.update {
            it.copy(
                selectedSensorType = defaultSensor,
                availableSensorTypesForCurrentActivityType = ActivityType.getSensorTypeArray(activityType, context).toList(),
                selectedDeviceId = -1,
                selectedDeviceName = context.getString(R.string.bestSensor),
                availableDevices = emptyList(), // Will be updated by side-effect if needed
                selectedViewSize = ViewSize.NORMAL,
                filterSummary = FilterType.INSTANTANEOUS.getSummary(context, 1.0)
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
            }

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
                movingAverageUnit = initialUnit
            )
        }
    }

    fun onSensorTypeChanged(newSensorType: SensorType) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            _uiState.update {
                it.copy(
                    // update the selected sensor type
                    selectedSensorType = newSensorType,
                    // but also:
                    // set 'Best' device as source
                    selectedDeviceId = -1,
                    selectedDeviceName = context.getString(R.string.bestSensor),
                    availableDevices = getFullDeviceList(newSensorType),
                    // set filter to instantaneous
                    filterSummary = FilterType.INSTANTANEOUS.getSummary(context, 1.0),
                    selectedFilterType = FilterType.INSTANTANEOUS,
                    filterConstant = 1.0
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

    fun onFilterTypeChanged(newFilterType: FilterType) {
        _uiState.update {
            it.copy(
                selectedFilterType = newFilterType,
                filterSummary = newFilterType.getSummary(getApplication<Application>().applicationContext, it.filterConstant)
            )
        }
    }

    fun onFilterConstantChanged(newConstant: Double) {
        _uiState.update {
            it.copy(
                filterConstant = newConstant,
                filterSummary = it.selectedFilterType.getSummary(getApplication<Application>().applicationContext, newConstant)
            )
        }
    }

    fun onUnitChanged(newUnit: String) {
        _uiState.update {
            it.copy(
                movingAverageUnit = newUnit,
                filterSummary = it.selectedFilterType.getSummary(getApplication<Application>().applicationContext, it.filterConstant)
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
                it.copy(
                    showFilterConfigDialog = false,
                    filterSummary = initialConfig.filterType.getSummary(context, initialConfig.filterConstant),
                    selectedFilterType = FilterType.INSTANTANEOUS,
                    filterConstant = 1.0
                )

            }
            else {
                // otherwise, set it to the instantaneous filter
                it.copy(
                    showFilterConfigDialog = false,
                    filterSummary = FilterType.INSTANTANEOUS.getSummary(context, 1.0),
                    selectedFilterType = FilterType.INSTANTANEOUS,
                    filterConstant = 1.0
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
        return if (state.selectedFilterType == FilterType.MOVING_AVERAGE_TIME && state.movingAverageUnit == "min") {
            state.filterConstant * 60
        } else {
            state.filterConstant
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
        val deviceLists = trackingViewsRepository.getDeviceLists(sensorType) ?: return listOf(-1L to getApplication<Application>().getString(R.string.bestSensor))
        val context = getApplication<Application>().applicationContext
        val devices = deviceLists.deviceIds.zip(deviceLists.names).toMutableList()
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

