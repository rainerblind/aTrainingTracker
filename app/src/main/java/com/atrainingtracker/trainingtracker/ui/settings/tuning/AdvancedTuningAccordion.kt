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
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.EditWorkoutFieldPreferences
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Enumeration of the canonical, semantic subsections of Advanced Settings in athletic lifecycle sequence (REQ-UI-357).
 */
enum class TuningSection {
    NAVIGATION,
    COCKPIT_TYPOGRAPHY,
    BATTERY_SAVER,
    SENSOR_SEARCH,
    SENSORS_GPS,
    CYCLING_RADAR,
    WORKOUT_MASKS_CARDS,
    PHYSIOLOGY_MODELS,
    ROUTE_CLUSTERING,
    AFTERMATH_ANALYSIS
}

/**
 * Pure, deterministic subtitle formatters generating live active-value summary strings
 * for collapsed and expanded accordion section headers (REQ-UI-222, REQ-UI-258, REQ-UI-403).
 */
object TuningSubtitleFormatter {

    fun formatCockpitSubtitle(
        family: CockpitFontFamily, weight: CockpitFontWeight, variant: SensorFieldVariant, context: Context
    ): String = "${context.getString(family.getDisplayNameRes())}, ${context.getString(weight.getDisplayNameRes())} · ${context.getString(variant.titleResId)}"

    @Deprecated("Use formatCockpitSubtitle(family, weight, variant, context)")
    fun formatCockpitSubtitle(family: CockpitFontFamily, weight: CockpitFontWeight, context: Context): String =
        "${context.getString(family.getDisplayNameRes())}, ${context.getString(weight.getDisplayNameRes())}"

    fun formatBatterySaverSubtitle(
        fullDim: Float, mediumDim: Float, flatSlope: Float, steepSlope: Float, context: Context? = null
    ): String {
        val flatLabel = context?.getString(R.string.tuning_battery_saver_flat_short) ?: "Flat"
        val steepLabel = context?.getString(R.string.tuning_battery_saver_steep_short) ?: "Steep"
        return "${(fullDim * 100).roundToInt()}% / ${(mediumDim * 100).roundToInt()}%, $flatLabel: ${String.format(Locale.getDefault(), "%.1f%%", flatSlope)}, $steepLabel: ${String.format(Locale.getDefault(), "%.1f%%", steepSlope)}"
    }

    fun formatNavigationSubtitle(
        radiusKm: Float,
        dismissSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
        context: Context? = null
    ): String {
        val effectiveDismiss = if (dismissSec in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS) dismissSec else TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        return "${effectiveDismiss}s, ${DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())}"
    }

    fun formatSensorsGpsSubtitle(
        gpsAccuracy: Float, altitudeWindowSec: Int, slopeMinSpeed: Float, context: Context? = null
    ): String {
        val altLabel = context?.getString(R.string.tuning_sensors_alt_short) ?: "Alt"
        val speedLabel = context?.getString(R.string.tuning_sensors_speed_short) ?: "Speed"
        return "GPS: ${gpsAccuracy.roundToInt()}m, $altLabel: ${altitudeWindowSec}s, $speedLabel: ${String.format(Locale.getDefault(), "%.1f m/s", slopeMinSpeed)}"
    }

    fun formatSensorSearchSubtitle(
        numberOfSearchTries: Int, startSearchWhenAppStarts: Boolean, startSearchWhenResumeFromPaused: Boolean,
        startSearchWhenUserChangesSport: Boolean, startSearchWhenTrackingStarts: Boolean, context: Context
    ): String {
        val anyAutoSearch = startSearchWhenAppStarts || startSearchWhenResumeFromPaused || startSearchWhenUserChangesSport || startSearchWhenTrackingStarts
        val autoSearchStatus = context.getString(if (anyAutoSearch) R.string.tuning_sensor_search_active else R.string.tuning_sensor_search_disabled)
        return context.getString(R.string.tuning_sensor_search_subtitle, numberOfSearchTries, autoSearchStatus)
    }

    fun formatPhysiologyModelsSubtitle(
        enableCogganPowerMetrics: Boolean, enablePmcModel: Boolean, enableAerobicEfficiencyModel: Boolean,
        enableCriticalPowerModel: Boolean, enableCardioPowerDynamics: Boolean = false, context: Context
    ): String {
        val activeModels = buildList {
            if (enableCogganPowerMetrics) add(context.getString(R.string.tuning_physiology_coggan_short))
            if (enablePmcModel) add(context.getString(R.string.tuning_physiology_pmc_short))
            if (enableAerobicEfficiencyModel) add(context.getString(R.string.tuning_physiology_ef_short))
            if (enableCriticalPowerModel) add(context.getString(R.string.tuning_physiology_cp_short))
            if (enableCardioPowerDynamics) add(context.getString(R.string.cardio_power_domain_label))
        }
        return activeModels.ifEmpty { listOf(context.getString(R.string.tuning_physiology_none_enabled)) }.joinToString(", ")
    }

