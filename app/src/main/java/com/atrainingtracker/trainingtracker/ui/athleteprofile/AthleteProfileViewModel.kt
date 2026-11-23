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
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

/**
 * Isolated tab UI state for Tab 1 (Body & Thresholds) (`REQ-UI-2803.3`).
 */
data class AthleteBodyTabUiState(
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
    val isImperial: Boolean = false,
    val enableCriticalPowerModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CRITICAL_POWER_MODEL
)

/**
 * Isolated tab UI state for Tab 2 (Banister Performance Management Chart) (`REQ-UI-2803.3`).
 */
data class AthletePmcTabUiState(
    val enablePmcModel: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_PMC_MODEL,
    val selectedPmcTimeRange: MetricsTimeRange = MetricsTimeRange.SIX_MONTHS,
    val pmcSummary: PmcSummaryResult = PmcSummaryResult(),
    val pmcPlotPoints: List<PmcDailyPoint> = emptyList()
)

/**
 * Isolated tab UI state for Tab 3 (HRV & Readiness) (`REQ-UI-2803.3`).
 */
data class AthleteHrvTabUiState(
    val hrvReadinessEntries: List<HrvReadinessEntry> = emptyList(),
    val hrvTrendSeries: HrvTrendSeries? = null,
    val selectedHrvPosture: HrvPosture = HrvPosture.SUPINE,
    val selectedHrvMetricMode: HrvTrendMetricMode = HrvTrendMetricMode.LN_RMSSD,
    val showHrvTrainingLoad: Boolean = false,
    val hasHrvReadings: Boolean = false
)

/**
 * Isolated tab UI state for Tab 4 (Cardio-Power Dynamics) (`REQ-UI-2803.3`).
 */
data class AthleteCardioTabUiState(
    val enableCardioPowerDynamics: Boolean = TuningPreferencesDefaults.DEFAULT_ENABLE_CARDIO_POWER_DYNAMICS,
    val selectedCardioTrendMetric: CardioTrendMetric = CardioTrendMetric.HR_200W,
    val selectedCardioTimeRange: MetricsTimeRange = MetricsTimeRange.ALL,
    val cardioTrendPoints: List<CardioPlotPoint> = emptyList(),
    val cardioTrendRollingPoints: List<CardioPlotPoint> = emptyList(),
    val cardioTrendYDomain: Pair<Double, Double> = 110.0 to 170.0
)

/**
 * ViewModel driving [AthleteProfileScreen] (`REQ-UI-391.5`, `REQ-UI-399.1`, `REQ-UI-401.3`, `REQ-UI-403`, `REQ-REV-011`, `REQ-UI-2803.3`).
 *
 * Exposes 4 independent tab-specific [StateFlow]s ([bodyUiState], [pmcUiState], [hrvUiState],
 * [cardioUiState]) so UI interactions on one tab never trigger recomputation of unrelated tabs
 * (in particular, Banister PMC only recomputes when workout training-load samples, latest
 * FTP / resting HR thresholds, or reactive `_hrMaxBpm` change). [uiState] projects the 4
 * precomputed tab states with zero extra math.
 */
