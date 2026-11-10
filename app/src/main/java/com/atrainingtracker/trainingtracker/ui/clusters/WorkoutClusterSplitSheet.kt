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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.getVariantColor
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Interactive bottom sheet dialog for detecting and splitting route sub-corridors into distinct clusters (REQ-SET-096.3, ATT-5900).
 *
 * Provides live slider modulation of corridor grouping sensitivity, interactive color-coded preview,
 * customized variant naming, and atomic transaction commit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutClusterSplitSheet(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distanceFormatter = remember { DistanceFormatter() }
    val threshold by viewModel.splitCorridorThreshold.collectAsState()
    val variants by viewModel.detectedVariants.collectAsState()
    val splitVariantPaths by viewModel.splitVariantPaths.collectAsState()

    LaunchedEffect(cluster.id) {
        viewModel.openSplitPreview(cluster)
    }

    val clusterBounds = remember(cluster) {
        if (cluster.minLat != null && cluster.minLng != null && cluster.maxLat != null && cluster.maxLng != null) {
            LatLngBounds(LatLng(cluster.minLat, cluster.minLng), LatLng(cluster.maxLat, cluster.maxLng))
        } else if (cluster.startLat != 0.0 && cluster.startLng != 0.0 && cluster.endLat != 0.0 && cluster.endLng != 0.0) {
            val builder = LatLngBounds.Builder()
            builder.include(LatLng(cluster.startLat, cluster.startLng))
            builder.include(LatLng(cluster.endLat, cluster.endLng))
            if (cluster.maxDispLat != 0.0 && cluster.maxDispLng != 0.0) {
                builder.include(LatLng(cluster.maxDispLat, cluster.maxDispLng))
            }
            try {
                builder.build()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val selectedVariantIds = remember(variants) {
        mutableStateMapOf<Int, Boolean>().apply {
            variants.filter { !it.isPrimary }.forEach { put(it.groupId, true) }
        }
    }

    val customNames = remember(variants) {
        mutableStateMapOf<Int, String>().apply {
            variants.filter { !it.isPrimary }.forEach { put(it.groupId, it.suggestedName) }
        }
    }

    var renameWorkouts by rememberSaveable { mutableStateOf(true) }

    val activeSelectedIds = selectedVariantIds.filter { it.value }.keys
    val canConfirm = variants.size > 1 &&
            activeSelectedIds.isNotEmpty() &&
            activeSelectedIds.all { (customNames[it] ?: "").isNotBlank() }

    AppModalBottomSheet(
        title = stringResource(R.string.cluster_split_dialog_title),
        onDismissRequest = onDismiss,
        icon = Icons.Default.CallSplit,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    val payload = variants
                        .filter { activeSelectedIds.contains(it.groupId) }
                        .map { variant ->
                            val name = customNames[variant.groupId]?.trim()?.takeIf { it.isNotBlank() }
                                ?: variant.suggestedName
                            name to variant.workouts
                        }
                    viewModel.executeClusterSplit(payload, renameWorkouts)
                    onDismiss()
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.cluster_split_action_confirm),
                saveEnabled = canConfirm
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- EMBEDDED COLOR-CODED MAP PREVIEW (REQ-UI-505.1, ATT-6318) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                PathPreviewMap(
                    paths = splitVariantPaths,
                    modifier = Modifier.fillMaxSize(),
                    targetBounds = clusterBounds
                )
            }

            // --- 1. SENSITIVITY / CORRIDOR WIDTH SLIDER ---
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.cluster_split_corridor_threshold,
                                distanceFormatter.format_with_units(threshold)
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { viewModel.setSplitCorridorThreshold(it.toDouble()) },
                        valueRange = 250f..1500f,
                        steps = 24, // 50m intervals between 250m and 1500m
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // --- 2. VARIANTS LIST ---
            if (variants.size <= 1) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.cluster_split_no_variants),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    variants.forEachIndexed { index, variant ->
                        val color = getVariantColor(index)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                color.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (variant.isPrimary) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(color),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "1",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(
                                                    R.string.cluster_split_primary_label,
                                                    variant.workoutIds.size,
                                                    distanceFormatter.format_with_units(variant.avgDistance)
                                                ),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = cluster.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Checkbox(
                                            checked = selectedVariantIds[variant.groupId] ?: false,
                                            onCheckedChange = { checked ->
                                                selectedVariantIds[variant.groupId] = checked
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(color),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${variant.groupId}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Text(
                                            text = stringResource(
                                                R.string.cluster_split_variant_label,
                                                variant.groupId,
                                                variant.workoutIds.size,
                                                distanceFormatter.format_with_units(variant.avgDistance)
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (!variant.isPrimary && (selectedVariantIds[variant.groupId] == true)) {
                                    OutlinedTextField(
                                        value = customNames[variant.groupId] ?: variant.suggestedName,
                                        onValueChange = { customNames[variant.groupId] = it },
                                        label = { Text(stringResource(R.string.name)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 34.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. OPTIONS SECTION ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = renameWorkouts,
                    onCheckedChange = { renameWorkouts = it }
                )
                Text(
                    text = stringResource(R.string.cluster_split_rename_workouts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
