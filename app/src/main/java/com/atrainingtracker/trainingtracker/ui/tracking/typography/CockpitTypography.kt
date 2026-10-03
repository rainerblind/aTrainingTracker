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

package com.atrainingtracker.trainingtracker.ui.tracking.typography

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.atrainingtracker.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Boldness / weight options for the Cockpit HUD (REQ-UI-212 / ATT-1751).
 */
enum class CockpitFontWeight {
    NORMAL,
    SEMI_BOLD,
    BOLD;

    fun asFontWeight(): FontWeight = when (this) {
        NORMAL -> FontWeight.Normal
        SEMI_BOLD -> FontWeight.SemiBold
        BOLD -> FontWeight.Bold
    }

    fun getDisplayNameRes(): Int = when (this) {
        NORMAL -> R.string.tuning_weight_normal
        SEMI_BOLD -> R.string.tuning_weight_semi_bold
        BOLD -> R.string.tuning_weight_bold
    }
}

/**
 * Curated selectable font families for the Cockpit HUD (REQ-UI-212 / ATT-1751).
 */
enum class CockpitFontFamily {
    SYSTEM_DEFAULT,
    SEVEN_SEGMENT,
    MODERN_ATHLETIC,
    MONOSPACE,
    PLAYFUL,
    BEBAS_NEUE,
    TEKO,
    BARLOW_CONDENSED,
    OSWALD,
    CHAKRA_PETCH,
    OXANIUM,
    RAJDHANI,
    MONTSERRAT;

    fun getDisplayNameRes(): Int = when (this) {
        SYSTEM_DEFAULT -> R.string.tuning_font_system_default
        SEVEN_SEGMENT -> R.string.tuning_font_seven_segment
        MODERN_ATHLETIC -> R.string.tuning_font_modern_athletic
        MONOSPACE -> R.string.tuning_font_monospace
        PLAYFUL -> R.string.tuning_font_playful
        BEBAS_NEUE -> R.string.tuning_font_bebas_neue
        TEKO -> R.string.tuning_font_teko
        BARLOW_CONDENSED -> R.string.tuning_font_barlow_condensed
        OSWALD -> R.string.tuning_font_oswald
        CHAKRA_PETCH -> R.string.tuning_font_chakra_petch
        OXANIUM -> R.string.tuning_font_oxanium
        RAJDHANI -> R.string.tuning_font_rajdhani
        MONTSERRAT -> R.string.tuning_font_montserrat
    }
}

/**
 * Active typography configuration for the Cockpit HUD.
 */
data class CockpitTypographyConfig(
    val family: CockpitFontFamily = CockpitFontFamily.SYSTEM_DEFAULT,
    val weight: CockpitFontWeight = CockpitFontWeight.SEMI_BOLD,
    val resolvedFontFamily: FontFamily = FontFamily.Default
)

/**
 * CompositionLocal providing active Cockpit HUD typography configuration.
 * Decoupled from SensorFieldState and database schemas.
 */
val LocalCockpitTypography = compositionLocalOf { CockpitTypographyConfig() }

/**
 * Safe resolver for Cockpit typography with offline and JVM unit test fallbacks.
 */
object CockpitTypography {

    private val fontCache = ConcurrentHashMap<CockpitFontFamily, FontFamily>()

    /**
     * Resolves the [FontFamily] corresponding to the given [CockpitFontFamily].
     * Uses defensive fallback on exceptions or unavailable font resources.
     */
    fun resolveFontFamily(family: CockpitFontFamily): FontFamily {
        return fontCache.computeIfAbsent(family) {
            try {
                when (family) {
                    CockpitFontFamily.SYSTEM_DEFAULT -> FontFamily.Default
                    CockpitFontFamily.SEVEN_SEGMENT -> FontFamily(Font(R.font.orbitron))
                    CockpitFontFamily.MODERN_ATHLETIC -> FontFamily(Font(R.font.roboto_condensed))
                    CockpitFontFamily.MONOSPACE -> FontFamily(Font(R.font.roboto_mono))
                    CockpitFontFamily.PLAYFUL -> FontFamily(Font(R.font.comic_neue))
                    CockpitFontFamily.BEBAS_NEUE -> FontFamily(Font(R.font.bebas_neue))
                    CockpitFontFamily.TEKO -> FontFamily(Font(R.font.teko))
                    CockpitFontFamily.BARLOW_CONDENSED -> FontFamily(Font(R.font.barlow_condensed))
                    CockpitFontFamily.OSWALD -> FontFamily(Font(R.font.oswald))
                    CockpitFontFamily.CHAKRA_PETCH -> FontFamily(Font(R.font.chakra_petch))
                    CockpitFontFamily.OXANIUM -> FontFamily(Font(R.font.oxanium))
                    CockpitFontFamily.RAJDHANI -> FontFamily(Font(R.font.rajdhani))
                    CockpitFontFamily.MONTSERRAT -> FontFamily(Font(R.font.montserrat))
                }
            } catch (t: Throwable) {
                fallbackFontFamily(family)
            }
        }
    }

    /**
     * Creates a [CockpitTypographyConfig] with the given family, weight, and resolved [FontFamily].
     */
    fun resolveConfig(
        family: CockpitFontFamily = CockpitFontFamily.SYSTEM_DEFAULT,
        weight: CockpitFontWeight = CockpitFontWeight.SEMI_BOLD
    ): CockpitTypographyConfig {
        return CockpitTypographyConfig(
            family = family,
            weight = weight,
            resolvedFontFamily = resolveFontFamily(family)
        )
    }

    /**
     * Standard fallback Compose font families for test environments and offline devices.
     */
    fun fallbackFontFamily(family: CockpitFontFamily): FontFamily {
        return when (family) {
            CockpitFontFamily.SYSTEM_DEFAULT -> FontFamily.Default
            CockpitFontFamily.SEVEN_SEGMENT -> FontFamily.Default
            CockpitFontFamily.MODERN_ATHLETIC -> FontFamily.SansSerif
            CockpitFontFamily.MONOSPACE -> FontFamily.Monospace
            CockpitFontFamily.PLAYFUL -> FontFamily.Cursive
            CockpitFontFamily.BEBAS_NEUE,
            CockpitFontFamily.TEKO,
            CockpitFontFamily.BARLOW_CONDENSED,
            CockpitFontFamily.OSWALD,
            CockpitFontFamily.CHAKRA_PETCH,
            CockpitFontFamily.OXANIUM,
            CockpitFontFamily.RAJDHANI,
            CockpitFontFamily.MONTSERRAT -> FontFamily.SansSerif
        }
    }
}
