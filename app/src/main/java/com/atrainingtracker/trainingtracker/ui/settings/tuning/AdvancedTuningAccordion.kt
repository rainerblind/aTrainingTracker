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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.EditWorkoutFieldPreferences
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Enumeration of the 5 canonical, semantic subsections of Advanced Settings.
 */
enum class TuningSection {
    COCKPIT_TYPOGRAPHY,
    BATTERY_SAVER,
    SENSORS_GPS,
    AFTERMATH_ANALYSIS,
    WORKOUT_MASKS_CARDS,
    NAVIGATION
}

/**
 * Pure, deterministic subtitle formatters generating live active-value summary strings
 * for collapsed and expanded accordion section headers (REQ-UI-222, REQ-UI-258).
 */
object TuningSubtitleFormatter {

    fun formatCockpitSubtitle(
        family: CockpitFontFamily,
        weight: CockpitFontWeight,
        variant: SensorFieldVariant,
        context: Context
    ): String {
        val familyName = context.getString(family.getDisplayNameRes())
        val weightName = context.getString(weight.getDisplayNameRes())
        val variantName = context.getString(variant.titleResId)
        return "$familyName, $weightName · $variantName"
    }

    @Deprecated("Use formatCockpitSubtitle(family, weight, variant, context)")
    fun formatCockpitSubtitle(
        family: CockpitFontFamily,
        weight: CockpitFontWeight,
        context: Context
    ): String {
        val familyName = context.getString(family.getDisplayNameRes())
        val weightName = context.getString(weight.getDisplayNameRes())
        return "$familyName, $weightName"
    }

    fun formatBatterySaverSubtitle(
        fullDim: Float,
        mediumDim: Float,
        flatSlope: Float,
        steepSlope: Float
    ): String {
        val fullPercent = (fullDim * 100).roundToInt()
        val mediumPercent = (mediumDim * 100).roundToInt()
        val flatStr = String.format(Locale.getDefault(), "%.1f%%", flatSlope)
        val steepStr = String.format(Locale.getDefault(), "%.1f%%", steepSlope)
        return "$fullPercent% / $mediumPercent%, Flat: $flatStr, Steep: $steepStr"
    }

    fun formatNavigationSubtitle(
        radiusKm: Float,
        transparency: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY,
        dismissSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
        context: Context? = null
    ): String {
        val transPercent = (transparency * 100f).roundToInt()
        val dismissStr = if (dismissSec == 0) {
            context?.getString(R.string.tuning_nav_cue_dismiss_persistent) ?: "Persistent"
        } else {
            "${dismissSec}s"
        }
        val radiusStr = String.format(Locale.getDefault(), "%.1f km", radiusKm)
        return "$transPercent%, $dismissStr, $radiusStr"
    }

    fun formatSensorsGpsSubtitle(
        gpsAccuracy: Float,
        altitudeWindowSec: Int,
        slopeMinSpeed: Float
    ): String {
        val gpsMeters = gpsAccuracy.roundToInt()
        val speedStr = String.format(Locale.getDefault(), "%.1f m/s", slopeMinSpeed)
        return "GPS: ${gpsMeters}m, Alt: ${altitudeWindowSec}s, Speed: $speedStr"
    }

    fun formatAftermathSubtitle(
        elevationDomain: ProfileXAxisDomain,
        telemetryDomain: ProfileXAxisDomain,
        context: Context,
        smoothingSigma: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS
    ): String {
        val elevStr = if (elevationDomain == ProfileXAxisDomain.DISTANCE) {
            context.getString(R.string.tuning_profile_x_axis_distance)
        } else {
            context.getString(R.string.tuning_profile_x_axis_time)
        }
        val telemStr = if (telemetryDomain == ProfileXAxisDomain.DISTANCE) {
            context.getString(R.string.tuning_profile_x_axis_distance)
        } else {
            context.getString(R.string.tuning_profile_x_axis_time)
        }
        val elevPrefix = context.getString(R.string.tuning_profile_x_axis_title)
        val telemPrefix = context.getString(R.string.tuning_telemetry_x_axis_title)
        return "$elevPrefix: $elevStr | $telemPrefix: $telemStr | \u03c3: ${smoothingSigma.roundToInt()}m"
    }

    @Deprecated("Use formatAftermathSubtitle(elevationDomain, telemetryDomain, context)")
    fun formatAftermathSubtitle(
        domain: ProfileXAxisDomain,
        context: Context
    ): String {
        val domainName = if (domain == ProfileXAxisDomain.DISTANCE) {
            context.getString(R.string.tuning_profile_x_axis_distance)
        } else {
            context.getString(R.string.tuning_profile_x_axis_time)
        }
        val prefix = context.getString(R.string.tuning_profile_x_axis_title)
        return "$prefix: $domainName"
    }

    fun formatWorkoutMatrixSubtitle(
        cardPrefs: WorkoutCardSectionPreferences,
        detailPrefs: WorkoutDetailPreferences,
        context: Context
    ): String {
        val listCount = listOf(
            cardPrefs.showDescription,
            cardPrefs.showExtrema,
            cardPrefs.showLaps,
            cardPrefs.showStrava,
            cardPrefs.showMapPreview,
            cardPrefs.showElevationProfile,
            cardPrefs.showTelemetryCharts,
            cardPrefs.showZoneAnalysis
        ).count { it }

        val detailCount = listOf(
            detailPrefs.showDescription,
            detailPrefs.showExtrema,
            detailPrefs.showLaps,
            detailPrefs.showStrava,
            detailPrefs.showMap,
            detailPrefs.showElevationProfile,
            detailPrefs.showTelemetryCharts,
            detailPrefs.showZoneAnalysis
        ).count { it }

        return context.getString(R.string.tuning_summary_matrix_format, listCount, detailCount)
    }

    fun formatWorkoutMasksSubtitle(
        cardPrefs: WorkoutCardSectionPreferences,
        context: Context
    ): String {
        val activeCards = listOf(
            cardPrefs.showDescription,
            cardPrefs.showExtrema,
            cardPrefs.showLaps,
            cardPrefs.showStrava,
            cardPrefs.showMapPreview,
            cardPrefs.showElevationProfile,
            cardPrefs.showTelemetryCharts,
            cardPrefs.showZoneAnalysis
        ).count { it }

        return context.getString(R.string.tuning_summary_masks_cards_format, activeCards)
    }

    @Deprecated("Use formatWorkoutMasksSubtitle(cardPrefs, context)")
    fun formatWorkoutMasksSubtitle(
        cardPrefs: WorkoutCardSectionPreferences,
        editPrefs: EditWorkoutFieldPreferences,
        context: Context
    ): String {
        return formatWorkoutMasksSubtitle(cardPrefs, context)
    }
}

/**
 * Reusable, collapsible Material 3 accordion container for Advanced Settings (REQ-UI-222).
 */
@Composable
fun TuningAccordionSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "accordionChevronRotation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category Leading Icon
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                // Title & Subtitle Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trailing Animated Chevron
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation)
                )
            }

            // Animated Collapsible Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    content()
                }
            }
        }
    }
}
