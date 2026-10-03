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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design specifications and prototyping variants for cockpit sensor fields (REQ-UI-258, ATT-2058).
 * Enables structured visual evaluation and side-by-side comparison across corner radii,
 * card elevation, and inter-tile grid spacing while preserving production defaults.
 */
enum class SensorFieldVariant(val displayName: String) {
    VARIANT_0_BASELINE("Variant 0: Baseline (Status Quo)"),
    VARIANT_1_OUTLINED_TILES("Variant 1: Modern Outlined Sport Tiles"),
    VARIANT_2_ELEVATED_CARDS("Variant 2: Elevated Sports Cards"),
    VARIANT_3_CAPSULES("Variant 3: Soft Accent Capsules")
}

data class SensorFieldStyle(
    val shape: Shape = RectangleShape,
    val gridSpacing: Dp = 0.dp,
    val defaultElevation: Dp = 0.dp,
    val cornerRadius: Dp = 0.dp
) {
    companion object {
        val Variant0_Baseline = SensorFieldStyle(
            shape = RectangleShape,
            gridSpacing = 0.dp,
            defaultElevation = 0.dp,
            cornerRadius = 0.dp
        )

        val Variant1_OutlinedTiles = SensorFieldStyle(
            shape = RoundedCornerShape(6.dp),
            gridSpacing = 4.dp,
            defaultElevation = 0.dp,
            cornerRadius = 6.dp
        )

        val Variant2_ElevatedCards = SensorFieldStyle(
            shape = RoundedCornerShape(8.dp),
            gridSpacing = 6.dp,
            defaultElevation = 2.dp,
            cornerRadius = 8.dp
        )

        val Variant3_Capsules = SensorFieldStyle(
            shape = RoundedCornerShape(12.dp),
            gridSpacing = 8.dp,
            defaultElevation = 1.dp,
            cornerRadius = 12.dp
        )

        fun forVariant(variant: SensorFieldVariant): SensorFieldStyle = when (variant) {
            SensorFieldVariant.VARIANT_0_BASELINE -> Variant0_Baseline
            SensorFieldVariant.VARIANT_1_OUTLINED_TILES -> Variant1_OutlinedTiles
            SensorFieldVariant.VARIANT_2_ELEVATED_CARDS -> Variant2_ElevatedCards
            SensorFieldVariant.VARIANT_3_CAPSULES -> Variant3_Capsules
        }
    }
}
