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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.physiology.PmcDailyPoint
import com.atrainingtracker.trainingtracker.physiology.PmcSummaryResult
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.physiology.WorkoutTrainingLoadSample
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure helper resolving which [PlotMetricType] pills are visible in [AthleteProfileScreen] (`REQ-UI-403`).
 */
fun resolveVisiblePlotMetrics(enableCriticalPowerModel: Boolean): List<PlotMetricType> =
    if (enableCriticalPowerModel) {
        PlotMetricType.entries
    } else {
        PlotMetricType.entries.filter { it != PlotMetricType.CP && it != PlotMetricType.W_PRIME }
    }

/**
 * Pure helper resolving the active [PlotMetricType], falling back to [PlotMetricType.WEIGHT]
 * when Critical Power model is disabled and CP or W' was selected (`REQ-UI-403`).
 */
fun resolveEffectivePlotMetric(
    selected: PlotMetricType,
    enableCriticalPowerModel: Boolean
): PlotMetricType =
    if (!enableCriticalPowerModel && (selected == PlotMetricType.CP || selected == PlotMetricType.W_PRIME)) {
        PlotMetricType.WEIGHT
    } else {
        selected
    }

/**
 * Pure helper resolving which [AthleteMetricType] history filter chips are visible (`REQ-UI-403`).
 */
fun resolveVisibleHistoryFilterMetrics(enableCriticalPowerModel: Boolean): List<AthleteMetricType?> {
    val base = listOf<AthleteMetricType?>(
        null,
        AthleteMetricType.WEIGHT_KG,
        AthleteMetricType.HR_REST_BPM,
        AthleteMetricType.FTP_WATTS
    )
    return if (enableCriticalPowerModel) {
        base + listOf(AthleteMetricType.CP_WATTS, AthleteMetricType.W_PRIME_KJ)
    } else {
        base
    }
}

/**
 * Pure helper resolving the active history filter, falling back to `null` (All)
 * when Critical Power model is disabled and CP or W' was selected (`REQ-UI-403`).
 */
fun resolveEffectiveHistoryFilter(
    selected: AthleteMetricType?,
    enableCriticalPowerModel: Boolean
): AthleteMetricType? =
    if (!enableCriticalPowerModel && (selected == AthleteMetricType.CP_WATTS || selected == AthleteMetricType.W_PRIME_KJ)) {
        null
    } else {
        selected
    }

/**
 * Immutable UI state for [AthleteProfileScreen] (`REQ-UI-391.5`, `REQ-UI-401.3`, `REQ-UI-403`).
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
    val pmcSummary: PmcSummaryResult = PmcSummaryResult(),
    val pmcPlotPoints: List<PmcDailyPoint> = emptyList(),
    val historyFilterMetric: AthleteMetricType? = null,
    val filteredHistory: List<AthleteMetricEntry> = emptyList(),
    val isImperial: Boolean = false,
    val enablePmcModel: Boolean = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL,
    val enableCriticalPowerModel: Boolean = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL
)

/**
 * ViewModel driving [AthleteProfileScreen] (`REQ-UI-391.5`, `REQ-UI-399.1`, `REQ-UI-401.3`, `REQ-UI-403`).
 */
class AthleteProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AthleteMetricsRepository.getInstance(application)
    private val settingsDataStore = SettingsDataStore.getInstance(application)
    private val tuningDataStore = TuningPreferencesDataStore(application)

    private val _selectedPlotMetric = MutableStateFlow(PlotMetricType.WEIGHT)
    private val _selectedTimeRange = MutableStateFlow(MetricsTimeRange.ALL)
    private val _historyFilterMetric = MutableStateFlow<AthleteMetricType?>(null)
    private val _workoutLoadSamples = MutableStateFlow<List<WorkoutTrainingLoadSample>>(emptyList())
    private val _hrMaxBpm = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<AthleteProfileUiState> = combine(
        repository.entriesFlow,
        _selectedPlotMetric,
        _selectedTimeRange,
        _historyFilterMetric,
        combine(_workoutLoadSamples, tuningDataStore.tuningConfigFlow) { samples, tuning ->
            samples to tuning
        }
    ) { entries, rawPlotMetric, timeRange, rawHistoryFilter, (workoutSamples, tuningConfig) ->
        val isImperial = try {
            TrainingApplication.getUnit() == MyUnits.IMPERIAL
        } catch (_: Throwable) {
            false
        }

        val enablePmc = tuningConfig.enablePmcModel
        val enableCp = tuningConfig.enableCriticalPowerModel
        val plotMetric = resolveEffectivePlotMetric(rawPlotMetric, enableCp)
        val historyFilter = resolveEffectiveHistoryFilter(rawHistoryFilter, enableCp)

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

        val pmcSummary = PowerPhysiologyCalculator.computePerformanceManagementChart(
            workoutSamples = workoutSamples,
            nowMs = nowMs,
            ftpWatts = latestFtp?.value,
            hrRestBpm = latestHrRest?.value,
            hrMaxBpm = _hrMaxBpm.value?.toDouble()
        )
        val pmcPlotPoints = AthleteMetricsPlotMath.filterPmcPointsByTimeRange(
            points = pmcSummary.dailyPoints,
            timeRange = timeRange,
            nowMs = nowMs
        )

        val visibleEntries = if (enableCp) {
            entries
        } else {
            entries.filter {
                it.metricType != AthleteMetricType.CP_WATTS && it.metricType != AthleteMetricType.W_PRIME_KJ
            }
        }

        val filteredHistory = if (historyFilter == null) {
            visibleEntries
        } else {
            visibleEntries.filter { it.metricType == historyFilter }
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
            pmcSummary = pmcSummary,
            pmcPlotPoints = pmcPlotPoints,
            historyFilterMetric = historyFilter,
            filteredHistory = filteredHistory,
            isImperial = isImperial,
            enablePmcModel = enablePmc,
            enableCriticalPowerModel = enableCp
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
        viewModelScope.launch {
            settingsDataStore.hrMaxFlow.collect { hrMax ->
                _hrMaxBpm.value = hrMax
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val samples = try {
                WorkoutSummariesDatabaseManager.getInstance(getApplication()).getWorkoutTrainingLoadRecords()
            } catch (_: Throwable) {
                emptyList()
            }
            _workoutLoadSamples.value = samples
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
