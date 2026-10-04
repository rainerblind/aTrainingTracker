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

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Design specifications and configurable styling variants for cockpit sensor fields (REQ-UI-258, ATT-2058).
 * Enables user selection and live evaluation in Expert Settings across corner radii,
 * card elevation, and inter-tile grid spacing while preserving production defaults.
 */
enum class SensorFieldVariant(@StringRes val titleResId: Int) {
    CLASSIC_SEAMLESS(R.string.sensor_field_variant_v0),
    OUTLINED_TILES(R.string.sensor_field_variant_v1),
    ELEVATED_CARDS(R.string.sensor_field_variant_v2),
    SOFT_CAPSULES(R.string.sensor_field_variant_v3);

    val displayName: String
        get() = when (this) {
            CLASSIC_SEAMLESS -> "Classic Seamless Grid"
            OUTLINED_TILES -> "Modern Outlined Sport Tiles"
            ELEVATED_CARDS -> "Elevated Sports Cards"
            SOFT_CAPSULES -> "Soft Accent Capsules"
        }

    companion object {
        // Backward-compatibility aliases
        val VARIANT_0_BASELINE = CLASSIC_SEAMLESS
        val VARIANT_1_OUTLINED_TILES = OUTLINED_TILES
        val VARIANT_2_ELEVATED_CARDS = ELEVATED_CARDS
        val VARIANT_3_CAPSULES = SOFT_CAPSULES

        /**
         * Safely decodes a persisted variant name from DataStore preferences.
         * Gracefully maps legacy enum names to new canonical enum values.
         */
        fun fromStorage(name: String?): SensorFieldVariant {
            return when (name) {
                "CLASSIC_SEAMLESS", "VARIANT_0_BASELINE" -> CLASSIC_SEAMLESS
                "OUTLINED_TILES", "VARIANT_1_OUTLINED_TILES" -> OUTLINED_TILES
                "ELEVATED_CARDS", "VARIANT_2_ELEVATED_CARDS" -> ELEVATED_CARDS
                "SOFT_CAPSULES", "VARIANT_3_CAPSULES" -> SOFT_CAPSULES
                else -> CLASSIC_SEAMLESS
            }
        }
    }
}

data class SensorFieldStyle(
    val shape: Shape = RectangleShape,
    val gridSpacing: Dp = 0.dp,
    val defaultElevation: Dp = 0.dp,
    val cornerRadius: Dp = 0.dp
) {
    val elevation: CardElevation
        @Composable get() = CardDefaults.cardElevation(defaultElevation = defaultElevation)

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
            SensorFieldVariant.CLASSIC_SEAMLESS -> Variant0_Baseline
            SensorFieldVariant.OUTLINED_TILES -> Variant1_OutlinedTiles
            SensorFieldVariant.ELEVATED_CARDS -> Variant2_ElevatedCards
            SensorFieldVariant.SOFT_CAPSULES -> Variant3_Capsules
        }
    }
}
