package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ViewSize {
    XSMALL, SMALL, NORMAL, LARGE, XLARGE, HUGE, XHUGE
}

/**
 * A Composable that displays a single sensor field.
 * It is a "dumb" component that simply renders the FieldState it's given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorFieldView(
    fieldState: SensorFieldState,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    onLongClick: () -> Unit = {}
) {
    // Determine text styles based on the size parameter.
    val valueStyle: TextStyle
    val unitStyle: TextStyle
    val labelStyle: TextStyle
    val filterStyle: TextStyle
    when (fieldState.viewSize) {
        ViewSize.XSMALL -> {
        // --- MANUALLY DECREASE FONT SIZE ---
            valueStyle = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 20.sp // Manually set a much smaller font size
            )
            unitStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp
            )
            labelStyle = MaterialTheme.typography.bodySmall
            filterStyle = MaterialTheme.typography.labelSmall
        }

        ViewSize.SMALL -> {
            valueStyle = MaterialTheme.typography.headlineMedium
            unitStyle = MaterialTheme.typography.bodySmall
            labelStyle = MaterialTheme.typography.bodyMedium
            filterStyle = MaterialTheme.typography.bodySmall
        }
        ViewSize.NORMAL -> {
            valueStyle = MaterialTheme.typography.displaySmall
            unitStyle = MaterialTheme.typography.bodyLarge
            labelStyle = MaterialTheme.typography.titleMedium
            filterStyle = MaterialTheme.typography.bodySmall
        }
        ViewSize.LARGE -> {
            valueStyle = MaterialTheme.typography.displayMedium
            unitStyle = MaterialTheme.typography.headlineSmall
            labelStyle = MaterialTheme.typography.titleLarge
            filterStyle = MaterialTheme.typography.bodyMedium
        }
        ViewSize.XLARGE -> {
            valueStyle = MaterialTheme.typography.displayLarge.copy(
                fontSize = 50.sp // Manually set a much larger font size
            )
            unitStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 32.sp // Also increase the unit size
            )
            labelStyle = MaterialTheme.typography.headlineSmall
            filterStyle = MaterialTheme.typography.bodyLarge
        }
        ViewSize.HUGE -> {
            valueStyle = MaterialTheme.typography.displayLarge.copy(
                fontSize = 76.sp // Significantly larger
            )
            unitStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 40.sp
            )
            labelStyle = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 28.sp
            )
            filterStyle = MaterialTheme.typography.bodyLarge
        }
        ViewSize.XHUGE -> {
            valueStyle = MaterialTheme.typography.displayLarge.copy(
                fontSize = 100.sp // Very large "Jumbotron" size
            )
            unitStyle = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 48.sp
            )
            labelStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 32.sp
            )
            filterStyle = MaterialTheme.typography.titleMedium
        }

    }

    Card(
        modifier = modifier.fillMaxWidth().
        combinedClickable(
            onClick = {},
            onLongClick = onLongClick
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = fieldState.zoneColor),
        border = border
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top row for Label and Filter information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Label on the top-left
                Text(
                    text = fieldState.label,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurface // Ensure readability
                )
                // Filter info on the top-right
                Text(
                    text = fieldState.filterDescription,
                    style = filterStyle,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Value and Unit Row, centered
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = fieldState.value,
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = fieldState.units,
                    style = unitStyle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


//================================================================================
// PREVIEW IMPLEMENTATION
//================================================================================

private class ViewSizeProvider : PreviewParameterProvider<ViewSize> {
    override val values = ViewSize.values().asSequence()
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewPreview(
    @PreviewParameter(ViewSizeProvider::class) size: ViewSize
) {
    val mockSensorFieldState = SensorFieldState(
        configHash = 1,
        sensorFieldId = 0,
        rowNr = 1,
        colNr = 1,
        viewSize = size,
        label = "Pace",
        filterDescription = "GPS: 5 s avg",
        value = "5:32",
        units = "/km",
        zoneColor = MaterialTheme.colorScheme.surfaceVariant
    )

    MaterialTheme {
        SensorFieldView(fieldState = mockSensorFieldState)
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZonePreview() {
    val mockSensorFieldStateInZone = SensorFieldState(
        configHash = 2,
        sensorFieldId = 2,
        rowNr = 1,
        colNr = 2,
        viewSize = ViewSize.NORMAL,
        label = "Heart Rate",
        filterDescription = "Inst.",
        value = "175",
        units = "bpm",
        zoneColor = Color(0xFF4CAF50) // A sample green zone color
    )

    MaterialTheme {
        SensorFieldView(fieldState = mockSensorFieldStateInZone)
    }
}