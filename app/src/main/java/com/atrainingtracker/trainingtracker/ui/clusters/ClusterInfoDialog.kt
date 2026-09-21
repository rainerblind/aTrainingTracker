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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

/**
 * Educational informational bottom sheet presenting the 3D topological clustering algorithm
 * ("Lieblingsstrecken" / Favorite Tracks) to athletes (REQ-UI-149, TST-UI-102).
 *
 * Covers:
 * 1. The concept and benefits of Lieblingsstrecken (automatic naming, gear assignment, comparison).
 * 2. The 3D topological fingerprint metrics (Start, End, Apex / Max Line Distance, Distance, Min/Max Altitude).
 * 3. Sport type awareness and dynamic centroid learning.
 * 4. Sensitivity adjustment (Master Slider) and individual parameter fine-tuning.
 *
 * @param onDismissRequest Callback invoked when the user dismisses the dialog via "Got it", close icon, or swipe down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterInfoDialog(
    onDismissRequest: () -> Unit
) {
    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cluster_info_title),
        icon = Icons.Default.Info,
        actions = {
            AppDialogActions.Confirm(
                onConfirm = onDismissRequest,
                confirmText = stringResource(R.string.cluster_info_close)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: What are Favorite Tracks?
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.cluster_info_what_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.cluster_info_what_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 2: 3D Topological Fingerprint
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.cluster_info_fingerprint_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.cluster_info_fingerprint_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 3: Sport Awareness & Adaptive Centroid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.cluster_info_sports_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.cluster_info_sports_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 4: Sensitivity & Parameter Tuning
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.cluster_info_tuning_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.cluster_info_tuning_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
