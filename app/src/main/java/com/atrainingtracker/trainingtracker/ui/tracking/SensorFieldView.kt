package com.atrainingtracker.trainingtracker.ui.tracking

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

enum class ViewSize {
    XSMALL, SMALL, NORMAL, LARGE, XLARGE
}

/**
 * A Composable that displays a single sensor field.
 * It is a "dumb" component that simply renders the FieldState it's given.
 */
@Composable
fun SensorFieldView(
    fieldState: SensorFieldState,
    size: ViewSize = ViewSize.NORMAL,
    modifier: Modifier = Modifier
) {
    // Determine text styles based on the size parameter.
    val valueStyle: TextStyle
    val unitStyle: TextStyle
    val labelStyle: TextStyle
    val filterStyle: TextStyle
    when (size) {
        ViewSize.XSMALL -> {
            valueStyle = MaterialTheme.typography.headlineMedium
            unitStyle = MaterialTheme.typography.bodySmall
            labelStyle = MaterialTheme.typography.bodyMedium
            filterStyle = MaterialTheme.typography.bodySmall
        }
        ViewSize.SMALL -> {
            valueStyle = MaterialTheme.typography.headlineLarge
            unitStyle = MaterialTheme.typography.bodyMedium
            labelStyle = MaterialTheme.typography.bodyLarge
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
            valueStyle = MaterialTheme.typography.displayLarge
            unitStyle = MaterialTheme.typography.headlineMedium
            labelStyle = MaterialTheme.typography.headlineSmall
            filterStyle = MaterialTheme.typography.bodyLarge
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = fieldState.zoneColor)
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
        viewId = 0,
        rowNr = 1,
        colNr = 1,
        label = "Pace",
        filterDescription = "5 s avg",
        value = "5:32",
        units = "/km",
        zoneColor = MaterialTheme.colorScheme.surfaceVariant
    )

    MaterialTheme {
        SensorFieldView(fieldState = mockSensorFieldState, size = size)
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZonePreview() {
    val mockSensorFieldStateInZone = SensorFieldState(
        configHash = 2,
        viewId = 2,
        rowNr = 1,
        colNr = 2,
        label = "Heart Rate",
        filterDescription = "Inst.",
        value = "175",
        units = "bpm",
        zoneColor = Color(0xFF4CAF50) // A sample green zone color
    )

    MaterialTheme {
        SensorFieldView(fieldState = mockSensorFieldStateInZone, size = ViewSize.NORMAL)
    }
}