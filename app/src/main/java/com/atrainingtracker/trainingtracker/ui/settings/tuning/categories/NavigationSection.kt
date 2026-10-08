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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Navigation tuning category composable (REQ-UI-281, REQ-UI-287 / ATT-2460, ATT-2632).
 * Encapsulates pre-ride route selection proximity radius, cue overlay transparency, and auto-dismiss duration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationSection(
    routeSelectionRadiusKm: Float,
    onRadiusChange: (Float) -> Unit,
    navigationCueTransparency: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY,
    onTransparencyChange: (Float) -> Unit = {},
    navigationCueDismissDurationSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
    onDismissDurationChange: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Route Selection Radius
        TuningSliderItem(
            title = stringResource(R.string.tuning_route_selection_radius_title),
            valueText = String.format(Locale.getDefault(), "%.1f km", routeSelectionRadiusKm),
            helperText = stringResource(R.string.tuning_route_selection_radius_desc),
            defaultText = stringResource(R.string.tuning_default_format, "1.0 km"),
            value = routeSelectionRadiusKm,
            onValueChange = { snapped ->
                val rounded = (snapped * 2f).roundToInt() / 2f
                onRadiusChange(rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM,
                    TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM
                ))
            },
            valueRange = TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM..TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM,
            steps = 18
        )

        // Navigation Cue Transparency (20% - 100%, 5% increments)
        TuningSliderItem(
            title = stringResource(R.string.tuning_nav_cue_transparency_title),
            valueText = "${(navigationCueTransparency * 100f).roundToInt()}%",
            helperText = stringResource(R.string.tuning_nav_cue_transparency_desc),
            defaultText = stringResource(R.string.tuning_default_format, "80%"),
            value = navigationCueTransparency,
            onValueChange = { snapped ->
                val rounded = (snapped * 20f).roundToInt() / 20f
                onTransparencyChange(rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_NAVIGATION_CUE_TRANSPARENCY,
                    TuningPreferencesDefaults.MAX_NAVIGATION_CUE_TRANSPARENCY
                ))
            },
            valueRange = TuningPreferencesDefaults.MIN_NAVIGATION_CUE_TRANSPARENCY..TuningPreferencesDefaults.MAX_NAVIGATION_CUE_TRANSPARENCY,
            steps = 15
        )

        // Navigation Cue Dismiss Duration (2s, 3s, 4s [default], 5s, 8s, Persistent)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tuning_nav_cue_dismiss_duration_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (navigationCueDismissDurationSec == 0) {
                        stringResource(R.string.tuning_nav_cue_dismiss_persistent)
                    } else {
                        "${navigationCueDismissDurationSec}s"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(R.string.tuning_nav_cue_dismiss_duration_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.tuning_default_format, "4s"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS.forEach { sec ->
                    val isSelected = navigationCueDismissDurationSec == sec
                    val chipText = if (sec == 0) {
                        stringResource(R.string.tuning_nav_cue_dismiss_persistent)
                    } else {
                        "${sec}s"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDismissDurationChange(sec) },
                        label = { Text(chipText) }
                    )
                }
            }
        }
    }
}
