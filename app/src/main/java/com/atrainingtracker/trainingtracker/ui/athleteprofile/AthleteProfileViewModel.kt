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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Immutable UI state for [AthleteProfileScreen] (`REQ-UI-391.5`).
 */
data class AthleteProfileUiState(
    val latestWeightEntry: AthleteMetricEntry? = null,
    val latestHrRestEntry: AthleteMetricEntry? = null,
    val latestFtpEntry: AthleteMetricEntry? = null,
    val latestCpEntry: AthleteMetricEntry? = null,
    val latestWPrimeEntry: AthleteMetricEntry? = null,
    val currentPowerToWeight: Double? = null,
    val latestPowerToWeightTimestampMs: Long? = null,
    val selectedPlotMetric: PlotMetricType = PlotMetricType.WEIGHT,
    val selectedTimeRange: MetricsTimeRange = MetricsTimeRange.ALL,
    val plotPoints: List<MetricPlotPoint> = emptyList(),
    val historyFilterMetric: AthleteMetricType? = null,
    val filteredHistory: List<AthleteMetricEntry> = emptyList(),
    val isImperial: Boolean = false
)

/**
 * ViewModel driving [AthleteProfileScreen] (`REQ-UI-391.5`, `REQ-UI-399.1`).
 */
class AthleteProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AthleteMetricsRepository.getInstance(application)

    private val _selectedPlotMetric = MutableStateFlow(PlotMetricType.WEIGHT)
    private val _selectedTimeRange = MutableStateFlow(MetricsTimeRange.ALL)
    private val _historyFilterMetric = MutableStateFlow<AthleteMetricType?>(null)

    val uiState: StateFlow<AthleteProfileUiState> = combine(
        repository.entriesFlow,
        _selectedPlotMetric,
        _selectedTimeRange,
        _historyFilterMetric
    ) { entries, plotMetric, timeRange, historyFilter ->
        val isImperial = try {
            TrainingApplication.getUnit() == MyUnits.IMPERIAL
        } catch (_: Throwable) {
            false
        }

        val latestWeight = entries.firstOrNull { it.metricType == AthleteMetricType.WEIGHT_KG }
        val latestHrRest = entries.firstOrNull { it.metricType == AthleteMetricType.HR_REST_BPM }
        val latestFtp = entries.firstOrNull { it.metricType == AthleteMetricType.FTP_WATTS }
        val latestCp = entries.firstOrNull { it.metricType == AthleteMetricType.CP_WATTS }
        val latestWPrime = entries.firstOrNull { it.metricType == AthleteMetricType.W_PRIME_KJ }

        val currentWPerKg = AthleteMetricsPlotMath.computeCurrentPowerToWeight(
            latestFtpWatts = latestFtp?.value,
            latestWeightKg = latestWeight?.value
        )
        val latestWPerKgTs = if (latestFtp != null && latestWeight != null) {
            maxOf(latestFtp.timestampMs, latestWeight.timestampMs)
        } else {
            null
        }

        val nowMs = System.currentTimeMillis()
        val plotPoints = AthleteMetricsPlotMath.filterAndConvertSeries(
            entries = entries,
            metricType = plotMetric,
            timeRange = timeRange,
            nowMs = nowMs,
            isImperial = isImperial
        )

        val filteredHistory = if (historyFilter == null) {
            entries
        } else {
            entries.filter { it.metricType == historyFilter }
        }

        AthleteProfileUiState(
            latestWeightEntry = latestWeight,
            latestHrRestEntry = latestHrRest,
            latestFtpEntry = latestFtp,
            latestCpEntry = latestCp,
            latestWPrimeEntry = latestWPrime,
            currentPowerToWeight = currentWPerKg,
            latestPowerToWeightTimestampMs = latestWPerKgTs,
            selectedPlotMetric = plotMetric,
            selectedTimeRange = timeRange,
            plotPoints = plotPoints,
            historyFilterMetric = historyFilter,
            filteredHistory = filteredHistory,
            isImperial = isImperial
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AthleteProfileUiState()
    )

    init {
        viewModelScope.launch {
            repository.initializeAndRefresh()
        }
    }

    fun selectPlotMetric(metric: PlotMetricType) {
        _selectedPlotMetric.value = metric
    }

    fun selectTimeRange(range: MetricsTimeRange) {
        _selectedTimeRange.value = range
    }

    fun selectHistoryFilter(metric: AthleteMetricType?) {
        _historyFilterMetric.value = metric
    }

    fun addEntry(metricType: AthleteMetricType, canonicalValue: Double, timestampMs: Long) {
        viewModelScope.launch {
            repository.addEntry(metricType = metricType, value = canonicalValue, timestampMs = timestampMs)
        }
    }

    fun updateEntry(id: Long, metricType: AthleteMetricType, canonicalValue: Double, timestampMs: Long) {
        viewModelScope.launch {
            repository.updateEntry(
                id = id,
                metricType = metricType,
                value = canonicalValue,
                timestampMs = timestampMs
            )
        }
    }

    fun deleteEntry(id: Long, metricType: AthleteMetricType) {
        viewModelScope.launch {
            repository.deleteEntry(id = id, metricType = metricType)
        }
    }
}
