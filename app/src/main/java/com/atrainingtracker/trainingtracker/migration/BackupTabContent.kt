/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import java.text.DateFormat
import java.util.Date

/**
 * Composable rendering the Backup tab of [ImportBackupTabsScreen].
 * Provides manual backup creation, provider-aware unified cloud backup triggers, automated backup configuration,
 * and localized status diagnostics with per-target breakdown (REQ-MIG-036).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupTabContent(
    viewModel: BackupRestoreViewModel,
    isBusy: Boolean,
    isDropboxConnected: Boolean,
    isGoogleDriveConnected: Boolean = false,
    onCreateBackupClick: () -> Unit,
    onUploadToCloudClick: (() -> Unit)? = null,
    onUploadToDropboxClick: (() -> Unit)? = null
) {
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsState()
    val effectiveUploadClick = onUploadToCloudClick ?: onUploadToDropboxClick ?: {}
    BackupTabContentLayout(
        isBusy = isBusy,
        isDropboxConnected = isDropboxConnected,
        isGoogleDriveConnected = isGoogleDriveConnected,
        automatedBackupsEnabled = viewModel.automatedBackupsEnabled,
        backupIntervalDays = viewModel.backupIntervalDays,
        lastBackupInfo = lastBackupInfo,
        onCreateBackupClick = onCreateBackupClick,
        onUploadToCloudClick = effectiveUploadClick,
        onUpdateAutomatedBackupsEnabled = { viewModel.updateAutomatedBackupsEnabled(it) },
        onUpdateBackupIntervalDays = { viewModel.updateBackupIntervalDays(it) }
    )
}

/**
 * Stateless layout implementation for [BackupTabContent] enabling preview and testability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupTabContentLayout(
    isBusy: Boolean,
    isDropboxConnected: Boolean,
    isGoogleDriveConnected: Boolean,
    automatedBackupsEnabled: Boolean,
    backupIntervalDays: Int,
    lastBackupInfo: BackupRestoreViewModel.LastBackupInfo?,
    onCreateBackupClick: () -> Unit,
    onUploadToCloudClick: () -> Unit,
    onUpdateAutomatedBackupsEnabled: (Boolean) -> Unit,
    onUpdateBackupIntervalDays: (Int) -> Unit
) {
    val isAnyCloudConnected = isDropboxConnected || isGoogleDriveConnected
    val uploadButtonColors = if (isAnyCloudConnected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    val cloudButtonTextRes = when {
        isDropboxConnected && isGoogleDriveConnected -> R.string.create_and_upload_to_both
        isDropboxConnected -> R.string.create_and_upload_to_dropbox
        isGoogleDriveConnected -> R.string.create_and_upload_to_google_drive
        else -> R.string.create_and_upload_to_cloud
    }

    val automatedBackupSummaryRes = when {
        isDropboxConnected && isGoogleDriveConnected -> R.string.automated_backups_summary_both
        isDropboxConnected -> R.string.automated_backups_summary
        isGoogleDriveConnected -> R.string.automated_backups_summary_google_drive
        else -> R.string.automated_backups_summary_local_only
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Backup Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.create_backup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCreateBackupClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.create_backup))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onUploadToCloudClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    colors = uploadButtonColors
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(cloudButtonTextRes),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- Automated Backups Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.automated_backups),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.automated_backups))
                        Text(
                            text = stringResource(automatedBackupSummaryRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = automatedBackupsEnabled,
                        onCheckedChange = onUpdateAutomatedBackupsEnabled,
                        modifier = Modifier.scale(0.8f)
                    )
                }

                if (automatedBackupsEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.backup_interval),
                        style = MaterialTheme.typography.labelLarge
                    )
                    val intervals = listOf(1, 3, 7, 30)
                    val labels = listOf(
                        stringResource(R.string.backup_interval_daily),
                        stringResource(R.string.backup_interval_3days),
                        stringResource(R.string.backup_interval_weekly),
                        stringResource(R.string.backup_interval_monthly)
                    )
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        val currentLabel = labels[intervals.indexOf(backupIntervalDays).coerceAtLeast(0)]
                        TextField(
                            value = currentLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            intervals.forEachIndexed { index, days ->
                                DropdownMenuItem(
                                    text = { Text(labels[index]) },
                                    onClick = {
                                        onUpdateBackupIntervalDays(days)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    lastBackupInfo?.let { info ->
                        Spacer(modifier = Modifier.height(12.dp))
                        val dateStr = DateFormat.getDateTimeInstance().format(Date(info.timestamp))
                        val (statusText, statusColor) = when (info.status) {
                            "SUCCESS" -> stringResource(R.string.last_backup, dateStr) to MaterialTheme.colorScheme.primary
                            "LOCAL_SUCCESS" -> stringResource(R.string.last_backup_local_success, dateStr) to MaterialTheme.colorScheme.primary
                            "PARTIAL_SUCCESS" -> stringResource(R.string.last_backup_partial_success, dateStr) to MaterialTheme.colorScheme.tertiary
                            else -> stringResource(R.string.last_backup_failed_format, info.status) to MaterialTheme.colorScheme.error
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )

                        // Target Breakdown Badges
                        if (info.targets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                info.targets.forEach { (targetKey, isTargetSuccess) ->
                                    val targetLabel = when (targetKey) {
                                        "local" -> stringResource(R.string.backup_target_local)
                                        "dropbox" -> stringResource(R.string.backup_target_dropbox)
                                        "google_drive" -> stringResource(R.string.backup_target_google_drive)
                                        else -> targetKey.replaceFirstChar { it.uppercase() }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                             verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isTargetSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                contentDescription = null,
                                                tint = if (isTargetSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = targetLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Both Cloud Providers Connected", showBackground = true)
@Composable
private fun BackupTabContentBothCloudsPreview() {
    MaterialTheme {
        BackupTabContentLayout(
            isBusy = false,
            isDropboxConnected = true,
            isGoogleDriveConnected = true,
            automatedBackupsEnabled = true,
            backupIntervalDays = 1,
            lastBackupInfo = BackupRestoreViewModel.LastBackupInfo(
                timestamp = System.currentTimeMillis(),
                status = "SUCCESS",
                targets = mapOf("local" to true, "dropbox" to true, "google_drive" to true)
            ),
            onCreateBackupClick = {},
            onUploadToCloudClick = {},
            onUpdateAutomatedBackupsEnabled = {},
            onUpdateBackupIntervalDays = {}
        )
    }
}

@Preview(name = "Dropbox Only Connected", showBackground = true)
@Composable
private fun BackupTabContentDropboxOnlyPreview() {
    MaterialTheme {
        BackupTabContentLayout(
            isBusy = false,
            isDropboxConnected = true,
            isGoogleDriveConnected = false,
            automatedBackupsEnabled = true,
            backupIntervalDays = 3,
            lastBackupInfo = BackupRestoreViewModel.LastBackupInfo(
                timestamp = System.currentTimeMillis(),
                status = "SUCCESS",
                targets = mapOf("local" to true, "dropbox" to true)
            ),
            onCreateBackupClick = {},
            onUploadToCloudClick = {},
            onUpdateAutomatedBackupsEnabled = {},
            onUpdateBackupIntervalDays = {}
        )
    }
}

@Preview(name = "Google Drive Only Connected", showBackground = true)
@Composable
private fun BackupTabContentGoogleDriveOnlyPreview() {
    MaterialTheme {
        BackupTabContentLayout(
            isBusy = false,
            isDropboxConnected = false,
            isGoogleDriveConnected = true,
            automatedBackupsEnabled = true,
            backupIntervalDays = 7,
            lastBackupInfo = BackupRestoreViewModel.LastBackupInfo(
                timestamp = System.currentTimeMillis(),
                status = "SUCCESS",
                targets = mapOf("local" to true, "google_drive" to true)
            ),
            onCreateBackupClick = {},
            onUploadToCloudClick = {},
            onUpdateAutomatedBackupsEnabled = {},
            onUpdateBackupIntervalDays = {}
        )
    }
}

@Preview(name = "No Cloud Provider Connected (Muted)", showBackground = true)
@Composable
private fun BackupTabContentNoCloudPreview() {
    MaterialTheme {
        BackupTabContentLayout(
            isBusy = false,
            isDropboxConnected = false,
            isGoogleDriveConnected = false,
            automatedBackupsEnabled = true,
            backupIntervalDays = 7,
            lastBackupInfo = BackupRestoreViewModel.LastBackupInfo(
                timestamp = System.currentTimeMillis(),
                status = "LOCAL_SUCCESS",
                targets = mapOf("local" to true)
            ),
            onCreateBackupClick = {},
            onUploadToCloudClick = {},
            onUpdateAutomatedBackupsEnabled = {},
            onUpdateBackupIntervalDays = {}
        )
    }
}
