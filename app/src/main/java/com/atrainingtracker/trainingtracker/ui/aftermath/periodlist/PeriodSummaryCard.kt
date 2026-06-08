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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds

@Composable
fun PeriodSummaryCard(
    summary: PeriodSummary,
    isPlayServiceAvailable: Boolean,
    onHeaderClick: (PeriodSummary) -> Unit,
    onMapClick: (PeriodSummary) -> Unit,
    onSportClick: (PeriodSummary, BSportType) -> Unit,
    modifier: Modifier = Modifier
) {
    val df = DistanceFormatter()
    val tf = TimeFormatter()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // --- 1. CONTENT SECTION ---
            Column(modifier = Modifier.padding(16.dp)) {
                // PERIOD HEADER
                Surface(
                    onClick = { onHeaderClick(summary) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = summary.periodLabel,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = summary.periodDateRange,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.workout_periods__workouts,
                                    summary.totalWorkouts,
                                    summary.totalWorkouts
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = tf.format_with_units(summary.totalDurationSec),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // SPORT SPECIFIC BREAKDOWN
                summary.sportStats.forEach { (bSportType, stats) ->
                    SportStatsRow(
                        bSportType = bSportType,
                        stats = stats,
                        tf = tf,
                        df = df,
                        onClick = { onSportClick(summary, bSportType) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // --- 2. THE MAP SECTION ---
            if (isPlayServiceAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    PeriodMultiWorkoutMap(
                        polylines = summary.polylines,
                        onMapClick = { onMapClick(summary) }
                    )
                }
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp), // Add slight padding for touch target
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sport Icon
            Icon(
                painter = painterResource(id = bSportType.iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Sport Name & Count
            Column(modifier = Modifier.weight(1.2f)) {
                Text(
                    text = stringResource(bSportType.stringResId),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.workout_periods__workouts,
                        stats.count,
                        stats.count
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Distance & Ascent
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = df.format_with_units(stats.totalDistanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stats.totalAscentMeters.toInt()} m ↑",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Time for this sport
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = tf.format_with_units(stats.totalDurationSec),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PeriodMultiWorkoutMap(
    polylines: List<String>,
    onMapClick: () -> Unit) {
    // Decode all polylines once
    val allPaths = remember(polylines) {
        polylines.mapNotNull { if (it.isNotEmpty()) PolyUtil.decode(it) else null }
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
                color = TrackType.BEST.color,
                width = 8f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND
            )
        }
    }
}



@Preview(showBackground = true, name = "Weekly Summary")
@Composable
fun PreviewPeriodSummary() {
    val mockSummary = PeriodSummary(
        periodLabel = "Week 20",
        periodDateRange = "May 11 - May 17, 2026",
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
                totalAscentMeters = 1250
            ),
            BSportType.RUN to SportStats(
                count = 2,
                totalDurationSec = 4600,
                totalDistanceMeters = 18200.0,
                totalAscentMeters = 120
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
            onHeaderClick = {},
            onMapClick = {},
            onSportClick = { _, _ ->}
        )
    }
}

@Preview(showBackground = true, name = "Empty Period")
@Composable
fun PreviewEmptyPeriod() {
    val emptySummary = PeriodSummary(
        periodLabel = "June 2026",
        periodDateRange = "No workouts recorded",
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
            onHeaderClick = {},
            onMapClick = {},
            onSportClick = { _, _ ->}
        )
    }
}