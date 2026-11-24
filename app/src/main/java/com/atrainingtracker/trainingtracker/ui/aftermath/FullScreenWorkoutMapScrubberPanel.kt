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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.FullScreenScrubReadout
import com.atrainingtracker.trainingtracker.ui.map.FullScreenScrubberMetric
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraph
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMath
import com.atrainingtracker.trainingtracker.ui.map.formatFullScreenScrubReadout
import com.atrainingtracker.trainingtracker.ui.map.interpolateScrubAltitude
import com.atrainingtracker.trainingtracker.ui.map.toTelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * Deterministic semantics test tags for the collapsible full-screen workout map metric scrubber
 * and its floating live readout pill (`REQ-MAP-070`, `ATT-9030`).
 */
internal object FullScreenWorkoutScrubberTestTags {
    const val PANEL_ROOT = "fullscreen_map_scrubber_root"
    const val EXPANDED_CARD = "fullscreen_map_scrubber_expanded"
    const val COLLAPSED_PILL = "fullscreen_map_scrubber_collapsed"
    const val COLLAPSE_BUTTON = "fullscreen_map_scrubber_collapse_button"
    const val EXPAND_BUTTON = "fullscreen_map_scrubber_expand_button"
    const val READOUT_PILL = "fullscreen_map_scrubber_readout_pill"
    const val READOUT_CLEAR_BUTTON = "fullscreen_map_scrubber_readout_clear"
    const val GRAPH_CONTAINER = "fullscreen_map_scrubber_graph"
    fun metricChip(metric: FullScreenScrubberMetric): String = "fullscreen_map_scrubber_metric_${metric.name}"
    fun activeMetricGraph(metric: FullScreenScrubberMetric): String = "fullscreen_map_scrubber_graph_${metric.name}"
}

/**
 * Collapsible bottom metric scrubber panel and floating live scrub readout pill docked at the bottom
 * of the full-screen workout map (`REQ-MAP-070.1`, `REQ-MAP-070.2`, `REQ-MAP-070.3`).
 *
 * Reuses [ElevationProfile] (preserving canonical adaptive height per Design Guidelines Section 2.3)
 * and [TelemetryMetricGraph] plotted against [ProfileXAxisDomain.DISTANCE] with
 * `clearSelectionOnDragEnd = false` so the athlete's scrubbed position remains pinned upon finger lift.
 */
