package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.DefaultBackgroundColor
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.aTrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize

// height of the lap button.  Necessary to add spacer with same height when there also the map is shown.
private val LapButtonHeight = 88.dp

/**
 * The main screen that displays a grid of sensor fields.
 * It groups fields by their 'rowNr' and then lays them out horizontally
 * using a weighted 'Row' for the columns.
 *
 * @param state The current state of the screen, containing the list of fields.
 */
@Composable
fun TrackingScreen(
    state: TrackingScreenState,
    showMap: Boolean,
    showLapButton: Boolean,
    onFieldLongClick: (fieldState: SensorFieldState) -> Unit,
    mapContent: @Composable () -> Unit
) {


    Column(Modifier.fillMaxSize()) {
        // This inner Column holds the dynamic grid and will expand to fill available space.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Group all fields by their row number. This returns a Map<Int, List<SensorFieldState>>.
            val fieldsByRow = state.fields.groupBy { it.rowNr }

            // Get the sorted row numbers to ensure the layout is in the correct order.
            val sortedRows = fieldsByRow.keys.sorted()

            // Create a Row for each group of fields.
            sortedRows.forEach { rowNr ->
                val fieldsInRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()

                // Each Row in the column will take up a proportional amount of the vertical space.
                Row {
                    // Add each SensorFieldView to the Row.
                    fieldsInRow.forEach { fieldState ->
                        SensorFieldView(
                            modifier = Modifier
                                .weight(1f), // Distribute width equally among all columns in this row
                            onLongClick = { onFieldLongClick(fieldState) },
                            fieldState = fieldState
                        )
                    }
                }
            }
        }

        // Conditionally display the map which will expand.
        if (showMap) {
            // This Box will expand to fill the remaining space
            Box(modifier = Modifier.weight(1f)) {
                mapContent() // Invoke the passed-in composable
            }
        }

        // Add a Spacer at the bottom of the main content when the lap button is shown for this tab.
        if (showLapButton) {
            Spacer(modifier = Modifier.height(LapButtonHeight))
        }
    }
}

/**
 * A simple placeholder that reserves space for the map fragment.
 */
@Composable
private fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant), // A background to see the area
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Map Area",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF212121)
@Composable
fun TrackingScreenPreview() {
    aTrainingTrackerTheme {
        val previewFields = listOf(
            // Row 0, Col 0
            SensorFieldState(
                configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0,
                viewSize = ViewSize.SMALL,
                label = "Pace",
                filterDescription = "10s", value = "5:31", units = "/km",
                zoneColor = DefaultBackgroundColor
            ),
            // Row 0, Col 1
            SensorFieldState(
                configHash = 4, sensorFieldId = 1, rowNr = 0, colNr = 1,
                viewSize = ViewSize.LARGE,
                label = "Distance",
                filterDescription = "Total", value = "10.34", units = "km",
                zoneColor = DefaultBackgroundColor
            ),
            // Row 1, Col 0
            SensorFieldState(
                configHash = 2, sensorFieldId = 1, rowNr = 1, colNr = 0,
                viewSize = ViewSize.LARGE,
                label = "Heart Rate",
                filterDescription = "inst.", value = "145", units = "bpm",
                zoneColor = Zone1
            ),
            // Row 2, Col 0
            SensorFieldState(
                configHash = 3, sensorFieldId = 1, rowNr = 2, colNr = 0,
                viewSize = ViewSize.XLARGE,
                label = "Power",
                filterDescription = "3s", value = "280", units = "W",
                zoneColor = DefaultBackgroundColor
            )
        )
        TrackingScreen(
            state = TrackingScreenState(fields = previewFields),
            showMap = true,
            showLapButton = true,
            onFieldLongClick = { field ->
                // In a preview, we can just log it or do nothing.
                println("Long clicked on: ${field.label}")
            },
            mapContent = {
                MapPlaceholder()
            }
        )
    }
}