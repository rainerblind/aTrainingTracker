package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.DefaultBackgroundColor
import com.atrainingtracker.trainingtracker.ui.theme.Zone1
import com.atrainingtracker.trainingtracker.ui.theme.aTrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView

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
    onFieldLongClick: (fieldState: SensorFieldState) -> Unit
) {
    // Group all fields by their row number. This returns a Map<Int, List<SensorFieldState>>.
    val fieldsByRow = state.fields.groupBy { it.rowNr }

    // Get the sorted row numbers to ensure the layout is in the correct order.
    val sortedRows = fieldsByRow.keys.sorted()

    // The main layout is a Column that fills the entire screen.
    Column(Modifier.fillMaxWidth()) {
        // Create a Row for each group of fields.
        sortedRows.forEach { rowNr ->
            val fieldsInRow = fieldsByRow[rowNr] ?: emptyList()
            Log.i("TrackingScreen", "Row $rowNr has ${fieldsInRow.size} fields.")

            // Each Row in the column will take up a proportional amount of the vertical space.
            Row(
                modifier = Modifier
                    // .weight(1f) // Distribute height equally among all rows
                    // .height(IntrinsicSize.Min) // Ensure content within the row can fill the height
            ) {
                // Sort fields by column number to ensure correct horizontal order.
                val sortedFields = fieldsInRow.sortedBy { it.colNr }

                // Add each SensorFieldView to the Row.
                sortedFields.forEach { fieldState ->
                    // Each field in the row takes up a proportional amount of the horizontal space.
                    SensorFieldView(
                        modifier = Modifier
                            .weight(1f), // Distribute width equally among all columns in this row
                            // .fillMaxHeight(),
                        border = BorderStroke(width = 1.dp, color = Color.Gray),
                        onLongClick = { onFieldLongClick(fieldState) },
                        fieldState = fieldState
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF212121)
@Composable
fun TrackingScreenPreview() {
    aTrainingTrackerTheme {
        val previewFields = listOf(
            // Row 0, Col 0
            SensorFieldState(
                configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, label = "Pace",
                filterDescription = "10s", value = "5:31", units = "/km",
                zoneColor = DefaultBackgroundColor
            ),
            // Row 0, Col 1
            SensorFieldState(
                configHash = 4, sensorFieldId = 1, rowNr = 0, colNr = 1, label = "Distance",
                filterDescription = "Total", value = "10.34", units = "km",
                zoneColor = DefaultBackgroundColor
            ),
            // Row 1, Col 0
            SensorFieldState(
                configHash = 2, sensorFieldId = 1, rowNr = 1, colNr = 0, label = "Heart Rate",
                filterDescription = "inst.", value = "145", units = "bpm",
                zoneColor = Zone1
            ),
            // Row 2, Col 0
            SensorFieldState(
                configHash = 3, sensorFieldId = 1, rowNr = 2, colNr = 0, label = "Power",
                filterDescription = "3s", value = "280", units = "W",
                zoneColor = DefaultBackgroundColor
            )
        )
        TrackingScreen(
            state = TrackingScreenState(fields = previewFields),
            onFieldLongClick = { field ->
                // In a preview, we can just log it or do nothing.
                println("Long clicked on: ${field.label}")
            }
        )
    }
}