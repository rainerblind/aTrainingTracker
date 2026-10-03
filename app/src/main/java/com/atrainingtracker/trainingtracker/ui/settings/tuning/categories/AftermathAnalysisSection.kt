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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningPaceCeilingFormatter
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem

/**
 * Aftermath telemetry & elevation analysis tuning category composable (REQ-UI-262).
 * Encapsulates independent X-axis domain chips and pace ceiling slider (15 discrete steps).
 */
@Composable
fun AftermathAnalysisSection(
    elevationXAxisDomain: ProfileXAxisDomain,
    onElevationDomainChange: (ProfileXAxisDomain) -> Unit,
    telemetryXAxisDomain: ProfileXAxisDomain,
    onTelemetryDomainChange: (ProfileXAxisDomain) -> Unit,
    paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM,
    onPaceCeilingChange: (Float) -> Unit = {}
) {
    val isMetric = remember { TrainingApplication.getUnit() == MyUnits.METRIC }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Minimum Pace Ceiling (defaults to 3:00 min/km)
        TuningSliderItem(
            title = stringResource(R.string.tuning_pace_ceiling_title),
            valueText = TuningPaceCeilingFormatter.formatPaceCeiling(paceCeilingMinKm, isMetric),
            helperText = stringResource(R.string.tuning_pace_ceiling_desc),
            defaultText = stringResource(
                R.string.tuning_default_format,
                TuningPaceCeilingFormatter.formatPaceCeiling(TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM, isMetric)
            ),
            value = paceCeilingMinKm,
            onValueChange = onPaceCeilingChange,
            valueRange = TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM..TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM,
            steps = 15
        )

        // Elevation Profile X-Axis Domain (defaults to Distance)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_profile_x_axis_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.tuning_profile_x_axis_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = elevationXAxisDomain == ProfileXAxisDomain.DISTANCE,
                    onClick = { onElevationDomainChange(ProfileXAxisDomain.DISTANCE) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_distance)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = elevationXAxisDomain == ProfileXAxisDomain.TIME,
                    onClick = { onElevationDomainChange(ProfileXAxisDomain.TIME) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_time)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Telemetry Graphs X-Axis Domain (defaults to Time)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_telemetry_x_axis_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.tuning_telemetry_x_axis_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = telemetryXAxisDomain == ProfileXAxisDomain.DISTANCE,
                    onClick = { onTelemetryDomainChange(ProfileXAxisDomain.DISTANCE) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_distance)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = telemetryXAxisDomain == ProfileXAxisDomain.TIME,
                    onClick = { onTelemetryDomainChange(ProfileXAxisDomain.TIME) },
                    label = { Text(stringResource(R.string.tuning_profile_x_axis_time)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Deprecated("Use overload accepting elevationXAxisDomain and telemetryXAxisDomain")
@Composable
fun AftermathAnalysisSection(
    profileXAxisDomain: ProfileXAxisDomain,
    onDomainChange: (ProfileXAxisDomain) -> Unit
) {
    AftermathAnalysisSection(
        elevationXAxisDomain = profileXAxisDomain,
        onElevationDomainChange = onDomainChange,
        telemetryXAxisDomain = profileXAxisDomain,
        onTelemetryDomainChange = onDomainChange
    )
}
