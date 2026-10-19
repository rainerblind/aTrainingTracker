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

import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlin.math.roundToInt

/**
 * Pure mathematical helper for normalizing W' anaerobic work capacity,
 * evaluating battery fraction, discrete warning tiers, and telemetry staleness (REQ-UI-416.2).
 *
 * Implements the mathematical transformation of Skiba's anaerobic battery model:
 * - Battery Fraction = clamp(W'bal / W'total, 0.0, 1.0)
 * - Percentage = round(Battery Fraction * 100)
 *
 * Fatigue thresholds:
 * - >30%: [WPrimeWarningLevel.NORMAL] (Green)
 * - 20%..30%: [WPrimeWarningLevel.LOW] (Amber)
 * - <20%: [WPrimeWarningLevel.CRITICAL] (Red alert border)
 * - <=0%: [WPrimeWarningLevel.DEPLETED] (Red alert border)
 *
 * Staleness timeout:
 * If no fresh sample is received for >3000 ms, [WPrimeBatteryState.isStale] becomes true and the gauge dims to 0.35 alpha.
 */
object WPrimeBatteryMath {

    /**
     * Telemetry expiration timeout in milliseconds. If telemetry updates cease for longer than
     * this duration, the gauge transitions to stale contract.
     */
    const val STALE_TIMEOUT_MS = 3000L

    /**
     * Calculates immutable [WPrimeBatteryState] from instantaneous remaining and total W' energy.
     *
     * @param remainingKj Instantaneous remaining anaerobic energy in kJ.
     * @param totalKj Total configured anaerobic capacity in kJ.
     * @param timestampMs Telemetry sample epoch timestamp in milliseconds.
     * @param nowMs Current reference epoch time in milliseconds (defaults to system time).
     * @return Normalized and bounds-clamped [WPrimeBatteryState].
     */
    fun calculateBatteryState(
        remainingKj: Double?,
        totalKj: Double,
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): WPrimeBatteryState {
        val isStale = remainingKj == null ||
            !remainingKj.isFinite() ||
            (nowMs - timestampMs > STALE_TIMEOUT_MS)

        if (isStale || totalKj <= 0.0) {
            return WPrimeBatteryState(
                remainingKj = remainingKj,
                totalKj = totalKj.coerceAtLeast(1.0),
                percentage = 0,
                batteryFraction = null,
                warningLevel = WPrimeWarningLevel.DEPLETED,
                batteryColor = TTColor.Zone5,
                isWarning = false,
                isStale = true,
                timestampMs = timestampMs
            )
        }

        // Clamp remaining energy to [0.0, totalKj] to handle mid-session baseline changes
        val clampedRemaining = remainingKj.coerceIn(0.0, totalKj)
        val fraction = (clampedRemaining / totalKj).toFloat().coerceIn(0.0f, 1.0f)
        val percentage = (fraction * 100f).roundToInt().coerceIn(0, 100)

        val warningLevel = when {
            percentage > 30 -> WPrimeWarningLevel.NORMAL
            percentage >= 20 -> WPrimeWarningLevel.LOW
            percentage > 0 -> WPrimeWarningLevel.CRITICAL
            else -> WPrimeWarningLevel.DEPLETED
        }

        val color = when (warningLevel) {
            WPrimeWarningLevel.NORMAL -> TTColor.WPrimeBalGreen
            WPrimeWarningLevel.LOW -> TTColor.Zone3
            WPrimeWarningLevel.CRITICAL, WPrimeWarningLevel.DEPLETED -> TTColor.Zone5
        }

        val isWarning = percentage < 20

        return WPrimeBatteryState(
            remainingKj = clampedRemaining,
            totalKj = totalKj,
            percentage = percentage,
            batteryFraction = fraction,
            warningLevel = warningLevel,
            batteryColor = color,
            isWarning = isWarning,
            isStale = false,
            timestampMs = timestampMs
        )
    }
}
