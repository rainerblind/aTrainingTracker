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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import java.text.DateFormat
import java.util.Date

/**
 * Composable rendering the Restore tab of [ImportBackupTabsScreen].
 * Exposes incremental workout merge, automatic local rolling backup restore,
 * external SAF file restore, and cloud restore options (Dropbox, Google Drive) (REQ-MIG-036).
 */
@Composable
internal fun RestoreTabContent(
    isBusy: Boolean,
    isDropboxConnected: Boolean,
    isGoogleDriveConnected: Boolean,
    automaticBackupTimestamp: Long?,
    onIncrementalImportClick: () -> Unit,
    onAutomaticBackupRestoreClick: () -> Unit,
    onLocalRestoreClick: () -> Unit,
    onDropboxRestoreClick: () -> Unit,
    onGoogleDriveRestoreClick: () -> Unit
) {
    val dropboxRestoreColors = if (isDropboxConnected) {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
    val dropboxRestoreBorder = if (isDropboxConnected) {
        ButtonDefaults.outlinedButtonBorder(enabled = !isBusy)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    }

    val googleDriveRestoreColors = if (isGoogleDriveConnected) {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
    val googleDriveRestoreBorder = if (isGoogleDriveConnected) {
        ButtonDefaults.outlinedButtonBorder(enabled = !isBusy)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Import Card (Moved here from Import tab) ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.import_workouts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.incremental_import_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onIncrementalImportClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_workouts))
                }
            }
        }

        // --- Restore Card ---
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.restore_backup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.full_restore_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Dedicated Automatic Local Backup Restore
                val hasAutoBackup = automaticBackupTimestamp != null
                OutlinedButton(
                    onClick = onAutomaticBackupRestoreClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !isBusy && hasAutoBackup
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.restore_automatic_local_backup),
                            fontWeight = FontWeight.SemiBold
                        )
                        val timestampSubtitle = automaticBackupTimestamp?.let {
                            val dateStr = DateFormat.getDateTimeInstance().format(Date(it))
                            stringResource(R.string.restore_automatic_local_backup_format, dateStr)
                        } ?: stringResource(R.string.restore_automatic_backup_not_found)
                        Text(
                            text = timestampSubtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. SAF External File Restore
                OutlinedButton(
                    onClick = onLocalRestoreClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_from_file))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Dropbox Restore
                OutlinedButton(
                    onClick = onDropboxRestoreClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dropboxRestoreColors,
                    border = dropboxRestoreBorder,
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_from_dropbox))
                }

                // 4. Google Drive Restore (when enabled)
                if (isGoogleDriveConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onGoogleDriveRestoreClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = googleDriveRestoreColors,
                        border = googleDriveRestoreBorder,
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.restore_from_google_drive))
                    }
                }
            }
        }
    }
}
