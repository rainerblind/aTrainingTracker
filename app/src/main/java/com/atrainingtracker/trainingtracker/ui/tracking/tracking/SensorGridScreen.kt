package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.DefaultBackgroundColor
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize

interface GridActions {
    fun onEditField(fieldState: SensorFieldState)
    fun onDeleteField(fieldState: SensorFieldState)
    fun onAddRow(atRow: Int)
    fun onAddCol(atRow: Int, atCol: Int)
}

/**
 * A generic screen that displays a grid of sensor fields for either tracking or configuration.
 * It adapts its UI and behavior based on the provided [screenMode].
 */
@Composable
fun SensorGridScreen(
    state: TrackingScreenState,
    screenMode: ScreenMode,
    gridActions: GridActions,
    showMap: Boolean = false,
    mapContent: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        // In CONFIGURATION mode, use the slightly more complex grid builder.
        // In TRACKING mode, use the simple, efficient renderer.
        if (screenMode == ScreenMode.CONFIGURATION) {
            ConfigGrid(state = state, gridActions = gridActions)
        } else {
            TrackingGrid(state = state, gridActions = gridActions)
        }

        // Conditionally display the map
        if (showMap) {
            Log.i("SensorGridScreen", "Map is visible")
            Box(modifier = Modifier.weight(1f)) {
                mapContent()
            }
        }
    }
}

/**
 * Renders the simple grid for TRACKING mode.
 */
@Composable
private fun TrackingGrid(state: TrackingScreenState, gridActions: GridActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        val fieldsByRow = state.fields.groupBy { it.rowNr }
        val sortedRows = fieldsByRow.keys.sorted()

        sortedRows.forEach { rowNr ->
            val fieldsInRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
            Row {
                fieldsInRow.forEach { fieldState ->
                    SensorFieldView(
                        modifier = Modifier.weight(1f),
                        fieldState = fieldState,
                        screenMode = ScreenMode.TRACKING,
                        onEdit = { gridActions.onEditField(fieldState) }
                    )
                }
            }
        }
    }
}

/**
 * Renders the advanced, editable grid for CONFIGURATION mode.
 */
@Composable
private fun ConfigGrid(state: TrackingScreenState, gridActions: GridActions) {
    val fieldsByRow = state.fields.groupBy { it.rowNr }
    val sortedRows = fieldsByRow.keys.sorted()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- ADD ROW AT TOP ---
        RowAdder(onClick = { gridActions.onAddRow(0) })

        sortedRows.forEach { rowNr ->
            val fieldsInThisRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min) // Important for vertical adder alignment
            ) {
                // --- ADD COL AT START OF ROW ---
                ColAdder(onClick = { gridActions.onAddCol(rowNr, 0) })

                fieldsInThisRow.forEach { fieldState ->
                    Box(modifier = Modifier.weight(1f)) {
                        SensorFieldView(
                            fieldState = fieldState,
                            screenMode = ScreenMode.CONFIGURATION,
                            onEdit = { gridActions.onEditField(fieldState) },
                            onDelete = { gridActions.onDeleteField(fieldState) }
                        )
                    }
                    // --- ADD COL BETWEEN FIELDS ---
                    ColAdder(onClick = { gridActions.onAddCol(rowNr, fieldState.colNr + 1) })
                }
            }
            // --- ADD ROW BETWEEN ROWS ---
            RowAdder(onClick = { gridActions.onAddRow(rowNr + 1) })
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


// Preview for Configuration Mode
@Preview(showBackground = true, name = "Config Mode")
@Composable
fun SensorGridScreenConfigPreview() {
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.NORMAL, label = "Pace", value = "5:31", units = "/min", zoneColor = DefaultBackgroundColor, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.NORMAL, label = "Heart Rate", value = "145", units = "bpm", zoneColor = Zone1, filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = DefaultBackgroundColor, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(atRow: Int) {}
            override fun onAddCol(atRow: Int, atCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.CONFIGURATION,
            gridActions = mockActions
        )
    }
}

// Preview for Tracking Mode
@Preview(showBackground = true, name = "Tracking Mode")
@Composable
fun SensorGridScreenTrackingPreview() {
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.LARGE, label = "Pace", value = "5:31", units = "/min", zoneColor = DefaultBackgroundColor, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.LARGE, label = "Heart Rate", value = "145", zoneColor = Zone1, units = "bpm", filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = DefaultBackgroundColor, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(atRow: Int) {}
            override fun onAddCol(atRow: Int, atCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.TRACKING,
            gridActions = mockActions
        )
    }
}