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
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvReadinessTrendMath
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvTrendMetricMode
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvTrendSeries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.physiology.PmcDailyPoint
import com.atrainingtracker.trainingtracker.physiology.PmcSummaryResult
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.physiology.WorkoutTrainingLoadSample
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolves the list of active tab string resource IDs for the Fitness & Physiology hub
 * in physiological hierarchy order (REQ-UI-513.4, TST-UI-513.3):
 * 1. Baseline Anthropometrics & Thresholds (Body & Thresholds)
 * 2. Training Load & Fatigue Progression (Banister PMC, conditional on [enablePmcModel])
 * 3. Autonomic Recovery & Readiness (HRV & Readiness)
 * 4. Longitudinal Efficiency (Cardio-Power, conditional on [enableCardioPowerDynamics])
 */
fun resolveFitnessHubTabs(
    enablePmcModel: Boolean,
    enableCardioPowerDynamics: Boolean
): List<Int> = buildList {
    add(R.string.fitness_tab_body)
    if (enablePmcModel) {
        add(R.string.fitness_tab_pmc)
    }
    add(R.string.fitness_tab_hrv)
    if (enableCardioPowerDynamics) {
        add(R.string.fitness_tab_cardio_power)
    }
}

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
    val selectedPmcTimeRange: MetricsTimeRange = MetricsTimeRange.SIX_MONTHS,
    val plotPoints: List<MetricPlotPoint> = emptyList(),
    val pmcSummary: PmcSummaryResult = PmcSummaryResult(),
    val pmcPlotPoints: List<PmcDailyPoint> = emptyList(),
    val historyFilterMetric: AthleteMetricType? = null,
    val filteredHistory: List<AthleteMetricEntry> = emptyList(),
    val isImperial: Boolean = false,
    val enablePmcModel: Boolean = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL,
    val enableCriticalPowerModel: Boolean = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL,
    val enableCardioPowerDynamics: Boolean = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS,
    val selectedCardioTrendMetric: CardioTrendMetric = CardioTrendMetric.HR_200W,
    val selectedCardioTimeRange: MetricsTimeRange = MetricsTimeRange.ALL,
    val cardioTrendPoints: List<CardioPlotPoint> = emptyList(),
    val cardioTrendRollingPoints: List<CardioPlotPoint> = emptyList(),
    val cardioTrendYDomain: Pair<Double, Double> = 110.0 to 170.0,
    val hrvReadinessEntries: List<HrvReadinessEntry> = emptyList(),
    val hrvTrendSeries: HrvTrendSeries? = null,
    val selectedHrvPosture: HrvPosture = HrvPosture.SUPINE,
    val selectedHrvMetricMode: HrvTrendMetricMode = HrvTrendMetricMode.LN_RMSSD,
    val showHrvTrainingLoad: Boolean = false,
    val hasHrvReadings: Boolean = false
)

private data class HrvCombinedState(
    val entries: List<HrvReadinessEntry> = emptyList(),
    val posture: HrvPosture? = null,
    val metricMode: HrvTrendMetricMode = HrvTrendMetricMode.LN_RMSSD,
    val showTrainingLoad: Boolean = false
)

private data class ProfileCombinedInputs(
    val workoutSamples: List<WorkoutTrainingLoadSample>,
    val tuningConfig: TuningConfig,
    val cardioState: Triple<List<CardioPowerTrendSample>, CardioTrendMetric, MetricsTimeRange>,
    val hrvState: HrvCombinedState
)

/**
 * ViewModel driving [AthleteProfileScreen] (`REQ-UI-391.5`, `REQ-UI-399.1`, `REQ-UI-401.3`, `REQ-UI-403`, `REQ-REV-011`).
 */
