package com.atrainingtracker.trainingtracker.ui.tracking.configfilter

import androidx.activity.result.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfigureFilterUiState(
    val selectedFilterType: FilterType = FilterType.INSTANTANEOUS,
    val filterConstant: Double = 1.0,
    val movingAverageUnit: String = "sec" // "sec", "min", or "samples"
)

class ConfigureFilterViewModel(
    val repository: TrackingRepository,
    private val sensorViewId: Long,
    initialFilterType: FilterType,
    initialFilterConstant: Double
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigureFilterUiState())
    val uiState = _uiState.asStateFlow()

    init {
        var initialUnit = "sec"
        var displayConstant = initialFilterConstant

        if (initialFilterType == FilterType.MOVING_AVERAGE_TIME) {
            if (initialFilterConstant >= 60 && initialFilterConstant % 60 == 0.0) {
                initialUnit = "min"
                displayConstant = initialFilterConstant / 60
            }
        } else if (initialFilterType == FilterType.MOVING_AVERAGE_NUMBER) {
            initialUnit = "samples"
        }

        _uiState.value = ConfigureFilterUiState(
            selectedFilterType = initialFilterType,
            filterConstant = displayConstant,
            movingAverageUnit = initialUnit
        )
    }

    fun onFilterTypeChanged(newFilterType: FilterType) {
        _uiState.update { it.copy(selectedFilterType = newFilterType) }
    }

    fun onFilterConstantChanged(newConstant: Double) {
        _uiState.update { it.copy(filterConstant = newConstant) }
    }

    fun onUnitChanged(newUnit: String) {
        _uiState.update { it.copy(movingAverageUnit = newUnit) }
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

    fun saveFilterChanges() {
        viewModelScope.launch {
            repository.updateSensorFilter(
                rowId = sensorViewId,
                filterType = getFinalFilterType(),
                filterConstant = getFinalFilterConstant()
            )
        }
    }
}

class ConfigureFilterViewModelFactory(
    private val repository: TrackingRepository,
    private val sensorViewId: Long,
    private val initialFilterType: FilterType,
    private val initialFilterConstant: Double
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfigureFilterViewModel::class.java)) {
            return ConfigureFilterViewModel(repository, sensorViewId, initialFilterType, initialFilterConstant) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}