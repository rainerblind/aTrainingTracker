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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Modern Material 3 permission rationale bottom sheet educating the athlete on the athletic
 * necessity of location, bluetooth sensors, and workout controls (REQ-PRI-003, TST-PRI-002, ATT-2075).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleSheet(
    modifier: Modifier = Modifier,
    isPermanentlyDenied: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        PermissionRationaleContent(
            isPermanentlyDenied = isPermanentlyDenied,
            onContinue = onContinue,
            onOpenSettings = onOpenSettings,
            onDismissRequest = onDismissRequest
        )
    }
}

@Composable
fun PermissionRationaleContent(
    modifier: Modifier = Modifier,
    isPermanentlyDenied: Boolean = false,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.my_locations),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(id = R.string.permission_rationale_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        val subtitleText = if (isPermanentlyDenied) {
            stringResource(id = R.string.permission_rationale_settings_explanation)
        } else {
            stringResource(id = R.string.permission_rationale_subtitle)
        }

        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Card 1: GPS & Location
        RationaleValueItem(
            iconRes = R.drawable.ic_location,
            title = stringResource(id = R.string.permission_rationale_location_title),
            description = stringResource(id = R.string.permission_rationale_location_desc)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Bluetooth & Sensors
        RationaleValueItem(
            iconRes = R.drawable.ic_my_paired_devices,
            title = stringResource(id = R.string.permission_rationale_bluetooth_title),
            description = stringResource(id = R.string.permission_rationale_bluetooth_desc)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card 3: Workout Controls & Notifications
        RationaleValueItem(
            iconRes = R.drawable.ic_lap_timer,
            title = stringResource(id = R.string.permission_rationale_notification_title),
            description = stringResource(id = R.string.permission_rationale_notification_desc)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.permission_rationale_not_now))
            }

            Button(
                onClick = if (isPermanentlyDenied) onOpenSettings else onContinue,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isPermanentlyDenied) {
                        stringResource(id = R.string.permission_rationale_open_settings)
                    } else {
                        stringResource(id = R.string.permission_rationale_continue)
                    }
                )
            }
        }
    }
}

@Composable
private fun RationaleValueItem(
    iconRes: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Previews ---

@Preview(name = "Light Mode", showBackground = true)
@Composable
fun PreviewPermissionRationaleContentLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            PermissionRationaleContent(
                isPermanentlyDenied = false,
                onContinue = {},
                onOpenSettings = {},
                onDismissRequest = {}
            )
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PreviewPermissionRationaleContentDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            PermissionRationaleContent(
                isPermanentlyDenied = false,
                onContinue = {},
                onOpenSettings = {},
                onDismissRequest = {}
            )
        }
    }
}

@Preview(name = "Settings Explanation Mode", showBackground = true)
@Composable
fun PreviewPermissionRationaleContentDenied() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            PermissionRationaleContent(
                isPermanentlyDenied = true,
                onContinue = {},
                onOpenSettings = {},
                onDismissRequest = {}
            )
        }
    }
}
