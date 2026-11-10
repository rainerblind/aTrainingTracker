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

package com.atrainingtracker.trainingtracker.ui.tracking.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Modal quick-switch sheet for Garmin Varia BLE radar tail light mode selection and audio snooze (REQ-CON-031.3, REQ-TRK-064.1).
 *
 * Triggered by tapping the top status badge on [RadarEdgeOverlay]. Dynamically prunes flashing
 * modes when StVZO-compliant hardware is connected, and provides 1-tap audio snooze actions across all radar devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarLightQuickSwitchSheet(
    currentMode: RadarLightMode,
    availableModes: List<RadarLightMode>,
    isStvzo: Boolean,
    isLightControlSupported: Boolean = true,
    snoozeState: RadarAudioSnoozeState = RadarAudioSnoozeState.INACTIVE,
    onModeSelected: (RadarLightMode) -> Unit,
    onSnoozeSelected: (RadarAudioSnoozeMode) -> Unit = {},
    onResumeAudio: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = if (isLightControlSupported) {
        stringResource(R.string.radar_light_sheet_title)
    } else {
        stringResource(R.string.radar_snooze_title)
    }

    AppModalBottomSheet(
        title = titleText,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("RadarLightQuickSwitchSheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLightControlSupported) {
                if (isStvzo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TTColor.RadarDisconnectedAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.radar_light_stvzo_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                availableModes.forEach { mode ->
                    val isSelected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onModeSelected(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("RadarLightMode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dotColor = when (mode) {
                            RadarLightMode.OFF -> Color.Gray
                            RadarLightMode.SOLID -> TTColor.RadarThreatRed
                            RadarLightMode.DAY_FLASH -> TTColor.RadarThreatAmber
                            RadarLightMode.NIGHT_FLASH -> TTColor.RadarThreatAmber.copy(alpha = 0.7f)
                            RadarLightMode.PELOTON -> TTColor.RadarThreatRed.copy(alpha = 0.5f)
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = stringResource(mode.titleResId),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )

                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // Audio Snooze Section (REQ-TRK-064.1)
            Text(
                text = stringResource(R.string.radar_snooze_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            if (snoozeState.isSnoozed) {
                Button(
                    onClick = {
                        onResumeAudio()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("RadarSnooze_Resume")
                ) {
                    Text(text = stringResource(R.string.radar_snooze_resume))
                }
            }

            // Snooze Options
            val snoozeOptions = listOf(
                Pair(RadarAudioSnoozeMode.SNOOZE_15M, R.string.radar_snooze_15m),
                Pair(RadarAudioSnoozeMode.SNOOZE_30M, R.string.radar_snooze_30m),
                Pair(RadarAudioSnoozeMode.FAST_ONLY, R.string.radar_snooze_fast_only),
                Pair(RadarAudioSnoozeMode.REST_OF_RIDE, R.string.radar_snooze_rest_of_ride)
            )

            snoozeOptions.forEach { (mode, stringResId) ->
                val isSelected = snoozeState.isSnoozed && snoozeState.mode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onSnoozeSelected(mode)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("RadarSnooze_${mode.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(stringResId),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )

                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            onSnoozeSelected(mode)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
