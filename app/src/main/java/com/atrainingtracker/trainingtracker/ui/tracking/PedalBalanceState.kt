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

enum class PedalBalanceTier {
    OPTIMAL,
    MILD,
    SEVERE
}

enum class DominantSide {
    BALANCED,
    LEFT,
    RIGHT
}

/**
 * Immutable UI model representing the state of the real-time pedal power balance indicator (REQ-UI-377).
 *
 * @property leftPercent The calculated Left pedal percentage [0..100].
 * @property rightPercent The calculated Right pedal percentage [0..100].
 * @property deviationPercent Absolute deviation magnitude from 50% (|Right% - 50|).
 * @property dominantSide Which side is contributing more power (LEFT, RIGHT, or BALANCED).
 * @property tier Physiological asymmetry classification tier (OPTIMAL, MILD, SEVERE).
 * @property tierColor Theme/domain color corresponding to the physiological tier.
 * @property indicatorFraction Normalized horizontal position [0.0..1.0] of the needle, or null if telemetry is stale.
 * @property isStale Whether telemetry is absent, disconnected, or non-finite.
 */
data class PedalBalanceState(
    val leftPercent: Int,
    val rightPercent: Int,
    val deviationPercent: Int,
    val dominantSide: DominantSide,
    val tier: PedalBalanceTier,
    val tierColor: Color,
    val indicatorFraction: Float?,
    val isStale: Boolean
)
