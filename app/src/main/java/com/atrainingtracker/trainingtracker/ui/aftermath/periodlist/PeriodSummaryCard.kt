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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds

@Composable
fun PeriodSummaryCard(
    summary: PeriodSummary,
    isPlayServiceAvailable: Boolean,
    isHeatmapEnabled: Boolean,
    onHeaderClick: (PeriodSummary) -> Unit,
    onMapClick: (PeriodSummary) -> Unit,
    onSportClick: (PeriodSummary, BSportType) -> Unit,
    onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val df = DistanceFormatter()
    val tf = TimeFormatter()
    val af = AltitudeFormatter()

    MappableListItem(
        modifier = modifier,
        onClick = { onHeaderClick(summary) }
    ) {
            // --- 1. CONTENT SECTION ---
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                // PERIOD HEADER
                Surface(
                    onClick = { onHeaderClick(summary) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (summary.periodType) {
                                PeriodType.DAY -> Icons.Default.CalendarToday
                                PeriodType.WEEK -> Icons.Default.DateRange
                                PeriodType.MONTH -> Icons.Default.CalendarMonth
                                PeriodType.YEAR -> Icons.Default.CalendarMonth
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.periodLabel,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = summary.periodDateRange,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_time_active),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = tf.format_with_units(summary.totalDurationSec),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.workout_periods__workouts,
                                        summary.totalWorkouts,
                                        summary.totalWorkouts
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SPORT SPECIFIC BREAKDOWN
                summary.sportStats.entries.forEachIndexed { index, entry ->
                    val (bSportType, stats) = entry
                    SportStatsRow(
                        bSportType = bSportType,
                        stats = stats,
                        tf = tf, df = df, af = af,
                        showDetails = true,
                        onClick = { onSportClick(summary, bSportType) },
                        onLongestWorkoutClick = { workoutId -> onLongestWorkoutClick(summary, bSportType, workoutId) }
                    )
                    if (index < summary.sportStats.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // --- 2. THE MAP SECTION ---
            if (isPlayServiceAvailable && summary.polylines.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    PeriodMultiWorkoutMap(
                        polylines = summary.polylines,
                        periodType = summary.periodType,
                        isHeatmapEnabled = isHeatmapEnabled,
                        onMapClick = { onMapClick(summary) }
                    )
                    
                    // Bottom Scrim for visual transition
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = TTAlpha.Ghost))
                                )
                            )
                    )
                }
            }
        }
}

