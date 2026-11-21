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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap
import com.atrainingtracker.trainingtracker.ui.map.PreviewPolyline
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import androidx.compose.material.icons.filled.EmojiEvents
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutClusterStats

/** Standard landscape map preview width in dp (`REQ-UI-493.3`, `ATT-8748`). */
internal const val THUMBNAIL_WIDTH_DP = 175

/** Standard landscape map preview height in dp (`REQ-UI-493.3`, `ATT-8748`). */
internal const val THUMBNAIL_HEIGHT_DP = 120

/** Camera bounds padding in pixels for landscape card previews (`16..20 px`, `REQ-UI-493.3`). */
internal const val THUMBNAIL_BOUNDS_PADDING_PX = 18

internal const val CLUSTER_THUMBNAIL_TEST_TAG = "cluster_map_preview_thumbnail"
internal const val WORKOUT_THUMBNAIL_TEST_TAG = "unclustered_workout_map_preview_thumbnail"
internal const val CLUSTER_METADATA_TEST_TAG = "cluster_metadata_block"
internal const val WORKOUT_METADATA_TEST_TAG = "unclustered_workout_metadata_block"

private const val THUMBNAIL_SPACER_DP = 12
private const val MIN_METADATA_WIDTH_DP = 160
private const val MIN_THUMBNAIL_WIDTH_DP = 110
private const val CARD_HORIZONTAL_OVERHEAD_DP = 52
private const val THUMBNAIL_CORNER_RADIUS_DP = 12
private const val DEFAULT_POLYLINE_WIDTH_PX = 4f
private const val EMPHASIZED_POLYLINE_WIDTH_PX = 6f
private const val HEATMAP_POLYLINE_ALPHA = 0.2f
private const val AUTHORITATIVE_ROUTE_Z_INDEX = 10f
private const val MAX_VALID_LAT = 90.0

/**
 * Resolves the landscape map preview width in dp so metadata retains at least 160 dp on narrow
 * viewports (e.g., 360 dp) without placing `BoxWithConstraints` inside `IntrinsicSize.Min`.
 */
internal fun resolveClusterThumbnailWidthDp(screenWidthDp: Int): Int {
    val maxThumbnailForMetadata = screenWidthDp - CARD_HORIZONTAL_OVERHEAD_DP - MIN_METADATA_WIDTH_DP
    return maxThumbnailForMetadata.coerceIn(MIN_THUMBNAIL_WIDTH_DP, THUMBNAIL_WIDTH_DP)
}

private fun resolveMapBounds(
    minLat: Double?,
    maxLat: Double?,
    minLng: Double?,
    maxLng: Double?,
    start: LatLng?,
    end: LatLng?,
    apex: LatLng?
): LatLngBounds? {
    return if (minLat != null && maxLat != null && minLng != null && maxLng != null && minLat < MAX_VALID_LAT) {
        LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
    } else if (start != null && end != null && apex != null) {
        LatLngBounds.Builder().include(start).include(end).include(apex).build()
    } else {
        null
    }
}

