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

package com.atrainingtracker.trainingtracker.ui.settings.healthconnect

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectHrvImporter
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Settings bottom sheet dialog for configuring Android Health Connect integration (REQ-SET-103).
 */
@Composable
fun HealthConnectSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var syncEnabled by remember { mutableStateOf(TrainingApplication.syncToHealthConnect()) }
    var hasPermissions by remember { mutableStateOf(false) }

    var hrvImportEnabled by remember { mutableStateOf(TrainingApplication.isHealthConnectHrvImportEnabled()) }
    var hasHrvPermissions by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isAvailable = remember { HealthConnectManager.isAvailable(context) }
    val sdkStatus = remember { HealthConnectManager.getSdkStatus(context) }

    val lastSyncTimestamp = remember { TrainingApplication.getHealthConnectLastSyncTimestamp() }
    val lastSyncText = if (lastSyncTimestamp > 0) {
        val dateStr = DateFormat.getMediumDateFormat(context).format(Date(lastSyncTimestamp))
        val timeStr = DateFormat.getTimeFormat(context).format(Date(lastSyncTimestamp))
        stringResource(R.string.google_drive_last_sync, "$dateStr $timeStr")
    } else {
        stringResource(R.string.google_drive_never_synced)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasPermissions = HealthConnectManager.REQUIRED_PERMISSIONS.all { it in granted }
        if (!hasPermissions) {
            syncEnabled = false
        }
    }

    val hrvPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasHrvPermissions = HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in granted
        if (!hasHrvPermissions) {
            hrvImportEnabled = false
            TrainingApplication.setHealthConnectHrvImportEnabled(false)
        } else if (hrvImportEnabled) {
            TrainingApplication.setHealthConnectHrvImportEnabled(true)
            coroutineScope.launch(Dispatchers.IO) {
                HealthConnectHrvImporter.importRecentHrv(context.applicationContext, daysBack = 60)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isAvailable) {
            hasPermissions = HealthConnectManager.hasAllPermissions(context)
            hasHrvPermissions = HealthConnectManager.hasHrvReadPermissions(context)
        }
    }

    AppBottomSheetContent(
        title = stringResource(R.string.health_connect_title),
        iconPainter = safePainterResource(id = R.drawable.ic_health_connect, fallback = Icons.Default.Favorite),
        iconTint = Color.Unspecified,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    TrainingApplication.setSyncToHealthConnect(syncEnabled && isAvailable)
                    TrainingApplication.setHealthConnectHrvImportEnabled(hrvImportEnabled && isAvailable)
                    onDismiss()
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isAvailable) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (sdkStatus == 3) {
                            stringResource(R.string.health_connect_update_required)
                        } else {
                            stringResource(R.string.health_connect_not_available)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.health_connect_sync_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.health_connect_sync_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastSyncText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = { checked ->
                            syncEnabled = checked
                            if (checked && !hasPermissions) {
                                permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                            }
                        }
                    )
                }

                if (syncEnabled && !hasPermissions) {
                    Text(
                        text = stringResource(R.string.health_connect_permissions_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.health_connect_sync_title))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.health_connect_hrv_import_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.health_connect_hrv_import_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hrvImportEnabled,
                        onCheckedChange = { checked ->
                            hrvImportEnabled = checked
                            if (checked) {
                                if (!hasHrvPermissions) {
                                    hrvPermissionLauncher.launch(HealthConnectManager.HRV_READ_PERMISSIONS)
                                } else {
                                    TrainingApplication.setHealthConnectHrvImportEnabled(true)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        HealthConnectHrvImporter.importRecentHrv(
                                            context = context.applicationContext,
                                            daysBack = 60
                                        )
                                    }
                                }
                            } else {
                                TrainingApplication.setHealthConnectHrvImportEnabled(false)
                            }
                        }
                    )
                }

                if (hrvImportEnabled && !hasHrvPermissions) {
                    Text(
                        text = stringResource(R.string.health_connect_hrv_permissions_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            hrvPermissionLauncher.launch(HealthConnectManager.HRV_READ_PERMISSIONS)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.health_connect_hrv_import_title))
                    }
                }
            }
        }
    }
}
