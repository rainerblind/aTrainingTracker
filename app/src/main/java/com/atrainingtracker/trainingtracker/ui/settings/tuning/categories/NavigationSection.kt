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

import androidx.compose.material3.HorizontalDivider
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem

/**
 * Navigation tuning category composable (REQ-UI-281, REQ-UI-287, REQ-MAP-042 / ATT-2460, ATT-2632, ATT-2946).
 * Encapsulates pre-ride route selection proximity radius, cue overlay transparency, auto-dismiss duration,
 * and Follow-Me map camera parameters (base zoom, speed zoom, tilt, lookahead padding).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationSection(
    routeSelectionRadiusKm: Float,
    onRadiusChange: (Float) -> Unit,
    navigationCueTransparency: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY,
    onTransparencyChange: (Float) -> Unit = {},
    navigationCueDismissDurationSec: Int = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
    onDismissDurationChange: (Int) -> Unit = {},
    mapFollowMeInitialZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM,
    onMapFollowMeInitialZoomChange: (Float) -> Unit = {},
    mapFollowMeSpeedZoomEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
    onMapFollowMeSpeedZoomEnabledChange: (Boolean) -> Unit = {},
    mapFollowMeCruisingZoom: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_CRUISING_ZOOM,
    onMapFollowMeCruisingZoomChange: (Float) -> Unit = {},
    mapFollowMeTiltAngle: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE,
    onMapFollowMeTiltAngleChange: (Float) -> Unit = {},
    mapFollowMeLookaheadPaddingPercent: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
    onMapFollowMeLookaheadPaddingPercentChange: (Float) -> Unit = {}
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

        // Follow-Me Map Camera Heading / Divider
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.tuning_map_camera_section_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Follow-Me Base Zoom (15.0 - 21.0, 0.5 steps)
        TuningSliderItem(
            title = stringResource(R.string.tuning_map_base_zoom_title),
            valueText = String.format(Locale.getDefault(), "%.1f", mapFollowMeInitialZoom),
            helperText = stringResource(R.string.tuning_map_base_zoom_desc),
            defaultText = stringResource(R.string.tuning_default_format, "20.0"),
            value = mapFollowMeInitialZoom,
            onValueChange = { snapped ->
                val rounded = (snapped * 2f).roundToInt() / 2f
                onMapFollowMeInitialZoomChange(rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_INITIAL_ZOOM,
                    TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_INITIAL_ZOOM
                ))
            },
            valueRange = TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_INITIAL_ZOOM..TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_INITIAL_ZOOM,
            steps = 11
        )

        // Speed-Dependent Zoom Toggle
        TuningToggleItem(
            title = stringResource(R.string.tuning_map_speed_zoom_enabled_title),
            isChecked = mapFollowMeSpeedZoomEnabled,
            onCheckedChange = onMapFollowMeSpeedZoomEnabledChange,
            summary = stringResource(R.string.tuning_map_speed_zoom_enabled_desc)
        )

        // Cruising Speed Zoom (14.0 - 19.5, 0.5 steps)
        if (mapFollowMeSpeedZoomEnabled) {
            TuningSliderItem(
                title = stringResource(R.string.tuning_map_cruising_zoom_title),
                valueText = String.format(Locale.getDefault(), "%.1f", mapFollowMeCruisingZoom),
                helperText = stringResource(R.string.tuning_map_cruising_zoom_desc),
                defaultText = stringResource(R.string.tuning_default_format, "18.0"),
                value = mapFollowMeCruisingZoom,
                onValueChange = { snapped ->
                    val rounded = (snapped * 2f).roundToInt() / 2f
                    onMapFollowMeCruisingZoomChange(rounded.coerceIn(
                        TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_CRUISING_ZOOM,
                        TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_CRUISING_ZOOM
                    ))
                },
                valueRange = TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_CRUISING_ZOOM..TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_CRUISING_ZOOM,
                steps = 10
            )
        }

        // Camera Tilt Angle (0° - 70°, 5° steps)
        TuningSliderItem(
            title = stringResource(R.string.tuning_map_tilt_angle_title),
            valueText = "${mapFollowMeTiltAngle.roundToInt()}°",
            helperText = stringResource(R.string.tuning_map_tilt_angle_desc),
            defaultText = stringResource(R.string.tuning_default_format, "70°"),
            value = mapFollowMeTiltAngle,
            onValueChange = { snapped ->
                val rounded = (snapped / 5f).roundToInt() * 5f
                onMapFollowMeTiltAngleChange(rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE,
                    TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE
                ))
            },
            valueRange = TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE..TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE,
            steps = 13
        )

        // Forward Lookahead Bottom Padding (10% - 50%, 5% steps)
        TuningSliderItem(
            title = stringResource(R.string.tuning_map_lookahead_padding_title),
            valueText = "${mapFollowMeLookaheadPaddingPercent.roundToInt()}%",
            helperText = stringResource(R.string.tuning_map_lookahead_padding_desc),
            defaultText = stringResource(R.string.tuning_default_format, "30%"),
            value = mapFollowMeLookaheadPaddingPercent,
            onValueChange = { snapped ->
                val rounded = (snapped / 5f).roundToInt() * 5f
                onMapFollowMeLookaheadPaddingPercentChange(rounded.coerceIn(
                    TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
                    TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT
                ))
            },
            valueRange = TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT..TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
            steps = 7
        )
    }
}