@Composable
internal fun FullScreenWorkoutMapScrubberPanel(
    path: List<PathPoint>,
    bSportType: BSportType,
    availableMetrics: List<FullScreenScrubberMetric>,
    selectedMetric: FullScreenScrubberMetric,
    onSelectMetric: (FullScreenScrubberMetric) -> Unit,
    isCollapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    selectedDistance: Double?,
    activeScrubPoint: PathPoint?,
    onDistanceSelected: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    climbs: List<Climb> = emptyList(),
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    hrZoneDistribution: ZoneDistributionData? = null,
    powerZoneDistribution: ZoneDistributionData? = null,
    tuningConfig: TuningConfig = TuningConfig(),
    onDockHeightChanged: ((Dp) -> Unit)? = null
) {
    if (availableMetrics.isEmpty() || path.size < 2) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) {
        configuration.locales.let { if (it.isEmpty) Locale.getDefault() else it[0] }
    }
    val unit = remember {
        runCatching { TrainingApplication.getUnit() }.getOrDefault(MyUnits.METRIC)
    }
    val effectiveHrThresholds = remember(hrZoneThresholds, bSportType, context) {
        val resolved = hrZoneThresholds ?: runCatching {
            TelemetryZoneMath.loadHeartRateThresholds(context, bSportType)
        }.getOrNull()
        resolved?.takeIf { TelemetryZoneMath.isValidHeartRateThresholds(it) }
    }
    val effectivePowerThresholds = remember(powerZoneThresholds, context) {
        val resolved = powerZoneThresholds ?: runCatching {
            TelemetryZoneMath.loadPowerThresholds(context)
        }.getOrNull()
        resolved?.takeIf { TelemetryZoneMath.isValidPowerThresholds(it) }
    }

    val interpolatedAltitude = remember(path, selectedDistance) {
        selectedDistance?.let { interpolateScrubAltitude(path, it) }
    }

    val readout = remember(
        activeScrubPoint,
        selectedMetric,
        bSportType,
        unit,
        interpolatedAltitude,
        effectiveHrThresholds,
        effectivePowerThresholds,
        currentLocale
    ) {
        activeScrubPoint?.let { point ->
            formatFullScreenScrubReadout(
                point = point,
                selectedMetric = selectedMetric,
                bSportType = bSportType,
                unit = unit,
                interpolatedAltitude = interpolatedAltitude,
                hrZoneThresholds = effectiveHrThresholds,
                powerZoneThresholds = effectivePowerThresholds,
                locale = currentLocale
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(FullScreenWorkoutScrubberTestTags.PANEL_ROOT),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isCollapsed) {
            CollapsedScrubberExpandPill(
                metricLabel = resolveFullScreenScrubberMetricLabel(selectedMetric, bSportType),
                onExpand = { onCollapsedChange(false) },
                modifier = Modifier.onSizeChanged { size ->
                    onDockHeightChanged?.invoke(with(density) { size.height.toDp() } + 16.dp)
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedDistance != null && readout != null) {
                    FullScreenScrubReadoutPill(
                        readout = readout,
                        onClear = { onDistanceSelected(null) }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 6.dp,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            onDockHeightChanged?.invoke(with(density) { size.height.toDp() } + 16.dp)
                        }
                        .testTag(FullScreenWorkoutScrubberTestTags.EXPANDED_CARD)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        // Header Row: Horizontal scrollable metric chips + pinned Collapse icon button
                        val selectorCd = stringResource(R.string.workout_map_scrubber_metric_selector_cd)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                                    .semantics { contentDescription = selectorCd },
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                availableMetrics.forEach { metric ->
                                    ScrubberMetricChip(
                                        label = resolveFullScreenScrubberMetricLabel(metric, bSportType),
                                        selected = metric == selectedMetric,
                                        onClick = { onSelectMetric(metric) },
                                        testTag = FullScreenWorkoutScrubberTestTags.metricChip(metric)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onCollapsedChange(true) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag(FullScreenWorkoutScrubberTestTags.COLLAPSE_BUTTON)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.workout_map_scrubber_collapse),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Single Active Scrubbable Graph
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(FullScreenWorkoutScrubberTestTags.GRAPH_CONTAINER)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(FullScreenWorkoutScrubberTestTags.activeMetricGraph(selectedMetric))
                            ) {
                                val telemetryType = selectedMetric.toTelemetryMetricType(bSportType)
                                if (telemetryType == null) {
                                    ElevationProfile(
                                        pathPoints = path,
                                        currentDistance = selectedDistance,
                                        minAltitudeOverride = minAltitudeOverride,
                                        maxAltitudeOverride = maxAltitudeOverride,
                                        onDistanceSelected = onDistanceSelected,
                                        showZoomControls = true,
                                        xAxisDomain = ProfileXAxisDomain.DISTANCE,
                                        bSportType = bSportType,
                                        showScrubbingBadge = false,
                                        hrZoneThresholds = effectiveHrThresholds,
                                        powerZoneThresholds = effectivePowerThresholds,
                                        climbs = climbs,
                                        alignWithTelemetryCharts = false,
                                        clearSelectionOnDragEnd = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    TelemetryMetricGraph(
                                        pathPoints = path,
                                        metricType = telemetryType,
                                        currentDistance = selectedDistance,
                                        onDistanceSelected = onDistanceSelected,
                                        xAxisDomain = ProfileXAxisDomain.DISTANCE,
                                        bSportType = bSportType,
                                        hrZoneThresholds = effectiveHrThresholds,
                                        powerZoneThresholds = effectivePowerThresholds,
                                        paceCeilingMinKm = tuningConfig.paceCeilingMinKm,
                                        enableGestures = true,
                                        zoneDistribution = when (telemetryType) {
                                            TelemetryMetricType.HEART_RATE -> hrZoneDistribution
                                            TelemetryMetricType.POWER -> powerZoneDistribution
                                            else -> null
                                        },
                                        zoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                                        enableCardioPowerDynamics = false,
                                        dfaA1ThresholdT1 = tuningConfig.dfaA1ThresholdT1,
                                        dfaA1ThresholdT2 = tuningConfig.dfaA1ThresholdT2,
                                        clearSelectionOnDragEnd = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact floating live scrub readout pill anchored directly above the expanded scrubber graph
 * (`REQ-MAP-070.3`). Displays `Distance · Time · Selected Metric` (with optional zone tag) on the
 * primary line and concise available secondary metrics underneath.
 */
@Composable
internal fun FullScreenScrubReadoutPill(
    readout: FullScreenScrubReadout,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier.testTag(FullScreenWorkoutScrubberTestTags.READOUT_PILL)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val primaryAnnotated = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                        append(readout.distanceText)
                    }
                    if (!readout.timeText.isNullOrEmpty()) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(" · ")
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)) {
                            append(readout.timeText)
                        }
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" · ")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append(readout.selectedMetricText)
                    }
                    val zone = readout.selectedZoneNumber
                    if (zone != null && zone in 1..TelemetryZoneMath.ZONE_COLORS.size) {
                        withStyle(
                            SpanStyle(
                                color = TelemetryZoneMath.ZONE_COLORS[zone - 1],
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" · Z$zone")
                        }
                    }
                }
                Text(
                    text = primaryAnnotated,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (readout.secondaryContextItems.isNotEmpty()) {
                    Text(
                        text = readout.secondaryContextItems.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(FullScreenWorkoutScrubberTestTags.READOUT_CLEAR_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.workout_map_scrubber_clear_selection),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CollapsedScrubberExpandPill(
    metricLabel: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.testTag(FullScreenWorkoutScrubberTestTags.COLLAPSED_PILL)
    ) {
        Surface(
            onClick = onExpand,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .testTag(FullScreenWorkoutScrubberTestTags.EXPAND_BUTTON)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = metricLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.workout_map_scrubber_expand),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScrubberMetricChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .heightIn(min = 34.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun resolveFullScreenScrubberMetricLabel(
    metric: FullScreenScrubberMetric,
    bSportType: BSportType
): String = when (metric) {
    FullScreenScrubberMetric.ELEVATION -> stringResource(R.string.workout_map_scrubber_metric_elevation)
    FullScreenScrubberMetric.HEART_RATE -> stringResource(R.string.graph_heading_heart_rate)
    FullScreenScrubberMetric.POWER -> stringResource(R.string.graph_heading_power)
    FullScreenScrubberMetric.SPEED_PACE -> if (bSportType == BSportType.RUN) {
        stringResource(R.string.graph_heading_pace)
    } else {
        stringResource(R.string.graph_heading_speed)
    }
    FullScreenScrubberMetric.CADENCE -> stringResource(R.string.graph_heading_cadence)
    FullScreenScrubberMetric.SLOPE -> stringResource(R.string.graph_heading_slope)
    FullScreenScrubberMetric.W_PRIME_BAL -> stringResource(R.string.w_prime_bal)
    FullScreenScrubberMetric.HRV_DFA_A1 -> stringResource(R.string.hrv_dfa_a1)
    FullScreenScrubberMetric.HRV_RMSSD -> stringResource(R.string.hrv_rmssd)
}

@Preview(name = "Scrubber Expanded Light", showBackground = true)
@Preview(name = "Scrubber Expanded Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FullScreenWorkoutMapScrubberExpandedPreview() {
    val samplePath = listOf(
        PathPoint(0.0, LatLng(48.137, 11.575), altitude = 520.0, timeSec = 0L, hr = 132, power = 210, speedMps = 8.2, slope = 1.2, cadence = 86),
        PathPoint(7100.0, LatLng(48.148, 11.592), altitude = 595.0, timeSec = 860L, hr = 154, power = 265, speedMps = 7.9, slope = 4.6, cadence = 88),
        PathPoint(14200.0, LatLng(48.161, 11.610), altitude = 642.0, timeSec = 1785L, hr = 164, power = 285, speedMps = 8.7, slope = 4.2, cadence = 90),
        PathPoint(21500.0, LatLng(48.175, 11.628), altitude = 540.0, timeSec = 2610L, hr = 148, power = 230, speedMps = 9.4, slope = -2.8, cadence = 92)
    )
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FullScreenWorkoutMapScrubberPanel(
                path = samplePath,
                bSportType = BSportType.BIKE,
                availableMetrics = listOf(
                    FullScreenScrubberMetric.ELEVATION,
                    FullScreenScrubberMetric.HEART_RATE,
                    FullScreenScrubberMetric.POWER,
                    FullScreenScrubberMetric.SPEED_PACE,
                    FullScreenScrubberMetric.CADENCE
                ),
                selectedMetric = FullScreenScrubberMetric.POWER,
                onSelectMetric = {},
                isCollapsed = false,
                onCollapsedChange = {},
                selectedDistance = 14200.0,
                activeScrubPoint = samplePath[2],
                onDistanceSelected = {}
            )
        }
    }
}

@Preview(name = "Scrubber Collapsed Light", showBackground = true)
@Preview(name = "Scrubber Collapsed Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FullScreenWorkoutMapScrubberCollapsedPreview() {
    val samplePath = listOf(
        PathPoint(0.0, LatLng(48.137, 11.575), altitude = 520.0, timeSec = 0L),
        PathPoint(10000.0, LatLng(48.161, 11.610), altitude = 642.0, timeSec = 1200L)
    )
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FullScreenWorkoutMapScrubberPanel(
                path = samplePath,
                bSportType = BSportType.BIKE,
                availableMetrics = listOf(FullScreenScrubberMetric.ELEVATION),
                selectedMetric = FullScreenScrubberMetric.ELEVATION,
                onSelectMetric = {},
                isCollapsed = true,
                onCollapsedChange = {},
                selectedDistance = null,
                activeScrubPoint = null,
                onDistanceSelected = {}
            )
        }
    }
}