class AthleteProfileViewModel @JvmOverloads constructor(
    application: Application,
    private val workoutRepository: WorkoutRepository = WorkoutRepository.getInstance(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val repository = AthleteMetricsRepository.getInstance(application)
    private val settingsDataStore = SettingsDataStore.getInstance(application)
    private val tuningDataStore = TuningPreferencesDataStore.getInstance(application)
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
    // Channel.CONFLATED retains 1 pending refresh token even before the consumer starts on ioDispatcher,
    // eliminating cold-start lost-wakeup races while serializing & conflating concurrent analytics reloads.
    private val analyticsRefreshTriggers = Channel<Unit>(Channel.CONFLATED)

    private val _hrvReadinessEntries = MutableStateFlow<List<HrvReadinessEntry>>(emptyList())
    private val _selectedHrvPosture = MutableStateFlow<HrvPosture?>(null)
    private val _selectedHrvMetricMode = MutableStateFlow(HrvTrendMetricMode.LN_RMSSD)
    private val _showHrvTrainingLoad = MutableStateFlow(false)

    private val enableCriticalPowerFlow = tuningDataStore.tuningConfigFlow
        .map { it.enableCriticalPowerModel }
        .distinctUntilChanged()

    private val enablePmcFlow = tuningDataStore.tuningConfigFlow
        .map { it.enablePmcModel }
        .distinctUntilChanged()

    private val enableCardioFlow = tuningDataStore.tuningConfigFlow
        .map { it.enableCardioPowerDynamics }
        .distinctUntilChanged()

    private val latestFtpAndHrRestFlow = repository.entriesFlow
        .map { entries ->
            val ftp = entries.firstOrNull { it.metricType == AthleteMetricType.FTP_WATTS }?.value
            val hrRest = entries.firstOrNull { it.metricType == AthleteMetricType.HR_REST_BPM }?.value
            ftp to hrRest
        }
        .distinctUntilChanged()

    private val dailyTrainingLoadsFlow = _workoutLoadSamples.map { workoutSamples ->
        buildDailyTrainingLoadsMap(workoutSamples)
    }

    /** Tab 1 (Body & Thresholds) isolated state flow (`REQ-UI-2803.3`). */
    val bodyUiState: StateFlow<AthleteBodyTabUiState> = combine(
        repository.entriesFlow,
        _selectedPlotMetric,
        _selectedTimeRange,
        _historyFilterMetric,
        enableCriticalPowerFlow
    ) { entries, rawPlotMetric, timeRange, rawHistoryFilter, enableCp ->
        val isImperial = try {
            TrainingApplication.getUnit() == MyUnits.IMPERIAL
        } catch (_: Throwable) {
            false
        }
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

        AthleteBodyTabUiState(
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
            isImperial = isImperial,
            enableCriticalPowerModel = enableCp
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AthleteBodyTabUiState()
    )

    // Banister PMC summary strictly observes physiological load & threshold inputs (incl. reactive _hrMaxBpm per Rule 39).
    private val pmcSummaryFlow = combine(
        _workoutLoadSamples,
        latestFtpAndHrRestFlow,
        _hrMaxBpm
    ) { workoutSamples, (ftpWatts, hrRestBpm), hrMaxBpm ->
        PowerPhysiologyCalculator.computePerformanceManagementChart(
            workoutSamples = workoutSamples,
            nowMs = System.currentTimeMillis(),
            ftpWatts = ftpWatts,
            hrRestBpm = hrRestBpm,
            hrMaxBpm = hrMaxBpm?.toDouble(),
            zoneId = java.time.ZoneId.systemDefault()
        )
    }

    /** Tab 2 (Banister PMC) isolated state flow (`REQ-UI-2803.3`). */
    val pmcUiState: StateFlow<AthletePmcTabUiState> = combine(
        pmcSummaryFlow,
        _selectedPmcTimeRange,
        enablePmcFlow
    ) { pmcSummary, pmcTimeRange, enablePmc ->
        val pmcPlotPoints = AthleteMetricsPlotMath.filterPmcPointsByTimeRange(
            points = pmcSummary.dailyPoints,
            timeRange = pmcTimeRange,
            nowMs = System.currentTimeMillis()
        )
        AthletePmcTabUiState(
            enablePmcModel = enablePmc,
            selectedPmcTimeRange = pmcTimeRange,
            pmcSummary = pmcSummary,
            pmcPlotPoints = pmcPlotPoints
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AthletePmcTabUiState()
    )

    /** Tab 3 (HRV & Readiness) isolated state flow (`REQ-UI-2803.3`). */
    val hrvUiState: StateFlow<AthleteHrvTabUiState> = combine(
        _hrvReadinessEntries,
        _selectedHrvPosture,
        _selectedHrvMetricMode,
        _showHrvTrainingLoad,
        dailyTrainingLoadsFlow
    ) { hrvEntries, selectedPosture, metricMode, showTrainingLoad, dailyLoads ->
        val (defaultPosture, availablePostures) = HrvReadinessTrendMath.resolveAvailablePostures(hrvEntries)
        val postureToUse = if (selectedPosture != null && (availablePostures.isEmpty() || availablePostures.contains(selectedPosture))) {
            selectedPosture
        } else {
            defaultPosture
        }
        val hrvSeries = if (hrvEntries.isNotEmpty()) {
            HrvReadinessTrendMath.calculateTrendSeries(
                entries = hrvEntries,
                selectedPosture = postureToUse,
                metricMode = metricMode,
                dailyTrainingLoads = dailyLoads
            )
        } else {
            null
        }
        AthleteHrvTabUiState(
            hrvReadinessEntries = hrvEntries,
            hrvTrendSeries = hrvSeries,
            selectedHrvPosture = postureToUse,
            selectedHrvMetricMode = metricMode,
            showHrvTrainingLoad = showTrainingLoad,
            hasHrvReadings = hrvEntries.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AthleteHrvTabUiState()
    )

    /** Tab 4 (Cardio-Power Dynamics) isolated state flow (`REQ-UI-2803.3`). */
    val cardioUiState: StateFlow<AthleteCardioTabUiState> = combine(
        _cardioTrendSamples,
        _selectedCardioTrendMetric,
        _selectedCardioTimeRange,
        enableCardioFlow
    ) { cardioSamples, cardioMetric, cardioRange, enableCardio ->
        val nowMs = System.currentTimeMillis()
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
        AthleteCardioTabUiState(
            enableCardioPowerDynamics = enableCardio,
            selectedCardioTrendMetric = cardioMetric,
            selectedCardioTimeRange = cardioRange,
            cardioTrendPoints = cardioPoints,
            cardioTrendRollingPoints = cardioRollingPoints,
            cardioTrendYDomain = cardioYDomain
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AthleteCardioTabUiState()
    )

    /**
     * Unified read-only projection assembling the 4 precomputed tab-specific [StateFlow]s with
     * zero cross-tab recomputation (`REQ-UI-2803.3`).
     */
    val uiState: StateFlow<AthleteProfileUiState> = combine(
        bodyUiState,
        pmcUiState,
        hrvUiState,
        cardioUiState
    ) { body, pmc, hrv, cardio ->
        AthleteProfileUiState(
            latestWeightEntry = body.latestWeightEntry,
            latestHrRestEntry = body.latestHrRestEntry,
            latestFtpEntry = body.latestFtpEntry,
            latestCpEntry = body.latestCpEntry,
            latestWPrimeEntry = body.latestWPrimeEntry,
            currentPowerToWeight = body.currentPowerToWeight,
            latestPowerToWeightTimestampMs = body.latestPowerToWeightTimestampMs,
            selectedPlotMetric = body.selectedPlotMetric,
            selectedTimeRange = body.selectedTimeRange,
            selectedPmcTimeRange = pmc.selectedPmcTimeRange,
            plotPoints = body.plotPoints,
            pmcSummary = pmc.pmcSummary,
            pmcPlotPoints = pmc.pmcPlotPoints,
            historyFilterMetric = body.historyFilterMetric,
            filteredHistory = body.filteredHistory,
            isImperial = body.isImperial,
            enablePmcModel = pmc.enablePmcModel,
            enableCriticalPowerModel = body.enableCriticalPowerModel,
            enableCardioPowerDynamics = cardio.enableCardioPowerDynamics,
            selectedCardioTrendMetric = cardio.selectedCardioTrendMetric,
            selectedCardioTimeRange = cardio.selectedCardioTimeRange,
            cardioTrendPoints = cardio.cardioTrendPoints,
            cardioTrendRollingPoints = cardio.cardioTrendRollingPoints,
            cardioTrendYDomain = cardio.cardioTrendYDomain,
            hrvReadinessEntries = hrv.hrvReadinessEntries,
            hrvTrendSeries = hrv.hrvTrendSeries,
            selectedHrvPosture = hrv.selectedHrvPosture,
            selectedHrvMetricMode = hrv.selectedHrvMetricMode,
            showHrvTrainingLoad = hrv.showHrvTrainingLoad,
            hasHrvReadings = hrv.hasHrvReadings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AthleteProfileUiState()
    )

    private fun buildDailyTrainingLoadsMap(workoutSamples: List<WorkoutTrainingLoadSample>): Map<String, Double> {
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
        return dailyLoads
    }

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
            for (trigger in analyticsRefreshTriggers) {
                reloadWorkoutAnalytics()
            }
        }
        viewModelScope.launch(ioDispatcher) {
            // REQ-STB-020.4: Only finished workout changes trigger historical cardio/load SQLite queries.
            // Tracks post-finish analytics/backfill fields (rpe, tss, normalizedPowerWatts, polarSyncPending,
            // cardioPowerDynamics scalars, HR/POWER AVG extrema) in addition to core workout identity/totals.
            val hrShortLabel = runCatching { SensorType.HR.getShortName(getApplication<Application>()) }.getOrNull()
            val powerShortLabel = runCatching { SensorType.POWER.getShortName(getApplication<Application>()) }.getOrNull()
            workoutRepository.allWorkouts
                .map { workouts ->
                    workouts.filter { it.finished }.map { workout ->
                        workout.toFinishedSyncKey(hrShortLabel = hrShortLabel, powerShortLabel = powerShortLabel)
                    }
                }
                .distinctUntilChanged()
                .collect {
                    analyticsRefreshTriggers.trySend(Unit)
                }
        }
        viewModelScope.launch(ioDispatcher) {
            // REQ-DAT-049.4: Authoritative post-commit deletion/finalization notifications ensure PMC,
            // Cardio-Power Trends, and HRV daily training loads recalculate even if allWorkouts is empty.
            workoutRepository.workoutMutationEvents.collect {
                analyticsRefreshTriggers.trySend(Unit)
            }
        }
        viewModelScope.launch(ioDispatcher) {
            loadHrvData()
        }
    }

    private fun reloadWorkoutAnalytics() {
        val cardioSamples = try {
            workoutRepository.getCardioPowerDynamicsFitnessTrendSamples(0.50)
        } catch (_: Throwable) {
            emptyList()
        }
        _cardioTrendSamples.value = cardioSamples
        val loadSamples = try {
            workoutRepository.getWorkoutTrainingLoadRecords()
        } catch (_: Throwable) {
            emptyList()
        }
        _workoutLoadSamples.value = loadSamples
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
        analyticsRefreshTriggers.trySend(Unit)
    }

    override fun onCleared() {
        super.onCleared()
        analyticsRefreshTriggers.close()
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
        if (_selectedTimeRange.value != range) {
            _selectedTimeRange.value = range
        }
        if (_selectedPmcTimeRange.value != range) {
            _selectedPmcTimeRange.value = range
        }
        if (_selectedCardioTimeRange.value != range) {
            _selectedCardioTimeRange.value = range
        }
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

    private fun WorkoutData.toFinishedSyncKey(
        hrShortLabel: String?,
        powerShortLabel: String?
    ): FinishedWorkoutSyncKey {
        val cpd: CardioPowerDynamicsRecord? = cardioPowerDynamics
        val rows: List<ExtremaDataRow> = extremaRows.orEmpty()
        return FinishedWorkoutSyncKey(
            id = id,
            finished = finished,
            startTimeS = startTimeS,
            activeTimeSec = activeTimeSec,
            totalTimeSec = totalTimeSec,
            totalDistance = totalDistance,
            sportId = sportId,
            bSportType = bSportType,
            rpe = rpe,
            tss = tss,
            normalizedPowerWatts = normalizedPowerWatts,
            polarSyncPending = polarSyncPending,
            cardioTauStart = cpd?.tauStart,
            cardioHr0Start = cpd?.hr0Start,
            cardioKStart = cpd?.kStart,
            cardioHr200Start = cpd?.hr200Start,
            cardioR2Start = cpd?.r2Start,
            cardioRmseStart = cpd?.rmseStart,
            cardioTauEnd = cpd?.tauEnd,
            cardioHr0End = cpd?.hr0End,
            cardioKEnd = cpd?.kEnd,
            cardioHr200End = cpd?.hr200End,
            cardioR2End = cpd?.r2End,
            cardioRmseEnd = cpd?.rmseEnd,
            hrAvg = rows.findExtremaAvg(R.drawable.ic_heart_rate, "HR", hrShortLabel),
            powerAvg = rows.findExtremaAvg(R.drawable.ic_power, "Power", powerShortLabel)
        )
    }

    private fun List<ExtremaDataRow>.findExtremaAvg(
        iconResId: Int,
        canonicalFallbackLabel: String,
        localizedLabel: String?
    ): String? {
        return firstOrNull { row ->
            row.iconResId == iconResId ||
                row.sensorLabel.equals(canonicalFallbackLabel, ignoreCase = true) ||
                (!localizedLabel.isNullOrEmpty() && row.sensorLabel == localizedLabel)
        }?.avgValue
    }

    private data class FinishedWorkoutSyncKey(
        val id: Long,
        val finished: Boolean,
        val startTimeS: Long,
        val activeTimeSec: Long,
        val totalTimeSec: Long,
        val totalDistance: Double,
        val sportId: Long,
        val bSportType: BSportType,
        val rpe: Int?,
        val tss: Double?,
        val normalizedPowerWatts: Double?,
        val polarSyncPending: Boolean,
        val cardioTauStart: Double?,
        val cardioHr0Start: Double?,
        val cardioKStart: Double?,
        val cardioHr200Start: Double?,
        val cardioR2Start: Double?,
        val cardioRmseStart: Double?,
        val cardioTauEnd: Double?,
        val cardioHr0End: Double?,
        val cardioKEnd: Double?,
        val cardioHr200End: Double?,
        val cardioR2End: Double?,
        val cardioRmseEnd: Double?,
        val hrAvg: String?,
        val powerAvg: String?
    )
}
