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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Typography
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.typography.LocalCockpitTypography


enum class ViewSize {
    XSMALL, SMALL, NORMAL, LARGE, XLARGE, HUGE, XHUGE, XXHUGE, XXXHUGE
}

/**
 * An extension function that returns the localized display name for a ViewSize from string resources.
 */
fun ViewSize.getDisplayName(context: Context): String {
    val resourceId = when (this) {
        ViewSize.XSMALL -> R.string.view_size_xsmall
        ViewSize.SMALL -> R.string.view_size_small
        ViewSize.NORMAL -> R.string.view_size_normal
        ViewSize.LARGE -> R.string.view_size_large
        ViewSize.XLARGE -> R.string.view_size_xlarge
        ViewSize.HUGE -> R.string.view_size_huge
        ViewSize.XHUGE -> R.string.view_size_xhuge
        ViewSize.XXHUGE -> R.string.view_size_xxhuge
        ViewSize.XXXHUGE -> R.string.view_size_xxxhuge
    }
    return context.getString(resourceId)
}

/**
 * Resolves the typography style for the primary sensor metric value, enforcing configured or default font family and weight (ATT-1264, ATT-1751 / REQ-UI-212).
 */
fun getSensorValueTextStyle(
    viewSize: ViewSize,
    typography: Typography,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.SemiBold
): TextStyle {
    val baseStyle = when (viewSize) {
        ViewSize.XSMALL -> typography.headlineSmall.copy(fontSize = 20.sp)
        ViewSize.SMALL -> typography.headlineMedium
        ViewSize.NORMAL -> typography.displaySmall
        ViewSize.LARGE -> typography.displayMedium
        ViewSize.XLARGE -> typography.displayLarge.copy(fontSize = 50.sp)
        ViewSize.HUGE -> typography.displayLarge.copy(fontSize = 76.sp)
        ViewSize.XHUGE -> typography.displayLarge.copy(fontSize = 100.sp)
        ViewSize.XXHUGE -> typography.displayLarge.copy(fontSize = 140.sp)
        ViewSize.XXXHUGE -> typography.displayLarge.copy(fontSize = 180.sp)
    }
    return if (fontFamily != null) {
        baseStyle.copy(fontWeight = fontWeight, fontFamily = fontFamily)
    } else {
        baseStyle.copy(fontWeight = fontWeight)
    }
}

/**
 * Resolves the typography style for the sensor metric unit annotation (ATT-1264, ATT-1751 / REQ-UI-212).
 */
