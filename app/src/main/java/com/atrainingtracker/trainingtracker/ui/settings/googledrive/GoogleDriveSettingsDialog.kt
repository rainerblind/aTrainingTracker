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

package com.atrainingtracker.trainingtracker.ui.settings.googledrive

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthErrorResolver
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.migration.BackupWorker
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Bottom sheet composable for configuring Google Drive cloud synchronization and automated backup settings.
 *
 * Architectural Role:
 * - Provides Google Drive cloud integration modal bottom sheet dialog.
 * - Integrates native Google Play Services Sign-In activity result launcher.
 * - Enforces least-privilege OAuth scope (drive.file) with real Bearer token acquisition.
 * - Provides interactive toggles for automated workout export, database backup, and Wi-Fi only restriction.
 * - Displays last sync timestamp, status, and localized error states.
 * - Conditionally suppresses synchronization switches when disconnected, displaying an informative connect prompt.
 * - Integrates standard [AppDialogActions.SaveCancel] to stage preference changes transactionally.
 *
 * @param onDismiss Callback invoked to dismiss the modal bottom sheet dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isConnected by remember { mutableStateOf(TrainingApplication.uploadToGoogleDrive()) }
    var accountEmail by remember { mutableStateOf(TrainingApplication.getGoogleDriveAccountEmail()) }
    var uploadWorkouts by remember { mutableStateOf(TrainingApplication.uploadWorkoutsToGoogleDrive()) }
    var uploadBackup by remember { mutableStateOf(TrainingApplication.uploadBackupToGoogleDrive()) }
    var wifiOnly by remember { mutableStateOf(TrainingApplication.uploadToGoogleDriveOnlyOnWifi()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessageResId by remember { mutableStateOf<Int?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = true
        errorMessageResId = null
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                coroutineScope.launch {
                    val tokenResult = GoogleDriveAuthManager.acquireBearerToken(context, account)
                    tokenResult.onSuccess {
                        isConnected = true
                        accountEmail = account.email ?: account.account?.name
                        isLoading = false
                    }.onFailure { ex ->
                        isLoading = false
                        errorMessageResId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
                    }
                }
            } else {
                isLoading = false
                errorMessageResId = R.string.google_drive_error_generic
            }
        } catch (e: Exception) {
            isLoading = false
            errorMessageResId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(e)
        }
    }

    val lastSyncTimestamp = TrainingApplication.getGoogleDriveLastSyncTimestamp()
    val lastSyncText = if (lastSyncTimestamp > 0) {
        val dateStr = DateFormat.getMediumDateFormat(context).format(Date(lastSyncTimestamp))
        val timeStr = DateFormat.getTimeFormat(context).format(Date(lastSyncTimestamp))
        stringResource(R.string.google_drive_last_sync, "$dateStr $timeStr")
    } else {
        stringResource(R.string.google_drive_never_synced)
    }

    AppBottomSheetContent(
        title = stringResource(R.string.google_drive),
        iconPainter = painterResource(id = R.drawable.ic_google_drive),
        iconTint = Color.Unspecified,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    TrainingApplication.setUploadWorkoutsToGoogleDrive(uploadWorkouts)
                    TrainingApplication.setUploadBackupToGoogleDrive(uploadBackup)
                    TrainingApplication.setUploadToGoogleDriveOnlyOnWifi(wifiOnly)
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
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessageResId != null) {
                Text(
                    text = stringResource(errorMessageResId!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Connection Status & Actions
            GoogleDriveConnectionHeader(
                isConnected = isConnected,
                accountEmail = accountEmail,
                onConnectClick = {
                    errorMessageResId = null
                    val client = GoogleDriveAuthManager.getClient(context)
                    signInLauncher.launch(client.signInIntent)
                },
                onDisconnectClick = {
                    coroutineScope.launch {
                        isLoading = true
                        errorMessageResId = null
                        GoogleDriveAuthManager.disconnect(context)
                        isConnected = false
                        accountEmail = null
                        isLoading = false
                    }
                }
            )

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
                                text = stringResource(R.string.upload_workouts_to_google_drive),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.upload_workouts_to_google_drive_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uploadWorkouts,
                            onCheckedChange = { uploadWorkouts = it },
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
                                text = stringResource(R.string.upload_backup_to_google_drive),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.upload_backup_to_google_drive_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uploadBackup,
                            onCheckedChange = { uploadBackup = it },
                            modifier = Modifier.scale(0.8f)
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
                                text = stringResource(R.string.google_drive_only_wifi),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.google_drive_only_wifi_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { wifiOnly = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    // Last Sync Status
                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.google_drive_connect_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
