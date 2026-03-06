package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
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
    fun onMoveField(field: SensorFieldState, toRow: Int, toCol: Int)
    fun onToggleDragMode(isDragging: Boolean)
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
    // CRITICAL: This state survives recompositions of 'state'
    var draggedFieldId by remember { mutableStateOf<Long?>(null) }

    // Find the currently dragged field from the latest state by ID
    val currentlyDraggedField = remember(draggedFieldId, state.fields) {
        state.fields.find { it.sensorFieldId == draggedFieldId }
    }

    Column(Modifier.fillMaxSize()) {
        val fieldsByRow = state.fields.groupBy { it.rowNr }
        val sortedRows = fieldsByRow.keys.sorted()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var maxRowNr = 0
            sortedRows.forEach { rowNr ->
                maxRowNr = rowNr
                // --- ADD field BETWEEN ROWS ---
                if (screenMode == ScreenMode.CONFIGURATION) {
                    RowAdder(
                        isTarget = draggedFieldId != null,
                        onClick = { gridActions.onAddRow(rowNr) },
                        onDrop = {
                            currentlyDraggedField?.let {
                                gridActions.onMoveField(it, rowNr, toCol = -1)
                            }
                            draggedFieldId = null // Reset after drop
                        }
                    )
                }

                val fieldsInThisRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min) // Important for vertical adder alignment
                ) {

                    var maxColNr = 0
                    fieldsInThisRow.forEach { fieldState ->
                        // --- ADD Field BETWEEN FIELDS ---
                        if (screenMode == ScreenMode.CONFIGURATION) {
                            ColAdder(
                                isTarget = draggedFieldId != null,
                                onClick = { gridActions.onAddCol(rowNr,fieldState.colNr) },
                                onDrop = {
                                    currentlyDraggedField?.let {
                                        gridActions.onMoveField(it, rowNr, fieldState.colNr)
                                    }
                                    draggedFieldId = null // Reset after drop
                                }
                            )
                        }
                        maxColNr = fieldState.colNr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (screenMode == ScreenMode.CONFIGURATION) Modifier.pointerInput(
                                        fieldState.sensorFieldId
                                    ) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedFieldId = fieldState.sensorFieldId
                                                gridActions.onToggleDragMode(true) // Signal ViewModel to pause updates
                                            },
                                            onDrag = { change, _ -> change.consume() },
                                            onDragEnd = {
                                                // If the user just lets go without hitting an Adder, we reset
                                                draggedFieldId = null
                                                gridActions.onToggleDragMode(false) // Signal ViewModel to resume updates
                                            },
                                            onDragCancel = {
                                                draggedFieldId = null
                                                gridActions.onToggleDragMode(false) // Signal ViewModel to resume updates
                                            }
                                        )
                                    } else Modifier)
                        ) {
                            SensorFieldView(
                                fieldState = fieldState,
                                screenMode = screenMode,
                                onEdit = { gridActions.onEditField(fieldState) },
                                onDelete = { gridActions.onDeleteField(fieldState) }
                            )
                        }
                    }
                    // --- ADD Field AT END OF the ROW ---
                    if (screenMode == ScreenMode.CONFIGURATION) {
                        ColAdder(
                            isTarget = draggedFieldId != null,
                            onClick = { gridActions.onAddCol(rowNr, maxColNr + 1) },
                            onDrop = {
                                currentlyDraggedField?.let {
                                    gridActions.onMoveField(it, rowNr, maxColNr + 1)
                                }
                                draggedFieldId = null // Reset after drop
                            }
                        )
                    }

                }
            }
            // -- ADD Field as a new row
            if (screenMode == ScreenMode.CONFIGURATION) {
                RowAdder(
                    isTarget = draggedFieldId != null,
                    onClick = { gridActions.onAddRow(maxRowNr + 1) },
                    onDrop = {
                        currentlyDraggedField?.let {
                            gridActions.onMoveField(it, maxRowNr + 1 , -1)
                        }
                        draggedFieldId = null // Reset after drop
                    }
                )
            }
        }


        // Conditionally display the map
        if (showMap) {
            Box(modifier = Modifier.weight(1f)) {
                mapContent()
            }
        }
    }
}

@Composable
private fun RowAdder(isTarget: Boolean, onClick: () -> Unit, onDrop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isTarget) Modifier.pointerInput(Unit) {// In a production app, use a more robust bounds check.
                // For simplicity, we trigger onDrop if the drag ends over this view.
                awaitPointerEventScope {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Release) onDrop()
                }
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add/Move Row",
                tint = if (isTarget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ColAdder(isTarget: Boolean, onClick: () -> Unit, onDrop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .then(if (isTarget) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Release) onDrop()
                }
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add/Move Column",
                tint = if (isTarget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
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
            override fun onMoveField(field: SensorFieldState, toRow: Int, toCol: Int) {}
            override fun onToggleDragMode(isDragging: Boolean) {}
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
            override fun onMoveField(field: SensorFieldState, toRow: Int, toCol: Int) {}
            override fun onToggleDragMode(isDragging: Boolean) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.TRACKING,
            gridActions = mockActions
        )
    }
}