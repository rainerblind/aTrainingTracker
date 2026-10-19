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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.graphics.Color

/**
 * Categorical fatigue warning level for remaining W' anaerobic capacity (REQ-UI-416.2).
 *
 * Grounded in Skiba's Critical Power model (Skiba et al., 2012):
 * - NORMAL (>30%): Substantial anaerobic reserve remaining; high power surges sustainable.
 * - LOW (20%..30%): Depleting anaerobic reserve; impending threshold fatigue.
 * - CRITICAL (>0%..<20%): Acute anaerobic exhaustion; severe fatigue and lactate accumulation.
 *   Triggers urgent visual warning alert border on cockpit sensor tile.
 * - DEPLETED (<=0%): Anaerobic battery fully exhausted; power must fall below CP to reconstitute.
 */
enum class WPrimeWarningLevel {
    NORMAL,    // > 30%
    LOW,       // 20% .. 30%
    CRITICAL,  // > 0% .. < 20%
    DEPLETED   // <= 0%
}

/**
 * Immutable UI state model representing the real-time W' anaerobic battery gauge (REQ-UI-416.2).
 *
 * Implements the real-time visualization of the Skiba et al. (2012) W' anaerobic work capacity
 * model. When instantaneous power output exceeds Critical Power (P > CP), the anaerobic work
 * reservoir W' is progressively depleted. When power drops below Critical Power (P < CP),
 * W' reconstitutes continuously following exponential kinetics parameterized by recovery time constant tau_W'.
 *
 * In accordance with physiological safety and performance monitoring:
 * - A 20% remaining threshold represents critical anaerobic depletion where neuromuscular failure
 *   and severe metabolic acidosis occur; this triggers [isWarning] = true and card border warning red.
 * - Strictly transient in-memory state attached to [SensorFieldState]; never persisted to SQLite.
 *   Preserves SQLite schema stability (DB_VERSION = 13 invariant).
 *
 * @property remainingKj Instantaneous remaining anaerobic work capacity in kJ, or null if telemetry is absent.
 * @property totalKj Total configured anaerobic work capacity in kJ (denominator).
 * @property percentage Clamped remaining capacity percentage [0..100].
 * @property batteryFraction Normalized fill fraction [0.0f..1.0f], or null if stale/absent.
 * @property warningLevel Discrete warning classification based on remaining percentage.
 * @property batteryColor Authoritative theme color for battery fill and gauge elements.
 * @property isWarning True when battery level is critically low (<20%) and telemetry is not stale.
 * @property isStale True when telemetry is absent, null, non-finite, or older than 3000 ms.
 * @property timestampMs Telemetry sample epoch timestamp in milliseconds.
 */
data class WPrimeBatteryState(
    val remainingKj: Double?,
    val totalKj: Double,
    val percentage: Int,
    val batteryFraction: Float?,
    val warningLevel: WPrimeWarningLevel,
    val batteryColor: Color,
    val isWarning: Boolean,
    val isStale: Boolean,
    val timestampMs: Long
)
