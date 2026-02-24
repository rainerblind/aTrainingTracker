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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType

/**
 * Defines the five available sizes for the sensor data views.
 * This will be loaded from the viewDatabase to control the layout.
 */
enum class ViewSize {
    XSMALL,
    SMALL,
    NORMAL,
    LARGE,
    XLARGE
}

/**
 * A runtime-ready Composable that displays all information within a FilteredSensorData object.
 * It dynamically adjusts its size and appearance based on the `size` parameter.
 */
@Composable
fun FilteredSensorDataView(
    filteredSensorData: FilteredSensorData<*>,
    size: ViewSize = ViewSize.NORMAL,
    modifier: Modifier = Modifier
) {
    // Determine the appropriate text styles based on the size parameter.
    val labelStyle: TextStyle
    val valueStyle: TextStyle
    val unitStyle: TextStyle
    when (size) {
        ViewSize.XSMALL -> {
            labelStyle = MaterialTheme.typography.bodySmall
            valueStyle = MaterialTheme.typography.headlineMedium
            unitStyle = MaterialTheme.typography.bodySmall
        }
        ViewSize.SMALL -> {
            labelStyle = MaterialTheme.typography.bodyMedium
            valueStyle = MaterialTheme.typography.headlineLarge
            unitStyle = MaterialTheme.typography.bodyMedium
        }
        ViewSize.NORMAL -> {
            labelStyle = MaterialTheme.typography.bodyLarge
            valueStyle = MaterialTheme.typography.displaySmall
            unitStyle = MaterialTheme.typography.bodyLarge
        }
        ViewSize.LARGE -> {
            labelStyle = MaterialTheme.typography.headlineSmall
            valueStyle = MaterialTheme.typography.displayMedium
            unitStyle = MaterialTheme.typography.headlineSmall
        }
        ViewSize.XLARGE -> {
            labelStyle = MaterialTheme.typography.headlineMedium
            valueStyle = MaterialTheme.typography.displayLarge
            unitStyle = MaterialTheme.typography.headlineMedium
        }
    }

    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                // Label on the top-left, from SensorType
                Text(
                    text = stringResource(filteredSensorData.sensorType.fullNameId),
                    style = labelStyle
                )
                // Filter info on the top-right
                Text(
                    text = filteredSensorData.filterType.getSummary(context, filteredSensorData.filterConstant),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Value and Unit Row, centered within the card
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // The main value, using the pre-formatted stringValue
                Text(
                    text = filteredSensorData.stringValue,
                    style = valueStyle
                )
                // The unit, which must be retrieved from the SensorType
                Text(
                    text = stringResource(filteredSensorData.sensorType.unitId),
                    style = unitStyle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}


//================================================================================
// PREVIEW IMPLEMENTATION
// This section provides stable previews and remains unchanged.
// It uses a "fake" data class to ensure Android Studio can render the UI reliably.
//================================================================================

/**
 * A private, lightweight data structure for use ONLY in Compose Previews.
 */
private data class PreviewData(
    val label: String,
    val value: String,
    val unit: String,
    val filterType: String,
    val filterConstant: Double
)

/**
 * A private "wrapper" composable for previewing. It uses the exact same layout
 * as the real composable above, but with preview-safe data.
 */
@Composable
private fun PreviewableFilteredSensorDataView(data: PreviewData, size: ViewSize) {
    // This styling logic is duplicated from the real composable for preview accuracy.
    val labelStyle: TextStyle
    val valueStyle: TextStyle
    val unitStyle: TextStyle
    when (size) {
        ViewSize.XSMALL -> {
            labelStyle = MaterialTheme.typography.bodySmall; valueStyle = MaterialTheme.typography.headlineMedium; unitStyle = MaterialTheme.typography.bodySmall
        }
        ViewSize.SMALL -> {
            labelStyle = MaterialTheme.typography.bodyMedium; valueStyle = MaterialTheme.typography.headlineLarge; unitStyle = MaterialTheme.typography.bodyMedium
        }
        ViewSize.NORMAL -> {
            labelStyle = MaterialTheme.typography.bodyLarge; valueStyle = MaterialTheme.typography.displaySmall; unitStyle = MaterialTheme.typography.bodyLarge
        }
        ViewSize.LARGE -> {
            labelStyle = MaterialTheme.typography.headlineSmall; valueStyle = MaterialTheme.typography.displayMedium; unitStyle = MaterialTheme.typography.headlineSmall
        }
        ViewSize.XLARGE -> {
            labelStyle = MaterialTheme.typography.headlineMedium; valueStyle = MaterialTheme.typography.displayLarge; unitStyle = MaterialTheme.typography.headlineMedium
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(text = data.label, style = labelStyle)
                Text(
                    text = "${data.filterType} (k=${data.filterConstant})",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(text = data.value, style = valueStyle)
                Text(text = data.unit, style = unitStyle, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
        }
    }
}

/**
 * Provides all five ViewSize values to the @Preview composable.
 */
private class ViewSizeProvider : PreviewParameterProvider<ViewSize> {
    override val values = ViewSize.values().asSequence()
}

/**
 * The definitive @Preview composable that will generate a preview for all five sizes.
 */
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun FilteredSensorDataViewSizedPreview(
    @PreviewParameter(ViewSizeProvider::class) size: ViewSize
) {
    // Using a mock FilteredSensorData object directly in the main composable for a true preview.
    // If this fails to render, the PreviewableFilteredSensorDataView with fake data is the fallback.
    val mockData = FilteredSensorData(
        SensorType.HR,
        148f,
        "148",
        "Garmin-HRM",
        FilterType.INSTANTANEOUS,
        1.0
    )

    MaterialTheme {
        FilteredSensorDataView(filteredSensorData = mockData, size = size)
    }
}