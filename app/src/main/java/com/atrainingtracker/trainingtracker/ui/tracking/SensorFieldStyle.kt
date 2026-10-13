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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Design specifications and configurable styling variants for cockpit sensor fields (REQ-UI-258, ATT-2058, REQ-UI-276, ATT-2456).
 * Enables user selection and live evaluation in Expert Settings across corner radii,
 * card elevation, border thickness, border contrast, and inter-tile grid spacing while preserving production defaults.
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
    val cornerRadius: Dp = 0.dp,
    val borderThickness: Dp = 1.0.dp,
    val borderContrast: Float = 0.0f
) {
    val elevation: CardElevation
        @Composable get() = CardDefaults.cardElevation(defaultElevation = defaultElevation)

    companion object {
        val Variant0_Baseline = SensorFieldStyle(
            shape = RectangleShape,
            gridSpacing = 0.dp,
            defaultElevation = 0.dp,
            cornerRadius = 0.dp,
            borderThickness = 1.0.dp,
            borderContrast = 0.0f
        )

        val Variant1_OutlinedTiles = SensorFieldStyle(
            shape = RoundedCornerShape(8.dp),
            gridSpacing = 4.dp,
            defaultElevation = 0.dp,
            cornerRadius = 8.dp,
            borderThickness = 2.0.dp,
            borderContrast = 0.5f
        )

        val Variant2_ElevatedCards = SensorFieldStyle(
            shape = RoundedCornerShape(10.dp),
            gridSpacing = 6.dp,
            defaultElevation = 3.dp,
            cornerRadius = 10.dp,
            borderThickness = 0.0.dp,
            borderContrast = 0.0f
        )

        val Variant3_Capsules = SensorFieldStyle(
            shape = RoundedCornerShape(16.dp),
            gridSpacing = 8.dp,
            defaultElevation = 1.dp,
            cornerRadius = 16.dp,
            borderThickness = 1.5.dp,
            borderContrast = 0.7f
        )

        fun forVariant(variant: SensorFieldVariant): SensorFieldStyle = when (variant) {
            SensorFieldVariant.CLASSIC_SEAMLESS -> Variant0_Baseline
            SensorFieldVariant.OUTLINED_TILES -> Variant1_OutlinedTiles
            SensorFieldVariant.ELEVATED_CARDS -> Variant2_ElevatedCards
            SensorFieldVariant.SOFT_CAPSULES -> Variant3_Capsules
        }

        /**
         * Resolves the Card Shape based on corner radius (0 dp = RectangleShape, > 0 dp = RoundedCornerShape).
         */
        fun resolveShape(cornerRadius: Dp): Shape {
            return if (cornerRadius <= 0.dp) RectangleShape else RoundedCornerShape(cornerRadius)
        }

        /**
         * Resolves the BorderStroke dynamically based on thickness, contrast (0..1), and theme.
         * Returns null if thickness <= 0.dp (borderless).
         * Prioritizes move-selection highlight if isSelectedForMove is true.
         */
        fun resolveBorder(
            borderThickness: Dp,
            borderContrast: Float,
            isDarkTheme: Boolean,
            isSelectedForMove: Boolean = false
        ): BorderStroke? {
            if (isSelectedForMove) {
                return BorderStroke(2.dp, Color(0xFF2196F3))
            }
            if (borderThickness <= 0.dp) {
                return null
            }
            val subtleColor = if (isDarkTheme) Color(0xFF383838) else Color(0xFFD6D6D6)
            val maxContrastColor = if (isDarkTheme) Color.White else Color.Black
            val t = borderContrast.coerceIn(0f, 1f)
            val color = lerp(subtleColor, maxContrastColor, t)
            return BorderStroke(borderThickness, color)
        }

        /**
         * Resolves inter-tile grid spacing dynamically based on preset baseline, corner radius,
         * and border thickness (REQ-UI-292, ATT-2624).
         * Guarantees 0.dp for the CLASSIC_SEAMLESS baseline (0 radius, <= 1.0.dp border),
         * and proportionally expands whitespace when borders are thick or corners are rounded
         * to prevent card collisions.
         */
        fun resolveGridSpacing(
            variant: SensorFieldVariant,
            cornerRadius: Dp,
            borderThickness: Dp
        ): Dp {
            val baseStyle = forVariant(variant)
            if (variant == SensorFieldVariant.CLASSIC_SEAMLESS && cornerRadius <= 0.dp && borderThickness <= 1.0.dp) {
                return 0.dp
            }
            val thicknessPadding = (borderThickness - 1.0.dp).coerceAtLeast(0.dp) * 1.5f
            val cornerPadding = if (cornerRadius > 0.dp) (cornerRadius * 0.15f).coerceAtLeast(1.dp) else 0.dp
            val computed = baseStyle.gridSpacing.coerceAtLeast(2.dp) + thicknessPadding + cornerPadding
            return computed.coerceIn(2.dp, 12.dp)
        }
    }
}
