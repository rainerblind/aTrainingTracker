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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRetrySyncCoordinator
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dispatches a pending Aftermath offline sync to [PolarVeritySenseOfflineController] when
 * [polarExerciseId] carries the Verity Sense tag, or to [PolarH10OfflineController] otherwise (`ATT-7924`).
 */
suspend fun syncPendingPolarWorkout(
    context: Context,
    workoutId: Long,
    polarExerciseId: String?
): Boolean {
    return if (PolarVeritySenseOfflineController.isVerityPendingSync(polarExerciseId)) {
        PolarVeritySenseOfflineController.syncPendingWorkout(context, workoutId)
    } else {
        PolarH10OfflineController.syncPendingWorkout(context, workoutId)
    }
}

/**
 * Stateful Aftermath retry-sync wrapper that detects Polar H10 vs. Polar Verity Sense pending workouts,
 * observes live pairing state via [DeviceDataRepository] and `ON_RESUME` lifecycle ticks (`REQ-SET-125`),
 * renders matching wake-up or unpaired-sensor guidance, and invokes [syncPendingPolarWorkout]
 * (`REQ-SET-106.2`, `REQ-SET-109.3`, `REQ-SET-119.1`, `REQ-SET-125.2`, `REQ-SET-125.3`).
 */
@Composable
fun PolarPendingRetrySyncSection(
    workoutId: Long,
    polarExerciseId: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    polarDeviceId: String? = null,
    isSensorPairedOverride: Boolean? = null,
    checkSensorPaired: (suspend (String?, Boolean) -> Boolean)? = null
) {
    val context = LocalContext.current
    val isVeritySense = PolarVeritySenseOfflineController.isVerityPendingSync(polarExerciseId)
    val isSwimBusy = PolarVeritySenseOfflineController.isVeritySwimBusy(polarExerciseId)
    val failedMsg = stringResource(
        if (isVeritySense) R.string.polar_verity_retry_sync_failed else R.string.polar_h10_retry_sync_failed
    )
    val syncStates by PolarRetrySyncCoordinator.states.collectAsState()
    val syncState = syncStates[workoutId] ?: PolarRetrySyncCoordinator.RetrySyncState()

    val isSensorPaired = if (isSensorPairedOverride != null) {
        isSensorPairedOverride
    } else {
        rememberPolarRetrySensorPaired(
            context = context,
            polarDeviceId = polarDeviceId,
            isVeritySense = isVeritySense,
            checkSensorPaired = checkSensorPaired
        )
    }

    PolarH10RetrySyncBanner(
        onSyncNow = {
            PolarRetrySyncCoordinator.startSync(
                context = context,
                workoutId = workoutId,
                polarExerciseId = polarExerciseId,
                failedMessage = failedMsg
            )
        },
        onDismiss = {
            PolarRetrySyncCoordinator.clearState(workoutId)
            onDismiss()
        },
        modifier = modifier,
        isSyncing = syncState.isSyncing,
        errorMessage = syncState.errorMessage,
        isVeritySense = isVeritySense,
        isSwimBusy = isSwimBusy,
        isSensorPaired = isSensorPaired
    )
}

@Composable
private fun rememberPolarRetrySensorPaired(
    context: Context,
    polarDeviceId: String?,
    isVeritySense: Boolean,
    checkSensorPaired: (suspend (String?, Boolean) -> Boolean)?
): Boolean {
    val deviceRepository = remember(context, checkSensorPaired) {
        if (checkSensorPaired == null) DeviceDataRepository.getInstance(context) else null
    }
    val devicesSnapshot = deviceRepository?.allDevices?.collectAsState()?.value
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRefreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Start as null (coding_standards.md §5.10) so Frame 0 does not flash a false unpaired warning
    // before the initial async pairing query completes.
    var resolvedPaired by remember(polarDeviceId, isVeritySense) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(polarDeviceId, isVeritySense, devicesSnapshot, resumeRefreshTick, checkSensorPaired) {
        resolvedPaired = if (checkSensorPaired != null) {
            checkSensorPaired(polarDeviceId, isVeritySense)
        } else {
            withContext(Dispatchers.IO) {
                deviceRepository?.isPolarRetrySensorPaired(polarDeviceId, isVeritySense) ?: true
            }
        }
    }

    return resolvedPaired ?: true
}

