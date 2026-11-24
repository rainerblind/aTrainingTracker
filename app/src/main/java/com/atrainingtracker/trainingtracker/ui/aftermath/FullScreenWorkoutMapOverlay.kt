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

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.MapContentScope
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.WorkoutBaseMapType
import com.atrainingtracker.trainingtracker.ui.map.WorkoutMapLayerConfig
import com.atrainingtracker.trainingtracker.ui.map.WorkoutMapSpatialLayer
import com.atrainingtracker.trainingtracker.ui.map.resolveFullscreenWorkoutMapContent
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Deterministic semantics test tags for full-screen workout map mode and its layer sheet (`REQ-MAP-069`).
 */
internal object FullScreenWorkoutMapTestTags {
    const val ROOT = "fullscreen_workout_map_root"
    const val CLOSE_BUTTON = "fullscreen_workout_map_close_button"
    const val LAYERS_BUTTON = "fullscreen_workout_map_layers_button"
    const val LAYERS_SHEET = "fullscreen_workout_map_layers_sheet"
    fun baseMapOption(type: WorkoutBaseMapType): String = "fullscreen_map_base_${type.name}"
    fun spatialLayerRow(layer: WorkoutMapSpatialLayer): String = "fullscreen_map_layer_${layer.name}"
    fun technicalTrackRow(type: TrackType): String = "fullscreen_map_tech_track_${type.name}"
}

/**
 * Full-screen edge-to-edge workout map modal (`REQ-MAP-069.1`, `REQ-MAP-069.2`, `REQ-MAP-069.3`).
 *
 * Hosted inside a separate edge-to-edge [Dialog] window so the underlying `WorkoutDetailTabbedContent`
 * retains its `HorizontalPager` tab/scroll state (`Rule 47`) and two Android `SurfaceView` map surfaces
 * never Z-fight. Exposes [bottomOverlay] and scrub pass-throughs for `ATT-9030`.
 */
