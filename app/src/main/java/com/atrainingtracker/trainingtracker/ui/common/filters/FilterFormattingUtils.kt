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

package com.atrainingtracker.trainingtracker.ui.common.filters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared unit conversion, consistent rounding, athletic presets, and chip formatting utilities
 * across filter bottom sheets and active filter chip rows (REQ-UI-508).
 */
object FilterFormattingUtils {

    /**
     * Remembers whether the active application unit system is Imperial (REQ-UI-508.1).
     */
    @Composable
    fun rememberIsImperialUnits(): Boolean = remember {
        TrainingApplication.getUnit() == MyUnits.IMPERIAL
    }

    /**
     * Returns the distance multiplier to convert SI meters to user display units (km or mi).
     */
    fun getDistanceMultiplier(isImperial: Boolean): Double =
        if (isImperial) BANALService.METER_PER_MILE else 1000.0

    /**
     * Returns the elevation multiplier to convert SI meters to user display units (m or ft).
     */
    fun getElevationMultiplier(isImperial: Boolean): Double =
        if (isImperial) BANALService.METER_PER_FOOT else 1.0

    /**
     * Returns the unit abbreviation for distance.
     */
    fun getDistanceUnit(isImperial: Boolean): String =
        if (isImperial) "mi" else "km"

    /**
     * Returns the unit abbreviation for elevation.
     */
    fun getElevationUnit(isImperial: Boolean): String =
        if (isImperial) "ft" else "m"

