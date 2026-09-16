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

package com.atrainingtracker.trainingtracker.ui.settings.dropbox

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.migration.BackupWorker
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth

/**
 * Bottom sheet composable for configuring Dropbox cloud synchronization and automated backup settings.
 *
 * Architectural Role:
 * - Replaces the full-screen [CloudUploadFragment] outlier with an [AppBottomSheetContent] dialog.
 * - Provides connection controls (OAuth PKCE launch and credential revocation) using authentic brand assets.
 * - Provides interactive toggles and selectors for automated backups and backup intervals.
 * - Integrates standard [AppDialogActions.SaveCancel] to stage preference changes transactionally.
 *
 * @param onDismiss Callback invoked to dismiss the modal bottom sheet dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropboxSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var isConnected by remember { mutableStateOf(TrainingApplication.uploadToDropbox()) }
    var automatedBackups by remember {
        mutableStateOf(prefs.getBoolean("automated_backups", true))
    }
    var backupIntervalDays by remember {
        mutableStateOf(prefs.getString("backup_interval_days", "1") ?: "1")
    }

    // Capture OAuth return when resuming after browser authorization redirect
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val dbxCredential = Auth.getDbxCredential()
                if (dbxCredential != null) {
                    TrainingApplication.storeDropboxCredential(dbxCredential)
                    TrainingApplication.setUploadToDropbox(true)
                }
                isConnected = TrainingApplication.uploadToDropbox()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AppBottomSheetContent(
        title = stringResource(R.string.Dropbox),
        iconPainter = painterResource(id = R.drawable.dropbox_logo_blue),
        iconTint = Color.Unspecified,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    prefs.edit()
                        .putBoolean("automated_backups", automatedBackups)
                        .putString("backup_interval_days", backupIntervalDays)
                        .apply()
                    BackupWorker.schedule(context)
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
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status & Actions
            DropboxConnectionHeader(
                isConnected = isConnected,
                onConnectClick = {
                    (context as? Activity)?.let { activity ->
                        Auth.startOAuth2PKCE(
                            activity,
                            BuildConfig.DROPBOX_APP_KEY,
                            DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY)
                        )
                    }
                },
                onDisconnectClick = {
                    TrainingApplication.deleteDropboxCredential()
                    TrainingApplication.setUploadToDropbox(false)
                    isConnected = false
                }
            )

            HorizontalDivider()

            // Automated Backups Configuration
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            text = stringResource(R.string.automated_backups),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.automated_backups_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = automatedBackups,
                        onCheckedChange = { automatedBackups = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                if (automatedBackups) {
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
                                backupIntervalDays = intervalValues[idx]
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
