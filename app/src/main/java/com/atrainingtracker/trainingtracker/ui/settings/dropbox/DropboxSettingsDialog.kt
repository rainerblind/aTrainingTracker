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
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.migration.BackupWorker
import com.atrainingtracker.trainingtracker.migration.DropboxBackupManager
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.settings.cloud.CloudProviderSettingsContent
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Recursively unwraps a [Context] to resolve the hosting [Activity], traversing any [ContextWrapper] layers
 * (e.g. ContextThemeWrapper or Compose wrapper hierarchies).
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Bottom sheet composable for configuring Dropbox cloud synchronization and automated backup settings.
 *
 * Architectural Role:
 * - Provides connection controls (OAuth PKCE launch and credential revocation) using authentic brand assets.
 * - Integrates shared [CloudProviderSettingsContent] and [DropboxConnectionHeader] per Rule 36 and Rule 62.
 * - Conditionally suppresses synchronization switches when disconnected, displaying an informative connect prompt.
 * - Displays granular toggles for automated workout export, database backup, backup interval, and Wi-Fi restriction.
 * - Displays last sync and last backup timestamps.
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
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var isConnected by remember {
        mutableStateOf(TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null)
    }
    var accountEmail by remember {
        mutableStateOf(TrainingApplication.getDropboxAccountEmail())
    }
    var uploadWorkouts by remember {
        mutableStateOf(TrainingApplication.uploadWorkoutsToDropbox())
    }
    var uploadBackup by remember {
        mutableStateOf(TrainingApplication.uploadBackupToDropbox())
    }
    var wifiOnly by remember {
        mutableStateOf(TrainingApplication.uploadToDropboxOnlyOnWifi())
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
                    coroutineScope.launch {
                        val email = DropboxBackupManager.fetchAccountEmail()
                        if (!email.isNullOrBlank()) {
                            TrainingApplication.setDropboxAccountEmail(email)
                            accountEmail = email
                        }
                    }
                }
                isConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
                accountEmail = TrainingApplication.getDropboxAccountEmail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lastSyncTimestamp = TrainingApplication.getDropboxLastSyncTimestamp()
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
        title = stringResource(R.string.Dropbox),
        iconPainter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud),
        iconTint = Color.Unspecified,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    TrainingApplication.setUploadWorkoutsToDropbox(uploadWorkouts)
                    TrainingApplication.setUploadBackupToDropbox(uploadBackup)
                    TrainingApplication.setUploadToDropboxOnlyOnWifi(wifiOnly)
                    prefs.edit()
                        .putString("backup_interval_days", backupIntervalDays)
                        .putBoolean("automated_backups", uploadBackup)
                        .apply()
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
            header = {
                DropboxConnectionHeader(
                    isConnected = isConnected,
                    accountEmail = accountEmail,
                    onConnectClick = {
                        context.findActivity()?.let { activity ->
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
                        TrainingApplication.setDropboxAccountEmail(null)
                        isConnected = false
                        accountEmail = null
                    }
                )
            },
            connectPrompt = stringResource(R.string.dropbox_connect_prompt),
            uploadWorkouts = uploadWorkouts,
            onUploadWorkoutsChange = { uploadWorkouts = it },
            uploadWorkoutsSummary = stringResource(R.string.upload_workouts_to_dropbox_summary),
            uploadBackup = uploadBackup,
            onUploadBackupChange = { uploadBackup = it },
            uploadBackupSummary = stringResource(R.string.upload_backup_to_dropbox_summary),
            backupIntervalDays = backupIntervalDays,
            onBackupIntervalChange = { backupIntervalDays = it },
            wifiOnly = wifiOnly,
            onWifiOnlyChange = { wifiOnly = it },
            lastSyncText = lastSyncText,
            lastBackupText = lastBackupText
        )
    }
}