    fun formatPhysiologyModelsSubtitle(
        enableCogganPowerMetrics: Boolean, enablePmcModel: Boolean, enableAerobicEfficiencyModel: Boolean,
        enableCriticalPowerModel: Boolean, context: Context
    ): String = formatPhysiologyModelsSubtitle(enableCogganPowerMetrics, enablePmcModel, enableAerobicEfficiencyModel, enableCriticalPowerModel, false, context)

    fun formatAftermathSubtitle(
        elevationDomain: ProfileXAxisDomain, telemetryDomain: ProfileXAxisDomain, context: Context,
        smoothingSigma: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS
    ): String {
        val elevStr = context.getString(if (elevationDomain == ProfileXAxisDomain.DISTANCE) R.string.tuning_profile_x_axis_distance else R.string.tuning_profile_x_axis_time)
        val telemStr = context.getString(if (telemetryDomain == ProfileXAxisDomain.DISTANCE) R.string.tuning_profile_x_axis_distance else R.string.tuning_profile_x_axis_time)
        return "${context.getString(R.string.tuning_profile_x_axis_title)}: $elevStr | ${context.getString(R.string.tuning_telemetry_x_axis_title)}: $telemStr | \u03c3: ${smoothingSigma.roundToInt()}m"
    }

    @Deprecated("Use formatAftermathSubtitle(elevationDomain, telemetryDomain, context)")
    fun formatAftermathSubtitle(domain: ProfileXAxisDomain, context: Context): String {
        val domainName = context.getString(if (domain == ProfileXAxisDomain.DISTANCE) R.string.tuning_profile_x_axis_distance else R.string.tuning_profile_x_axis_time)
        return "${context.getString(R.string.tuning_profile_x_axis_title)}: $domainName"
    }

    fun formatWorkoutMatrixSubtitle(
        cardPrefs: WorkoutCardSectionPreferences, detailPrefs: WorkoutDetailPreferences, context: Context
    ): String {
        val listCount = listOf(cardPrefs.showDescription, cardPrefs.showExtrema, cardPrefs.showLaps, cardPrefs.showStrava, cardPrefs.showMapPreview, cardPrefs.showElevationProfile, cardPrefs.showTelemetryCharts, cardPrefs.showZoneAnalysis, cardPrefs.showRpeAndFeel).count { it }
        val detailCount = listOf(detailPrefs.showDescription, detailPrefs.showExtrema, detailPrefs.showLaps, detailPrefs.showStrava, detailPrefs.showMap, detailPrefs.showElevationProfile, detailPrefs.showTelemetryCharts, detailPrefs.showZoneAnalysis).count { it }
        return context.getString(R.string.tuning_summary_matrix_format, listCount, detailCount)
    }

    fun formatWorkoutMasksSubtitle(cardPrefs: WorkoutCardSectionPreferences, context: Context): String {
        val activeCards = listOf(cardPrefs.showDescription, cardPrefs.showExtrema, cardPrefs.showLaps, cardPrefs.showStrava, cardPrefs.showMapPreview, cardPrefs.showElevationProfile, cardPrefs.showTelemetryCharts, cardPrefs.showZoneAnalysis, cardPrefs.showRpeAndFeel).count { it }
        return context.getString(R.string.tuning_summary_masks_cards_format, activeCards)
    }

    @Deprecated("Use formatWorkoutMasksSubtitle(cardPrefs, context)")
    fun formatWorkoutMasksSubtitle(
        cardPrefs: WorkoutCardSectionPreferences, editPrefs: EditWorkoutFieldPreferences, context: Context
    ): String = formatWorkoutMasksSubtitle(cardPrefs, context)

