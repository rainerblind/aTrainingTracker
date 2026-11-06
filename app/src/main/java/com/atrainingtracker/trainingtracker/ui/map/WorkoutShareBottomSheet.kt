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
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.helpers.PhotoTrackOverlayComposer
import com.atrainingtracker.trainingtracker.helpers.saveAndShare
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import androidx.compose.material3.ExperimentalMaterial3Api
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet allowing athletes to select their preferred workout share mode:
 * - Option A: Classic Map & Analytics composite snapshot (Header + Map + Profile + Analytics + Footer)
 * - Option B: Photo with GPS track silhouette, key stats, and branding overlay (REQ-UI-447 / ATT-30)
 *
 * @param workoutData The workout data containing telemetry, stats, and GPS polyline.
 * @param onDismissRequest Invoked when the bottom sheet is dismissed.
 * @param onSelectMapSnapshot Invoked when the athlete chooses Option A (classic snapshot).
 * @param attachedPhotos Optional list of photos attached to the workout (for ATT-43 integration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutShareBottomSheet(
    workoutData: WorkoutData,
    onDismissRequest: () -> Unit,
    onSelectMapSnapshot: () -> Unit,
    attachedPhotos: List<Uri> = emptyList(),
    initialPhotoUri: Uri? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var composedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isComposing by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    LaunchedEffect(initialPhotoUri) {
        if (initialPhotoUri != null && composedBitmap == null) {
            isComposing = true
            val photo = withContext(Dispatchers.IO) {
                PhotoTrackOverlayComposer.decodeSampledBitmap(context, initialPhotoUri)
            }
            if (photo != null) {
                composedBitmap = withContext(Dispatchers.Default) {
                    PhotoTrackOverlayComposer.composeOverlay(context, photo, workoutData)
                }
            } else {
                Toast.makeText(context, R.string.share_error_photo_load, Toast.LENGTH_SHORT).show()
            }
            isComposing = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isComposing = true
                val photo = withContext(Dispatchers.IO) {
                    PhotoTrackOverlayComposer.decodeSampledBitmap(context, uri)
                }
                if (photo != null) {
                    val composed = withContext(Dispatchers.Default) {
                        PhotoTrackOverlayComposer.composeOverlay(context, photo, workoutData)
                    }
                    composedBitmap = composed
                } else {
                    Toast.makeText(context, R.string.share_error_photo_load, Toast.LENGTH_SHORT).show()
                }
                isComposing = false
            }
        }
    }

    AppModalBottomSheet(
        title = stringResource(if (composedBitmap != null) R.string.share_preview_title else R.string.share_workout_title),
        icon = Icons.Default.Share,
        onDismissRequest = onDismissRequest
    ) {
        if (isComposing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (composedBitmap != null) {
            val bitmap = composedBitmap!!
            val aspect = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)).coerceIn(0.5f, 2.0f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_preview_title),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Action: Share (Stacked full-width per Rule 73 / REQ-UI-447.5)
                Button(
                    onClick = {
                        val bitmapToShare = composedBitmap ?: return@Button
                        scope.launch {
                            isSharing = true
                            saveAndShare(context, bitmapToShare, "workout_photo_${workoutData.id}.png")
                            isSharing = false
                            onDismissRequest()
                        }
                    },
                    enabled = !isSharing && !isComposing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.share_cta_button))
                    }
                }

                // Secondary Action: Choose different photo (Stacked full-width per Rule 73 / REQ-UI-447.5)
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.share_choose_photo))
                }
            }
        } else {
            // Mode Selection: Option A (Classic Snapshot) vs Option B (Photo with Track Overlay)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Option A: Classic Map & Analytics
                ShareModeOptionCard(
                    title = stringResource(R.string.share_option_map_snapshot),
                    description = stringResource(R.string.share_option_map_snapshot_desc),
                    icon = Icons.Default.Map,
                    onClick = {
                        onDismissRequest()
                        onSelectMapSnapshot()
                    }
                )

                // Option B: Photo with Track & Stats Overlay
                ShareModeOptionCard(
                    title = stringResource(R.string.share_option_photo_track),
                    description = stringResource(R.string.share_option_photo_track_desc),
                    icon = Icons.Default.AddPhotoAlternate,
                    onClick = {
                        if (workoutData.mapPolyline.isEmpty()) {
                            Toast.makeText(context, R.string.share_error_no_gps, Toast.LENGTH_SHORT).show()
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareModeOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
