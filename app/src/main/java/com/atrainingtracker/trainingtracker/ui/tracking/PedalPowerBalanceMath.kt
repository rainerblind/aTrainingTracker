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
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure calculation engine for real-time pedal power balance telemetry (REQ-UI-377).
 *
 * Converts raw Right pedal contribution percentages into complementary Left/Right pairs,
 * deviation magnitudes, dominant side classifications, and physiological asymmetry tiers.
 */
object PedalPowerBalanceMath {

    val OPTIMAL_COLOR: Color = TTColor.ConnectionStatusGreen // #4CAF50
    val MILD_COLOR: Color = TTColor.Zone3                   // #FFA500
    val SEVERE_COLOR: Color = TTColor.Zone4                 // #FF0000

    /**
     * Calculates the pedal balance state from a raw Right pedal percentage telemetry reading.
     *
     * @param rightPercentValue The Right pedal contribution percentage in [0..100]. Returns stale state if null or non-finite.
     * @return [PedalBalanceState] containing calculated percentages, deviation, tier, and indicator fraction.
     */
    fun calculateBalanceState(rightPercentValue: Number?): PedalBalanceState {
        if (rightPercentValue == null || !rightPercentValue.toDouble().isFinite()) {
            return PedalBalanceState(
                leftPercent = 50,
                rightPercent = 50,
                deviationPercent = 0,
                dominantSide = DominantSide.BALANCED,
                tier = PedalBalanceTier.OPTIMAL,
                tierColor = OPTIMAL_COLOR,
                indicatorFraction = null,
                isStale = true
            )
        }

        val rightClamped = rightPercentValue.toDouble().roundToInt().coerceIn(0, 100)
        val left = 100 - rightClamped
        val deviation = abs(rightClamped - 50)

        val dominantSide = when {
            rightClamped > 50 -> DominantSide.RIGHT
            rightClamped < 50 -> DominantSide.LEFT
            else -> DominantSide.BALANCED
        }

        val tier = when {
            deviation <= 2 -> PedalBalanceTier.OPTIMAL
            deviation <= 5 -> PedalBalanceTier.MILD
            else -> PedalBalanceTier.SEVERE
        }

        val tierColor = when (tier) {
            PedalBalanceTier.OPTIMAL -> OPTIMAL_COLOR
            PedalBalanceTier.MILD -> MILD_COLOR
            PedalBalanceTier.SEVERE -> SEVERE_COLOR
        }

        val indicatorFraction = rightClamped / 100.0f

        return PedalBalanceState(
            leftPercent = left,
            rightPercent = rightClamped,
            deviationPercent = deviation,
            dominantSide = dominantSide,
            tier = tier,
            tierColor = tierColor,
            indicatorFraction = indicatorFraction,
            isStale = false
        )
    }

    /**
     * Formats the dual split balance readout string (e.g. "48 | 52" or "-- | --").
     */
    fun formatBalanceString(state: PedalBalanceState): String {
        return if (state.isStale) {
            "-- | --"
        } else {
            "${state.leftPercent} | ${state.rightPercent}"
        }
    }
}
