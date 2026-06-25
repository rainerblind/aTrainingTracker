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
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    minAltitudeOverride: Double? = null,
    maxAltitudeOverride: Double? = null,
    header: @Composable () -> Unit,
    mapContent: MapContentScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val headerLayer = rememberGraphicsLayer()
    val elevationLayer = rememberGraphicsLayer()

    var isSharing by remember { mutableStateOf(false) }
    var selectedDistance by remember { mutableStateOf<Double?>(null) }
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

    Column(modifier = modifier.fillMaxSize()) {

        // 1. HEADER (Slotted)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RectangleShape,
            modifier = Modifier.statusBarsPadding()
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
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ATrainingTrackerMap(
                zoomFocus = zoomFocus,
                bSportType = bSportType,
                currentLocationFlow = noLocation,
                selectedDistance = selectedDistance,
                activeScrubPath = activeScrubPath,
                modifier = Modifier.fillMaxSize(),
                shouldTakeSnapshot = isSharing,
                onSnapshotReady = { mapBitmap ->
                    scope.launch {
                        val hBmp = headerLayer.toImageBitmap().asAndroidBitmap()
                        val eBmp = elevationLayer.toImageBitmap().asAndroidBitmap()
                        combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp)
                        isSharing = false
                    }
                },
                content = mapContent
            )

            // SHARE BUTTON
            Surface(
                onClick = { isSharing = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 6.dp,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 3. ELEVATION PROFILE
        activeScrubPath?.let { path ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Box(modifier = Modifier.drawWithContent {
                    elevationLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(elevationLayer)
                }) {
                    ElevationProfile(
                        pathPoints = path,
                        currentDistance = selectedDistance,
                        minAltitudeOverride = minAltitudeOverride,
                        maxAltitudeOverride = maxAltitudeOverride,
                        onDistanceSelected = { selectedDistance = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
