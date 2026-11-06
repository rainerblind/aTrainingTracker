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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

/**
 * Bottom popup presented when a Free-tier athlete attempts to pair a 3rd device (REQ-SET-084, REQ-UI-149).
 * Allows a 1-tap atomic swap with a currently paired device (with smart recommendation)
 * or navigation to the Supporter screen ("Pay what you want").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapOrUpgradeDialog(
    candidateDevice: DeviceUiData,
    currentlyPairedDevices: List<DeviceUiData>,
    recommendedUnpairDeviceId: Long,
    onSwapConfirmed: (unpairDeviceId: Long, pairDeviceId: Long) -> Unit,
    onNavigateToSupporter: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUnpairId by remember(candidateDevice.id) {
        mutableStateOf(
            if (currentlyPairedDevices.any { it.id == recommendedUnpairDeviceId }) {
                recommendedUnpairDeviceId
            } else {
                currentlyPairedDevices.firstOrNull()?.id ?: -1L
            }
        )
    }

    AppModalBottomSheet(
        title = stringResource(R.string.swap_dialog_title),
        onDismissRequest = onDismiss,
        icon = Icons.Default.SwapHoriz,
        actions = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    textAlign = TextAlign.Center
                )
            }
        }
    ) {
        Text(
            text = stringResource(R.string.swap_dialog_message, candidateDevice.deviceName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = stringResource(R.string.swap_dialog_select_device_to_unpair),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        currentlyPairedDevices.forEach { device ->
            val isSelected = device.id == selectedUnpairId
            val isRecommended = device.id == recommendedUnpairDeviceId

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedUnpairId = device.id },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedUnpairId = device.id }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isRecommended) {
                            Text(
                                text = stringResource(R.string.supporter_recommended_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Stacked Full-Width Action Buttons (Rule 73 / REQ-SET-084.8 / ATT-5470)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (selectedUnpairId != -1L) {
                        onSwapConfirmed(selectedUnpairId, candidateDevice.id)
                    }
                },
                enabled = selectedUnpairId != -1L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.swap_dialog_action_swap))
            }

            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onNavigateToSupporter()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.swap_dialog_action_unlock))
            }
        }
    }
}