private fun decodePolylineSafely(encoded: String): List<LatLng> {
    if (encoded.isBlank()) return emptyList()
    return try {
        PolyUtil.decode(encoded)
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (_: IndexOutOfBoundsException) {
        emptyList()
    }
}

/**
 * Standard identity row for a Workout Cluster (Icon + Name).
 */
@Composable
fun WorkoutClusterIdentityRow(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    modifier: Modifier = Modifier
) {
    val bSportType = remember(cluster.probableSportId) { viewModel.getBSportType(cluster.probableSportId) }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = bSportType.iconResId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )
        Text(
            text = cluster.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Standard metadata block for a Workout Cluster (Distance, Sport, Equipment, HitCount, PR Best Time, Total Volume).
 */
@Composable
fun WorkoutClusterMetadataBlock(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    modifier: Modifier = Modifier,
    includeSpacer: Boolean = false,
    onHitCountClick: (() -> Unit)? = null
) {
    val distanceFormatter = remember { DistanceFormatter() }
    val timeFormatter = remember { TimeFormatter() }
    val sportName = remember(cluster.probableSportId) { viewModel.getSportName(cluster.probableSportId) }
    val linkedEquipment = remember(cluster.probableSportId) { viewModel.getLinkedEquipment(cluster.probableSportId) }
    val statsMap by viewModel.clusterStats.collectAsState()
    val stats = statsMap[cluster.id]
    val startLocationName = remember(cluster.startLat, cluster.startLng) {
        viewModel.getLocationName(cluster.startLat, cluster.startLng)
    }
    val endLocationName = remember(cluster.endLat, cluster.endLng) {
        viewModel.getLocationName(cluster.endLat, cluster.endLng)
    }

    Column(modifier = modifier) {
        // 1. Reference Distance & Best Time (PR)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricItem(
                iconRes = R.drawable.ic_distance,
                value = distanceFormatter.format_with_units(cluster.refDistance),
                isPrimary = false,
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            )

            if (stats?.bestDurationSec != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = stringResource(R.string.cluster_stats_best_time),
                        tint = TTColor.Gold, // Gold
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = timeFormatter.format(stats.bestDurationSec),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 1b. Recognized Location Badge (REQ-UI-186, REQ-UI-493.2, ATT-5846)
        val locationBadgeText = when {
            startLocationName != null && endLocationName != null -> {
                if (startLocationName == endLocationName) {
                    stringResource(R.string.workout_start_and_destination, startLocationName)
                } else {
                    "$startLocationName → $endLocationName"
                }
            }
            startLocationName != null -> stringResource(R.string.cluster_start_location, startLocationName)
            endLocationName != null -> stringResource(R.string.workout_destination_location, endLocationName)
            else -> null
        }

        if (locationBadgeText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = locationBadgeText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 2. Sport Type
        Text(
            text = sportName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )


        // 3. Resulting Equipment (REQ-UI-493.3)
        if (linkedEquipment.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cluster_equipment_mapping_format, linkedEquipment.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (includeSpacer) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 4. Hit Count (Self-healing display, REQ-UI-493.1)
        val effectiveHitCount = maxOf(cluster.hitCount, stats?.workoutCount ?: 0)
        Text(
            text = pluralStringResource(R.plurals.cluster_recordings, effectiveHitCount, effectiveHitCount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = if (onHitCountClick != null) {
                Modifier.clickable { onHitCountClick() }
            } else {
                Modifier
            }
        )
    }
}


@Composable
fun ClusterItem(
    cluster: WorkoutCluster,
    viewModel: WorkoutClustersViewModel,
    onClick: () -> Unit,
    onDeleteRequest: (WorkoutCluster) -> Unit,
    onHitCountClick: (WorkoutCluster) -> Unit,
    onMergeRequest: ((WorkoutCluster) -> Unit)? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val thumbnailWidthDp = resolveClusterThumbnailWidthDp(screenWidthDp)

    Box {
        MappableListItem(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showContextMenu = true
            }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // --- 1. TOP ROW: Standard Header (Icon + Name) ---
                    WorkoutClusterIdentityRow(cluster = cluster, viewModel = viewModel)

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. BOTTOM AREA: Details on left, Map on right ---
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // LEFT SIDE: Metadata & Metrics
                        WorkoutClusterMetadataBlock(
                            cluster = cluster,
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(CLUSTER_METADATA_TEST_TAG),
                            includeSpacer = true,
                            onHitCountClick = { onHitCountClick(cluster) }
                        )

                        Spacer(modifier = Modifier.width(THUMBNAIL_SPACER_DP.dp))

                        // RIGHT SIDE: LANDSCAPE MAP THUMBNAIL (REQ-UI-493.3, ATT-8748)
                        val start = LatLng(cluster.startLat, cluster.startLng)
                        val end = LatLng(cluster.endLat, cluster.endLng)
                        val apex = LatLng(cluster.maxDispLat, cluster.maxDispLng)
                        val bounds = remember(
                            cluster.minLat,
                            cluster.maxLat,
                            cluster.minLng,
                            cluster.maxLng,
                            start,
                            end,
                            apex
                        ) {
                            resolveMapBounds(
                                minLat = cluster.minLat,
                                maxLat = cluster.maxLat,
                                minLng = cluster.minLng,
                                maxLng = cluster.maxLng,
                                start = start,
                                end = end,
                                apex = apex
                            )
                        }
                        ClusterMapPreviewThumbnail(
                            widthDp = thumbnailWidthDp,
                            bounds = bounds,
                            previewPolylines = cluster.previewPaths,
                            authoritativePolyline = cluster.routePolyline,
                            start = start,
                            end = end,
                            apex = apex,
                            testTag = CLUSTER_THUMBNAIL_TEST_TAG,
                            onClick = onClick,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showContextMenu = true
                            }
                        )
                    }
                }
            }
        }

        // Context Menu for deletion (Pinned to Top-Start to cover the header area)
        ClusterItemContextMenu(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            onMergeRequest = onMergeRequest?.let { merge -> { merge(cluster) } },
            onDeleteRequest = { onDeleteRequest(cluster) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        )
    }
}

@Composable
private fun ClusterItemContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMergeRequest: (() -> Unit)?,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            if (onMergeRequest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cluster_menu_merge)) },
                    onClick = {
                        onDismiss()
                        onMergeRequest()
                    },
                    leadingIcon = { Icon(Icons.Default.CallMerge, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    onDismiss()
                    onDeleteRequest()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
    }
}

