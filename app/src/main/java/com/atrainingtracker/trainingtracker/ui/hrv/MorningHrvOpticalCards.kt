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

package com.atrainingtracker.trainingtracker.ui.hrv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvSensorModality
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Optical PPG PPI stillness and snug-contact guidance banner shown during Polar Verity Sense
 * morning HRV checks (`REQ-HRV-049.2`).
 */
@Composable
internal fun OpticalPpiGuidanceCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("morning_hrv_optical_guidance_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.morning_hrv_optical_guidance_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.morning_hrv_optical_guidance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Compact provenance pill rendered on completed morning HRV results recorded via optical PPI
 * (`REQ-HRV-049.2`).
 */
@Composable
internal fun OpticalPpiProvenanceBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.testTag("morning_hrv_optical_provenance_badge")
    ) {
        Text(
            text = stringResource(R.string.hrv_modality_optical_ppi_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Preview(name = "Optical PPI Morning HRV - Light Mode", showBackground = true)
@Composable
private fun MorningHrvOpticalPpiPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        MorningHrvCheckContent(
            uiState = MorningHrvUiState(
                phase = MorningHrvPhase.COMPLETED,
                posture = HrvPosture.SEATED,
                currentHeartRateBpm = 54,
                isOpticalPpiSession = true,
                computedEntry = HrvReadinessEntry(
                    timestampMs = 1731222000000L,
                    dateIso = "2026-11-20",
                    timezoneId = "UTC",
                    restingHrBpm = 54,
                    rmssdMs = 58.4,
                    lnRmssd = 4.067,
                    hrvScore100 = 81.3,
                    sdnnMs = 66.2,
                    artifactRatePct = 1.4,
                    durationSec = 60,
                    posture = HrvPosture.SEATED,
                    sensorModality = HrvSensorModality.OPTICAL_PPI
                )
            ),
            onNavigateBack = {},
            forceFullHrv = true
        )
    }
}

@Preview(name = "Optical PPI Morning HRV - Dark Mode", showBackground = true)
@Composable
private fun MorningHrvOpticalPpiPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        MorningHrvCheckContent(
            uiState = MorningHrvUiState(
                phase = MorningHrvPhase.COMPLETED,
                posture = HrvPosture.SEATED,
                currentHeartRateBpm = 54,
                isOpticalPpiSession = true,
                computedEntry = HrvReadinessEntry(
                    timestampMs = 1731222000000L,
                    dateIso = "2026-11-20",
                    timezoneId = "UTC",
                    restingHrBpm = 54,
                    rmssdMs = 58.4,
                    lnRmssd = 4.067,
                    hrvScore100 = 81.3,
                    sdnnMs = 66.2,
                    artifactRatePct = 1.4,
                    durationSec = 60,
                    posture = HrvPosture.SEATED,
                    sensorModality = HrvSensorModality.OPTICAL_PPI
                )
            ),
            onNavigateBack = {},
            forceFullHrv = true
        )
    }
}
