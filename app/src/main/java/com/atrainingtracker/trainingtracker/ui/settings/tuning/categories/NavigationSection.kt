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
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Navigation tuning category composable (REQ-UI-281, REQ-UI-287, REQ-MAP-050 / ATT-2460, ATT-2632, ATT-2946, ATT-3581).
 * Encapsulates pre-ride route selection proximity radius, cue dismiss duration,
 * cue countdown distance, and auto-follow unique route toggle.
 * Follow-Me map camera parameters are permanently calibrated and removed per REQ-MAP-050.
 */
@Composable
fun NavigationSection(
    routeSelectionRadiusKm: Float,
    onRadiusChange: (Float) -> Unit,
    navigationCueDismissDurationSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
    onDismissDurationChange: (Int) -> Unit = {},
    autoFollowUniqueRouteEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_FOLLOW_UNIQUE_ROUTE_ENABLED,
    onAutoFollowUniqueRouteEnabledChange: (Boolean) -> Unit = {},
    turnCueCountdownDistanceMeters: Float = TuningPreferencesDefaults.TURN_CUE_COUNTDOWN_DISTANCE_METERS,
    onTurnCueCountdownDistanceChange: (Float) -> Unit = {}
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

        // Navigation Cue Dismiss Duration (1s - 10s, 1s increments) (ATT-3450 / REQ-UI-287)
        TuningSliderItem(
            title = stringResource(R.string.tuning_nav_cue_dismiss_duration_title),
            valueText = "${navigationCueDismissDurationSec}s",
            helperText = stringResource(R.string.tuning_nav_cue_dismiss_duration_desc),
            defaultText = stringResource(
                R.string.tuning_default_format,
                "${TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC}s"
            ),
            value = navigationCueDismissDurationSec.toFloat(),
            onValueChange = { snapped ->
                onDismissDurationChange(
                    snapped.roundToInt().coerceIn(
                        TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                        TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
                    )
                )
            },
            valueRange = TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat()..TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat(),
            steps = 8
        )

        // Navigation Cue Countdown Distance (30m - 300m, 10m increments) (ATT-3321 / REQ-UI-287)
        TuningSliderItem(
            title = stringResource(R.string.tuning_nav_cue_distance_title),
            valueText = "${turnCueCountdownDistanceMeters.roundToInt()} m",
            helperText = stringResource(R.string.tuning_nav_cue_distance_desc),
            defaultText = stringResource(R.string.tuning_default_format, "100 m"),
            value = turnCueCountdownDistanceMeters,
            onValueChange = { snapped ->
                val rounded = (snapped / 10f).roundToInt() * 10f
                onTurnCueCountdownDistanceChange(rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
                    TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS
                ))
            },
            valueRange = TuningPreferencesDefaults.MIN_TURN_CUE_COUNTDOWN_DISTANCE_METERS..TuningPreferencesDefaults.MAX_TURN_CUE_COUNTDOWN_DISTANCE_METERS,
            steps = 26
        )

        // Auto-Follow Unique Route (ATT-3320 / REQ-MAP-031)
        TuningToggleItem(
            title = stringResource(R.string.tuning_auto_follow_unique_route_title),
            isChecked = autoFollowUniqueRouteEnabled,
            onCheckedChange = onAutoFollowUniqueRouteEnabledChange,
            summary = stringResource(R.string.tuning_auto_follow_unique_route_desc)
        )
    }
}
