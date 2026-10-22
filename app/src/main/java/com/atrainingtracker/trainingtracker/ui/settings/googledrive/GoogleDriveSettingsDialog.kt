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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthErrorResolver
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.migration.BackupWorker
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.settings.cloud.CloudProviderSettingsContent
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
 * - Integrates shared [CloudProviderSettingsContent] and [GoogleDriveConnectionHeader] per Rule 36 and Rule 62.
 * - Provides interactive toggles for automated workout export, database backup, backup interval, and Wi-Fi restriction.
 * - Displays last sync and last backup timestamps.
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
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var isConnected by remember { mutableStateOf(TrainingApplication.uploadToGoogleDrive()) }
    var accountEmail by remember { mutableStateOf(TrainingApplication.getGoogleDriveAccountEmail()) }
    var uploadWorkouts by remember { mutableStateOf(TrainingApplication.uploadWorkoutsToGoogleDrive()) }
    var uploadBackup by remember { mutableStateOf(TrainingApplication.uploadBackupToGoogleDrive()) }
    var wifiOnly by remember { mutableStateOf(TrainingApplication.uploadToGoogleDriveOnlyOnWifi()) }
    var backupIntervalDays by remember {
        mutableStateOf(prefs.getString("backup_interval_days", "1") ?: "1")
    }
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

    val lastBackupTimestamp = prefs.getLong("last_backup_timestamp", 0L)
    val lastBackupText = if (lastBackupTimestamp > 0L) {
        val dateStr = DateFormat.getMediumDateFormat(context).format(Date(lastBackupTimestamp))
        val timeStr = DateFormat.getTimeFormat(context).format(Date(lastBackupTimestamp))
        stringResource(R.string.last_backup, "$dateStr $timeStr")
    } else {
        stringResource(R.string.last_backup_never)
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
                    prefs.edit().putString("backup_interval_days", backupIntervalDays).apply()
                    BackupWorker.schedule(context)
                    onDismiss()
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        CloudProviderSettingsContent(
            isConnected = isConnected,
            isLoading = isLoading,
            errorMessage = errorMessageResId?.let { stringResource(it) },
            header = {
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
            },
            connectPrompt = stringResource(R.string.google_drive_connect_prompt),
            uploadWorkouts = uploadWorkouts,
            onUploadWorkoutsChange = { uploadWorkouts = it },
            uploadWorkoutsSummary = stringResource(R.string.upload_workouts_to_google_drive_summary),
            uploadBackup = uploadBackup,
            onUploadBackupChange = { uploadBackup = it },
            uploadBackupSummary = stringResource(R.string.upload_backup_to_google_drive_summary),
            backupIntervalDays = backupIntervalDays,
            onBackupIntervalChange = { backupIntervalDays = it },
            wifiOnly = wifiOnly,
            onWifiOnlyChange = { wifiOnly = it },
            lastSyncText = lastSyncText,
            lastBackupText = lastBackupText
        )
    }
}
