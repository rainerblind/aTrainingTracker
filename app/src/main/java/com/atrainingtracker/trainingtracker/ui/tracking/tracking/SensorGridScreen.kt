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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheet
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.LightBackground
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow

interface GridActions {
    fun onEditField(fieldState: SensorFieldState)
    fun onDeleteField(fieldState: SensorFieldState)
    fun onAddRow(beforeRow: Int)
    fun onAddCol(atRow: Int, beforeCol: Int)
}

/**
 * A generic screen that displays a grid of sensor fields for either tracking or configuration.
 * It adapts its UI and behavior based on the provided [screenMode].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorGridScreen(
    state: TrackingScreenState,
    screenMode: ScreenMode,
    gridActions: GridActions,
    currentLocationFlow: StateFlow<LatLng?>,
    liveSegments: StateFlow<List<LiveSegment>>,
) {

    val activeSegments by liveSegments.collectAsState()
    val activeSegment = activeSegments.firstOrNull()

    val showLiveSegments = state.showLiveSegments && activeSegment != null

    // Control the sheet state
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false // Allow it to hide if no segment
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetDragHandle = null, // Removes the large top spacer entirely
        // Only show sheet if we are in tracking mode and have an active segment
        sheetPeekHeight = if (showLiveSegments && screenMode == ScreenMode.TRACKING) 170.dp else 0.dp,
        sheetSwipeEnabled = showLiveSegments,
        sheetContent = {
            if (showLiveSegments) {
                LiveSegmentSheet(
                    liveSegment = activeSegment
                )
            } else {
                Box(Modifier
                    .fillMaxWidth()
                    .height(1.dp)) // Empty placeholder
            }
        }
    ) { paddingValues ->
        // Use a Column as the main container for the content
        // We do NOT apply the full paddingValues.bottom here because we want the
        // Map to draw UNDER the bottom sheet for a modern look.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()) // Only pad the top
        ) {
            // 1. The Sensor Grid (Scrollable)
            // This Column will only take as much space as the sensors need.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val fieldsByRow = state.fields.groupBy { it.rowNr }
                val sortedRows = fieldsByRow.keys.sorted()
                var maxRowNr = 0

                sortedRows.forEach { rowNr ->
                    maxRowNr = rowNr
                    if (screenMode == ScreenMode.CONFIGURATION) {
                        RowAdder(onClick = { gridActions.onAddRow(rowNr) })
                    }

                    val fieldsInThisRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        var maxColNr = 0
                        fieldsInThisRow.forEach { fieldState ->
                            if (screenMode == ScreenMode.CONFIGURATION) {
                                ColAdder(onClick = { gridActions.onAddCol(rowNr, fieldState.colNr) })
                            }
                            maxColNr = fieldState.colNr
                            Box(modifier = Modifier.weight(1f)) {
                                SensorFieldView(
                                    fieldState = fieldState,
                                    screenMode = screenMode,
                                    onEdit = { gridActions.onEditField(fieldState) },
                                    onDelete = { gridActions.onDeleteField(fieldState) }
                                )
                            }
                        }
                        if (screenMode == ScreenMode.CONFIGURATION) {
                            ColAdder(onClick = { gridActions.onAddCol(rowNr, maxColNr + 1) })
                        }
                    }
                }
                if (screenMode == ScreenMode.CONFIGURATION) {
                    RowAdder(onClick = { gridActions.onAddRow(maxRowNr + 1) })
                }
            }

            // 2. The Map (Expanded)
            // By using weight(1f) here, the Map will fill every pixel between
            // the bottom of the sensors and the bottom of the screen.
            if (state.showMap) {
                ATrainingTrackerMap(
                    mapState = state.mapState,
                    currentLocationFlow = currentLocationFlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Fills remaining space
                )
            }
        }
    }
}

@Composable
private fun RowAdder(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Row",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ColAdder(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxHeight()) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Column",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}


val dummyLocationFlow = kotlinx.coroutines.flow.MutableStateFlow(
    com.google.android.gms.maps.model.LatLng(48.8566, 2.3522) // Paris, for example
)
val dummySegmentsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<LiveSegment>>(emptyList())

// Preview for Configuration Mode
@Preview(showBackground = true, name = "Config Mode")
@Composable
fun SensorGridScreenConfigPreview() {
    val context = LocalContext.current
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.NORMAL, label = "Pace", value = "5:31", units = "/min", zoneColor = LightBackground, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.NORMAL, label = "Heart Rate", value = "145", units = "bpm", zoneColor = Zone1, filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = LightBackground, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.CONFIGURATION,
            gridActions = mockActions,
            currentLocationFlow = dummyLocationFlow,
            liveSegments = dummySegmentsFlow
        )
    }
}

// Preview for Tracking Mode
@Preview(showBackground = true, name = "Tracking Mode")
@Composable
fun SensorGridScreenTrackingPreview() {
    val context = LocalContext.current
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.LARGE, label = "Pace", value = "5:31", units = "/min", zoneColor = LightBackground, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.LARGE, label = "Heart Rate", value = "145", zoneColor = Zone1, units = "bpm", filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = LightBackground, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.TRACKING,
            gridActions = mockActions,
            currentLocationFlow = dummyLocationFlow,
            liveSegments = dummySegmentsFlow
        )
    }
}