fun getSensorUnitTextStyle(
    viewSize: ViewSize,
    typography: Typography,
    fontFamily: FontFamily? = null
): TextStyle {
    val baseStyle = when (viewSize) {
        ViewSize.XSMALL -> typography.bodySmall.copy(fontSize = 10.sp)
        ViewSize.SMALL -> typography.bodySmall
        ViewSize.NORMAL -> typography.bodyLarge
        ViewSize.LARGE -> typography.headlineSmall
        ViewSize.XLARGE -> typography.headlineMedium.copy(fontSize = 32.sp)
        ViewSize.HUGE -> typography.headlineMedium.copy(fontSize = 40.sp)
        ViewSize.XHUGE -> typography.headlineLarge.copy(fontSize = 48.sp)
        ViewSize.XXHUGE -> typography.headlineLarge.copy(fontSize = 56.sp)
        ViewSize.XXXHUGE -> typography.headlineLarge.copy(fontSize = 64.sp)
    }
    return if (fontFamily != null) baseStyle.copy(fontFamily = fontFamily) else baseStyle
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
    screenMode: ScreenMode,
    isSelectedForMove: Boolean = false,
    shape: Shape = RectangleShape,
    cardElevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = if (isSelectedForMove) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    },
    onStartMove: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val cockpitTypography = LocalCockpitTypography.current
    // Determine text styles based on the size parameter and configured cockpit typography.
    val valueStyle = getSensorValueTextStyle(
        viewSize = fieldState.viewSize,
        typography = MaterialTheme.typography,
        fontFamily = cockpitTypography.resolvedFontFamily,
        fontWeight = cockpitTypography.weight.asFontWeight()
    )
    val unitStyle = getSensorUnitTextStyle(
        viewSize = fieldState.viewSize,
        typography = MaterialTheme.typography,
        fontFamily = cockpitTypography.resolvedFontFamily
    )
    val labelStyle = when (fieldState.viewSize) {
        ViewSize.XSMALL -> MaterialTheme.typography.bodySmall
        ViewSize.SMALL -> MaterialTheme.typography.bodyMedium
        ViewSize.NORMAL -> MaterialTheme.typography.titleMedium
        ViewSize.LARGE -> MaterialTheme.typography.titleLarge
        ViewSize.XLARGE -> MaterialTheme.typography.headlineSmall
        ViewSize.HUGE -> MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp)
        ViewSize.XHUGE -> MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp)
        ViewSize.XXHUGE -> MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp)
        ViewSize.XXXHUGE -> MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp)
    }
    val filterStyle = when (fieldState.viewSize) {
        ViewSize.XSMALL -> MaterialTheme.typography.labelSmall
        ViewSize.SMALL -> MaterialTheme.typography.bodySmall
        ViewSize.NORMAL -> MaterialTheme.typography.bodySmall
        ViewSize.LARGE -> MaterialTheme.typography.bodyMedium
        ViewSize.XLARGE -> MaterialTheme.typography.bodyLarge
        ViewSize.HUGE -> MaterialTheme.typography.bodyLarge
        ViewSize.XHUGE -> MaterialTheme.typography.titleMedium
        ViewSize.XXHUGE -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
        ViewSize.XXXHUGE -> MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                // Implement correct click behavior based on screen mode
                onClick = {
                    if (screenMode == ScreenMode.CONFIGURATION) {
                        onEdit()
                    }
                },
                onLongClick = {
                    if (screenMode == ScreenMode.CONFIGURATION) {
                        onStartMove()
                    } else if (screenMode == ScreenMode.TRACKING) {
                        onEdit()
                    }
                }
            ),
        shape = shape,
        elevation = cardElevation,
        colors = CardDefaults.cardColors(
            containerColor = if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showBackground) {
                fieldState.zoneColor.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = border
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 1. Left Indicator Strip
            if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showLeftBar) {
                Spacer(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(fieldState.zoneColor)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showTextColor) {
                            fieldState.zoneColor
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = fieldState.units,
                        style = unitStyle,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Conditionally add the action buttons at the bottom in configuration mode
                if (screenMode == ScreenMode.CONFIGURATION) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onStartMove,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Move Field",
                                tint = if (isSelectedForMove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp) // Make the button compact
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Field", // For accessibility
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // 2. Right Indicator Strip
            if (fieldState.zoneColor != Color.Transparent && fieldState.zoneDisplayOptions.showRightBar) {
                Spacer(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(fieldState.zoneColor)
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

// Modify preview to pass the new required parameter
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
        zoneColor = Color.Transparent
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldState,
            screenMode = ScreenMode.TRACKING // Default preview to tracking mode
        )
    }
}

// Add a specific preview for the configuration mode to see the delete button
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewConfigPreview() {
    val mockSensorFieldState = SensorFieldState(
        configHash = 1,
        sensorFieldId = 0,
        rowNr = 1,
        colNr = 1,
        viewSize = ViewSize.NORMAL,
        label = "Pace",
        filterDescription = "GPS: 5 s avg",
        value = "5:32",
        units = "/km",
        zoneColor = Color.Transparent
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldState,
            screenMode = ScreenMode.CONFIGURATION, // Set mode to CONFIGURATION
            onDelete = {} // Provide dummy lambda
        )
    }
}


