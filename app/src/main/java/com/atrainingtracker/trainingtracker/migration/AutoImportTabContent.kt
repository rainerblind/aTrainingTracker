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

package com.atrainingtracker.trainingtracker.migration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import java.text.DateFormat
import java.util.Date

/**
 * Composable rendering the Auto-Import tab in [ImportBackupTabsScreen] (REQ-MIG-052).
 * Enables multi-device automatic background synchronization from Dropbox and Google Drive,
 * file format scoping (.fit, .tcx, .gpx, all), and downstream relays to Health Connect and Strava.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoImportTabContent(
    viewModel: BackupRestoreViewModel,
    isBusy: Boolean,
    isDropboxConnected: Boolean,
    isGoogleDriveConnected: Boolean
) {
    val context = LocalContext.current
    AutoImportTabContentLayout(
        isBusy = isBusy,
        isDropboxConnected = isDropboxConnected,
        isGoogleDriveConnected = isGoogleDriveConnected,
        autoImportEnabled = viewModel.autoImportEnabled,
        autoImportDropbox = viewModel.autoImportDropbox,
        autoImportGoogleDrive = viewModel.autoImportGoogleDrive,
        autoImportFormat = viewModel.autoImportFormat,
        syncImportedToHealthConnect = viewModel.syncImportedToHealthConnect,
        uploadToStravaOnImport = viewModel.uploadToStravaOnImport,
        lastSyncTimestamp = viewModel.autoImportLastSyncTime,
        lastSyncResult = viewModel.autoImportLastSyncResult,
        isSyncing = viewModel.isAutoImportSyncing,
        onUpdateAutoImportEnabled = { viewModel.updateAutoImportEnabled(it) },
        onUpdateAutoImportDropbox = { viewModel.updateAutoImportDropbox(it) },
        onUpdateAutoImportGoogleDrive = { viewModel.updateAutoImportGoogleDrive(it) },
        onUpdateAutoImportFormat = { viewModel.updateAutoImportFormat(it) },
        onUpdateSyncImportedToHealthConnect = { viewModel.updateSyncImportedToHealthConnect(it) },
        onUpdateUploadToStravaOnImport = { viewModel.updateUploadToStravaOnImport(it) },
        onSyncNowClick = { viewModel.triggerAutoImportSync(context) }
    )
}

/**
 * Stateless layout implementation for [AutoImportTabContent] enabling previews and isolated testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoImportTabContentLayout(
    isBusy: Boolean,
    isDropboxConnected: Boolean,
    isGoogleDriveConnected: Boolean,
    autoImportEnabled: Boolean,
    autoImportDropbox: Boolean,
    autoImportGoogleDrive: Boolean,
    autoImportFormat: String,
    syncImportedToHealthConnect: Boolean,
    uploadToStravaOnImport: Boolean,
    lastSyncTimestamp: Long,
    lastSyncResult: String,
    isSyncing: Boolean,
    onUpdateAutoImportEnabled: (Boolean) -> Unit,
    onUpdateAutoImportDropbox: (Boolean) -> Unit,
    onUpdateAutoImportGoogleDrive: (Boolean) -> Unit,
    onUpdateAutoImportFormat: (String) -> Unit,
    onUpdateSyncImportedToHealthConnect: (Boolean) -> Unit,
    onUpdateUploadToStravaOnImport: (Boolean) -> Unit,
    onSyncNowClick: () -> Unit
) {
    val formats = listOf(
        "fit" to stringResource(R.string.auto_import_format_fit),
        "tcx" to stringResource(R.string.auto_import_format_tcx),
        "gpx" to stringResource(R.string.auto_import_format_gpx),
        "all" to stringResource(R.string.auto_import_format_all)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Master Auto-Import Switch Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_import_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.auto_import_master_switch_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoImportEnabled,
                        onCheckedChange = onUpdateAutoImportEnabled,
                        enabled = !isBusy && !isSyncing
                    )
                }
            }
        }

        // --- 2. Cloud Sources Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.auto_import_cloud_sources_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Dropbox Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.dropbox_logo_blue),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_import_dropbox_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val statusText = if (isDropboxConnected) {
                            stringResource(R.string.auto_import_cloud_connected, "Connected")
                        } else {
                            stringResource(R.string.auto_import_cloud_not_connected)
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDropboxConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = autoImportDropbox,
                        onCheckedChange = onUpdateAutoImportDropbox,
                        enabled = autoImportEnabled && isDropboxConnected && !isBusy && !isSyncing
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Google Drive Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_drive),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_import_googledrive_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val statusText = if (isGoogleDriveConnected) {
                            stringResource(R.string.auto_import_cloud_connected, "Connected")
                        } else {
                            stringResource(R.string.auto_import_cloud_not_connected)
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isGoogleDriveConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = autoImportGoogleDrive,
                        onCheckedChange = onUpdateAutoImportGoogleDrive,
                        enabled = autoImportEnabled && isGoogleDriveConnected && !isBusy && !isSyncing
                    )
                }
            }
        }

        // --- 3. Preferred File Format Selector Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.auto_import_format_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    formats.forEachIndexed { index, (formatKey, label) ->
                        val selected = autoImportFormat.lowercase() == formatKey.lowercase()
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                            onClick = { onUpdateAutoImportFormat(formatKey) },
                            selected = selected,
                            enabled = autoImportEnabled && !isBusy && !isSyncing
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Downstream Relays Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.auto_import_relays_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Health Connect Relay Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_import_relay_health_connect),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.auto_import_relay_health_connect_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncImportedToHealthConnect,
                        onCheckedChange = onUpdateSyncImportedToHealthConnect,
                        enabled = autoImportEnabled && !isBusy && !isSyncing
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Strava Relay Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.logo_square_strava),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_import_relay_strava),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.auto_import_relay_strava_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uploadToStravaOnImport,
                        onCheckedChange = onUpdateUploadToStravaOnImport,
                        enabled = autoImportEnabled && !isBusy && !isSyncing
                    )
                }
            }
        }

        // --- 5. Sync Status & Manual Trigger Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val formattedTimestamp = remember(lastSyncTimestamp) {
                    if (lastSyncTimestamp > 0L) {
                        DateFormat.getDateTimeInstance().format(Date(lastSyncTimestamp))
                    } else null
                }

                val statusText = if (formattedTimestamp != null) {
                    val base = stringResource(R.string.auto_import_last_sync, formattedTimestamp)
                    if (lastSyncResult.isNotBlank()) "$base • $lastSyncResult" else base
                } else {
                    stringResource(R.string.auto_import_never_synced)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                val hasCloudSource = isDropboxConnected || isGoogleDriveConnected
                val canSync = autoImportEnabled && hasCloudSource && !isBusy && !isSyncing

                Button(
                    onClick = onSyncNowClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSync
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.auto_import_syncing))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.auto_import_sync_now))
                    }
                }
            }
        }
    }
}
