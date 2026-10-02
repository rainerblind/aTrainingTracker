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

package com.atrainingtracker.trainingtracker.ui.map

import android.graphics.Bitmap
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.components.core.GlobalTelemetryZoomToolbar
import com.atrainingtracker.trainingtracker.ui.components.core.GlobalTelemetryZoomToolbarDefaults
import com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import com.atrainingtracker.trainingtracker.ui.components.core.SplitPaneDivider
import com.atrainingtracker.trainingtracker.ui.components.core.SplitPaneMath
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A unified layout for screen-level map details (Aftermath, Routes, Segments).
 * It manages the standard layout (Header + Map + Profile), shared interaction state,
 * dynamic draggable viewport resizing (REQ-UI-223 / ATT-1890), dynamic peek baseline
 * self-measurement (REQ-UI-221 / ATT-1645), and snapshot generation logic.
 */
@Composable
fun MapDetailLayout(
    bSportType: BSportType,
    zoomFocus: MapZoomFocus,
    activeScrubPath: List<PathPoint>?,
    initialBounds: LatLngBounds? = null,
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    header: @Composable () -> Unit,
    mapContent: MapContentScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true,
    showElevationProfile: Boolean = true,
    showZoomControls: Boolean = true,
    onMapClick: ((LatLng) -> Unit)? = null,
    analyticsContent: (@Composable ColumnScope.() -> Unit)? = null,
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tuningDataStore = remember { TuningPreferencesDataStore(context) }
    val tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState(
        initial = TuningConfig()
    )

    val headerLayer = rememberGraphicsLayer()
    val elevationLayer = rememberGraphicsLayer()
    val analyticsLayer = rememberGraphicsLayer()

    var isSharing by remember { mutableStateOf(false) }
    var selectedDistance by remember { mutableStateOf<Double?>(null) }
    var profileZoomScale by remember(activeScrubPath) { mutableFloatStateOf(1.0f) }
    var viewportStartFraction by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }
    var isPanMode by remember(activeScrubPath) { mutableStateOf(false) }
    var splitFraction by rememberSaveable { mutableFloatStateOf(SplitPaneMath.DEFAULT_SPLIT_FRACTION) }
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

    val isTrackless = (activeScrubPath?.lastOrNull()?.distance ?: 0.0) == 0.0 && (activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0
    val activeTelemetryDomain = if (isTrackless) ProfileXAxisDomain.TIME else tuningConfig.telemetryXAxisDomain
    val isElevationTimeDomain = (tuningConfig.elevationXAxisDomain == ProfileXAxisDomain.TIME || isTrackless) && (activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0
    val elevationTotalSpan = if (isElevationTimeDomain) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)
    val telemetryTotalSpan = if (activeTelemetryDomain == ProfileXAxisDomain.TIME) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)
    val totalSpan = if (isElevationTimeDomain || isTrackless) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)

    val hasZoomToolbar = showZoomControls && activeScrubPath != null && activeScrubPath.isNotEmpty()
    val hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (
        TelemetryMetricUtils.hasHeartRateData(activeScrubPath) ||
        TelemetryMetricUtils.hasSpeedData(activeScrubPath) ||
        TelemetryMetricUtils.hasPowerData(activeScrubPath)
    )
    val hasScrollableContent = analyticsContent != null || hasTelemetryGraphs

    val mapBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(modifier = boxModifier) {
            ATrainingTrackerMap(
                zoomFocus = zoomFocus,
                initialBounds = initialBounds,
                bSportType = bSportType,
                currentLocationFlow = noLocation,
                selectedDistance = selectedDistance,
                activeScrubPath = activeScrubPath,
                modifier = Modifier.fillMaxSize(),
                shouldTakeSnapshot = isSharing,
                onMapClick = onMapClick,
                onSnapshotReady = { mapBitmap ->
                    scope.launch {
                        val hBmp = if (headerLayer.size.width > 0 && headerLayer.size.height > 0) {
                            withContext(Dispatchers.Default) {
                                headerLayer.toImageBitmap().asAndroidBitmap()
                            }
                        } else null

                        val eBmp = if (elevationLayer.size.width > 0 && elevationLayer.size.height > 0) {
                            withContext(Dispatchers.Default) {
                                elevationLayer.toImageBitmap().asAndroidBitmap()
                            }
                        } else null

                        val aBmp = if (analyticsLayer.size.width > 0 && analyticsLayer.size.height > 0) {
                            withContext(Dispatchers.Default) {
                                analyticsLayer.toImageBitmap().asAndroidBitmap()
                            }
                        } else null

                        combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp, aBmp)
                        isSharing = false
                    }
                },
                onSnapshotError = {
                    isSharing = false
                    android.widget.Toast.makeText(context, R.string.error_sharing_failed, android.widget.Toast.LENGTH_SHORT).show()
                },
                content = mapContent
            )

            overlay()

            // SHARE BUTTON
            Surface(
                onClick = { if (!isSharing) isSharing = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
                shadowElevation = 6.dp,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    val lowerColumn: @Composable (Modifier) -> Unit = { colModifier ->
        Column(modifier = colModifier) {
            if (showElevationProfile) {
                activeScrubPath?.let { path ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (analyticsContent == null) Modifier.navigationBarsPadding() else Modifier
                            )
                    ) {
                        Box(modifier = Modifier.drawWithContent {
                            elevationLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(elevationLayer)
                        }) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (showZoomControls) {
                                    Text(
                                        text = stringResource(R.string.graph_heading_elevation),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                ElevationProfile(
                                    pathPoints = path,
                                    currentDistance = selectedDistance,
                                    minAltitudeOverride = minAltitudeOverride,
                                    maxAltitudeOverride = maxAltitudeOverride,
                                    onDistanceSelected = { selectedDistance = it },
                                    showZoomControls = showZoomControls,
                                    xAxisDomain = tuningConfig.elevationXAxisDomain,
                                    bSportType = bSportType,
                                    zoomScale = profileZoomScale,
                                    startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, elevationTotalSpan, profileZoomScale),
                                    onZoomChanged = { z, s ->
                                        profileZoomScale = z
                                        viewportStartFraction = MapDetailViewportMath.domainToFraction(s, elevationTotalSpan, z)
                                    },
                                    isPanMode = isPanMode,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Telemetry Metric Graphs in detailed inspection view
                                if (showZoomControls) {
                                    // Speed / Pace Graph
                                    if (TelemetryMetricUtils.hasSpeedData(path)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val isRunning = bSportType == BSportType.RUN
                                        Text(
                                            text = stringResource(if (isRunning) R.string.graph_heading_pace else R.string.graph_heading_speed),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                        )
                                        TelemetryMetricGraph(
                                            pathPoints = path,
                                            metricType = if (isRunning) TelemetryMetricType.PACE else TelemetryMetricType.SPEED,
                                            currentDistance = selectedDistance,
                                            onDistanceSelected = { selectedDistance = it },
                                            xAxisDomain = activeTelemetryDomain,
                                            bSportType = bSportType,
                                            zoomScale = profileZoomScale,
                                            startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, telemetryTotalSpan, profileZoomScale),
                                            isPanMode = isPanMode,
                                            onZoomChanged = { z, s ->
                                                profileZoomScale = z
                                                viewportStartFraction = MapDetailViewportMath.domainToFraction(s, telemetryTotalSpan, z)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // HR Graph
                                    if (TelemetryMetricUtils.hasHeartRateData(path)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val activeHrPoint = if (selectedDistance != null) {
                                            if (isTrackless) {
                                                path.minByOrNull { abs(it.timeSec - selectedDistance!!) }
                                            } else {
                                                path.minByOrNull { abs(it.distance - selectedDistance!!) }
                                            }
                                        } else null
                                        val activeHr = activeHrPoint?.hr
                                        val hrHeaderText = if (activeHr != null) {
                                            val hrThresholds = runCatching {
                                                val zoneType = if (bSportType == BSportType.BIKE) {
                                                    com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType.HR_BIKE
                                                } else {
                                                    com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType.HR_RUN
                                                }
                                                val z1 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
                                                val z2 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
                                                val z3 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
                                                val z4 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
                                                if (z1 > 0 && z2 > 0 && z3 > 0 && z4 > 0) {
                                                    com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds(z1, z2, z3, z4)
                                                } else null
                                            }.getOrNull()
                                            val zoneTag = if (hrThresholds != null) {
                                                val zoneIdx = TelemetryZoneMath.determineHeartRateZone(activeHr.toDouble(), hrThresholds)
                                                " • Z$zoneIdx"
                                            } else ""
                                            "${stringResource(R.string.graph_heading_heart_rate)}: $activeHr bpm$zoneTag"
                                        } else {
                                            stringResource(R.string.graph_heading_heart_rate)
                                        }
                                        Text(
                                            text = hrHeaderText,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                        )
                                        TelemetryMetricGraph(
                                            pathPoints = path,
                                            metricType = TelemetryMetricType.HEART_RATE,
                                            currentDistance = selectedDistance,
                                            onDistanceSelected = { selectedDistance = it },
                                            xAxisDomain = activeTelemetryDomain,
                                            bSportType = bSportType,
                                            zoomScale = profileZoomScale,
                                            startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, telemetryTotalSpan, profileZoomScale),
                                            isPanMode = isPanMode,
                                            onZoomChanged = { z, s ->
                                                profileZoomScale = z
                                                viewportStartFraction = MapDetailViewportMath.domainToFraction(s, telemetryTotalSpan, z)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Power Graph
                                    if (TelemetryMetricUtils.hasPowerData(path)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val activePowerPoint = if (selectedDistance != null) {
                                            if (isTrackless) {
                                                path.minByOrNull { abs(it.timeSec - selectedDistance!!) }
                                            } else {
                                                path.minByOrNull { abs(it.distance - selectedDistance!!) }
                                            }
                                        } else null
                                        val activePower = activePowerPoint?.power
                                        val powerHeaderText = if (activePower != null) {
                                            val powerThresholds = runCatching {
                                                val zoneType = com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType.PWR_BIKE
                                                val z1 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
                                                val z2 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
                                                val z3 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
                                                val z4 = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
                                                if (z1 > 0 && z2 > 0 && z3 > 0 && z4 > 0) {
                                                    com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds(z1, z2, z3, z4)
                                                } else null
                                            }.getOrNull()
                                            val zoneTag = if (powerThresholds != null) {
                                                val zoneIdx = TelemetryZoneMath.determinePowerZone(activePower.toDouble(), powerThresholds)
                                                " • Z$zoneIdx"
                                            } else ""
                                            "${stringResource(R.string.graph_heading_power)}: $activePower W$zoneTag"
                                        } else {
                                            stringResource(R.string.graph_heading_power)
                                        }
                                        Text(
                                            text = powerHeaderText,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                        )
                                        TelemetryMetricGraph(
                                            pathPoints = path,
                                            metricType = TelemetryMetricType.POWER,
                                            currentDistance = selectedDistance,
                                            onDistanceSelected = { selectedDistance = it },
                                            xAxisDomain = activeTelemetryDomain,
                                            bSportType = bSportType,
                                            zoomScale = profileZoomScale,
                                            startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, telemetryTotalSpan, profileZoomScale),
                                            isPanMode = isPanMode,
                                            onZoomChanged = { z, s ->
                                                profileZoomScale = z
                                                viewportStartFraction = MapDetailViewportMath.domainToFraction(s, telemetryTotalSpan, z)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (analyticsContent == null && !useStatusBarsPadding) {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }

            // 4. ANALYTICS (Slotted - REQ-UI-205 / ATT-1393)
            analyticsContent?.let { content ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Box(modifier = Modifier.drawWithContent {
                        analyticsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(analyticsLayer)
                    }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            content()
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .then(
                if (showMap || hasScrollableContent) Modifier.fillMaxSize() else Modifier.wrapContentHeight()
            )
            .then(
                if (!useStatusBarsPadding) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier
            )
    ) {
        val density = LocalDensity.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    if (!useStatusBarsPadding && onHeaderHeightMeasured != null) {
                        val heightDp = with(density) { coordinates.size.height.toDp() }
                        onHeaderHeightMeasured(heightDp)
                    }
                }
        ) {
            // DRAG HANDLE (For sheets - REQ-UI-148, REQ-UI-189, REQ-UI-196, ATT-1644)
            if (!useStatusBarsPadding) {
                MinimumDragHandle()
            }

            // 1. HEADER (Slotted)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape,
                modifier = if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier
            ) {
                Box(modifier = Modifier.drawWithContent {
                    headerLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(headerLayer)
                }) {
                    header()
                }
            }
        }

        // 2. RESIZABLE VIEWPORT (Map + SplitPaneDivider + Scrollable Lower Section)
        if (showMap && hasScrollableContent) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val density = LocalDensity.current
                val totalHeightPx = constraints.maxHeight.toFloat()
                val dividerHeightPx = with(density) { SplitPaneMath.DIVIDER_TOUCH_HEIGHT.toPx() }
                val toolbarHeightPx = if (hasZoomToolbar) with(density) { GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT.toPx() } else 0f
                val minMapHeightPx = with(density) { SplitPaneMath.MIN_MAP_HEIGHT.toPx() }
                val minLowerHeightPx = with(density) { SplitPaneMath.MIN_LOWER_HEIGHT.toPx() }

                val availableHeightPx = SplitPaneMath.calculateAvailableHeight(totalHeightPx, dividerHeightPx + toolbarHeightPx)
                val minFraction = SplitPaneMath.calculateMinFraction(minMapHeightPx, availableHeightPx)
                val maxFraction = SplitPaneMath.calculateMaxFraction(minLowerHeightPx, availableHeightPx, minFraction)

                Column(modifier = Modifier.fillMaxSize()) {
                    mapBox(
                        Modifier
                            .weight(splitFraction)
                            .heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)
                            .fillMaxWidth()
                    )

                    // INTERACTIVE DRAGGABLE SPLITTER (REQ-UI-223 / ATT-1890)
                    SplitPaneDivider(
                        onDelta = { delta ->
                            splitFraction = SplitPaneMath.updateFraction(
                                currentFraction = splitFraction,
                                deltaPx = delta,
                                availableHeightPx = availableHeightPx,
                                minFraction = minFraction,
                                maxFraction = maxFraction
                            )
                        },
                        onReset = {
                            splitFraction = SplitPaneMath.DEFAULT_SPLIT_FRACTION
                        }
                    )

                    // PERSISTENT STICKY GLOBAL ZOOM TOOLBAR (REQ-UI-225 / ATT-1876)
                    if (hasZoomToolbar) {
                        GlobalTelemetryZoomToolbar(
                            zoomScale = profileZoomScale,
                            startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, totalSpan, profileZoomScale),
                            totalSpan = totalSpan,
                            onZoomChanged = { z, s ->
                                profileZoomScale = z
                                viewportStartFraction = MapDetailViewportMath.domainToFraction(s, totalSpan, z)
                            },
                            isPanMode = isPanMode,
                            onPanModeToggle = { isPanMode = !isPanMode },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    lowerColumn(
                        Modifier
                            .weight(1f - splitFraction)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        } else {
            // When hasScrollableContent is false (Routes & Segments), or when showMap is false (LiveSegmentSheet)
            if (showMap) {
                mapBox(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            if (hasZoomToolbar) {
                GlobalTelemetryZoomToolbar(
                    zoomScale = profileZoomScale,
                    startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, totalSpan, profileZoomScale),
                    totalSpan = totalSpan,
                    onZoomChanged = { z, s ->
                        profileZoomScale = z
                        viewportStartFraction = MapDetailViewportMath.domainToFraction(s, totalSpan, z)
                    },
                    isPanMode = isPanMode,
                    onPanModeToggle = { isPanMode = !isPanMode },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val columnModifier = if (!showMap && hasScrollableContent) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            }

            lowerColumn(columnModifier)
        }
    }
}