@Composable
fun FullScreenWorkoutMapDialog(
    bSportType: BSportType,
    tracks: List<MapTrack>,
    availableTrackTypes: Set<TrackType>,
    enabledTrackTypes: Set<TrackType>,
    onToggleTrackType: (TrackType) -> Unit,
    showTechnicalTracks: Boolean,
    segments: List<MapSegment>,
    routes: List<MapRoute>,
    markers: List<LocationMarker>,
    knownLocations: List<KnownLocationItem>,
    climbs: List<Climb>,
    lapSegment: List<LatLng>,
    layerConfig: WorkoutMapLayerConfig,
    onToggleSpatialLayer: (WorkoutMapSpatialLayer) -> Unit,
    onSelectBaseMapType: (WorkoutBaseMapType) -> Unit,
    onClose: () -> Unit,
    activeScrubPath: List<PathPoint>? = null,
    selectedDistance: Double? = null,
    activeScrubPoint: PathPoint? = null,
    bottomOverlay: @Composable BoxScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Inherit host MaterialTheme CompositionLocals (including AMOLED/dark surface tokens)
        // directly without re-wrapping ATrainingTrackerTheme, and sync system-bar icon contrast
        // strictly on the Dialog's own window.
        val dialogView = LocalView.current
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        LaunchedEffect(dialogView, isDark) {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            if (dialogWindow != null) {
                WindowCompat.getInsetsController(dialogWindow, dialogView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
        FullScreenWorkoutMapContent(
            bSportType = bSportType,
            tracks = tracks,
            availableTrackTypes = availableTrackTypes,
            enabledTrackTypes = enabledTrackTypes,
            onToggleTrackType = onToggleTrackType,
            showTechnicalTracks = showTechnicalTracks,
            segments = segments,
            routes = routes,
            markers = markers,
            knownLocations = knownLocations,
            climbs = climbs,
            lapSegment = lapSegment,
            layerConfig = layerConfig,
            onToggleSpatialLayer = onToggleSpatialLayer,
            onSelectBaseMapType = onSelectBaseMapType,
            onClose = onClose,
            activeScrubPath = activeScrubPath,
            selectedDistance = selectedDistance,
            activeScrubPoint = activeScrubPoint,
            bottomOverlay = bottomOverlay
        )
    }
}

/**
 * Pure full-screen workout map layout hosting the map canvas, floating Close (`X`) and Layers controls,
 * modal [WorkoutMapLayersSheet], system Back handling, and optional [bottomOverlay] slot (`REQ-MAP-069`).
 */
@Composable
internal fun FullScreenWorkoutMapContent(
    bSportType: BSportType,
    tracks: List<MapTrack>,
    availableTrackTypes: Set<TrackType>,
    enabledTrackTypes: Set<TrackType>,
    onToggleTrackType: (TrackType) -> Unit,
    showTechnicalTracks: Boolean,
    segments: List<MapSegment>,
    routes: List<MapRoute>,
    markers: List<LocationMarker>,
    knownLocations: List<KnownLocationItem>,
    climbs: List<Climb>,
    lapSegment: List<LatLng>,
    layerConfig: WorkoutMapLayerConfig,
    onToggleSpatialLayer: (WorkoutMapSpatialLayer) -> Unit,
    onSelectBaseMapType: (WorkoutBaseMapType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    activeScrubPath: List<PathPoint>? = null,
    selectedDistance: Double? = null,
    activeScrubPoint: PathPoint? = null,
    initialShowLayersSheet: Boolean = false,
    bottomOverlay: @Composable BoxScope.() -> Unit = {},
    mapSlot: @Composable (
        modifier: Modifier,
        baseMapType: WorkoutBaseMapType,
        resolvedContent: com.atrainingtracker.trainingtracker.ui.map.ResolvedFullscreenWorkoutMapContent,
        mapContentBlock: MapContentScope.() -> Unit
    ) -> Unit = { mapModifier, baseMapType, _, mapContentBlock ->
        val noLocation = remember { MutableStateFlow<LatLng?>(null) }
        ATrainingTrackerMap(
            zoomFocus = MapZoomFocus.FIT_PRIMARY,
            bSportType = bSportType,
            currentLocationFlow = noLocation,
            selectedDistance = selectedDistance,
            activeScrubPath = activeScrubPath,
            activeScrubPoint = activeScrubPoint,
            mapTypeOverride = baseMapType.mapType,
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            modifier = mapModifier,
            content = mapContentBlock
        )
    }
) {
    var showLayersSheet by rememberSaveable { mutableStateOf(initialShowLayersSheet) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveBaseMapType = layerConfig.effectiveBaseMapType(isDarkTheme)
    val lapStartMarkerTitle = stringResource(R.string.marker_lap_start)
    val lapEndMarkerTitle = stringResource(R.string.marker_lap_end)

    val resolvedContent = remember(
        tracks,
        segments,
        routes,
        markers,
        knownLocations,
        climbs,
        lapSegment,
        lapStartMarkerTitle,
        lapEndMarkerTitle,
        layerConfig,
        enabledTrackTypes,
        showTechnicalTracks
    ) {
        resolveFullscreenWorkoutMapContent(
            tracks = tracks,
            segments = segments,
            routes = routes,
            markers = markers,
            knownLocations = knownLocations,
            climbs = climbs,
            lapSegment = lapSegment,
            lapStartMarkerTitle = lapStartMarkerTitle,
            lapEndMarkerTitle = lapEndMarkerTitle,
            layerConfig = layerConfig,
            enabledTrackTypes = enabledTrackTypes,
            showTechnicalTracks = showTechnicalTracks
        )
    }

    val mapContentBlock: MapContentScope.() -> Unit = {
        if (resolvedContent.tracks.isNotEmpty()) {
            tracks(resolvedContent.tracks)
        }
        if (resolvedContent.segments.isNotEmpty()) {
            contextualPaths(resolvedContent.segments)
        }
        if (resolvedContent.routes.isNotEmpty()) {
            contextualPaths(resolvedContent.routes)
        }
        if (resolvedContent.knownLocations.isNotEmpty()) {
            knownLocations(resolvedContent.knownLocations)
        }
        if (resolvedContent.climbs.isNotEmpty()) {
            climbs(resolvedContent.climbs)
        }
        if (resolvedContent.markers.isNotEmpty()) {
            markers(resolvedContent.markers)
        }
        if (resolvedContent.lapHighlight.isNotEmpty()) {
            lapHighlight(resolvedContent.lapHighlight)
        }
    }

    BackHandler(enabled = true) {
        if (showLayersSheet) {
            showLayersSheet = false
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(FullScreenWorkoutMapTestTags.ROOT)
    ) {
        mapSlot(
            Modifier.fillMaxSize(),
            effectiveBaseMapType,
            resolvedContent,
            mapContentBlock
        )

        // Floating Top-Start Close (X) Button (REQ-MAP-069.2)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(1f)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
        ) {
            Surface(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .testTag(FullScreenWorkoutMapTestTags.CLOSE_BUTTON),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
                shadowElevation = 6.dp,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.workout_map_close_fullscreen),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Floating Top-End Layers Button (REQ-MAP-069.3)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(1f)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
        ) {
            val hasCustomOverrides = layerConfig.hasCustomOverrides(
                enabledTrackTypes = enabledTrackTypes,
                showTechnicalTracks = showTechnicalTracks,
                isDark = isDarkTheme
            )
            Surface(
                onClick = { showLayersSheet = true },
                modifier = Modifier
                    .size(44.dp)
                    .testTag(FullScreenWorkoutMapTestTags.LAYERS_BUTTON),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
                shadowElevation = 6.dp,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = stringResource(R.string.map_layers),
                        modifier = Modifier.size(22.dp),
                        tint = if (hasCustomOverrides) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        // Extensibility slot for ATT-9030 bottom collapsible metric scrubber
        bottomOverlay()
    }

    if (showLayersSheet) {
        WorkoutMapLayersSheet(
            layerConfig = layerConfig,
            availableTrackTypes = availableTrackTypes,
            enabledTrackTypes = enabledTrackTypes,
            showTechnicalTracks = showTechnicalTracks,
            onToggleSpatialLayer = onToggleSpatialLayer,
            onToggleTrackType = onToggleTrackType,
            onSelectBaseMapType = onSelectBaseMapType,
            onDismiss = { showLayersSheet = false }
        )
    }
}

/**
 * Modal bottom sheet controlling base-map tile type and spatial overlays for the full-screen workout map
 * (`REQ-MAP-069.3`). Adheres to `docs/design_guidelines.md` (`AppModalBottomSheet`, M3
 * `SingleChoiceSegmentedButtonRow`, `HorizontalDivider` sectioning, and `MapScreenWithTrack`-aligned
 * color-swatch + `Checkbox` rows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutMapLayersSheet(
    layerConfig: WorkoutMapLayerConfig,
    availableTrackTypes: Set<TrackType>,
    enabledTrackTypes: Set<TrackType>,
    showTechnicalTracks: Boolean,
    onToggleSpatialLayer: (WorkoutMapSpatialLayer) -> Unit,
    onToggleTrackType: (TrackType) -> Unit,
    onSelectBaseMapType: (WorkoutBaseMapType) -> Unit,
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(
        title = stringResource(R.string.map_layers),
        icon = Icons.Default.Layers,
        onDismissRequest = onDismiss,
        showCloseButton = true,
        scrollable = true,
        modifier = Modifier.testTag(FullScreenWorkoutMapTestTags.LAYERS_SHEET)
    ) {
        WorkoutMapLayersSheetBody(
            layerConfig = layerConfig,
            availableTrackTypes = availableTrackTypes,
            enabledTrackTypes = enabledTrackTypes,
            showTechnicalTracks = showTechnicalTracks,
            onToggleSpatialLayer = onToggleSpatialLayer,
            onToggleTrackType = onToggleTrackType,
            onSelectBaseMapType = onSelectBaseMapType
        )
    }
}

/**
 * Content column of [WorkoutMapLayersSheet], factored cleanly for both [AppModalBottomSheet] and
 * Light/Dark Compose preview inspection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutMapLayersSheetBody(
    layerConfig: WorkoutMapLayerConfig,
    availableTrackTypes: Set<TrackType>,
    enabledTrackTypes: Set<TrackType>,
    showTechnicalTracks: Boolean,
    onToggleSpatialLayer: (WorkoutMapSpatialLayer) -> Unit,
    onToggleTrackType: (TrackType) -> Unit,
    onSelectBaseMapType: (WorkoutBaseMapType) -> Unit,
    modifier: Modifier = Modifier
) {
    val technicalTrackTypes = remember(availableTrackTypes, showTechnicalTracks) {
        if (showTechnicalTracks) {
            TrackType.entries.filter { it != TrackType.BEST && it in availableTrackTypes }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section 1: Mutually Exclusive Base-Map Style Selector
        Text(
            text = stringResource(R.string.workout_map_base_type_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val effectiveBaseMapType = layerConfig.effectiveBaseMapType(isDark)
        val baseMapEntries = WorkoutBaseMapType.entries
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            baseMapEntries.forEachIndexed { index, baseType ->
                SegmentedButton(
                    selected = effectiveBaseMapType == baseType,
                    onClick = { onSelectBaseMapType(baseType) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = baseMapEntries.size
                    ),
                    icon = {},
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.testTag(FullScreenWorkoutMapTestTags.baseMapOption(baseType))
                ) {
                    Text(
                        text = getWorkoutBaseMapTypeLabel(baseType),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Section 2: Spatial Overlays
        Text(
            text = stringResource(R.string.workout_map_layers_overlays_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        WorkoutMapSpatialLayer.entries.forEach { layer ->
            val (label, swatchColor) = resolveSpatialLayerPresentation(layer)
            WorkoutMapLayerToggleRow(
                label = label,
                swatchColor = swatchColor,
                checked = layer in layerConfig.enabledLayers,
                onToggle = { onToggleSpatialLayer(layer) },
                testTag = FullScreenWorkoutMapTestTags.spatialLayerRow(layer)
            )
        }

        // Section 3: Technical Tracks (shown only when available beyond BEST)
        if (technicalTrackTypes.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = stringResource(R.string.workout_map_layers_technical_tracks_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            technicalTrackTypes.forEach { trackType ->
                WorkoutMapLayerToggleRow(
                    label = getTechnicalTrackTypeLabel(trackType),
                    swatchColor = trackType.color,
                    checked = trackType in enabledTrackTypes,
                    onToggle = { onToggleTrackType(trackType) },
                    testTag = FullScreenWorkoutMapTestTags.technicalTrackRow(trackType)
                )
            }
        }
    }
}

@Composable
private fun WorkoutMapLayerToggleRow(
    label: String,
    swatchColor: Color,
    checked: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(12.dp),
            color = swatchColor,
            shape = RoundedCornerShape(2.dp)
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun getWorkoutBaseMapTypeLabel(type: WorkoutBaseMapType): String = when (type) {
    WorkoutBaseMapType.NORMAL -> stringResource(R.string.workout_map_base_normal)
    WorkoutBaseMapType.TERRAIN -> stringResource(R.string.workout_map_base_terrain)
    WorkoutBaseMapType.SATELLITE -> stringResource(R.string.workout_map_base_satellite)
    WorkoutBaseMapType.HYBRID -> stringResource(R.string.workout_map_base_hybrid)
}

@Composable
private fun resolveSpatialLayerPresentation(layer: WorkoutMapSpatialLayer): Pair<String, Color> = when (layer) {
    WorkoutMapSpatialLayer.PRIMARY_TRACK -> Pair(
        stringResource(R.string.workout_map_layer_primary_track),
        TrackType.BEST.color
    )
    WorkoutMapSpatialLayer.SEGMENTS -> Pair(
        stringResource(R.string.map_layer_segments),
        TTColor.StravaOrange
    )
    WorkoutMapSpatialLayer.ROUTES -> Pair(
        stringResource(R.string.map_layer_routes),
        TTColor.RouteSelected
    )
    WorkoutMapSpatialLayer.MARKERS -> Pair(
        stringResource(R.string.workout_map_layer_markers),
        MaterialTheme.colorScheme.tertiary
    )
    WorkoutMapSpatialLayer.CLIMBS -> Pair(
        stringResource(R.string.map_layer_climbs),
        getClimbCategoryColors(ClimbCategory.CAT_1).first
    )
    WorkoutMapSpatialLayer.LAP_HIGHLIGHTS -> Pair(
        stringResource(R.string.workout_map_layer_lap_highlights),
        MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun getTechnicalTrackTypeLabel(type: TrackType): String = when (type) {
    TrackType.BEST -> stringResource(R.string.track_type_best)
    TrackType.GPS -> stringResource(R.string.track_type_gps)
    TrackType.FUSED -> stringResource(R.string.track_type_fused)
    TrackType.ATT_FUSED -> stringResource(R.string.track_type_att_fused)
}

@Preview(name = "Layers Sheet Light", showBackground = true)
@Preview(name = "Layers Sheet Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutMapLayersSheetBodyPreview() {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WorkoutMapLayersSheetBody(
                layerConfig = WorkoutMapLayerConfig(
                    enabledLayers = WorkoutMapSpatialLayer.DEFAULT_ENABLED + WorkoutMapSpatialLayer.CLIMBS,
                    baseMapType = WorkoutBaseMapType.TERRAIN
                ),
                availableTrackTypes = setOf(TrackType.BEST, TrackType.GPS, TrackType.FUSED, TrackType.ATT_FUSED),
                enabledTrackTypes = setOf(TrackType.BEST, TrackType.GPS),
                showTechnicalTracks = true,
                onToggleSpatialLayer = {},
                onToggleTrackType = {},
                onSelectBaseMapType = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "FullScreen Workout Map Light", showBackground = true)
@Preview(name = "FullScreen Workout Map Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FullScreenWorkoutMapContentPreview() {
    ATrainingTrackerTheme {
        FullScreenWorkoutMapContent(
            bSportType = BSportType.BIKE,
            tracks = emptyList(),
            availableTrackTypes = setOf(TrackType.BEST, TrackType.GPS),
            enabledTrackTypes = setOf(TrackType.BEST),
            onToggleTrackType = {},
            showTechnicalTracks = true,
            segments = emptyList(),
            routes = emptyList(),
            markers = emptyList(),
            knownLocations = emptyList(),
            climbs = emptyList(),
            lapSegment = emptyList(),
            layerConfig = WorkoutMapLayerConfig(),
            onToggleSpatialLayer = {},
            onSelectBaseMapType = {},
            onClose = {}
        )
    }
}