@Composable
fun SportStatsRow(
    bSportType: BSportType,
    stats: SportStats,
    tf: TimeFormatter,
    df: DistanceFormatter,
    af: AltitudeFormatter,
    showDetails: Boolean = false,
    onClick: () -> Unit,
    onLongestWorkoutClick: (Long) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = TTAlpha.SemiTransparent)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // MAIN SPORT HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = bSportType.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(bSportType.stringResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.workout_periods__workouts,
                            stats.count,
                            stats.count
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    MetricItem(
                        iconRes = R.drawable.ic_time_active,
                        value = tf.format_with_units(stats.totalDurationSec),
                        isPrimary = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricItem(R.drawable.ic_distance, df.format_with_units(stats.totalDistanceMeters), isPrimary = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricItem(R.drawable.ic_ascent, af.format_with_units(stats.totalAscentMeters))
                    }
                }
            }

            // NESTED DETAILS
            val longestWorkout = stats.longestWorkout
            val showLongestWorkout = stats.count > 1 && longestWorkout != null
            if (showDetails && (stats.detailedSportStats.size > 1 || showLongestWorkout)) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Subtle)
                )

                // Sub-Sport Types
                if (stats.detailedSportStats.size > 1) {
                    stats.detailedSportStats.forEach { (name, detailed) ->
                        CompactMetricRow(
                            label = name,
                            count = detailed.count,
                            distance = df.format_with_units(detailed.totalDistanceMeters),
                            duration = tf.format_with_units(detailed.totalDurationSec)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Longest Workout Highlight
                if (showLongestWorkout) {
                    // longestWorkout is smart-cast to non-null because it's a local val and showLongestWorkout checks for null
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.clickable { onLongestWorkoutClick(longestWorkout.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.workout_periods__longest_workout),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            MetricItem(
                                iconRes = R.drawable.ic_time_active,
                                value = tf.format_with_units(longestWorkout.durationSec),
                                isPrimary = true,
                                iconSize = 14.dp,
                                valueStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = longestWorkout.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetricItem(
                                    R.drawable.ic_distance,
                                    df.format_with_units(longestWorkout.distanceMeters),
                                    isPrimary = true,
                                    iconSize = 14.dp,
                                    valueStyle = MaterialTheme.typography.bodySmall
                                )
                                MetricItem(
                                    R.drawable.ic_ascent,
                                    af.format_with_units(longestWorkout.ascentMeters),
                                    iconSize = 14.dp,
                                    valueStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMetricRow(label: String, count: Int, distance: String, duration: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricItem(
                iconRes = R.drawable.ic_distance,
                value = distance,
                iconSize = 14.dp,
                valueStyle = MaterialTheme.typography.bodySmall
            )
            MetricItem(
                iconRes = R.drawable.ic_time_active,
                value = duration,
                isPrimary = true,
                iconSize = 14.dp,
                valueStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PeriodMultiWorkoutMap(
    polylines: List<String>,
    periodType: PeriodType,
    isHeatmapEnabled: Boolean,
    onMapClick: () -> Unit) {
    // Decode all polylines once
    val allPaths = remember(polylines) {
        polylines.mapNotNull { if (it.isNotEmpty()) PolyUtil.decode(it) else null }
    }

    val visuals = remember(allPaths, periodType, isHeatmapEnabled) {
        getPeriodMapVisuals(periodType, allPaths, isHeatmapEnabled)
    }

    if (allPaths.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("No GPS data for this period", style = MaterialTheme.typography.labelSmall)
        }
        return
    }

    // 2. Calculate the Bounds for all points in all paths
    val bounds = remember(allPaths) {
        val builder = LatLngBounds.Builder()
        var hasPoints = false
        allPaths.forEach { path ->
            path.forEach { point ->
                builder.include(point)
                hasPoints = true
            }
        }
        if (hasPoints) builder.build() else null
    }

    val cameraPositionState = rememberCameraPositionState()
    var isMapLoaded by remember { mutableStateOf(false) }

    // 3. Apply the zoom as soon as the map is loaded or bounds change
    LaunchedEffect(bounds, isMapLoaded) {
        if (isMapLoaded) {
            bounds?.let {
                try {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngBounds(it, 50) // 50dp padding
                    )
                } catch (e: Exception) {
                    // Map size might still be 0
                }
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.TERRAIN),
        onMapLoaded = { isMapLoaded = true },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false
        ),
        onMapClick = { onMapClick() }
    ) {
        allPaths.forEach { path ->
            Polyline(
                points = path,
                color = TrackType.BEST.color.copy(alpha = visuals.polylineAlpha),
                width = visuals.polylineWidth,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND
            )
        }

        // Heatmap Layer (Drawn on top)
        visuals.heatmapProvider?.let {
            TileOverlay(tileProvider = it)
        }
    }
}



@Preview(showBackground = true, name = "Weekly Summary")
@Composable
fun PreviewPeriodSummary() {
    val mockSummary = PeriodSummary(
        periodLabel = "Week 20",
        periodDateRange = "May 11 - May 17, 2026",
        periodType = PeriodType.WEEK,
        startTimestampS = 1000,
        endTimestampS = 15000,
        totalWorkouts = 5,
        totalDurationSec = 15400,
        polylines = listOf("_p~iF~ps|U_ulLnnqC", "a~lF|ym|U_geC~izE"), // Mock short polylines
        sportStats = mapOf(
            BSportType.BIKE to SportStats(
                count = 3,
                totalDurationSec = 10800,
                totalDistanceMeters = 85400.0,
                totalAscentMeters = 1250,
                detailedSportStats = mapOf(
                    "Road Bike" to DetailedStats(2, 7200, 60000.0, 800),
                    "Mountain Bike" to DetailedStats(1, 3600, 25400.0, 450)
                ),
                longestWorkout = LongestWorkout(1, "Sunday Ride", 7200, 60000.0, 800)
            ),
            BSportType.RUN to SportStats(
                count = 2,
                totalDurationSec = 4600,
                totalDistanceMeters = 18200.0,
                totalAscentMeters = 120,
                detailedSportStats = mapOf(
                    "Running" to DetailedStats(2, 4600, 18200.0, 120)
                ),
                longestWorkout = LongestWorkout(2, "Morning Run", 2800, 10000.0, 70)
            )
        ),
        sortKey = "",
        workoutIdToPolylineMap = emptyMap(),
        workoutIdToSportMap = emptyMap()
    )

    MaterialTheme {
        PeriodSummaryCard(
            summary = mockSummary,
            isPlayServiceAvailable = true,
            isHeatmapEnabled = true,
            onHeaderClick = {},
            onMapClick = {},
            onSportClick = { _, _ ->},
            onLongestWorkoutClick = { _, _, _ ->}
        )
    }
}

@Preview(showBackground = true, name = "Empty Period")
@Composable
fun PreviewEmptyPeriod() {
    val emptySummary = PeriodSummary(
        periodLabel = "June 2026",
        periodDateRange = "No workouts recorded",
        periodType = PeriodType.MONTH,
        startTimestampS = 0,
        endTimestampS = 0,
        totalWorkouts = 0,
        totalDurationSec = 0,
        polylines = emptyList(),
        sportStats = emptyMap(),
        sortKey = "",
        workoutIdToPolylineMap = emptyMap(),
        workoutIdToSportMap = emptyMap()
    )

    MaterialTheme {
        PeriodSummaryCard(
            summary = emptySummary,
            isPlayServiceAvailable = false,
            isHeatmapEnabled = true,
            onHeaderClick = {},
            onMapClick = {},
            onSportClick = { _, _ ->},
            onLongestWorkoutClick = { _, _, _ ->}
        )
    }
}