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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.TrackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodMapScreen(
    summary: PeriodSummary,
    onWorkoutClick: (Long) -> Unit,
    peekedWorkoutDataWithTrack: WorkoutDataWithTrack?,
    clearPeekSelection: () -> Unit
) {
    val df = DistanceFormatter()
    val tf = TimeFormatter()

    // Prepare MapState for the TrackOnMapScreen
    val mapState = remember(peekedWorkoutDataWithTrack) {
        peekedWorkoutDataWithTrack?.let { workout ->
            MapState(
                tracks = listOf(
                    MapTrack(
                        id = workout.workoutData!!.id,
                        type = TrackType.BEST,
                        path = workout.trackPoints
                    )
                ),
                zoomFocus = MapZoomFocus.TRACK_AND_MARKERS
            )
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )

    // 2. Control sheet expansion when a workout is tapped
    LaunchedEffect(peekedWorkoutDataWithTrack) {
        if (peekedWorkoutDataWithTrack != null) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
    }
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (peekedWorkoutDataWithTrack != null) 200.dp else 0.dp,
        sheetDragHandle = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                Box(Modifier.size(width = 32.dp, height = 3.dp))
            }
        },
        sheetContent = {
            if (peekedWorkoutDataWithTrack != null) {
                // Here we show the TrackOnMapScreen for the specific workout
                TrackOnMapScreen(
                    workoutData = peekedWorkoutDataWithTrack.workoutData!!,
                    mapState = mapState!!,
                    modifier = Modifier
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. HEADER (Stats)
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                // PERIOD HEADER
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // SPORT SPECIFIC BREAKDOWN
                summary.sportStats.forEach { (sport, stats) ->
                    SportStatsRow(sport, stats, tf, df)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 2. INTERACTIVE MAP
            InteractivePeriodMap(
                workouts = summary.workoutIdToPolylineMap,
                onWorkoutClick = onWorkoutClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}