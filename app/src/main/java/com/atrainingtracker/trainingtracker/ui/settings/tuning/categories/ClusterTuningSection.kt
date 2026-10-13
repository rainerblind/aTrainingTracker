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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterTuningContent

/**
 * Workout route clustering tuning category composable (REQ-SET-075, Rule 36, ATT-3452).
 * Single Source of Truth for algorithmic route matching preferences embedded in Advanced Settings.
 *
 * Provides:
 * - Direct route clustering tuning controls via [ClusterTuningContent].
 * - Recalculate action button guarded by a high-contrast Material 3 [AlertDialog] confirmation.
 * - Architectural decoupling: persists parameters and executes background recalculation via
 *   [WorkoutClusterRepository.recalculateClustersAsync] on the persistent repository CoroutineScope.
 * - Reactive UI state observation: disables sliders and action button during active calculation,
 *   rendering an inline indeterminate progress indicator.
 *
 * @param endpointTolerance Active endpoint tolerance in meters.
 * @param onEndpointToleranceChange Callback when endpoint tolerance changes.
 * @param apexTolerance Active apex/shape tolerance in meters.
 * @param onApexToleranceChange Callback when apex tolerance changes.
 * @param distanceTolerance Active total distance tolerance in percent.
 * @param onDistanceToleranceChange Callback when distance tolerance changes.
 * @param altitudePositionTolerance Active elevation/ascent tolerance in percent.
 * @param onAltitudePositionToleranceChange Callback when altitude tolerance changes.
 * @param useSportTypeForClustering Flag indicating whether sport type partition is enforced.
 * @param onUseSportTypeChange Callback when sport type partition toggle changes.
 * @param useAltitudePosForClustering Flag indicating whether altitude position is compared.
 * @param onUseAltitudePosChange Callback when altitude position toggle changes.
 * @param onValueChangeFinished Optional callback when slider adjustment finishes.
 */
@Composable
fun ClusterTuningSection(
    endpointTolerance: Float,
    onEndpointToleranceChange: (Float) -> Unit,
    apexTolerance: Float,
    onApexToleranceChange: (Float) -> Unit,
    distanceTolerance: Float,
    onDistanceToleranceChange: (Float) -> Unit,
    altitudePositionTolerance: Float,
    onAltitudePositionToleranceChange: (Float) -> Unit,
    useSportTypeForClustering: Boolean,
    onUseSportTypeChange: (Boolean) -> Unit,
    useAltitudePosForClustering: Boolean,
    onUseAltitudePosChange: (Boolean) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { WorkoutClusterRepository.getInstance(context) }
    val migrationStatus by repository.migrationStatus.collectAsStateWithLifecycle()
    val isRecalculating = migrationStatus != null

    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ClusterTuningContent(
            endpointTolerance = endpointTolerance,
            onEndpointToleranceChange = onEndpointToleranceChange,
            apexTolerance = apexTolerance,
            onApexToleranceChange = onApexToleranceChange,
            distanceTolerance = distanceTolerance,
            onDistanceToleranceChange = onDistanceToleranceChange,
            altitudePositionTolerance = altitudePositionTolerance,
            onAltitudePositionToleranceChange = onAltitudePositionToleranceChange,
            useSportTypeForClustering = useSportTypeForClustering,
            onUseSportTypeChange = onUseSportTypeChange,
            useAltitudePosForClustering = useAltitudePosForClustering,
            onUseAltitudePosChange = onUseAltitudePosChange,
            onValueChangeFinished = onValueChangeFinished,
            isDialog = false,
            enabled = !isRecalculating
        )

        Button(
            onClick = { showConfirmDialog = true },
            enabled = !isRecalculating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRecalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.cluster_tuning_recalculating))
            } else {
                Text(text = stringResource(R.string.cluster_tuning_recalculate_button))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(text = stringResource(R.string.cluster_tuning_recalculate_button))
            },
            text = {
                Text(text = stringResource(R.string.cluster_tuning_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        repository.recalculateClustersAsync(
                            endpointTol = endpointTolerance,
                            apexTol = apexTolerance,
                            distTol = distanceTolerance,
                            altPosTol = altitudePositionTolerance,
                            useSport = useSportTypeForClustering,
                            useAlt = useAltitudePosForClustering
                        )
                        onValueChangeFinished()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.cluster_tuning_recalculate_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
