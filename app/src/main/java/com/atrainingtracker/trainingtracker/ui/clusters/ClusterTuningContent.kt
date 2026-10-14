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

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha

/**
 * Reusable, canonical workout route clustering tuning content (REQ-SET-075, Rule 36).
 * Single Source of Truth for clustering parameters shared across Advanced Settings,
 * standalone tuning screen, and import backup tabs.
 */
@Composable
fun ClusterTuningContent(
    endpointTolerance: Float,
    onEndpointToleranceChange: (Float) -> Unit,
    apexTolerance: Float,
    onApexToleranceChange: (Float) -> Unit,
    distanceTolerance: Float,
    onDistanceToleranceChange: (Float) -> Unit,
    altitudePositionTolerance: Float = 400f,
    onAltitudePositionToleranceChange: (Float) -> Unit = {},
    useSportTypeForClustering: Boolean = true,
    onUseSportTypeChange: (Boolean) -> Unit = {},
    useAltitudePosForClustering: Boolean = true,
    onUseAltitudePosChange: (Boolean) -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
    isDialog: Boolean = false,
    enabled: Boolean = true
) {
    var showDetails by remember { mutableStateOf(false) }

    // Constants for mapping
    val endRange = 10f..500f
    val apexRange = 10f..500f
    val distRange = 0.01f..0.2f
    val altRange = 10f..500f

    // Derived master sensitivity (average of normalized values)
    val currentMasterValue = remember(endpointTolerance, apexTolerance, distanceTolerance, altitudePositionTolerance, useAltitudePosForClustering) {
        val nEnd = (endpointTolerance - endRange.start) / (endRange.endInclusive - endRange.start)
        val nApex = (apexTolerance - apexRange.start) / (apexRange.endInclusive - apexRange.start)
        val nDist = (distanceTolerance - distRange.start) / (distRange.endInclusive - distRange.start)
        if (useAltitudePosForClustering) {
            val nAlt = (altitudePositionTolerance - altRange.start) / (altRange.endInclusive - altRange.start)
            (nEnd + nApex + nDist + nAlt) / 4f
        } else {
            (nEnd + nApex + nDist) / 3f
        }
    }

    val verticalSpacing = if (isDialog) 12.dp else 24.dp

    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        // 1. Master Slider
        Column {
            Text(
                text = stringResource(R.string.cluster_tuning_master_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = currentMasterValue,
                onValueChange = { sensitivity ->
                    onEndpointToleranceChange(endRange.start + (endRange.endInclusive - endRange.start) * sensitivity)
                    onApexToleranceChange(apexRange.start + (apexRange.endInclusive - apexRange.start) * sensitivity)
                    onDistanceToleranceChange(distRange.start + (distRange.endInclusive - distRange.start) * sensitivity)
                    if (useAltitudePosForClustering) {
                        onAltitudePositionToleranceChange(altRange.start + (altRange.endInclusive - altRange.start) * sensitivity)
                    }
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 0f..1f,
                enabled = enabled
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.cluster_tuning_strict), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.cluster_tuning_relaxed), style = MaterialTheme.typography.labelSmall)
            }
        }

        // 2. Details Toggle
        TuningToggleItem(
            title = stringResource(R.string.cluster_tuning_show_details),
            isChecked = showDetails,
            onCheckedChange = { showDetails = it },
            enabled = enabled
        )

        // 3. Detailed Parameters (Optional)
        if (showDetails) {
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val isImperial = TrainingApplication.getUnit() == MyUnits.IMPERIAL
            val lengthUnit = stringResource(if (isImperial) R.string.units_distance_imperial else R.string.units_distance_metric)
            val lengthMultiplier = if (isImperial) (1.0 / BANALService.METER_PER_MILE).toFloat() else 0.001f

            // A. Switches above sliders
            // Sport Type Awareness (ATT-350)
            TuningToggleItem(
                title = stringResource(R.string.cluster_tuning_use_sport_type_label),
                summary = stringResource(R.string.cluster_tuning_use_sport_type_summary),
                isChecked = useSportTypeForClustering,
                onCheckedChange = onUseSportTypeChange,
                enabled = enabled
            )

            // Altitude Extrema Awareness (ATT-502, REQ-SET-065)
            TuningToggleItem(
                title = stringResource(R.string.cluster_tuning_use_altitude_label),
                summary = stringResource(R.string.cluster_tuning_use_altitude_summary),
                isChecked = useAltitudePosForClustering,
                onCheckedChange = onUseAltitudePosChange,
                enabled = enabled
            )

            // B. Tolerance Sliders
            TuningSliderItem(
                title = stringResource(R.string.cluster_tuning_endpoint_label),
                valueText = String.format(locale, "%.3f %s", endpointTolerance * lengthMultiplier, lengthUnit),
                helperText = "",
                defaultText = "",
                value = endpointTolerance,
                onValueChange = onEndpointToleranceChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = endRange,
                enabled = enabled
            )

            TuningSliderItem(
                title = stringResource(R.string.cluster_tuning_apex_label),
                valueText = String.format(locale, "%.3f %s", apexTolerance * lengthMultiplier, lengthUnit),
                helperText = "",
                defaultText = "",
                value = apexTolerance,
                onValueChange = onApexToleranceChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = apexRange,
                enabled = enabled
            )

            TuningSliderItem(
                title = stringResource(R.string.cluster_tuning_distance_label),
                valueText = String.format(locale, "%.0f %s", distanceTolerance * 100f, stringResource(R.string.units_percent)),
                helperText = "",
                defaultText = "",
                value = distanceTolerance,
                onValueChange = onDistanceToleranceChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = distRange,
                enabled = enabled
            )

            TuningSliderItem(
                title = stringResource(R.string.cluster_tuning_altitude_pos_label),
                valueText = String.format(locale, "%.3f %s", altitudePositionTolerance * lengthMultiplier, lengthUnit),
                helperText = "",
                defaultText = "",
                value = altitudePositionTolerance,
                onValueChange = onAltitudePositionToleranceChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = altRange,
                enabled = enabled && useAltitudePosForClustering
            )
        }
    }
}

/**
 * Legacy bespoke slider for cluster tuning.
 *
 * @deprecated Use [TuningSliderItem] from TuningFormControls.kt instead (Rule 36, REQ-SET-077).
 */
@Deprecated(
    message = "Use TuningSliderItem from TuningFormControls.kt instead (Rule 36, REQ-SET-077).",
    replaceWith = ReplaceWith(
        expression = "TuningSliderItem(title = label, valueText = \"\", helperText = \"\", defaultText = \"\", value = value, onValueChange = onValueChange, valueRange = valueRange, steps = 0, enabled = enabled, onValueChangeFinished = onValueChangeFinished)",
        imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"]
    )
)
@Composable
fun TuningSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    displayMultiplier: Float = 1f,
    decimalPlaces: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val contentAlpha = if (enabled) 1.0f else TTAlpha.Disabled

    Column(
        modifier = Modifier.alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else TTAlpha.Disabled)
        )
        Text(
            text = java.lang.String.format(locale, "%.${decimalPlaces}f %s", value * displayMultiplier, unit),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Disabled)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