@Composable
fun UnclusteredWorkoutItem(
    workout: WorkoutData,
    viewModel: WorkoutClustersViewModel,
    onClick: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val thumbnailWidthDp = resolveClusterThumbnailWidthDp(screenWidthDp)

    Box {
        MappableListItem(
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // --- 1. TOP ROW: Standard Header (Sport Icon + Workout Name) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = workout.bSportType.iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = workout.workoutName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. BOTTOM AREA: Details on left, Map on right ---
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // LEFT SIDE: Metadata & Metrics
                        val distanceFormatter = remember { DistanceFormatter() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(WORKOUT_METADATA_TEST_TAG)
                        ) {
                            MetricItem(
                                iconRes = R.drawable.ic_distance,
                                value = distanceFormatter.format_with_units(workout.totalDistance),
                                isPrimary = false,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = workout.sportName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!workout.equipmentName.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.cluster_equipment_mapping_format, workout.equipmentName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = workout.formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(THUMBNAIL_SPACER_DP.dp))

                        // RIGHT SIDE: LANDSCAPE MAP THUMBNAIL (REQ-UI-493.3, ATT-8748)
                        val start = workout.startLatLng
                        val end = workout.endLatLng
                        val apex = workout.maxDisplacementLatLng
                        val bounds = remember(
                            workout.minLat,
                            workout.maxLat,
                            workout.minLng,
                            workout.maxLng,
                            start,
                            end,
                            apex
                        ) {
                            resolveMapBounds(
                                minLat = workout.minLat,
                                maxLat = workout.maxLat,
                                minLng = workout.minLng,
                                maxLng = workout.maxLng,
                                start = start,
                                end = end,
                                apex = apex
                            )
                        }
                        val previewPolylines = remember(workout.mapPolyline) {
                            if (workout.mapPolyline.isNotEmpty()) listOf(workout.mapPolyline) else emptyList()
                        }
                        ClusterMapPreviewThumbnail(
                            widthDp = thumbnailWidthDp,
                            bounds = bounds,
                            previewPolylines = previewPolylines,
                            authoritativePolyline = null,
                            start = start,
                            end = end,
                            apex = apex,
                            testTag = WORKOUT_THUMBNAIL_TEST_TAG,
                            onClick = onClick,
                            onLongClick = null
                        )
                    }
                }
            }
        }
    }
}

/**
 * Decodes cluster heatmap preview polylines and an optional authoritative route polyline into
 * styled [PreviewPolyline] overlays for [PathPreviewMap] (`REQ-UI-2789.3`, `REQ-UI-493.3`).
 */
