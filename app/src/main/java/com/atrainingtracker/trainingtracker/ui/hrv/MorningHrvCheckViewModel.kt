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

package com.atrainingtracker.trainingtracker.ui.hrv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.devices.hrv.FilteredRrBeat
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityAnalyzer
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityResult
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.banalservice.devices.hrv.RrArtifactFilter
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessResult
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Protocol lifecycle phases for guided morning resting HRV acquisition (`REQ-PER-032.1`).
 */
enum class MorningHrvPhase {
    READY,
    STABILIZING,
    MEASURING,
    COMPLETED
}

/**
 * UI State container for the morning HRV measurement screen (`REQ-PER-032`).
 */
data class MorningHrvUiState(
    val phase: MorningHrvPhase = MorningHrvPhase.READY,
    val posture: HrvPosture = HrvPosture.SUPINE,
    val durationSec: Int = 60,
    val remainingSec: Int = 0,
    val stabilizationDurationSec: Int = 15,
    val currentHeartRateBpm: Int? = null,
    val beatCount: Int = 0,
    val artifactRatePct: Double? = null,
    val highArtifactWarning: Boolean = false,
    val signalQualityStatus: HrvSignalQualityStatus = HrvSignalQualityStatus.INSUFFICIENT_DATA,
    val signalQualityResult: HrvSignalQualityResult? = null,
    val computedEntry: HrvReadinessEntry? = null,
    val readinessResult: HrvReadinessResult? = null,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel orchestrating the standardized waking resting HRV measurement protocol (`REQ-PER-032`).
 *
 * Implements strict zero-workout generation (never touches TrackerService or WorkoutSummaries.db),
 * sticky posture preferences, post-hoc centered filtering via [RrArtifactFilter], and SWC readiness persistence.
 */
class MorningHrvCheckViewModel @JvmOverloads constructor(
    application: Application,
    private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application),
    private val hrvReadinessRepository: HrvReadinessRepository = HrvReadinessRepository.getInstance(application),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : AndroidViewModel(application) {

    private val bufferedBeats = CopyOnWriteArrayList<RrBeatEvent>()
    private val artifactFilter = RrArtifactFilter()
    private var timerJob: Job? = null

    private val _uiState: MutableStateFlow<MorningHrvUiState>
    val uiState: StateFlow<MorningHrvUiState>

    private val rrBeatListener = RrBeatListener { event ->
        handleRrBeat(event)
    }

    init {
        val initialPosture = HrvPosture.fromNameOrNull(TrainingApplication.getLastHrvPosture())
            ?: HrvPosture.SUPINE

        _uiState = MutableStateFlow(
            MorningHrvUiState(
                posture = initialPosture,
                durationSec = DURATION_STANDARD_SEC,
                stabilizationDurationSec = DEFAULT_STABILIZATION_SEC
            )
        )
        uiState = _uiState.asStateFlow()

        banalServiceRepository.bindToBANALService()
        banalServiceRepository.addRrBeatListener(rrBeatListener)
    }

    /**
     * Updates the posture preference and persists it in sticky shared preferences (`REQ-PER-032.2`).
     */
    fun setPosture(posture: HrvPosture) {
        if (_uiState.value.phase == MorningHrvPhase.READY) {
            TrainingApplication.setLastHrvPosture(posture.name)
            _uiState.update { it.copy(posture = posture) }
        }
    }

    /**
     * Configures measurement duration (60s standard vs 120s extended) (`REQ-PER-032.1`).
     */
    fun setDurationSec(durationSec: Int) {
        if (_uiState.value.phase == MorningHrvPhase.READY) {
            _uiState.update { it.copy(durationSec = durationSec) }
        }
    }

    /**
     * Configures stabilization duration in seconds (primarily for testing or fast verification).
     */
    fun setStabilizationDurationSec(stabilizationDurationSec: Int) {
        if (_uiState.value.phase == MorningHrvPhase.READY) {
            _uiState.update { it.copy(stabilizationDurationSec = stabilizationDurationSec) }
        }
    }

    /**
     * Starts the standardized measurement protocol (`REQ-PER-032.1`).
     * Transitions: READY -> STABILIZING -> MEASURING -> COMPLETED.
     */
    fun startMeasurement() {
        if (_uiState.value.phase != MorningHrvPhase.READY && _uiState.value.phase != MorningHrvPhase.COMPLETED) {
            return
        }

        timerJob?.cancel()
        bufferedBeats.clear()

        _uiState.update {
            it.copy(
                phase = MorningHrvPhase.STABILIZING,
                remainingSec = it.stabilizationDurationSec,
                beatCount = 0,
                artifactRatePct = null,
                highArtifactWarning = false,
                signalQualityStatus = HrvSignalQualityStatus.INSUFFICIENT_DATA,
                signalQualityResult = null,
                computedEntry = null,
                readinessResult = null,
                isSaved = false,
                errorMessage = null
            )
        }

        timerJob = viewModelScope.launch(dispatcher) {
            // Phase 1: Stabilization Countdown
            val stabSec = _uiState.value.stabilizationDurationSec
            for (sec in stabSec downTo 1) {
                _uiState.update { it.copy(remainingSec = sec) }
                delay(1000L)
            }

            // Phase 2: Active Measurement
            val targetSec = _uiState.value.durationSec
            bufferedBeats.clear()
            _uiState.update {
                it.copy(
                    phase = MorningHrvPhase.MEASURING,
                    remainingSec = targetSec,
                    beatCount = 0
                )
            }

            for (sec in targetSec downTo 1) {
                _uiState.update { it.copy(remainingSec = sec) }
                delay(1000L)
            }

            // Phase 3: Completion and Post-Hoc Centered Filtering
            completeMeasurement()
        }
    }

    /**
     * Cancels the active measurement and returns to READY state (`REQ-PER-032.1`).
     */
    fun cancelMeasurement() {
        timerJob?.cancel()
        timerJob = null
        bufferedBeats.clear()
        _uiState.update {
            it.copy(
                phase = MorningHrvPhase.READY,
                remainingSec = 0,
                beatCount = 0,
                signalQualityStatus = HrvSignalQualityStatus.INSUFFICIENT_DATA,
                signalQualityResult = null,
                errorMessage = null
            )
        }
    }

    /**
     * Resets the screen state back to READY after completion or discard.
     */
    fun resetMeasurement() {
        cancelMeasurement()
    }

    /**
     * Handles real-time RR beat events from [banalServiceRepository] (`REQ-PER-032.3`).
     */
    fun handleRrBeat(event: RrBeatEvent) {
        val hr = if (event.rrMs > 0.0) (60000.0 / event.rrMs).roundToInt() else null

        if (_uiState.value.phase == MorningHrvPhase.MEASURING) {
            bufferedBeats.add(event)
            _uiState.update {
                it.copy(
                    currentHeartRateBpm = hr,
                    beatCount = bufferedBeats.size
                )
            }
        } else {
            _uiState.update { it.copy(currentHeartRateBpm = hr) }
        }
    }

    /**
     * Post-hoc full-window centered filtering and readiness calculation (`REQ-PER-032.4`).
     */
    fun completeMeasurement() {
        val beats = bufferedBeats.toList()
        if (beats.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = MorningHrvPhase.READY,
                    errorMessage = "No RR intervals recorded. Please check heart rate sensor connection."
                )
            }
            return
        }

        val filterResult = artifactFilter.filterBidirectionalBeats(beats)
        val filteredBeats = filterResult.beats
        val artifactRate = filterResult.artifactRatePercent
        val isHighArtifact = artifactRate > ARTIFACT_THRESHOLD_PCT

        val rmssdMs = calculateRmssd(filteredBeats)
        val lnRmssd = if (rmssdMs > 0.0) ln(rmssdMs) else 0.0
        val hrvScore100 = (20.0 * lnRmssd).coerceIn(0.0, 100.0)
        val sdnnMs = calculateSdnn(filteredBeats)

        val normalRrs = filteredBeats.filter { !it.isArtifact }.map { it.rawRrMs }
        val hrSourceRrs = if (normalRrs.isNotEmpty()) normalRrs else filteredBeats.map { it.rawRrMs }
        val meanRr = if (hrSourceRrs.isNotEmpty()) hrSourceRrs.average() else 1000.0
        val restingHrBpm = if (meanRr > 0.0) (60000.0 / meanRr).roundToInt() else 60

        val nowMs = System.currentTimeMillis()
        val dateIso = LocalDate.now().toString()
        val timezoneId = ZoneId.systemDefault().id

        val qualityResult = HrvSignalQualityAnalyzer.analyzeBeats(
            beats = filteredBeats,
            artifactRatePercent = artifactRate,
            rmssdMs = rmssdMs
        )

        val entry = HrvReadinessEntry(
            timestampMs = nowMs,
            dateIso = dateIso,
            timezoneId = timezoneId,
            restingHrBpm = restingHrBpm,
            rmssdMs = rmssdMs,
            lnRmssd = lnRmssd,
            hrvScore100 = hrvScore100,
            sdnnMs = sdnnMs,
            artifactRatePct = artifactRate,
            durationSec = _uiState.value.durationSec,
            posture = _uiState.value.posture,
            isPrimaryForDay = true
        )

        _uiState.update {
            it.copy(
                phase = MorningHrvPhase.COMPLETED,
                remainingSec = 0,
                artifactRatePct = artifactRate,
                highArtifactWarning = isHighArtifact,
                signalQualityStatus = qualityResult.status,
                signalQualityResult = qualityResult,
                computedEntry = entry,
                isSaved = false
            )
        }

        viewModelScope.launch(dispatcher) {
            val initialResult = hrvReadinessRepository.calculateReadiness(dateIso, entry.posture)
            _uiState.update {
                it.copy(readinessResult = initialResult)
            }
        }
    }

    /**
     * Persists the computed reading to `HrvReadiness.db` and recalculates baseline readiness (`REQ-PER-032.5`).
     */
    suspend fun saveReading(): Long {
        val entry = _uiState.value.computedEntry ?: return -1L
        val rowId = hrvReadinessRepository.insertEntry(entry)
        if (rowId > 0) {
            val updatedResult = hrvReadinessRepository.calculateReadiness(entry.dateIso, entry.posture)
            _uiState.update {
                it.copy(
                    isSaved = true,
                    readinessResult = updatedResult
                )
            }
        }
        return rowId
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        banalServiceRepository.removeRrBeatListener(rrBeatListener)
        banalServiceRepository.unbindFromBANALService()
    }

    companion object {
        const val ARTIFACT_THRESHOLD_PCT = 5.0
        const val DEFAULT_STABILIZATION_SEC = 15
        const val DURATION_STANDARD_SEC = 60
        const val DURATION_EXTENDED_SEC = 120

        /**
         * Computes bias-free rMSSD by excluding successive pairs adjacent to artifacts (`REQ-PER-032.4`).
         */
        @JvmStatic
        fun calculateRmssd(beats: List<FilteredRrBeat>): Double {
            if (beats.size < 2) return 0.0
            var sumSquaredDiffs = 0.0
            var validPairCount = 0

            for (i in 0 until beats.size - 1) {
                val b1 = beats[i]
                val b2 = beats[i + 1]
                if (!b1.isArtifact && !b2.isArtifact) {
                    val diff = b2.rawRrMs - b1.rawRrMs
                    sumSquaredDiffs += diff * diff
                    validPairCount++
                }
            }

            return if (validPairCount > 0) sqrt(sumSquaredDiffs / validPairCount.toDouble()) else 0.0
        }

        /**
         * Computes unbiased sample standard deviation of normal-to-normal intervals (SDNN) (`REQ-PER-032.4`).
         */
        @JvmStatic
        fun calculateSdnn(beats: List<FilteredRrBeat>): Double? {
            val normalRrs = beats.filter { !it.isArtifact }.map { it.rawRrMs }
            if (normalRrs.size < 2) return null
            val mean = normalRrs.average()
            val sumSquaredDiffs = normalRrs.sumOf { (it - mean) * (it - mean) }
            return sqrt(sumSquaredDiffs / (normalRrs.size - 1).toDouble())
        }
    }
}
