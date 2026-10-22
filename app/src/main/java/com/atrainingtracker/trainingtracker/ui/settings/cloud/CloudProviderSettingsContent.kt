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

package com.atrainingtracker.trainingtracker.ui.settings.cloud

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector

/**
 * Shared parameterized layout for cloud provider synchronization settings (Dropbox, Google Drive).
 *
 * Features:
 * - Connection header slot
 * - Loading indicator and error banner
 * - Informative disconnected prompt when not authenticated
 * - Symmetrical connected synchronization switches:
 *   - Upload Workouts toggle
 *   - Upload Database Backup toggle
 *   - Nested conditional Backup Interval DropdownSelector
 *   - Wi-Fi only toggle
 *   - Last sync and last backup timestamp footers
 *
 * Enforces Rule 36 (DRY) and Rule 62 (Multi-Provider Cloud UI Symmetry).
 */
@Composable
fun CloudProviderSettingsContent(
    isConnected: Boolean,
    connectPrompt: String,
    uploadWorkouts: Boolean,
    onUploadWorkoutsChange: (Boolean) -> Unit,
    uploadWorkoutsSummary: String,
    uploadBackup: Boolean,
    onUploadBackupChange: (Boolean) -> Unit,
    uploadBackupSummary: String,
    backupIntervalDays: String,
    onBackupIntervalChange: (String) -> Unit,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    lastSyncText: String,
    lastBackupText: String,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    uploadWorkoutsTitle: String = stringResource(R.string.upload_workouts_to_google_drive),
    uploadBackupTitle: String = stringResource(R.string.upload_backup_to_google_drive),
    wifiOnlyTitle: String = stringResource(R.string.google_drive_only_wifi),
    wifiOnlySummary: String = stringResource(R.string.google_drive_only_wifi_summary)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Provider Connection Header
        header()

        if (isConnected) {
            HorizontalDivider()

            // Synchronizations Configuration
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Upload Workouts Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = uploadWorkoutsTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = uploadWorkoutsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uploadWorkouts,
                        onCheckedChange = onUploadWorkoutsChange,
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Upload Backup Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = uploadBackupTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = uploadBackupSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uploadBackup,
                        onCheckedChange = onUploadBackupChange,
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Conditional Backup Interval Dropdown
                if (uploadBackup) {
                    val intervalEntries = stringArrayResource(R.array.backup_interval_entries).toList()
                    val intervalValues = stringArrayResource(R.array.backup_interval_values).toList()
                    val currentIndex = intervalValues.indexOf(backupIntervalDays).coerceAtLeast(0)
                    val currentEntry = if (currentIndex in intervalEntries.indices) {
                        intervalEntries[currentIndex]
                    } else {
                        intervalEntries.firstOrNull() ?: ""
                    }

                    DropdownSelector(
                        label = stringResource(R.string.backup_interval),
                        options = intervalEntries,
                        selectedOption = currentEntry,
                        onOptionSelected = { selected ->
                            val idx = intervalEntries.indexOf(selected)
                            if (idx in intervalValues.indices) {
                                onBackupIntervalChange(intervalValues[idx])
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Wi-Fi Only Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = wifiOnlyTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = wifiOnlySummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = onWifiOnlyChange,
                        modifier = Modifier.scale(0.8f)
                    )
                }

                // Status Footers (Last Sync & Last Backup)
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lastBackupText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Disconnected Prompt
            Text(
                text = connectPrompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
