package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
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
    val filterSummary: String = "",
    val selectedFilterType: FilterType = FilterType.INSTANTANEOUS,
    val filterConstant: Double = 1.0,
    val movingAverageUnit: String = "sec" // "sec", "min", or "samples"
)

class EditSensorFieldViewModel(
    application: Application,
    private val activityType: ActivityType,
    private val sensorFieldId: Long,
    private val repository: TrackingRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditDialogUiState())
    val uiState: StateFlow<EditDialogUiState> = _uiState.asStateFlow()

    // This flow will ONLY be used to receive updates from the database.
    private val configFromRepoFlow = repository.getSensorFieldConfig(sensorFieldId).filterNotNull()
    lateinit var initialConfig: SensorFieldConfig

    init {
        // Load the initial state once.
        loadInitialState()
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

    fun onFilterEditCancel() {
        val context = getApplication<Application>().applicationContext

        _uiState.update {
            // when the sensor type and the source device is unchanged
            if (it.selectedSensorType == initialConfig.sensorType &&
                it.selectedDeviceId == initialConfig.sourceDeviceId) {
                // then copy the filter stuff rom the initial config
                it.copy(
                    filterSummary = initialConfig.filterType.getSummary(context, initialConfig.filterConstant),
                    selectedFilterType = FilterType.INSTANTANEOUS,
                    filterConstant = 1.0
                )

            }
            else {
                // otherwise, set it to the instantaneous filter
                it.copy(
                    filterSummary = FilterType.INSTANTANEOUS.getSummary(context, 1.0),
                    selectedFilterType = FilterType.INSTANTANEOUS,
                    filterConstant = 1.0
                )
            }
        }
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
        val currentState = _uiState.value
        val sensorType = currentState.selectedSensorType ?: return

        viewModelScope.launch {
            repository.updateSensorFieldConfig(
                sensorFieldId = sensorFieldId,
                newSensorType = sensorType,
                newViewSize =  currentState.selectedViewSize,
                newSourceDeviceId = currentState.selectedDeviceId,
                newSourceDeviceName = currentState.selectedDeviceName,
                newFilterType = getFinalFilterType(),
                newFilterConstant = getFinalFilterConstant()
            )
        }
    }

    private suspend fun getFullDeviceList(sensorType: SensorType): List<Pair<Long, String>> {
        val deviceLists = repository.getDeviceLists(sensorType) ?: return listOf(-1L to getApplication<Application>().getString(R.string.bestSensor))
        val context = getApplication<Application>().applicationContext
        val devices = deviceLists.deviceIds.zip(deviceLists.names).toMutableList()
        devices.add(0, -1L to context.getString(R.string.bestSensor))
        return devices
    }
}

class EditSensorFieldViewModelFactory(
    private val application: Application,
    private val activityType: ActivityType,
    private val sensorFieldId: Long,
    private val repository: TrackingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditSensorFieldViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditSensorFieldViewModel(application, activityType, sensorFieldId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