internal fun buildClusterThumbnailPolylines(
    previewPolylines: List<String>,
    authoritativePolyline: String?,
    pathColor: Color
): List<PreviewPolyline> = buildList {
    val isHeatmap = previewPolylines.size > 1
    previewPolylines.forEach { encoded ->
        val points = decodePolylineSafely(encoded)
        if (points.isNotEmpty()) {
            add(
                PreviewPolyline(
                    points = points,
                    color = if (isHeatmap) pathColor.copy(alpha = HEATMAP_POLYLINE_ALPHA) else pathColor,
                    widthPx = if (isHeatmap) EMPHASIZED_POLYLINE_WIDTH_PX else DEFAULT_POLYLINE_WIDTH_PX,
                    roundCaps = true
                )
            )
        }
    }
    authoritativePolyline?.let { encoded ->
        val points = decodePolylineSafely(encoded)
        if (points.isNotEmpty()) {
            add(
                PreviewPolyline(
                    points = points,
                    color = TTColor.RouteSelected,
                    widthPx = EMPHASIZED_POLYLINE_WIDTH_PX,
                    roundCaps = true,
                    zIndex = AUTHORITATIVE_ROUTE_Z_INDEX
                )
            )
        }
    }
}

@Composable
private fun ClusterMapPreviewThumbnail(
    widthDp: Int,
    bounds: LatLngBounds?,
    previewPolylines: List<String>,
    authoritativePolyline: String?,
    start: LatLng?,
    end: LatLng?,
    apex: LatLng?,
    testTag: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val pathColor = MaterialTheme.colorScheme.primary
    val additionalPaths = remember(previewPolylines, authoritativePolyline, pathColor) {
        buildClusterThumbnailPolylines(
            previewPolylines = previewPolylines,
            authoritativePolyline = authoritativePolyline,
            pathColor = pathColor
        )
    }

    Surface(
        modifier = Modifier
            .size(width = widthDp.dp, height = THUMBNAIL_HEIGHT_DP.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        PathPreviewMap(
            modifier = Modifier.fillMaxSize(),
            start = start,
            end = end,
            apex = apex,
            targetBounds = bounds,
            onMapClick = onClick,
            additionalPaths = additionalPaths,
            boundsPaddingPx = THUMBNAIL_BOUNDS_PADDING_PX,
            onMapLongClick = onLongClick
        )
    }
}

/**
 * Standard selection item for a Workout Cluster (Used in dropdowns and dialogs).
 */
@Composable
fun WorkoutClusterSelectionItem(
    cluster: WorkoutCluster,
    score: Double,
    sportName: String,
    bSportType: BSportType,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = bSportType.iconResId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // 1. Identity Row: Name (weighted) + Score (pinned right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cluster.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!score.isNaN()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.cluster_score_brackets_format, score),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 2. Sport Row
            Text(
                text = sportName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 3. Hit Count Row (Blue/Primary, not bold)
            Text(
                text = pluralStringResource(R.plurals.cluster_recordings, cluster.hitCount, cluster.hitCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Standard dialog for selecting a Workout Cluster from a list of candidates.
 * Harmonized between Edit Workout and Manual Reassignment (SCRUM-214).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutClusterSelectionDialog(
    title: String,
    candidates: List<Pair<WorkoutCluster, Double>>,
    onSelect: (WorkoutCluster) -> Unit,
    onDismiss: () -> Unit,
    sportNameResolver: (Long) -> String,
    bSportTypeResolver: (Long) -> BSportType,
    onCreateNew: (() -> Unit)? = null,
    onUnassign: (() -> Unit)? = null
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Default.LocationOn,
        actions = {
            AppDialogActions.CancelOnly(onCancel = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // ATT-7754: Optional action to remove workout from its cluster ("leave unclustered")
            if (onUnassign != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUnassign()
                            onDismiss()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.cluster_naming__leave_unclustered),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ATT-5674: Optional escape hatch to create a new favorite route from selection dialog
            if (onCreateNew != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCreateNew()
                            onDismiss()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.cluster_naming__create_new),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = stringResource(R.string.cluster_suggestions_title),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                itemsIndexed(candidates) { index, pair ->
                    val cluster = pair.first
                    val score = pair.second
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onSelect(cluster)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        WorkoutClusterSelectionItem(
                            cluster = cluster,
                            score = score,
                            sportName = sportNameResolver(cluster.probableSportId),
                            bSportType = bSportTypeResolver(cluster.probableSportId)
                        )
                    }
                    
                    if (index < candidates.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Comprehensive bottom sheet for managing a workout's cluster assignment in Edit Workout (REQ-SET-059, REQ-UI-149, ATT-900).
 * Provides explicit actions for:
 * 1. "Leave Unclustered"
 * 2. "Create New Route..."
 * 3. Selecting from suggested candidate clusters
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutClusterDialog(
    currentClusterId: Long,
    candidates: List<Pair<WorkoutCluster, Double>>,
    initialWorkoutName: String,
    onSelectCluster: (WorkoutCluster) -> Unit,
    onUnassignCluster: () -> Unit,
    onCreateNewCluster: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    sportNameResolver: (Long) -> String,
    bSportTypeResolver: (Long) -> BSportType
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newClusterName by remember(initialWorkoutName) {
        mutableStateOf(WorkoutClusterEngine.stripHitCount(initialWorkoutName))
    }
    var newClusterHasCounter by remember { mutableStateOf(true) }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cluster_naming__title),
        icon = Icons.Default.LocationOn,
        actions = {
            AppDialogActions.CancelOnly(onCancel = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Leave Unclustered Action
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUnassignCluster()
                        onDismiss()
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (currentClusterId <= 0) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = if (currentClusterId <= 0) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.cluster_naming__leave_unclustered),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (currentClusterId <= 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentClusterId <= 0) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Create New Cluster Action
            if (!isCreatingNew) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCreatingNew = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.cluster_naming__create_new),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cluster_naming__new_name_prompt),
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedTextField(
                            value = newClusterName,
                            onValueChange = { newClusterName = it },
                            label = { Text(stringResource(R.string.cluster_naming__new_route_name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.cluster_counter_enabled_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.cluster_counter_enabled_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = newClusterHasCounter,
                                onCheckedChange = { newClusterHasCounter = it }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isCreatingNew = false }) {
                                Text(stringResource(R.string.Cancel))
                            }
                            Button(
                                onClick = {
                                    if (newClusterName.isNotBlank()) {
                                        onCreateNewCluster(newClusterName.trim(), newClusterHasCounter)
                                        onDismiss()
                                    }
                                },
                                enabled = newClusterName.isNotBlank()
                            ) {
                                Text(stringResource(R.string.OK))
                            }
                        }
                    }
                }
            }

            // 3. Candidate Clusters (Suggestions)
            if (candidates.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = stringResource(R.string.cluster_suggestions_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    itemsIndexed(candidates) { index, pair ->
                        val cluster = pair.first
                        val score = pair.second

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCluster(cluster)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            WorkoutClusterSelectionItem(
                                cluster = cluster,
                                score = score,
                                sportName = sportNameResolver(cluster.probableSportId),
                                bSportType = bSportTypeResolver(cluster.probableSportId)
                            )
                        }

                        if (index < candidates.size - 1) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ClusterItemPreviewShell(darkTheme: Boolean) {
    ATrainingTrackerTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MappableListItem(onClick = {}) {
                Column(modifier = Modifier.fillMaxWidth().padding(THUMBNAIL_CORNER_RADIUS_DP.dp)) {
                    ClusterPreviewHeaderRow()
                    Spacer(modifier = Modifier.height(8.dp))
                    ClusterPreviewBodyRow()
                }
            }
        }
    }
}

@Composable
private fun ClusterPreviewHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACER_DP.dp)
    ) {
        Icon(
            painter = painterResource(id = BSportType.BIKE.iconResId),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )
        Text(
            text = "Isar Loop #12",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ClusterPreviewBodyRow() {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            MetricItem(
                iconRes = R.drawable.ic_distance,
                value = "42.5 km",
                isPrimary = false,
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            )
            Text(
                text = "Road Bike",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "12 Recordings",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(THUMBNAIL_SPACER_DP.dp))
        ClusterMapPreviewThumbnail(
            widthDp = THUMBNAIL_WIDTH_DP,
            bounds = null,
            previewPolylines = emptyList(),
            authoritativePolyline = "sample",
            start = null,
            end = null,
            apex = null,
            testTag = CLUSTER_THUMBNAIL_TEST_TAG,
            onClick = {},
            onLongClick = null
        )
    }
}

@Preview(showBackground = true, widthDp = 411)
@Composable
private fun ClusterItemLightPreview() {
    ClusterItemPreviewShell(darkTheme = false)
}

@Preview(showBackground = true, widthDp = 411)
@Composable
private fun ClusterItemDarkPreview() {
    ClusterItemPreviewShell(darkTheme = true)
}

