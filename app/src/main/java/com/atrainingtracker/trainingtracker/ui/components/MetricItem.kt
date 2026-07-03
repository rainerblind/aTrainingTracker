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

package com.atrainingtracker.trainingtracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Defines the layout orientation for a MetricItem.
 */
enum class MetricLayout {
    HORIZONTAL,
    VERTICAL
}

/**
 * A unified component for displaying metrics (Icon + Label + Value).
 * Used across WorkoutDetails, SegmentDetails, and RouteHeaders for visual consistency.
 */
@Composable
fun MetricItem(
    @androidx.annotation.DrawableRes iconRes: Int?,
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    secondaryValue: String? = null,
    layout: MetricLayout = MetricLayout.HORIZONTAL,
    iconSize: Dp = 20.dp,
    isPrimary: Boolean = false,
    valueStyle: TextStyle? = null,
    labelStyle: TextStyle? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val finalValueStyle = valueStyle ?: if (layout == MetricLayout.HORIZONTAL) {
        if (isPrimary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    } else {
        if (isPrimary) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    }

    val finalLabelStyle = labelStyle ?: MaterialTheme.typography.labelSmall

    when (layout) {
        MetricLayout.HORIZONTAL -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconRes != null && iconRes != 0) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = iconColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = value,
                    style = finalValueStyle,
                    fontWeight = if (valueStyle == null && isPrimary) FontWeight.SemiBold else finalValueStyle.fontWeight,
                    color = valueColor
                )
            }
        }
        MetricLayout.VERTICAL -> {
            Column(modifier = modifier) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (iconRes != null && iconRes != 0) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = iconColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column {
                        if (label != null) {
                            Text(
                                text = label,
                                style = finalLabelStyle,
                                color = labelColor
                            )
                        }
                        Text(
                            text = value,
                            style = finalValueStyle.copy(fontWeight = if (valueStyle == null) FontWeight.Bold else finalValueStyle.fontWeight),
                            color = valueColor
                        )
                    }
                }
                if (secondaryValue != null) {
                    Text(
                        text = secondaryValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true, name = "Horizontal Basic")
@Composable
fun PreviewMetricItemHorizontal() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MetricItem(
                iconRes = com.atrainingtracker.R.drawable.ic_distance,
                value = "12.5 km"
            )
        }
    }
}

@Preview(showBackground = true, name = "Vertical Detailed")
@Composable
fun PreviewMetricItemVertical() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MetricItem(
                iconRes = com.atrainingtracker.R.drawable.ic_time_active,
                label = "Active Time",
                value = "1:20:45",
                secondaryValue = "(Total: 1:35:12)",
                layout = MetricLayout.VERTICAL,
                iconSize = 28.dp,
                isPrimary = true
            )
        }
    }
}

@Preview(showBackground = true, name = "Metric Gallery")
@Composable
fun PreviewMetricGallery() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal variants
            MetricItem(
                iconRes = com.atrainingtracker.R.drawable.ic_distance,
                value = "5.2 km",
                isPrimary = true
            )
            MetricItem(
                iconRes = com.atrainingtracker.R.drawable.ic_ascent,
                value = "450 m"
            )

            HorizontalDivider()

            // Vertical variants (Workout style)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricItem(
                    iconRes = com.atrainingtracker.R.drawable.ic_time_active,
                    label = "Time",
                    value = "0:45:00",
                    layout = MetricLayout.VERTICAL,
                    iconSize = 24.dp,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = com.atrainingtracker.R.drawable.ic_distance,
                    label = "Distance",
                    value = "10.0 km",
                    layout = MetricLayout.VERTICAL,
                    iconSize = 24.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