    fun formatClusterTuningSubtitle(
        endpointTolerance: Float, apexTolerance: Float, distanceTolerance: Float, altitudePositionTolerance: Float,
        useSportTypeForClustering: Boolean, useAltitudePosForClustering: Boolean, isImperial: Boolean, context: Context
    ): String {
        val locale = Locale.getDefault()
        fun formatTolerance(meters: Float): String = if (isImperial) {
            String.format(locale, "%.3f%s", meters * (1.0 / BANALService.METER_PER_MILE).toFloat(), context.getString(R.string.units_distance_imperial))
        } else if (meters <= 1000f) {
            "${meters.roundToInt()} ${context.getString(R.string.units_distance_basic)}"
        } else {
            String.format(locale, "%.3f%s", meters * 0.001f, context.getString(R.string.units_distance_metric))
        }
        val endStr = formatTolerance(endpointTolerance)
        val apexStr = formatTolerance(apexTolerance)
        val distPctStr = "${(distanceTolerance * 100).roundToInt()}%"
        return if (useAltitudePosForClustering) {
            "${context.getString(R.string.tuning_cluster_end_short)}: $endStr · ${context.getString(R.string.tuning_cluster_apex_short)}: $apexStr · ${context.getString(R.string.tuning_cluster_dist_short)}: $distPctStr · ${context.getString(R.string.tuning_cluster_alt_short)}: ${formatTolerance(altitudePositionTolerance)}"
        } else {
            "${context.getString(R.string.tuning_cluster_end_short)}: $endStr · ${context.getString(R.string.tuning_cluster_apex_short)}: $apexStr · ${context.getString(R.string.tuning_cluster_dist_short)}: $distPctStr"
        }
    }

    fun formatAutoPauseSubtitle(
        speedThreshold: Int, speedUnit: String, debounceSec: Int, resumeDebounceSec: Int, context: Context
    ): String = context.getString(
        R.string.tuning_auto_pause_subtitle_format,
        context.getString(R.string.tuning_speed_value_format, speedThreshold, speedUnit),
        context.getString(R.string.tuning_seconds_value_format, debounceSec),
        context.getString(R.string.tuning_seconds_value_format, resumeDebounceSec)
    )

    /**
     * Formats live active Radar edge, alert mode, tail light mode, and traffic simulator status (REQ-UI-512, ATT-6702).
     */
    fun formatCyclingRadarSubtitle(
        edgeSide: RadarEdgeSide,
        alertMode: RadarAudioAlertMode,
        lightMode: RadarLightMode,
        isRunning: Boolean = RadarTrafficSimulator.isSimulating,
        scenario: RadarScenario? = RadarTrafficSimulator.activeScenario,
        context: Context
    ): String {
        return if (isRunning) {
            val scenarioName = when (scenario) {
                RadarScenario.SINGLE_CAR -> context.getString(R.string.radar_scenario_single_car)
                RadarScenario.CONVOY -> context.getString(R.string.radar_scenario_convoy)
                RadarScenario.HIGH_SPEED -> context.getString(R.string.radar_scenario_high_speed)
                RadarScenario.DISCONNECT_RECONNECT -> context.getString(R.string.radar_scenario_disconnect)
                RadarScenario.COUNTRY_ROAD_LOOP -> context.getString(R.string.radar_scenario_country_road)
                null -> ""
            }
            val runningLabel = context.getString(R.string.radar_simulator_running)
            if (scenarioName.isNotEmpty()) "$runningLabel • $scenarioName" else runningLabel
        } else {
            val edgeStr = context.getString(if (edgeSide == RadarEdgeSide.LEFT) R.string.radar_overlay_edge_left else R.string.radar_overlay_edge_right)
            val alertStr = when (alertMode) {
                RadarAudioAlertMode.ALL_ALERTS -> context.getString(R.string.radar_alert_mode_all)
                RadarAudioAlertMode.APPROACH_AND_FAST -> context.getString(R.string.radar_alert_mode_approach_fast)
                RadarAudioAlertMode.FAST_ONLY -> context.getString(R.string.radar_alert_mode_fast_only)
                RadarAudioAlertMode.MUTED -> context.getString(R.string.radar_alert_mode_muted)
            }
            "$edgeStr · $alertStr · ${context.getString(lightMode.titleResId)}"
        }
    }

    fun formatCyclingRadarSubtitle(config: TuningConfig, context: Context): String =
        formatCyclingRadarSubtitle(
            edgeSide = config.radarEdgeSide,
            alertMode = config.radarAudioAlertMode,
            lightMode = config.radarLightDefaultMode,
            isRunning = RadarTrafficSimulator.isSimulating,
            scenario = RadarTrafficSimulator.activeScenario,
            context = context
        )
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

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

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation)
                )
            }

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

/**
 * Thematic group subheader separating accordion blocks in Expert Settings (ATT-5670 / REQ-UI-463).
 */
@Composable
fun TuningGroupSubheader(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}
