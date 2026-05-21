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

import androidx.activity.compose.BackHandler
import androidx.activity.result.launch
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.helpers.combineAndShare
import com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodMapScreen(
    summary: PeriodSummary,
    onWorkoutClick: (Long) -> Unit,
    peekedWorkoutDataWithTrack: WorkoutDataWithTrack?,
    clearPeekSelection: () -> Unit,
    onBack: () -> Unit
) {
    val df = DistanceFormatter()
    val tf = TimeFormatter()

    // Track multiple selected sports
    var selectedSports by rememberSaveable { mutableStateOf(setOf<BSportType>()) }
    val filteredWorkouts = remember(summary, selectedSports) {
        if (selectedSports.isEmpty()) {
            summary.workoutIdToPolylineMap
        } else {
            summary.workoutIdToPolylineMap.filter { (id, _) ->
                val sport = summary.workoutIdToSportMap[id]
                selectedSports.contains(sport)
            }
        }
    }

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
                zoomFocus = MapZoomFocus.TRACK_AND_MARKERS,
                bSportType = workout.workoutData.bSportType
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // This layer will "record" the stats header
    val statsGraphicsLayer = rememberGraphicsLayer()

    // We need a reference to trigger the map snapshot
    var mapSnapshotTrigger by remember { mutableStateOf(false) }

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
            // 1. HEADER (Stats) - Wrapped in GraphicsLayer for sharing
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .drawWithContent {
                    statsGraphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(statsGraphicsLayer)
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // PERIOD HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                        val isSelected = selectedSports.contains(sport)
                        // Logic: If nothing is selected, everything is 1f.
                        // If something is selected, dim everything except the selected ones.
                        // val rowAlpha = if (selectedSports.isEmpty() || isSelected) 1f else 0.5f
                        val rowAlpha = if (isSelected) 1f else 0.5f

                        Box(modifier = Modifier.alpha(rowAlpha)) {
                            SportStatsRow(
                                bSportType = sport,
                                stats = stats,
                                df = df, tf = tf,
                                onClick = {
                                    // Multi-select toggle logic
                                    selectedSports = if (isSelected) {
                                        selectedSports - sport // Remove if already there
                                    } else {
                                        selectedSports + sport // Add if not there
                                    }

                                    // Close peek if a filter change makes the peeked workout disappear
                                    if (peekedWorkoutDataWithTrack != null) {
                                        val peekedSport =
                                            peekedWorkoutDataWithTrack.workoutData?.bSportType
                                        if (selectedSports.isNotEmpty() && !selectedSports.contains(
                                                peekedSport
                                            )
                                        ) {
                                            clearPeekSelection()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }


            // 2. INTERACTIVE MAP WITH OVERLAYED BUTTON
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                InteractivePeriodMap(
                    workouts = filteredWorkouts,
                    onWorkoutClick = onWorkoutClick,
                    modifier = Modifier.fillMaxSize(),
                    shouldTakeSnapshot = mapSnapshotTrigger,
                    onSnapshotReady = { mapBitmap ->
                        scope.launch {
                            val headerBitmap = statsGraphicsLayer.toImageBitmap().asAndroidBitmap()
                            combineAndShare(context, headerBitmap, mapBitmap)
                            mapSnapshotTrigger = false
                        }
                    },
                )

                // FLOATING SHARE BUTTON
                Surface(
                    onClick = { mapSnapshotTrigger = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 6.dp,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Handle Back Press to remove the peeked workout or return to list
    BackHandler() {
        if (peekedWorkoutDataWithTrack != null) {
            clearPeekSelection()
        }
        else {
            onBack()
        }
    }
}