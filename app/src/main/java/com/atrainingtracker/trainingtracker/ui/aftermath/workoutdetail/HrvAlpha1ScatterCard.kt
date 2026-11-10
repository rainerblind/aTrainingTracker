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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * Dedicated Workout Details card displaying DFA alpha 1 aerobic threshold dynamics,
 * ordinary least-squares regression line, threshold crosshairs, and toggleable HR/Power intensity
 * conforming to REQ-PER-035.4, REQ-PER-035.5, Rule 48, and Rule 64.
 */
@Composable
fun HrvAlpha1ScatterCard(
    points: List<PathPoint>,
    modifier: Modifier = Modifier
) {
    val hasHr = remember(points) {
        points.any { it.hr != null && it.hr > 0 }
    }
    val hasPower = remember(points) {
        points.any { it.power != null && it.power > 0 }
    }

    var selectedMode by rememberSaveable {
        mutableStateOf(if (hasPower) HrvScatterIntensityMode.POWER else HrvScatterIntensityMode.HEART_RATE)
    }

    val scatterPoints = remember(points, selectedMode) {
        HrvScatterMath.filterScatterPoints(points, selectedMode)
    }

    val regression = remember(scatterPoints) {
        HrvScatterMath.calculateLinearRegression(scatterPoints)
    }

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hrv_alpha1_scatter_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Icon + Title & Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_heart_rate),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hrv_scatter_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.hrv_scatter_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mode Selector Chips (HR vs Power)
            if (hasHr && hasPower) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == HrvScatterIntensityMode.HEART_RATE,
                        onClick = { selectedMode = HrvScatterIntensityMode.HEART_RATE },
                        label = { Text(stringResource(R.string.hrv_scatter_toggle_hr)) },
                        modifier = Modifier.testTag("hrv_scatter_mode_hr_chip")
                    )
                    FilterChip(
                        selected = selectedMode == HrvScatterIntensityMode.POWER,
                        onClick = { selectedMode = HrvScatterIntensityMode.POWER },
                        label = { Text(stringResource(R.string.hrv_scatter_toggle_power)) },
                        modifier = Modifier.testTag("hrv_scatter_mode_power_chip")
                    )
                }
            }

            // Regression & Threshold Metrics Grid
            val unitStr = if (selectedMode == HrvScatterIntensityMode.HEART_RATE) "bpm" else "W"
            val t1Val = regression?.intensityAt075?.takeIf { it in 30.0..600.0 }
            val t2Val = regression?.intensityAt050?.takeIf { it in 30.0..600.0 }
            val r2Val = regression?.rSquared

            val t1Formatted = if (t1Val != null) "${t1Val.toInt()} $unitStr" else "--"
            val t2Formatted = if (t2Val != null) "${t2Val.toInt()} $unitStr" else "--"
            val r2Formatted = if (r2Val != null) String.format(Locale.US, "%.2f", r2Val) else "--"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.hrv_scatter_aerobic_threshold),
                    value = t1Formatted,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.hrv_scatter_anaerobic_threshold),
                    value = t2Formatted,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    iconRes = null,
                    label = "R²",
                    value = r2Formatted,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier.weight(0.6f)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Scatter Chart
            HrvAlpha1ScatterChart(
                points = points,
                intensityMode = selectedMode,
                modifier = Modifier.fillMaxWidth()
            )

            // Artifact Suppression Footnote
            Text(
                text = stringResource(R.string.hrv_scatter_artifact_suppressed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "HRV Alpha1 Scatter Card Preview", showBackground = true)
@Composable
private fun HrvAlpha1ScatterCardPreview() {
    val samplePoints = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), hr = 125, power = 170, hrvDfaA1 = 0.92, hrvArtifactRate = 0.0),
        PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hr = 138, power = 195, hrvDfaA1 = 0.84, hrvArtifactRate = 0.8),
        PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), hr = 148, power = 220, hrvDfaA1 = 0.75, hrvArtifactRate = 0.2),
        PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), hr = 158, power = 250, hrvDfaA1 = 0.62, hrvArtifactRate = 1.5),
        PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), hr = 170, power = 290, hrvDfaA1 = 0.49, hrvArtifactRate = 0.5)
    )

    ATrainingTrackerTheme {
        HrvAlpha1ScatterCard(points = samplePoints)
    }
}
