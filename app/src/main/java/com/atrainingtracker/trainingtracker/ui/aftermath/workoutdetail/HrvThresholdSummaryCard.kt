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

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * Workout Details card displaying lag-aligned, confidence-gated physiological thresholds
 * (HRVT1 aerobic, HRVT2 anaerobic) derived from centered post-workout DFA alpha 1 telemetry
 * pursuant to REQ-PER-036, Rule 23, and Rule 81.
 */
@Composable
fun HrvThresholdSummaryCard(
    points: List<PathPoint>,
    protocolType: HrvProtocolType = HrvProtocolType.FREE_WORKOUT,
    dfaA1ThresholdT1: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1,
    dfaA1ThresholdT2: Float = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2,
    modifier: Modifier = Modifier
) {
    // Graceful omission for workouts without valid HRV data (REQ-PER-036.6)
    val hasHrv = remember(points) {
        points.any { it.hrvDfaA1 != null }
    }
    if (!hasHrv || points.isEmpty()) {
        return
    }

    val hasHr = remember(points) {
        points.any { it.hr != null && it.hr > 0 }
    }
    val hasPower = remember(points) {
        points.any { it.power != null && it.power > 0 }
    }
    val hasPace = remember(points) {
        points.any { it.speedMps != null && it.speedMps >= 0.55 }
    }

    val availableModes = remember(hasHr, hasPower, hasPace) {
        listOfNotNull(
            if (hasHr) HrvScatterIntensityMode.HEART_RATE else null,
            if (hasPower) HrvScatterIntensityMode.POWER else null,
            if (hasPace) HrvScatterIntensityMode.PACE else null
        )
    }

    var selectedMode by rememberSaveable(hasHr, hasPower, hasPace) {
        mutableStateOf(availableModes.firstOrNull() ?: HrvScatterIntensityMode.HEART_RATE)
    }

    val effectiveMode = if (selectedMode in availableModes) {
        selectedMode
    } else {
        availableModes.firstOrNull() ?: HrvScatterIntensityMode.HEART_RATE
    }

    val estimate = remember(points, effectiveMode, protocolType, dfaA1ThresholdT1, dfaA1ThresholdT2) {
        HrvThresholdEstimator.estimateThresholds(
            points = points,
            mode = effectiveMode,
            protocolType = protocolType,
            dfaA1ThresholdT1 = dfaA1ThresholdT1.toDouble(),
            dfaA1ThresholdT2 = dfaA1ThresholdT2.toDouble()
        )
    }

    val paceFormatter = remember { PaceFormatter() }

    val unitStr = when (effectiveMode) {
        HrvScatterIntensityMode.HEART_RATE -> "bpm"
        HrvScatterIntensityMode.POWER -> "W"
        HrvScatterIntensityMode.PACE -> ""
    }

    val noEstimateStr = stringResource(R.string.hrv_threshold_no_estimate)

    fun formatIntensity(intensity: Double?): String {
        if (intensity == null) return noEstimateStr
        return if (effectiveMode == HrvScatterIntensityMode.PACE) {
            paceFormatter.format_with_units(intensity)
        } else {
            "${intensity.toInt()} $unitStr"
        }
    }

    val hrvt1Formatted = formatIntensity(estimate.hrvt1.intensity)
    val hrvt2Formatted = formatIntensity(estimate.hrvt2.intensity)
    val r2Formatted = estimate.rSquared?.let { String.format(Locale.US, "%.2f", it) }
        ?: stringResource(R.string.hrv_threshold_no_estimate)

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag("hrv_threshold_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon, Titles & Provenance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hrv_threshold_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.hrv_threshold_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Protocol Provenance Badge Row
            val provenanceText = when (protocolType) {
                HrvProtocolType.FREE_WORKOUT -> stringResource(R.string.hrv_threshold_provenance_free)
                HrvProtocolType.GUIDED_STEP -> stringResource(R.string.hrv_threshold_provenance_step)
                HrvProtocolType.GUIDED_RAMP -> stringResource(R.string.hrv_threshold_provenance_ramp)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("hrv_threshold_provenance_badge")
            ) {
                Text(
                    text = provenanceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Mode Selector Segmented Buttons (if multiple telemetry modalities exist)
            if (availableModes.size > 1) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hrv_threshold_mode_selector")
                ) {
                    availableModes.forEachIndexed { index, mode ->
                        val labelRes = when (mode) {
                            HrvScatterIntensityMode.HEART_RATE -> R.string.hrv_scatter_toggle_hr
                            HrvScatterIntensityMode.POWER -> R.string.hrv_scatter_toggle_power
                            HrvScatterIntensityMode.PACE -> R.string.hrv_scatter_toggle_pace
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = availableModes.size),
                            onClick = { selectedMode = mode },
                            selected = effectiveMode == mode,
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }

            // Metrics Row: HRVT1, HRVT2, R²
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.hrv_threshold_hrvt1_label),
                    value = hrvt1Formatted,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hrv_threshold_hrvt1_metric")
                )
                MetricItem(
                    iconRes = null,
                    label = stringResource(R.string.hrv_threshold_hrvt2_label),
                    value = hrvt2Formatted,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hrv_threshold_hrvt2_metric")
                )
                MetricItem(
                    iconRes = null,
                    label = "R²",
                    value = r2Formatted,
                    layout = MetricLayout.VERTICAL,
                    modifier = Modifier
                        .weight(0.6f)
                        .testTag("hrv_threshold_r2_metric")
                )
            }

            HorizontalDivider()

            // Confidence & Quality Statistics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Confidence Chip
                val (badgeBg, badgeFg, badgeBorder, badgeTextRes) = when (estimate.overallConfidence) {
                    ThresholdConfidence.HIGH -> Quadruple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        R.string.hrv_threshold_confidence_high
                    )
                    ThresholdConfidence.MEDIUM -> Quadruple(
                        TTColor.ArtifactWarningContainer,
                        TTColor.ArtifactWarningTitle,
                        TTColor.ArtifactWarningBorder,
                        R.string.hrv_threshold_confidence_medium
                    )
                    ThresholdConfidence.LOW -> Quadruple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        R.string.hrv_threshold_confidence_low
                    )
                    ThresholdConfidence.INSUFFICIENT_SPAN -> Quadruple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        R.string.hrv_threshold_insufficient_span
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                    modifier = Modifier
                        .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                        .testTag("hrv_threshold_confidence_badge")
                ) {
                    Text(
                        text = stringResource(badgeTextRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Sample count & Mean artifact percentage
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val nLabel = if (protocolType == HrvProtocolType.GUIDED_STEP) {
                        stringResource(R.string.hrv_threshold_stat_neff_steps, estimate.effectiveSampleSize)
                    } else {
                        stringResource(R.string.hrv_threshold_stat_neff, estimate.effectiveSampleSize)
                    }
                    Text(
                        text = nLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.hrv_threshold_stat_artifact, estimate.meanArtifactRate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Diagnostic Feedback / Informational Warning Notice
            val diagnosticRes = estimate.hrvt1.diagnosticReasonRes ?: estimate.hrvt2.diagnosticReasonRes
            if (diagnosticRes != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hrv_threshold_diagnostic_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(diagnosticRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (estimate.overallConfidence != ThresholdConfidence.HIGH) {
                Text(
                    text = stringResource(R.string.hrv_threshold_informative_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Preview
@Composable
private fun HrvThresholdSummaryCardPreview() {
    ATrainingTrackerTheme {
        val dummyPoints = listOf(
            PathPoint(
                distance = 0.0,
                latLng = LatLng(48.0, 11.0),
                altitude = 500.0,
                timeSec = 600L,
                hr = 135,
                power = 180,
                hrvDfaA1 = 0.90,
                hrvArtifactRate = 1.2
            ),
            PathPoint(
                distance = 1000.0,
                latLng = LatLng(48.01, 11.01),
                altitude = 500.0,
                timeSec = 1800L,
                hr = 170,
                power = 260,
                hrvDfaA1 = 0.55,
                hrvArtifactRate = 1.5
            )
        )
        HrvThresholdSummaryCard(points = dummyPoints)
    }
}
