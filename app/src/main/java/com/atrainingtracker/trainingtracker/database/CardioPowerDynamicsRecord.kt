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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.database

/**
 * Entity representing 1st-order dynamical Power-to-Heart-Rate parameters identified from paired
 * telemetry streams (REQ-REV-009 / ATT-3904).
 *
 * Persisted in `WorkoutSummaries.db` table `cardio_power_dynamics`.
 */
data class CardioPowerDynamicsRecord @JvmOverloads constructor(
    val workoutId: Long,
    val tauStart: Double,
    val hr0Start: Double,
    val kStart: Double,
    val hr200Start: Double = hr0Start + 200.0 * kStart,
    val r2Start: Double,
    val rmseStart: Double,
    val tauEnd: Double? = null,
    val hr0End: Double? = null,
    val kEnd: Double? = null,
    val hr200End: Double? = if (hr0End != null && kEnd != null) hr0End + 200.0 * kEnd else null,
    val r2End: Double? = null,
    val rmseEnd: Double? = null,
    val deltaHr200: Double? = if (hr200End != null) hr200End - (hr0Start + 200.0 * kStart) else null,
    val deltaTau: Double? = if (tauEnd != null) tauEnd - tauStart else null,
    val deltaHr0: Double? = if (hr0End != null) hr0End - hr0Start else null
) {
    /** Aerobic slope in W/bpm (inverse of gain K in bpm/W) at start of ride. */
    val aerobicSlopeStart: Double? get() = if (kStart > 0.0) 1.0 / kStart else null

    /** Aerobic slope in W/bpm (inverse of gain K in bpm/W) at end of ride. */
    val aerobicSlopeEnd: Double? get() = if (kEnd != null && kEnd > 0.0) 1.0 / kEnd else null
}

/**
 * Time-series sample representing a workout's start timestamp paired with its 1st-order
 * dynamical cardio-power parameters for longitudinal trend plotting in Athlete Profile (REQ-REV-011 / ATT-3906).
 */
data class CardioPowerTrendSample @JvmOverloads constructor(
    val workoutId: Long,
    val timestampMs: Long,
    val record: CardioPowerDynamicsRecord
) {
    val hr200Start: Double get() = record.hr200Start
    val tauStart: Double get() = record.tauStart
    val aerobicSlopeStart: Double? get() = record.aerobicSlopeStart
    val r2Start: Double get() = record.r2Start
    val rmseStart: Double get() = record.rmseStart
}

