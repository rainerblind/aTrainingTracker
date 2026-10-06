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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Navigation tuning category composable (REQ-UI-281 / ATT-2460).
 * Encapsulates pre-ride route selection proximity radius and related navigation preferences.
 */
@Composable
fun NavigationSection(
    routeSelectionRadiusKm: Float,
    onRadiusChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
    }
}