class AthleteProfileViewModel @JvmOverloads constructor(
    application: Application,
    private val workoutRepository: WorkoutRepository = WorkoutRepository.getInstance(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val repository = AthleteMetricsRepository.getInstance(application)
    private val settingsDataStore = SettingsDataStore.getInstance(application)
    private val tuningDataStore = TuningPreferencesDataStore(application)
    private val hrvReadinessRepo: HrvReadinessRepository = HrvReadinessRepository.getInstance(application)

    private val _selectedPlotMetric = MutableStateFlow(PlotMetricType.WEIGHT)
    private val _selectedTimeRange = MutableStateFlow(MetricsTimeRange.ALL)
    private val _selectedPmcTimeRange = MutableStateFlow(MetricsTimeRange.SIX_MONTHS)
    private val _selectedCardioTrendMetric = MutableStateFlow(CardioTrendMetric.HR_200W)
    private val _selectedCardioTimeRange = MutableStateFlow(MetricsTimeRange.ALL)
    private val _historyFilterMetric = MutableStateFlow<AthleteMetricType?>(null)
    private val _workoutLoadSamples = MutableStateFlow<List<WorkoutTrainingLoadSample>>(emptyList())
    private val _cardioTrendSamples = MutableStateFlow<List<CardioPowerTrendSample>>(emptyList())
    private val _hrMaxBpm = MutableStateFlow<Int?>(null)

    private val _hrvReadinessEntries = MutableStateFlow<List<HrvReadinessEntry>>(emptyList())
    private val _selectedHrvPosture = MutableStateFlow<HrvPosture?>(null)
    private val _selectedHrvMetricMode = MutableStateFlow(HrvTrendMetricMode.LN_RMSSD)
    private val _showHrvTrainingLoad = MutableStateFlow(false)

    private val hrvStateFlow = combine(
        _hrvReadinessEntries,
        _selectedHrvPosture,
        _selectedHrvMetricMode,
        _showHrvTrainingLoad
    ) { entries, posture, mode, showLoad ->
        HrvCombinedState(entries, posture, mode, showLoad)
    }

    private val cardioStateFlow = combine(
        _cardioTrendSamples,
        _selectedCardioTrendMetric,
        _selectedCardioTimeRange
    ) { samples, metric, range ->
        Triple(samples, metric, range)
    }

    val uiState: StateFlow<AthleteProfileUiState> = combine(
        repository.entriesFlow,
        _selectedPlotMetric,
        combine(_selectedTimeRange, _selectedPmcTimeRange) { tr, pmcTr -> tr to pmcTr },
        _historyFilterMetric,
        combine(_workoutLoadSamples, tuningDataStore.tuningConfigFlow, cardioStateFlow, hrvStateFlow) { samples, tuning, cardio, hrv ->
            ProfileCombinedInputs(samples, tuning, cardio, hrv)
        }
    ) { entries, rawPlotMetric, (timeRange, pmcTimeRange), rawHistoryFilter, combinedInputs ->
        val (workoutSamples, tuningConfig, cardioState, hrvState) = combinedInputs
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
            timeRange = pmcTimeRange,
            nowMs = nowMs
        )

        val visibleEntries = if (enableCp) {
            entries
        } else {
            entries.filter {
                it.metricType != AthleteMetricType.CP_WATTS && it.metricType != AthleteMetricType.W_PRIME_KJ
            }
        }

        val enableCardio = tuningConfig.enableCardioPowerDynamics
        val (cardioSamples, cardioMetric, cardioRange) = cardioState
        val (cardioPoints, cardioRollingPoints, cardioYDomain) = if (enableCardio && cardioSamples.isNotEmpty()) {
            val allPts = CardioPowerTrendsPlotMath.convertSamplesToPoints(cardioSamples, cardioMetric)
            val allRollingPts = CardioPowerTrendsPlotMath.computeRollingTrend(allPts, windowDays = 30)
            val filteredPts = CardioPowerTrendsPlotMath.filterPointsByTimeRange(allPts, cardioRange, nowMs)
            val rollingPts = CardioPowerTrendsPlotMath.filterPointsByTimeRange(allRollingPts, cardioRange, nowMs)
            val yDomain = CardioPowerTrendsPlotMath.computeOutlierResilientDomain(filteredPts.map { it.value }, cardioMetric)
            Triple(filteredPts, rollingPts, yDomain)
        } else {
            Triple(emptyList(), emptyList(), CardioPowerTrendsPlotMath.computeOutlierResilientDomain(emptyList(), cardioMetric))
        }

        val filteredHistory = if (historyFilter == null) {
            visibleEntries
        } else {
            visibleEntries.filter { it.metricType == historyFilter }
        }

        val hrvEntries = hrvState.entries
        val (defaultPosture, availablePostures) = HrvReadinessTrendMath.resolveAvailablePostures(hrvEntries)
        val postureToUse = if (hrvState.posture != null && (availablePostures.isEmpty() || availablePostures.contains(hrvState.posture))) {
            hrvState.posture
        } else {
            defaultPosture
        }

        val dailyLoads = mutableMapOf<String, Double>()
        for (sample in workoutSamples) {
            if (sample.timestampMs > 0L) {
                try {
                    val dateIso = java.time.Instant.ofEpochMilli(sample.timestampMs)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                    val load = sample.powerTss ?: if (sample.activeTimeSeconds > 0) (sample.activeTimeSeconds / 3600.0 * 50.0) else 0.0
                    if (load > 0.0) {
                        dailyLoads[dateIso] = (dailyLoads[dateIso] ?: 0.0) + load
                    }
                } catch (_: Exception) {}
            }
        }

        val hrvSeries = if (hrvEntries.isNotEmpty()) {
            HrvReadinessTrendMath.calculateTrendSeries(
                entries = hrvEntries,
                selectedPosture = postureToUse,
                metricMode = hrvState.metricMode,
                dailyTrainingLoads = dailyLoads
            )
        } else {
            null
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
            selectedPmcTimeRange = pmcTimeRange,
            plotPoints = plotPoints,
            pmcSummary = pmcSummary,
            pmcPlotPoints = pmcPlotPoints,
            historyFilterMetric = historyFilter,
            filteredHistory = filteredHistory,
            isImperial = isImperial,
            enablePmcModel = enablePmc,
            enableCriticalPowerModel = enableCp,
            enableCardioPowerDynamics = enableCardio,
            selectedCardioTrendMetric = cardioMetric,
            selectedCardioTimeRange = cardioRange,
            cardioTrendPoints = cardioPoints,
            cardioTrendRollingPoints = cardioRollingPoints,
            cardioTrendYDomain = cardioYDomain,
            hrvReadinessEntries = hrvEntries,
            hrvTrendSeries = hrvSeries,
            selectedHrvPosture = postureToUse,
            selectedHrvMetricMode = hrvState.metricMode,
            showHrvTrainingLoad = hrvState.showTrainingLoad,
            hasHrvReadings = hrvEntries.isNotEmpty()
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
        viewModelScope.launch(ioDispatcher) {
            workoutRepository.allWorkouts.collect {
                loadCardioTrendSamples()
                val samples = try {
                    workoutRepository.getWorkoutTrainingLoadRecords()
                } catch (_: Throwable) {
                    emptyList()
                }
                _workoutLoadSamples.value = samples
            }
        }
        viewModelScope.launch(ioDispatcher) {
            loadHrvData()
        }
    }

    fun loadHrvData() {
        viewModelScope.launch(ioDispatcher) {
            if (com.atrainingtracker.trainingtracker.TrainingApplication.isHealthConnectHrvImportEnabled()) {
                try {
                    com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectHrvImporter.importRecentHrv(
                        context = getApplication(),
                        daysBack = 14,
                        repository = hrvReadinessRepo
                    )
                } catch (e: Exception) {
                    android.util.Log.w("AthleteProfileVM", "Background Health Connect HRV refresh failed: ${e.message}")
                }
            }
            val entries = try {
                hrvReadinessRepo.getAllEntries()
            } catch (_: Throwable) {
                emptyList()
            }
            _hrvReadinessEntries.value = entries
        }
    }

    fun deleteHrvReading(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            hrvReadinessRepo.deleteEntry(id)
            loadHrvData()
        }
    }

    fun selectHrvPosture(posture: HrvPosture) {
        _selectedHrvPosture.value = posture
    }

    fun selectHrvMetricMode(mode: HrvTrendMetricMode) {
        _selectedHrvMetricMode.value = mode
    }

    fun toggleHrvTrainingLoad(show: Boolean) {
        _showHrvTrainingLoad.value = show
    }

    fun loadCardioTrendSamples() {
        viewModelScope.launch(ioDispatcher) {
            val samples = try {
                workoutRepository.getCardioPowerDynamicsFitnessTrendSamples(0.50)
            } catch (_: Throwable) {
                emptyList()
            }
            _cardioTrendSamples.value = samples
        }
    }

    fun selectCardioTrendMetric(metric: CardioTrendMetric) {
        _selectedCardioTrendMetric.value = metric
    }

    fun selectCardioTimeRange(range: MetricsTimeRange) {
        _selectedCardioTimeRange.value = range
    }

    fun selectPlotMetric(metric: PlotMetricType) {
        _selectedPlotMetric.value = metric
    }

    fun selectTimeRange(range: MetricsTimeRange) {
        _selectedTimeRange.value = range
        _selectedPmcTimeRange.value = range
        _selectedCardioTimeRange.value = range
    }

    fun selectPmcTimeRange(range: MetricsTimeRange) {
        _selectedPmcTimeRange.value = range
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
