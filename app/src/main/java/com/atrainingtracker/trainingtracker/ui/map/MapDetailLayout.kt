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
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A unified layout for screen-level map details (Aftermath, Routes, Segments).
 * It manages the standard layout (Header + Map + Profile), shared interaction state,
 * and the snapshot generation logic.
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
    analyticsContent: (@Composable ColumnScope.() -> Unit)? = null
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
    var profileStartDist by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

    val hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (
        TelemetryMetricUtils.hasHeartRateData(activeScrubPath) ||
        TelemetryMetricUtils.hasSpeedData(activeScrubPath) ||
        TelemetryMetricUtils.hasPowerData(activeScrubPath)
    )
    val hasScrollableContent = analyticsContent != null || hasTelemetryGraphs

    Column(
        modifier = modifier
            .then(
                if (showMap) Modifier.fillMaxSize() else Modifier.wrapContentHeight()
            )
            .then(
                if (!useStatusBarsPadding) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier
            )
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

        // 2. MAP AREA with OVERLAYED SHARE BUTTON
        if (showMap) {
            val mapModifier = if (hasScrollableContent) {
                Modifier
                    .weight(1f)
                    .heightIn(min = 240.dp)
                    .fillMaxWidth()
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            }
            Box(modifier = mapModifier) {
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

        // 3. ELEVATION PROFILE & CONTINUOUS METRIC GRAPHS AND 4. ANALYTICS (REQ-UI-213 / ATT-1812)
        val lowerModifier = if (showMap && hasScrollableContent) {
            Modifier
                .weight(1.2f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        }

        Column(modifier = lowerModifier) {
            if (showElevationProfile) {
                activeScrubPath?.let { path ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
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
                                    xAxisDomain = tuningConfig.profileXAxisDomain,
                                    bSportType = bSportType,
                                    zoomScale = profileZoomScale,
                                    startDist = profileStartDist,
                                    onZoomChanged = { z, s ->
                                        profileZoomScale = z
                                        profileStartDist = s
                                    },
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
                                            xAxisDomain = tuningConfig.profileXAxisDomain,
                                            bSportType = bSportType,
                                            zoomScale = profileZoomScale,
                                            startDist = profileStartDist,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // HR Graph
                                    if (TelemetryMetricUtils.hasHeartRateData(path)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.graph_heading_heart_rate),
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
                                            xAxisDomain = tuningConfig.profileXAxisDomain,
                                            bSportType = bSportType,
                                            zoomScale = profileZoomScale,
                                            startDist = profileStartDist,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Power Graph
                                    if (TelemetryMetricUtils.hasPowerData(path)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.graph_heading_power),
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
                                            xAxisDomain = tuningConfig.profileXAxisDomain,
                                            bSportType = bSportType,
                                            zoomScale = profileZoomScale,
                                            startDist = profileStartDist,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
}
