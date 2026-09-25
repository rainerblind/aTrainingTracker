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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.map.createHeartPinMarker
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

/**
 * Modal Bottom Sheet for editing a known start location name and reference altitude (REQ-UI-165).
 *
 * Key Invariants:
 * - No manual lock checkbox: The user is never burdened with manual locking.
 * - Automatic Write-Protection: Saving a manually edited altitude automatically sets
 *   [ElevationSource.MANUAL_USER] and is_locked = 1.
 * - Configurable map view: Can be shown with or without the embedded map depending on caller context
 *   (e.g., shown with map when triggered from list; shown without map when triggered from map peek).
 *
 * Traceability: REQ-UI-165, TST-UI-117.8, TST-UI-117.9.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditKnownLocationDialog(
    location: KnownLocationItem,
    isMetric: Boolean,
    showMap: Boolean = false,
    onConfirm: (id: Long, name: String, altitudeMeters: Double, source: ElevationSource) -> Unit,
    onDismiss: () -> Unit,
    onFetchDem: (suspend (id: Long, latLng: LatLng) -> ElevationResult)? = null
) {
    var name by remember { mutableStateOf(location.name) }
    var altitudeText by remember {
        mutableStateOf(KnownLocationsUnitConversions.formatAltitudeForEdit(location.altitude, isMetric))
    }
    var currentSource by remember { mutableStateOf(location.source) }
    var isAltitudeError by remember { mutableStateOf(false) }

    val unitSuffix = if (isMetric) "m" else "ft"

    AppModalBottomSheet(
        title = stringResource(id = R.string.known_location_edit_title),
        onDismissRequest = onDismiss,
        iconPainter = painterResource(R.drawable.ic_table_edit),
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    val parsed = KnownLocationsUnitConversions.parseInputToMeters(altitudeText, isMetric)
                    if (parsed != null) {
                        val finalName = if (name.isNotBlank()) name.trim() else location.name
                        onConfirm(location.id, finalName, parsed, currentSource)
                    } else {
                        isAltitudeError = true
                    }
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Location Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_name_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reference Altitude Field
            OutlinedTextField(
                value = altitudeText,
                onValueChange = {
                    altitudeText = it
                    currentSource = ElevationSource.MANUAL_USER
                    isAltitudeError = false
                },
                label = { Text("${stringResource(R.string.altitude)} ($unitSuffix)") },
                singleLine = true,
                isError = isAltitudeError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_altitude_input")
            )

            if (isAltitudeError) {
                Text(
                    text = "Please enter a valid altitude number",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            // Embedded Map (shown when requested by caller context)
            if (showMap) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LocationMiniMap(
                        latLng = location.latLng,
                        radius = location.radius.toDouble().takeIf { it > 0 } ?: 200.0,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Standalone bottom sheet content for editing a known start location.
 * Uses [AppBottomSheetContent] so it can be previewed in Android Studio and rendered in bottom sheet dialogs.
 */
@Composable
fun EditKnownLocationSheetContent(
    location: KnownLocationItem,
    isMetric: Boolean,
    showMap: Boolean = false,
    onConfirm: (id: Long, name: String, altitudeMeters: Double, source: ElevationSource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(location.name) }
    var altitudeText by remember {
        mutableStateOf(KnownLocationsUnitConversions.formatAltitudeForEdit(location.altitude, isMetric))
    }
    var currentSource by remember { mutableStateOf(location.source) }
    var isAltitudeError by remember { mutableStateOf(false) }

    val unitSuffix = if (isMetric) "m" else "ft"

    AppBottomSheetContent(
        title = stringResource(id = R.string.known_location_edit_title),
        onDismissRequest = onDismiss,
        iconPainter = painterResource(R.drawable.ic_table_edit),
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    val parsed = KnownLocationsUnitConversions.parseInputToMeters(altitudeText, isMetric)
                    if (parsed != null) {
                        val finalName = if (name.isNotBlank()) name.trim() else location.name
                        onConfirm(location.id, finalName, parsed, currentSource)
                    } else {
                        isAltitudeError = true
                    }
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Location Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_name_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reference Altitude Field
            OutlinedTextField(
                value = altitudeText,
                onValueChange = {
                    altitudeText = it
                    currentSource = ElevationSource.MANUAL_USER
                    isAltitudeError = false
                },
                label = { Text("${stringResource(R.string.altitude)} ($unitSuffix)") },
                singleLine = true,
                isError = isAltitudeError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_altitude_input")
            )

            if (isAltitudeError) {
                Text(
                    text = "Please enter a valid altitude number",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            // Embedded Map (shown when requested by caller context)
            if (showMap) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LocationMiniMap(
                        latLng = location.latLng,
                        radius = location.radius.toDouble().takeIf { it > 0 } ?: 200.0,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Inline mini-map showing the known location marker and its geofence radius.
 * Handles [LocalInspectionMode] gracefully for Android Studio Compose previews.
 */
@Composable
fun LocationMiniMap(
    latLng: LatLng,
    radius: Double,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        // Visual schematic map representation for Compose Previews
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val circleRadius = size.minDimension * 0.35f
                drawCircle(
                    color = Color(0x332196F3),
                    radius = circleRadius,
                    center = center
                )
                drawCircle(
                    color = Color(0x882196F3),
                    radius = circleRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.my_locations),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f (r=%.0fm)", latLng.latitude, latLng.longitude, radius),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLng, 15f)
    }
    val context = LocalContext.current
    val heartMarkerIcon = remember {
        createHeartPinMarker(
            context = context,
            pinColor = Color(0xFF2196F3),
            heartColor = Color.White
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.TERRAIN),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        Marker(
            state = MarkerState(position = latLng),
            icon = heartMarkerIcon
        )
        Circle(
            center = latLng,
            radius = radius,
            fillColor = Color(0x332196F3),
            strokeColor = Color(0x882196F3),
            strokeWidth = 2f
        )
    }
}

// =============================================================================
// COMPOSE PREVIEWS (Edit Bottom Sheet)
// =============================================================================

private val previewEditMockLocation = KnownLocationItem(
    id = 1L,
    name = "Zuhause",
    altitude = 520.0,
    radius = 50,
    latLng = LatLng(48.137154, 11.576124),
    hitCount = 42,
    isLocked = true,
    source = ElevationSource.MANUAL_USER
)

@Preview(name = "Edit Sheet - Without Map (Light)", showBackground = true)
@Composable
fun PreviewEditKnownLocationSheetWithoutMapLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        EditKnownLocationSheetContent(
            location = previewEditMockLocation,
            isMetric = true,
            showMap = false,
            onConfirm = { _, _, _, _ -> },
            onDismiss = {}
        )
    }
}

@Preview(name = "Edit Sheet - With Map (Light)", showBackground = true)
@Composable
fun PreviewEditKnownLocationSheetWithMapLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        EditKnownLocationSheetContent(
            location = previewEditMockLocation,
            isMetric = true,
            showMap = true,
            onConfirm = { _, _, _, _ -> },
            onDismiss = {}
        )
    }
}

@Preview(name = "Edit Sheet - With Map (Dark)", showBackground = true)
@Composable
fun PreviewEditKnownLocationSheetWithMapDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        EditKnownLocationSheetContent(
            location = previewEditMockLocation,
            isMetric = true,
            showMap = true,
            onConfirm = { _, _, _, _ -> },
            onDismiss = {}
        )
    }
}
