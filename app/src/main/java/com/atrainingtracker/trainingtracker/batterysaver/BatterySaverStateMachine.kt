/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.batterysaver

class BatterySaverStateMachine(
    var tuningConfig: BatterySaverTuningConfig = BatterySaverTuningConfig()
) {
    constructor(hysteresisMs: Long) : this(
        tuningConfig = BatterySaverTuningConfig(downwardHysteresisMs = hysteresisMs)
    )

    val hysteresisMs: Long
        get() = tuningConfig.downwardHysteresisMs

    var currentLevel: DimmingLevel = DimmingLevel.NO_DIM
        private set

    private var pendingCandidate: DimmingLevel? = null
    private var pendingTimestamp: Long = 0L

    fun getBrightnessForLevel(level: DimmingLevel): Float {
        return when (level) {
            DimmingLevel.FULL_DIM -> tuningConfig.fullDimFactor
            DimmingLevel.MEDIUM_DIM -> tuningConfig.mediumDimFactor
            DimmingLevel.NO_DIM -> 1.0f
        }
    }

    fun evaluateRawLevel(snapshot: TelemetrySnapshot): DimmingLevel {
        val slope = snapshot.slopePercent ?: 0.0f
        val hr = snapshot.hrZone
        val pwr = if (snapshot.isCycling) snapshot.powerZone else null

        // Rule 3: No Dimming (Full Illumination)
        // Slope > slopeSteepThreshold OR HR >= Zone 4 OR Power >= Zone 4
        val isSteepClimb = slope > tuningConfig.slopeSteepThreshold
        val isHighHr = hr != null && hr >= 4
        val isHighPower = pwr != null && pwr >= 4

        if (isSteepClimb || isHighHr || isHighPower) {
            return DimmingLevel.NO_DIM
        }

        // Rule 2: Medium Dimming
        // Slope between slopeFlatThreshold and slopeSteepThreshold OR HR == Zone 3 OR Power == Zone 3
        val isModerateSlope = slope in tuningConfig.slopeFlatThreshold..tuningConfig.slopeSteepThreshold
        val isTempoHr = hr != null && hr == 3
        val isTempoPower = pwr != null && pwr == 3

        if (isModerateSlope || isTempoHr || isTempoPower) {
            return DimmingLevel.MEDIUM_DIM
        }

        // Rule 1: Full Dimming
        // Slope < slopeFlatThreshold AND HR <= Zone 2 AND Power <= Zone 2
        val isFlatOrDownhill = slope < tuningConfig.slopeFlatThreshold
        val isLowHr = hr == null || hr <= 2
        val isLowPower = pwr == null || pwr <= 2

        if (isFlatOrDownhill && isLowHr && isLowPower) {
            return DimmingLevel.FULL_DIM
        }

        return DimmingLevel.MEDIUM_DIM
    }

    fun update(snapshot: TelemetrySnapshot, currentTimeMs: Long = System.currentTimeMillis()): DimmingLevel {
        val raw = evaluateRawLevel(snapshot)
        val rawBrightness = getBrightnessForLevel(raw)
        val currentBrightness = getBrightnessForLevel(currentLevel)

        if (rawBrightness > currentBrightness) {
            // Immediate upward transition (brighter)
            currentLevel = raw
            pendingCandidate = null
        } else if (rawBrightness < currentBrightness) {
            // Downward transition requires damping hysteresis
            if (pendingCandidate != raw) {
                pendingCandidate = raw
                pendingTimestamp = currentTimeMs
            } else if (currentTimeMs - pendingTimestamp >= hysteresisMs) {
                currentLevel = raw
                pendingCandidate = null
            }
        } else {
            pendingCandidate = null
        }

        return currentLevel
    }

    fun forceLevel(level: DimmingLevel) {
        currentLevel = level
        pendingCandidate = null
        pendingTimestamp = 0L
    }

    fun reset(initialLevel: DimmingLevel = DimmingLevel.NO_DIM) {
        currentLevel = initialLevel
        pendingCandidate = null
        pendingTimestamp = 0L
    }
}
