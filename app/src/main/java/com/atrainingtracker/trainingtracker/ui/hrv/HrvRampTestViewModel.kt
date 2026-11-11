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
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityResult
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ViewModel orchestrating the guided DFA a1 progressive step and continuous ramp test protocols (`REQ-TRK-072`).
 *
 * Implements:
 * 1. Protocol lifecycle: CONFIG -> PRE_FLIGHT -> WARMUP -> TESTING -> EARLY_COMPLETION -> COOLDOWN -> FINISHED.
 * 2. Pre-flight sensor check evaluating RR artifact rate against strict HIGH (<= 3.0%), MODERATE (3.0-5.0%),
 *    and UNSUITABLE (> 5.0% or missing RR stream) quality tiers (`REQ-TRK-072.3`).
 * 3. Exact per-step pacing target increments and countdown timer (`REQ-TRK-072.1`, `REQ-TRK-072.4`).
 * 4. Automated sub-exhaustion early completion detection when live DFA a1 < 0.45 sustainably for >= 60 s (`REQ-TRK-072.5`).
 * 5. Session protocol tagging for downstream zero-bleed evaluation in HrvThresholdEstimator (`REQ-TRK-072.2`, `REQ-TRK-072.6`).
 */
class HrvRampTestViewModel @JvmOverloads constructor(
    application: Application,
    private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HrvRampTestUiState())
    val uiState: StateFlow<HrvRampTestUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var telemetryJob: Job? = null
    private val bufferedBeats = CopyOnWriteArrayList<RrBeatEvent>()

    private val rrBeatListener = RrBeatListener { event ->
        bufferedBeats.add(event)
        // Keep trailing 120 beats for rolling pre-flight assessment
        if (bufferedBeats.size > 180) {
            bufferedBeats.removeAt(0)
        }
    }

    init {
        // Collect live telemetry from BANALServiceRepository
        telemetryJob = viewModelScope.launch(dispatcher) {
            banalServiceRepository.allFilteredSensorData.collect { sensorDataList ->
                var hr: Int? = null
                var dfaA1: Double? = null
                var artifactRate: Double? = null

                for (data in sensorDataList) {
                    when (data.sensorType) {
                        SensorType.HR -> {
                            hr = (data.value as? Number)?.toInt()
                        }
                        SensorType.HRV_DFA_A1 -> {
                            dfaA1 = (data.value as? Number)?.toDouble()
                        }
                        SensorType.HRV_ARTIFACT_RATE -> {
                            artifactRate = (data.value as? Number)?.toDouble()
                        }
                        else -> { /* Ignore other sensor streams */ }
                    }
                }

                _uiState.update { current ->
                    current.copy(
                        currentHr = hr ?: current.currentHr,
                        currentDfaA1 = dfaA1 ?: current.currentDfaA1,
                        currentArtifactRate = artifactRate ?: current.currentArtifactRate
                    )
                }

                if (_uiState.value.phase == HrvTestPhase.PRE_FLIGHT) {
                    val hasRr = bufferedBeats.isNotEmpty() || artifactRate != null
                    val rate = artifactRate ?: calculateBufferedArtifactRate()
                    evaluatePreFlightQuality(
                        artifactRate = rate,
                        hasRrStream = hasRr,
                        isSmoothed = false
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Configuration Setters (CONFIG Phase)
    // ------------------------------------------------------------------------

    fun setSport(sport: HrvTestSport) {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        val (starting, increment) = when (sport) {
            HrvTestSport.CYCLING -> Pair(100.0, 15.0)
            HrvTestSport.RUNNING -> Pair(8.0, 0.5)
        }
        _uiState.update {
            it.copy(
                config = it.config.copy(
                    sport = sport,
                    startingTarget = starting,
                    stepIncrement = increment
                ),
                targetIntensity = starting
            )
        }
    }

    fun setProtocolType(protocolType: HrvTestProtocolType) {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        _uiState.update {
            it.copy(
                config = it.config.copy(protocolType = protocolType)
            )
        }
    }

    fun setStepDurationSec(stepDurationSec: Int) {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        val clamped = stepDurationSec.coerceAtLeast(180) // 3 min minimum
        _uiState.update {
            it.copy(
                config = it.config.copy(stepDurationSec = clamped),
                stepRemainingSec = clamped
            )
        }
    }

    fun setStartingTarget(startingTarget: Double) {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        _uiState.update {
            it.copy(
                config = it.config.copy(startingTarget = startingTarget),
                targetIntensity = startingTarget
            )
        }
    }

    fun setStepIncrement(stepIncrement: Double) {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        _uiState.update {
            it.copy(
                config = it.config.copy(stepIncrement = stepIncrement)
            )
        }
    }

    fun setWarmupDurationSec(warmupDurationSec: Int) {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        _uiState.update {
            it.copy(
                config = it.config.copy(warmupDurationSec = warmupDurationSec)
            )
        }
    }

    // ------------------------------------------------------------------------
    // Pre-Flight Sensor Assessment (PRE_FLIGHT Phase)
    // ------------------------------------------------------------------------

    fun startPreFlight() {
        if (_uiState.value.phase != HrvTestPhase.CONFIG) return
        banalServiceRepository.bindToBANALService()
        banalServiceRepository.startSearchingForPairedDevices()
        banalServiceRepository.addRrBeatListener(rrBeatListener)

        _uiState.update {
            it.copy(
                phase = HrvTestPhase.PRE_FLIGHT,
                preFlightMessageResId = R.string.hrv_test_preflight_checking,
                canStartTest = false
            )
        }
    }

    /**
     * Grades pre-flight sensor quality according to REQ-TRK-072.3:
     * - HIGH: ECG chest strap streaming beat-to-beat RR with artifacts <= 3.0%.
     * - MODERATE: Artifacts between 3.0% and 5.0% (warns that confidence is capped at MEDIUM).
     * - UNSUITABLE: Artifacts > 5.0%, firmware-smoothed stream, or no RR intervals (blocks start).
     */
    fun evaluatePreFlightQuality(
        artifactRate: Double?,
        hasRrStream: Boolean,
        isSmoothed: Boolean
    ) {
        val tier: SensorQualityTier
        val canStart: Boolean
        val messageResId: Int

        when {
            !hasRrStream -> {
                tier = SensorQualityTier.UNSUITABLE
                canStart = false
                messageResId = R.string.hrv_test_preflight_no_sensor
            }
            isSmoothed || (artifactRate != null && artifactRate > 5.0) -> {
                tier = SensorQualityTier.UNSUITABLE
                canStart = false
                messageResId = R.string.hrv_test_preflight_fail
            }
            artifactRate != null && artifactRate <= 3.0 -> {
                tier = SensorQualityTier.HIGH
                canStart = true
                messageResId = R.string.hrv_test_preflight_pass
            }
            else -> {
                tier = SensorQualityTier.MODERATE
                canStart = true
                messageResId = R.string.hrv_test_preflight_warning
            }
        }

        _uiState.update {
            it.copy(
                sensorQualityTier = tier,
                preFlightArtifactRate = artifactRate,
                preFlightMessageResId = messageResId,
                canStartTest = canStart
            )
        }
    }

    private fun calculateBufferedArtifactRate(): Double? {
        if (bufferedBeats.size < 10) return null
        val total = bufferedBeats.size
        val artifacts = bufferedBeats.count { it.artifactFlag != RrBeatEvent.ARTIFACT_NORMAL }
        return (artifacts.toDouble() / total.toDouble()) * 100.0
    }

    // ------------------------------------------------------------------------
    // Protocol Execution & Pacing Guidance (WARMUP / TESTING / COOLDOWN)
    // ------------------------------------------------------------------------

    fun startProtocol(autoStartTimer: Boolean = true) {
        val currentState = _uiState.value
        if (!currentState.canStartTest && currentState.phase != HrvTestPhase.CONFIG) {
            return
        }

        val hasWarmup = currentState.config.warmupDurationSec > 0
        val nextPhase = if (hasWarmup) HrvTestPhase.WARMUP else HrvTestPhase.TESTING
        val remaining = if (hasWarmup) currentState.config.warmupDurationSec else currentState.config.stepDurationSec

        _uiState.update {
            it.copy(
                phase = nextPhase,
                currentStepNumber = 1,
                targetIntensity = it.config.startingTarget,
                stepElapsedSec = 0,
                stepRemainingSec = remaining,
                totalElapsedSec = 0,
                sub45DurationSec = 0,
                earlyCompletionDetected = false,
                isCompleted = false
            )
        }

        if (autoStartTimer) {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                delay(1000L)
                processTick()
            }
        }
    }

    /**
     * Advances protocol state machine by 1 second. Exposed for deterministic unit testing.
     */
    fun processTick(
        injectedDfaA1: Double? = null,
        injectedHr: Int? = null,
        injectedArtifactRate: Double? = null
    ) {
        _uiState.update { state ->
            val dfaA1 = injectedDfaA1 ?: state.currentDfaA1
            val hr = injectedHr ?: state.currentHr
            val artifactRate = injectedArtifactRate ?: state.currentArtifactRate

            var currentPhase = state.phase
            var currentStep = state.currentStepNumber
            var target = state.targetIntensity
            var stepElapsed = state.stepElapsedSec + 1
            var stepRemaining = state.stepRemainingSec - 1
            val totalElapsed = state.totalElapsedSec + 1
            var sub45Duration = state.sub45DurationSec
            var earlyDetected = state.earlyCompletionDetected
            var completed = state.isCompleted

            when (currentPhase) {
                HrvTestPhase.WARMUP -> {
                    if (stepRemaining <= 0) {
                        currentPhase = HrvTestPhase.TESTING
                        currentStep = 1
                        stepElapsed = 0
                        stepRemaining = state.config.stepDurationSec
                        target = state.config.startingTarget
                    }
                }
                HrvTestPhase.TESTING -> {
                    // 1. Pacing progression
                    if (state.config.protocolType == HrvTestProtocolType.GUIDED_STEP) {
                        if (stepRemaining <= 0) {
                            currentStep += 1
                            target += state.config.stepIncrement
                            stepElapsed = 0
                            stepRemaining = state.config.stepDurationSec
                        }
                    } else if (state.config.protocolType == HrvTestProtocolType.GUIDED_RAMP) {
                        target = state.config.startingTarget + (stepElapsed / 60.0) * state.config.rampSlopeWattsPerMin
                    }

                    // 2. Sub-exhaustion early completion detection (REQ-TRK-072.5)
                    if (dfaA1 != null && dfaA1 < 0.45) {
                        sub45Duration += 1
                        if (sub45Duration >= 60) {
                            earlyDetected = true
                            currentPhase = HrvTestPhase.EARLY_COMPLETION
                            timerJob?.cancel()
                        }
                    } else {
                        sub45Duration = 0
                    }
                }
                HrvTestPhase.EARLY_COMPLETION -> {
                    // Waiting for athlete acknowledgement to transition to cool-down
                }
                HrvTestPhase.COOLDOWN -> {
                    if (stepRemaining <= 0) {
                        currentPhase = HrvTestPhase.FINISHED
                        completed = true
                        timerJob?.cancel()
                    }
                }
                HrvTestPhase.FINISHED, HrvTestPhase.CONFIG, HrvTestPhase.PRE_FLIGHT -> {
                    /* No timer progression */
                }
            }

            state.copy(
                phase = currentPhase,
                currentStepNumber = currentStep,
                targetIntensity = target,
                stepElapsedSec = stepElapsed,
                stepRemainingSec = stepRemaining.coerceAtLeast(0),
                totalElapsedSec = totalElapsed,
                currentHr = hr,
                currentDfaA1 = dfaA1,
                currentArtifactRate = artifactRate,
                sub45DurationSec = sub45Duration,
                earlyCompletionDetected = earlyDetected,
                isCompleted = completed
            )
        }
    }

    fun transitionToCooldown() {
        val recoveryTarget = (_uiState.value.config.startingTarget * 0.5).coerceAtLeast(0.0)
        _uiState.update {
            it.copy(
                phase = HrvTestPhase.COOLDOWN,
                targetIntensity = recoveryTarget,
                stepElapsedSec = 0,
                stepRemainingSec = it.config.cooldownDurationSec
            )
        }
    }

    fun finishTest() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                phase = HrvTestPhase.FINISHED,
                isCompleted = true
            )
        }
    }

    fun cancelTest() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                phase = HrvTestPhase.CONFIG,
                isCompleted = false,
                earlyCompletionDetected = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        telemetryJob?.cancel()
        banalServiceRepository.removeRrBeatListener(rrBeatListener)
    }
}
