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

/**
 * Supported athletic disciplines for the guided DFA a1 threshold protocol (`REQ-TRK-072.1`).
 */
enum class HrvTestSport {
    CYCLING,
    RUNNING
}

/**
 * Protocol progression profile (`REQ-TRK-072.1`).
 */
enum class HrvTestProtocolType {
    GUIDED_STEP,
    GUIDED_RAMP
}

/**
 * Lifecycle phase states for the guided threshold test protocol (`REQ-TRK-072.1`, `REQ-TRK-072.4`).
 */
enum class HrvTestPhase {
    CONFIG,
    PRE_FLIGHT,
    WARMUP,
    TESTING,
    EARLY_COMPLETION,
    COOLDOWN,
    FINISHED
}

/**
 * Pre-flight ECG chest strap signal quality grading (`REQ-TRK-072.3`).
 */
enum class SensorQualityTier {
    HIGH,
    MODERATE,
    UNSUITABLE
}

/**
 * Immutable configuration parameters for the guided DFA a1 test protocol (`REQ-TRK-072.1`).
 *
 * @param sport Target sport (Cycling in Watts, Running in Speed/Pace).
 * @param protocolType Step progression vs continuous linear ramp.
 * @param warmupDurationSec Duration of steady warm-up before protocol steps (default 600 s / 10 min).
 * @param stepDurationSec Duration per step in [HrvTestProtocolType.GUIDED_STEP] (default 240 s / 4 min; 180 s min).
 * @param startingTarget Initial target intensity (e.g. 100 W for cycling, 8.0 km/h for running).
 * @param stepIncrement Target intensity increment per step (e.g. +15 W / +20 W; +0.5 km/h).
 * @param rampSlopeWattsPerMin Slope for continuous ramp in W/min (default +15 W/min, tagging tau_MRT = 45 s).
 * @param cooldownDurationSec Cool-down duration after threshold capture (default 300 s / 5 min).
 */
data class HrvRampTestConfig(
    val sport: HrvTestSport = HrvTestSport.CYCLING,
    val protocolType: HrvTestProtocolType = HrvTestProtocolType.GUIDED_STEP,
    val warmupDurationSec: Int = 600,
    val stepDurationSec: Int = 240,
    val startingTarget: Double = 100.0,
    val stepIncrement: Double = 15.0,
    val rampSlopeWattsPerMin: Double = 15.0,
    val cooldownDurationSec: Int = 300
)

/**
 * Reactive UI state container for the guided DFA a1 protocol screen (`REQ-TRK-072`).
 */
data class HrvRampTestUiState(
    val phase: HrvTestPhase = HrvTestPhase.CONFIG,
    val config: HrvRampTestConfig = HrvRampTestConfig(),
    val sensorQualityTier: SensorQualityTier = SensorQualityTier.UNSUITABLE,
    val preFlightArtifactRate: Double? = null,
    val preFlightMessageResId: Int? = null,
    val canStartTest: Boolean = false,
    val currentStepNumber: Int = 1,
    val targetIntensity: Double = 100.0,
    val stepElapsedSec: Int = 0,
    val stepRemainingSec: Int = 240,
    val totalElapsedSec: Int = 0,
    val currentHr: Int? = null,
    val currentPower: Double? = null,
    val currentSpeedKmh: Double? = null,
    val currentDfaA1: Double? = null,
    val currentArtifactRate: Double? = null,
    val earlyCompletionDetected: Boolean = false,
    val sub45DurationSec: Int = 0,
    val isCompleted: Boolean = false
)