/**
 * Material 3 retry sync banner displayed in Aftermath (Workout Details)
 * when a workout has unretrieved offline data stored on a sleeping Polar H10 strap (`REQ-SET-106.2`, `ATT-7919`)
 * or a powered-off Polar Verity Sense optical sensor (`REQ-SET-109.3`, `ATT-7924`), guarding against
 * unpaired sensors (`REQ-SET-125.3`, `ATT-9509`).
 */
@Composable
fun PolarH10RetrySyncBanner(
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isSyncing: Boolean = false,
    errorMessage: String? = null,
    isVeritySense: Boolean = false,
    isSwimBusy: Boolean = false,
    isSensorPaired: Boolean = true
) {
    val titleRes = when {
        isSwimBusy -> R.string.polar_verity_swim_busy_title
        isVeritySense -> R.string.polar_verity_retry_sync_title
        else -> R.string.polar_h10_retry_sync_title
    }
    val descRes = when {
        isSwimBusy -> R.string.polar_verity_swim_busy_desc
        isVeritySense -> R.string.polar_verity_retry_sync_desc
        else -> R.string.polar_h10_retry_sync_desc
    }
    val syncingRes = when {
        isSwimBusy -> R.string.polar_verity_swim_syncing
        isVeritySense -> R.string.polar_verity_retry_syncing
        else -> R.string.polar_h10_retry_syncing
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            BannerHeader(titleRes = titleRes)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (isSyncing) {
                BannerSyncingStatus(syncingRes = syncingRes)
            }

            if (!isSensorPaired) {
                BannerUnpairedHint(isVeritySense = isVeritySense)
            } else if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BannerActions(
                isSyncing = isSyncing,
                isSensorPaired = isSensorPaired,
                onDismiss = onDismiss,
                onSyncNow = onSyncNow
            )
        }
    }
}

@Composable
private fun BannerUnpairedHint(isVeritySense: Boolean) {
    val hintRes = if (isVeritySense) {
        R.string.polar_verity_retry_sync_unpaired_hint
    } else {
        R.string.polar_h10_retry_sync_unpaired_hint
    }
    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BannerHeader(titleRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun BannerSyncingStatus(syncingRes: Int) {
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(syncingRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun BannerActions(
    isSyncing: Boolean,
    isSensorPaired: Boolean,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDismiss,
            enabled = !isSyncing
        ) {
            Text(
                text = stringResource(R.string.polar_h10_retry_sync_dismiss),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSyncNow,
            enabled = !isSyncing && isSensorPaired
        ) {
            Text(
                text = stringResource(R.string.polar_h10_retry_sync_action),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(name = "PolarH10RetrySyncBanner - Light", showBackground = true)
@Preview(name = "PolarH10RetrySyncBanner - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PolarH10RetrySyncBannerPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "PolarVerityRetrySyncBanner - Light", showBackground = true)
@Preview(name = "PolarVerityRetrySyncBanner - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PolarVerityRetrySyncBannerPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {},
            isVeritySense = true
        )
    }
}

@Preview(name = "PolarVeritySwimBusyBanner - Light", showBackground = true)
@Preview(name = "PolarVeritySwimBusyBanner - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PolarVeritySwimBusyBannerPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {},
            isVeritySense = true,
            isSwimBusy = true
        )
    }
}

@Preview(name = "PolarH10RetrySyncBanner - Syncing", showBackground = true)
@Composable
private fun PolarH10RetrySyncBannerSyncingPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {},
            isSyncing = true
        )
    }
}

@Preview(name = "PolarVerityRetrySyncBanner - Unpaired Light", showBackground = true)
@Preview(name = "PolarVerityRetrySyncBanner - Unpaired Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PolarVerityRetrySyncBannerUnpairedPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {},
            isVeritySense = true,
            isSensorPaired = false
        )
    }
}

@Preview(name = "PolarH10RetrySyncBanner - Unpaired Light", showBackground = true)
@Preview(name = "PolarH10RetrySyncBanner - Unpaired Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PolarH10RetrySyncBannerUnpairedPreview() {
    ATrainingTrackerTheme {
        PolarH10RetrySyncBanner(
            onSyncNow = {},
            onDismiss = {},
            isVeritySense = false,
            isSensorPaired = false
        )
    }
}