@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZone1Preview() {
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
        zoneColor = TTColor.Zone1
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldStateInZone,
            screenMode = ScreenMode.TRACKING // Default preview to tracking mode
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZone2Preview() {
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
        zoneColor = TTColor.Zone2
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldStateInZone,
            screenMode = ScreenMode.TRACKING // Default preview to tracking mode
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZone3Preview() {
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
        zoneColor = TTColor.Zone3
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldStateInZone,
            screenMode = ScreenMode.TRACKING // Default preview to tracking mode
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZone4Preview() {
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
        zoneColor = TTColor.Zone4
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldStateInZone,
            screenMode = ScreenMode.TRACKING // Default preview to tracking mode
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SensorFieldViewZone5Preview() {
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
        zoneColor = TTColor.Zone5
    )

    MaterialTheme {
        SensorFieldView(
            fieldState = mockSensorFieldStateInZone,
            screenMode = ScreenMode.TRACKING // Default preview to tracking mode
        )
    }
}

//================================================================================
// PROTOTYPING VARIANTS PREVIEW SUITE (REQ-UI-258, ATT-2058)
//================================================================================

@Composable
private fun MockCockpitGrid(style: SensorFieldStyle) {
    val heartRate = SensorFieldState(
        configHash = 1,
        sensorFieldId = 1,
        rowNr = 1,
        colNr = 1,
        viewSize = ViewSize.NORMAL,
        label = "Heart Rate",
        filterDescription = "Inst.",
        value = "168",
        units = "bpm",
        zoneColor = TTColor.Zone4
    )
    val speed = SensorFieldState(
        configHash = 2,
        sensorFieldId = 2,
        rowNr = 1,
        colNr = 2,
        viewSize = ViewSize.NORMAL,
        label = "Speed",
        filterDescription = "3 s avg",
        value = "32.4",
        units = "km/h",
        zoneColor = Color.Transparent
    )
    val cadence = SensorFieldState(
        configHash = 3,
        sensorFieldId = 3,
        rowNr = 2,
        colNr = 1,
        viewSize = ViewSize.NORMAL,
        label = "Cadence",
        filterDescription = "Inst.",
        value = "92",
        units = "rpm",
        zoneColor = Color.Transparent
    )
    val time = SensorFieldState(
        configHash = 4,
        sensorFieldId = 4,
        rowNr = 2,
        colNr = 2,
        viewSize = ViewSize.NORMAL,
        label = "Time",
        filterDescription = "Active",
        value = "1:24:35",
        units = "h:m:s",
        zoneColor = Color.Transparent
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (style.gridSpacing > 0.dp) 8.dp else 0.dp),
        verticalArrangement = if (style.gridSpacing > 0.dp) Arrangement.spacedBy(style.gridSpacing) else Arrangement.Top
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = if (style.gridSpacing > 0.dp) Arrangement.spacedBy(style.gridSpacing) else Arrangement.Start
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SensorFieldView(
                    fieldState = heartRate,
                    screenMode = ScreenMode.TRACKING,
                    shape = style.shape,
                    cardElevation = CardDefaults.cardElevation(defaultElevation = style.defaultElevation)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                SensorFieldView(
                    fieldState = speed,
                    screenMode = ScreenMode.TRACKING,
                    shape = style.shape,
                    cardElevation = CardDefaults.cardElevation(defaultElevation = style.defaultElevation)
                )
            }
        }
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = if (style.gridSpacing > 0.dp) Arrangement.spacedBy(style.gridSpacing) else Arrangement.Start
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SensorFieldView(
                    fieldState = cadence,
                    screenMode = ScreenMode.TRACKING,
                    shape = style.shape,
                    cardElevation = CardDefaults.cardElevation(defaultElevation = style.defaultElevation)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                SensorFieldView(
                    fieldState = time,
                    screenMode = ScreenMode.TRACKING,
                    shape = style.shape,
                    cardElevation = CardDefaults.cardElevation(defaultElevation = style.defaultElevation)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Cockpit V0: Baseline (Status Quo)", widthDp = 360)
@Composable
fun PreviewCockpitVariant0_Baseline() {
    MaterialTheme {
        MockCockpitGrid(style = SensorFieldStyle.Variant0_Baseline)
    }
}

@Preview(showBackground = true, name = "Cockpit V1: Modern Outlined Sport Tiles", widthDp = 360)
@Composable
fun PreviewCockpitVariant1_OutlinedTiles() {
    MaterialTheme {
        MockCockpitGrid(style = SensorFieldStyle.Variant1_OutlinedTiles)
    }
}

@Preview(showBackground = true, name = "Cockpit V2: Elevated Sports Cards", widthDp = 360)
@Composable
fun PreviewCockpitVariant2_ElevatedCards() {
    MaterialTheme {
        MockCockpitGrid(style = SensorFieldStyle.Variant2_ElevatedCards)
    }
}

@Preview(showBackground = true, name = "Cockpit V3: Soft Accent Capsules", widthDp = 360)
@Composable
fun PreviewCockpitVariant3_Capsules() {
    MaterialTheme {
        MockCockpitGrid(style = SensorFieldStyle.Variant3_Capsules)
    }
}

@Preview(showBackground = true, name = "Cockpit Variants Comparison", widthDp = 360, heightDp = 1000)
@Composable
fun PreviewCockpitVariantsComparison() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Variant 0: Baseline (Status Quo)", style = MaterialTheme.typography.titleSmall)
            MockCockpitGrid(style = SensorFieldStyle.Variant0_Baseline)

            Text(text = "Variant 1: Outlined Sport Tiles (6dp radius, 4dp gap)", style = MaterialTheme.typography.titleSmall)
            MockCockpitGrid(style = SensorFieldStyle.Variant1_OutlinedTiles)

            Text(text = "Variant 2: Elevated Sports Cards (8dp radius, 6dp gap)", style = MaterialTheme.typography.titleSmall)
            MockCockpitGrid(style = SensorFieldStyle.Variant2_ElevatedCards)

            Text(text = "Variant 3: Soft Accent Capsules (12dp radius, 8dp gap)", style = MaterialTheme.typography.titleSmall)
            MockCockpitGrid(style = SensorFieldStyle.Variant3_Capsules)
        }
    }
}