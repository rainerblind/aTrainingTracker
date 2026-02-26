package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// This class will hold all the state for our dialog
data class EditDialogUiState(
    val selectedSensorType: SensorType? = null,
    val availableSensorTypes: List<SensorType> = emptyList(),
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

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val config = repository.getSensorFieldConfig(sensorFieldId) ?: return@launch
            val context = getApplication<Application>().applicationContext

            _uiState.value = EditDialogUiState(
                selectedSensorType = config.sensorType,
                availableSensorTypes = ActivityType.getSensorTypeArray(activityType, context).toList(),
                selectedDeviceId = config.sourceDeviceId,
                availableDevices = getFullDeviceList(config.sensorType),
                selectedViewSize = config.viewSize,
                filterSummary = config.filterType.getSummary(context, config.filterConstant)
            )
        }
    }

    fun onSensorTypeChanged(newSensorType: SensorType) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedSensorType = newSensorType,
                    selectedDeviceId = -1,
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
                // Corrected: Pass the Long rowId
                sensorFieldId,
                sensorType,
                currentState.selectedViewSize,
                currentState.selectedDeviceId
            )
        }
    }

    private suspend fun getFullDeviceList(sensorType: SensorType): List<Pair<Long, String>> {
        val deviceLists = repository.getDeviceLists(sensorType)
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

