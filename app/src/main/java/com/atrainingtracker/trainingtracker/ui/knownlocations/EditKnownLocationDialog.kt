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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

/**
 * Modal Bottom Sheet for editing a known start location name and reference altitude (REQ-UI-165).
 *
 * Key Invariants:
 * - No manual lock checkbox: The user is never burdened with manual locking.
 * - Automatic Write-Protection: Saving a manually edited altitude automatically sets
 *   [ElevationSource.MANUAL_USER] and is_locked = 1.
 * - Internet DEM Fetch: Queries Open-Meteo DEM and updates altitude and source to [ElevationSource.INTERNET_DEM].
 *
 * Traceability: REQ-UI-165, TST-UI-117.8, TST-UI-117.9.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditKnownLocationDialog(
    location: KnownLocationItem,
    isMetric: Boolean,
    onConfirm: (id: Long, name: String, altitudeMeters: Double, source: ElevationSource) -> Unit,
    onFetchDem: suspend (id: Long, latLng: LatLng) -> ElevationResult,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(location.name) }
    var altitudeText by remember {
        mutableStateOf(KnownLocationsUnitConversions.formatAltitudeForEdit(location.altitude, isMetric))
    }
    var currentSource by remember { mutableStateOf(location.source) }
    var isFetchingDem by remember { mutableStateOf(false) }
    var fetchDemError by remember { mutableStateOf<String?>(null) }
    var isAltitudeError by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val unitSuffix = if (isMetric) "m" else "ft"

    AppModalBottomSheet(
        title = stringResource(id = R.string.known_location_edit_title),
        onDismissRequest = onDismiss,
        icon = Icons.Default.EditLocation,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Fetch from Internet (DEM) Button
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isFetchingDem = true
                        fetchDemError = null
                        val result = onFetchDem(location.id, location.latLng)
                        if (result is ElevationResult.Success) {
                            altitudeText = KnownLocationsUnitConversions.formatAltitudeForEdit(
                                result.elevationMeters,
                                isMetric
                            )
                            currentSource = ElevationSource.INTERNET_DEM
                            isAltitudeError = false
                        } else {
                            fetchDemError = "Could not fetch DEM altitude"
                        }
                        isFetchingDem = false
                    }
                },
                enabled = !isFetchingDem,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_location_fetch_dem_button")
            ) {
                if (isFetchingDem) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(R.string.known_location_fetch_dem))
            }

            if (fetchDemError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = fetchDemError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
