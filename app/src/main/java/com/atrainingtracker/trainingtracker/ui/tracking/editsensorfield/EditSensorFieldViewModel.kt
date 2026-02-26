package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// This class will hold all the state for our dialog
data class EditDialogUiState(
    val selectedSensorType: SensorType? = null,
    val availableSensorTypesForCurrentActivityType: List<SensorType> = emptyList(),
    val selectedDeviceId: Long = -1,
    val availableDevices: List<Pair<Long, String>> = emptyList(),
    val selectedViewSize: ViewSize = ViewSize.NORMAL,
    val availableViewSizes: List<ViewSize> = ViewSize.values().toList(),
    val filterSummary: String = ""
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

    init {
        // 1. Load the INITIAL state once.
        loadInitialState()
        // 2. Start a SEPARATE observer that ONLY updates the filter summary.
        observeFilterChanges()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            // Fetch the config just once to populate the dialog initially.
            val config = configFromRepoFlow.firstOrNull() ?: return@launch
            val context = getApplication<Application>().applicationContext

            _uiState.value = EditDialogUiState(
                selectedSensorType = config.sensorType,
                availableSensorTypesForCurrentActivityType = ActivityType.getSensorTypeArray(activityType, context).toList(),
                selectedDeviceId = config.sourceDeviceId,
                availableDevices = getFullDeviceList(config.sensorType),
                selectedViewSize = config.viewSize,
                filterSummary = config.filterType.getSummary(context, config.filterConstant)
            )
        }
    }

    private fun observeFilterChanges() {
        viewModelScope.launch {
            // updating the filter summary when the database changes.
            configFromRepoFlow.collect { updatedConfig ->
                val newFilterSummary = updatedConfig.filterType.getSummary(getApplication(), updatedConfig.filterConstant)

                // We use 'update' to safely modify only the field we care about,
                // preserving all other user-made changes.
                _uiState.update { currentState ->
                    currentState.copy(filterSummary = newFilterSummary)
                }
            }
        }
    }

    fun onSensorTypeChanged(newSensorType: SensorType) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedSensorType = newSensorType,
                    selectedDeviceId = -1, // Reset device selection
                    availableDevices = getFullDeviceList(newSensorType)
                )
            }
        }
    }

    fun onDeviceChanged(newDeviceId: Long) {
        _uiState.update { it.copy(selectedDeviceId = newDeviceId) }
    }

    fun onViewSizeChanged(newViewSize: ViewSize) {
        _uiState.update { it.copy(selectedViewSize = newViewSize) }
    }

    fun saveChanges() {
        val currentState = _uiState.value
        val sensorType = currentState.selectedSensorType ?: return

        viewModelScope.launch {
            repository.updateSensorFieldConfig(
                sensorFieldId,
                sensorType,
                currentState.selectedViewSize,
                currentState.selectedDeviceId
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