    /**
     * Formats distance in SI meters to a display value with at most 1 decimal place,
     * stripping trailing `.0` for whole numbers (REQ-UI-508.1).
     */
    fun formatDistanceValue(meters: Double, multiplier: Double): String {
        val value = meters / multiplier
        val rounded = (Math.round(value * 10.0) / 10.0)
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    /**
     * Formats distance in SI meters for text field inputs (returns "" for null).
     */
    fun formatDistanceInputValue(meters: Double?, multiplier: Double): String =
        meters?.let { formatDistanceValue(it, multiplier) } ?: ""

    /**
     * Formats elevation in SI meters to integer rounded display value (REQ-UI-508.1).
     */
    fun formatElevationValue(meters: Double, multiplier: Double): String {
        val value = meters / multiplier
        return value.roundToInt().toString()
    }

    /**
     * Formats elevation in SI meters for text field inputs (returns "" for null).
     */
    fun formatElevationInputValue(meters: Double?, multiplier: Double): String =
        meters?.let { formatElevationValue(it, multiplier) } ?: ""

    /**
     * Returns athletic presets for distance interval filtering (REQ-UI-508.1, REQ-UI-508.3).
     * Thresholds in the Triple are stored in SI meters (label, minMeters, maxMeters).
     */
    fun getDistancePresets(isImperial: Boolean): List<Triple<String, Double?, Double?>> =
        if (isImperial) {
            listOf(
                Triple("< 10 mi", null, 10.0 * BANALService.METER_PER_MILE),
                Triple("10 - 25 mi", 10.0 * BANALService.METER_PER_MILE, 25.0 * BANALService.METER_PER_MILE),
                Triple("25 - 50 mi", 25.0 * BANALService.METER_PER_MILE, 50.0 * BANALService.METER_PER_MILE),
                Triple("50 - 100 mi", 50.0 * BANALService.METER_PER_MILE, 100.0 * BANALService.METER_PER_MILE),
                Triple("> 100 mi", 100.0 * BANALService.METER_PER_MILE, null)
            )
        } else {
            listOf(
                Triple("< 10 km", null, 10000.0),
                Triple("10 - 25 km", 10000.0, 25000.0),
                Triple("25 - 50 km", 25000.0, 50000.0),
                Triple("50 - 100 km", 50000.0, 100000.0),
                Triple("> 100 km", 100000.0, null)
            )
        }

    /**
     * Returns athletic presets for elevation gain filtering (REQ-UI-508.1, REQ-UI-508.3).
     * Thresholds in the Triple are stored in SI meters (label, minMeters, maxMeters).
     */
    fun getElevationPresets(isImperial: Boolean): List<Triple<String, Double?, Double?>> =
        if (isImperial) {
            listOf(
                Triple("< 500 ft", null, 500.0 * BANALService.METER_PER_FOOT),
                Triple("500 - 1000 ft", 500.0 * BANALService.METER_PER_FOOT, 1000.0 * BANALService.METER_PER_FOOT),
                Triple("1000 - 2000 ft", 1000.0 * BANALService.METER_PER_FOOT, 2000.0 * BANALService.METER_PER_FOOT),
                Triple("2000 - 4000 ft", 2000.0 * BANALService.METER_PER_FOOT, 4000.0 * BANALService.METER_PER_FOOT),
                Triple("> 4000 ft", 4000.0 * BANALService.METER_PER_FOOT, null)
            )
        } else {
            listOf(
                Triple("< 100 m", null, 100.0),
                Triple("100 - 250 m", 100.0, 250.0),
                Triple("250 - 500 m", 250.0, 500.0),
                Triple("500 - 1000 m", 500.0, 1000.0),
                Triple("> 1000 m", 1000.0, null)
            )
        }

    /**
     * Formats distance filter chip text consuming localized string resources (REQ-UI-508.1, REQ-UI-508.2).
     */
    @Composable
    fun formatFilterDistanceChip(
        minMeters: Double?,
        maxMeters: Double?,
        isImperial: Boolean
    ): String {
        val multiplier = getDistanceMultiplier(isImperial)
        val minFormatted = minMeters?.let { formatDistanceValue(it, multiplier) }
        val maxFormatted = maxMeters?.let { formatDistanceValue(it, multiplier) }
        return when {
            minFormatted != null && maxFormatted != null -> {
                val resId = if (isImperial) R.string.filter_distance_interval_format_mi else R.string.filter_distance_interval_format
                stringResource(resId, minFormatted, maxFormatted)
            }
            minFormatted != null -> {
                val resId = if (isImperial) R.string.filter_min_distance_format_mi else R.string.filter_min_distance_format
                stringResource(resId, minFormatted)
            }
            maxFormatted != null -> {
                val resId = if (isImperial) R.string.filter_max_distance_format_mi else R.string.filter_max_distance_format
                stringResource(resId, maxFormatted)
            }
            else -> ""
        }
    }

    /**
     * Formats elevation filter chip text consuming localized string resources (REQ-UI-508.1, REQ-UI-508.2).
     */
    @Composable
    fun formatFilterElevationChip(
        minMeters: Double?,
        maxMeters: Double?,
        isImperial: Boolean
    ): String {
        val multiplier = getElevationMultiplier(isImperial)
        val minFormatted = minMeters?.let { formatElevationValue(it, multiplier) }
        val maxFormatted = maxMeters?.let { formatElevationValue(it, multiplier) }
        return when {
            minFormatted != null && maxFormatted != null -> {
                val resId = if (isImperial) R.string.filter_elevation_interval_format_ft else R.string.filter_elevation_interval_format
                stringResource(resId, minFormatted, maxFormatted)
            }
            minFormatted != null -> {
                val resId = if (isImperial) R.string.filter_min_elevation_format_ft else R.string.filter_min_elevation_format
                stringResource(resId, minFormatted)
            }
            maxFormatted != null -> {
                val resId = if (isImperial) R.string.filter_max_elevation_format_ft else R.string.filter_max_elevation_format
                stringResource(resId, maxFormatted)
            }
            else -> ""
        }
    }

    /**
     * String-based distance chip representation for unit testing and non-composable callers (TST-UI-508.1).
     */
    fun formatDistanceFilterString(
        minMeters: Double?,
        maxMeters: Double?,
        isImperial: Boolean
    ): String {
        val multiplier = getDistanceMultiplier(isImperial)
        val minFormatted = minMeters?.let { formatDistanceValue(it, multiplier) }
        val maxFormatted = maxMeters?.let { formatDistanceValue(it, multiplier) }
        val unit = getDistanceUnit(isImperial)
        return when {
            minFormatted != null && maxFormatted != null -> "$minFormatted - $maxFormatted $unit"
            minFormatted != null -> "> $minFormatted $unit"
            maxFormatted != null -> "< $maxFormatted $unit"
            else -> ""
        }
    }

    /**
     * String-based elevation chip representation for unit testing and non-composable callers (TST-UI-508.1).
     */
    fun formatElevationFilterString(
        minMeters: Double?,
        maxMeters: Double?,
        isImperial: Boolean
    ): String {
        val multiplier = getElevationMultiplier(isImperial)
        val minFormatted = minMeters?.let { formatElevationValue(it, multiplier) }
        val maxFormatted = maxMeters?.let { formatElevationValue(it, multiplier) }
        val unit = getElevationUnit(isImperial)
        return when {
            minFormatted != null && maxFormatted != null -> "$minFormatted - $maxFormatted $unit"
            minFormatted != null -> "> $minFormatted $unit"
            maxFormatted != null -> "< $maxFormatted $unit"
            else -> ""
        }
    }
}
