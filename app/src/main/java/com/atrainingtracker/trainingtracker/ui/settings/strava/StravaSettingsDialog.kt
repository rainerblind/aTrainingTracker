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

package com.atrainingtracker.trainingtracker.ui.settings.strava

import android.app.Activity
import android.content.SharedPreferences
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
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthRepository
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthState
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaDeauthorizationThread
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSynchronizeThread
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.segments.StravaSegmentsSyncWorker
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import androidx.compose.ui.res.stringArrayResource

/**
 * Bottom sheet composable for configuring Strava integration, synchronizations, and selective upload settings.
 *
 * Architectural Role:
 * - Replaces the legacy full-screen [StravaUploadFragment] outlier with an [AppBottomSheetContent] dialog.
 * - Provides connection controls (OAuth initiation, disconnection, and loading indicator) using authentic brand assets.
 * - Provides manual synchronization triggers for Strava equipment and routes with formatted timestamp summaries.
 * - Provides interactive toggles for selective telemetry upload (GPS, Altitude, Heart Rate, Power, Cadence).
 * - Integrates standard [AppDialogActions.SaveCancel] to stage selective upload preference changes transactionally.
 *
 * @param onDismiss Callback invoked to dismiss the modal bottom sheet dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var isConnected by remember { mutableStateOf(TrainingApplication.getStravaAccessToken() != null) }
    val authState by StravaAuthRepository.getInstance().authState.collectAsState()

    var equipmentLastUpdate by remember {
        mutableStateOf(TrainingApplication.getLastUpdateTimeOfStravaEquipment())
    }
    var routesLastUpdate by remember {
        mutableStateOf(TrainingApplication.getLastUpdateTimeOfStravaRoutes())
    }
    var segmentsLastUpdate by remember {
        mutableStateOf(TrainingApplication.getLastUpdateTimeOfStravaSegments())
    }
    var automatedSegmentsSync by remember {
        mutableStateOf(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())
    }
    var segmentsSyncIntervalDays by remember {
        mutableStateOf(TrainingApplication.getStravaSegmentsSyncIntervalDays())
    }

    var uploadGps by remember {
        mutableStateOf(prefs.getBoolean("uploadStravaGPS", true))
    }
    var uploadAltitude by remember {
        mutableStateOf(prefs.getBoolean("uploadStravaAltitude", true))
    }
    var uploadHr by remember {
        mutableStateOf(prefs.getBoolean("uploadStravaHR", true))
    }
    var uploadPower by remember {
        mutableStateOf(prefs.getBoolean("uploadStravaPower", true))
    }
    var uploadCadence by remember {
        mutableStateOf(prefs.getBoolean("uploadStravaCadence", true))
    }

    // Observer for SharedPreferences updates (e.g. sync timestamps, token changes)
    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT -> {
                    equipmentLastUpdate = TrainingApplication.getLastUpdateTimeOfStravaEquipment()
                }
                TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES -> {
                    routesLastUpdate = TrainingApplication.getLastUpdateTimeOfStravaRoutes()
                }
                TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS -> {
                    segmentsLastUpdate = TrainingApplication.getLastUpdateTimeOfStravaSegments()
                }
                TrainingApplication.SP_STRAVA_TOKEN -> {
                    isConnected = TrainingApplication.getStravaAccessToken() != null
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // React to OAuth authentication completion
    LaunchedEffect(authState) {
        if (authState is StravaAuthState.Success) {
            (context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }

            val repository = SegmentsRepository.getInstance(context)
            repository.syncSegmentsAsync(BSportType.BIKE)
            repository.syncSegmentsAsync(BSportType.RUN)

            val routesRepo = RoutesRepository.getInstance(context)
            routesRepo.syncRoutesFromStravaAsync()

            StravaSegmentsSyncWorker.schedule(context)

            StravaAuthRepository.getInstance().resetState()
            isConnected = true
        }
    }

    AppBottomSheetContent(
        title = stringResource(R.string.Strava),
        iconPainter = painterResource(id = R.drawable.logo_square_strava),
        iconTint = Color.Unspecified,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    prefs.edit()
                        .putBoolean("uploadStravaGPS", uploadGps)
                        .putBoolean("uploadStravaAltitude", uploadAltitude)
                        .putBoolean("uploadStravaHR", uploadHr)
                        .putBoolean("uploadStravaPower", uploadPower)
                        .putBoolean("uploadStravaCadence", uploadCadence)
                        .putBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, automatedSegmentsSync)
                        .putString(TrainingApplication.SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, segmentsSyncIntervalDays)
                        .apply()
                    StravaSegmentsSyncWorker.schedule(context)
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
            StravaConnectionHeader(
                isConnected = isConnected,
                isConnecting = authState is StravaAuthState.Loading,
                onConnectClick = {
                    StravaHelper.requestAccessToken(context)
                },
                onDisconnectClick = {
                    TrainingApplication.deleteStravaToken()
                    (context as? Activity)?.let { StravaDeauthorizationThread(it).start() }
                    StravaSegmentsSyncWorker.schedule(context)
                    isConnected = false
                }
            )

            if (isConnected) {
                HorizontalDivider()

                // Manual Synchronization Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedCard(
                        onClick = {
                            (context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.updateStravaEquipment),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = equipmentLastUpdate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            val routesRepo = RoutesRepository.getInstance(context)
                            routesRepo.syncRoutesFromStravaAsync()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.updateStravaRoutes),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = routesLastUpdate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            val repository = SegmentsRepository.getInstance(context)
                            repository.syncSegmentsAsync(BSportType.UNKNOWN)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.updateStravaSegments),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = segmentsLastUpdate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Automated Synchronization Configuration (matching Dropbox automated backups)
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
                                text = stringResource(R.string.automated_strava_segments_sync),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.automated_strava_segments_sync_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = automatedSegmentsSync,
                            onCheckedChange = { automatedSegmentsSync = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    if (automatedSegmentsSync) {
                        val intervalEntries = stringArrayResource(R.array.backup_interval_entries).toList()
                        val intervalValues = stringArrayResource(R.array.backup_interval_values).toList()
                        val currentIndex = intervalValues.indexOf(segmentsSyncIntervalDays).coerceAtLeast(0)
                        val currentEntry = if (currentIndex in intervalEntries.indices) {
                            intervalEntries[currentIndex]
                        } else {
                            intervalEntries.firstOrNull() ?: ""
                        }

                        DropdownSelector(
                            label = stringResource(R.string.strava_segments_sync_interval),
                            options = intervalEntries,
                            selectedOption = currentEntry,
                            onOptionSelected = { selected ->
                                val idx = intervalEntries.indexOf(selected)
                                if (idx in intervalValues.indices) {
                                    segmentsSyncIntervalDays = intervalValues[idx]
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider()

                // Selective Upload Configuration
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selectiveUpload),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SelectiveUploadToggleRow(
                        title = stringResource(R.string.uploadGPS),
                        checked = uploadGps,
                        onCheckedChange = { uploadGps = it }
                    )

                    SelectiveUploadToggleRow(
                        title = stringResource(R.string.uploadAltitude),
                        checked = uploadAltitude,
                        onCheckedChange = { uploadAltitude = it }
                    )

                    SelectiveUploadToggleRow(
                        title = stringResource(R.string.uploadHR),
                        checked = uploadHr,
                        onCheckedChange = { uploadHr = it }
                    )

                    SelectiveUploadToggleRow(
                        title = stringResource(R.string.uploadPower),
                        checked = uploadPower,
                        onCheckedChange = { uploadPower = it }
                    )

                    SelectiveUploadToggleRow(
                        title = stringResource(R.string.uploadCadence),
                        checked = uploadCadence,
                        onCheckedChange = { uploadCadence = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectiveUploadToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}
