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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun SegmentOnMapScreen(
    segmentSummary: SegmentSummary?,
    mapState: MapState,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val headerLayer = rememberGraphicsLayer()
    val elevationLayer = rememberGraphicsLayer()

    var isSharing by remember { mutableStateOf(false) }

    // Shared state for the "seeker" position on both Map and Profile
    var selectedDistance by remember { mutableStateOf<Double?>(null) }
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        segmentSummary?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RectangleShape,
                modifier = Modifier.statusBarsPadding()
            ) {
                Box(modifier = Modifier.drawWithContent {
                    headerLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(headerLayer)
                }) {
                    Column(modifier = modifier) {
                        SegmentSummaryHeader(
                            summary = it,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 2. MAP (Main content)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ATrainingTrackerMap(
                mapState = mapState,
                currentLocationFlow = noLocation,
                selectedDistance = selectedDistance,
                modifier = Modifier.fillMaxSize(),
                onSegmentClick = { },
                shouldTakeSnapshot = isSharing,
                onSnapshotReady = { mapBitmap ->
                    scope.launch {
                        val hBmp = headerLayer.toImageBitmap().asAndroidBitmap()
                        val eBmp = elevationLayer.toImageBitmap().asAndroidBitmap()
                        combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp)
                        isSharing = false
                    }
                }
            )

            // SHARE BUTTON positioned on top-right of the MAP
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
        mapState.segments.firstOrNull()?.let { segment ->
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
                        pathPoints = segment.path,
                        currentDistance = selectedDistance,
                        onDistanceSelected = { dist ->
                            selectedDistance = dist
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }
    }
}