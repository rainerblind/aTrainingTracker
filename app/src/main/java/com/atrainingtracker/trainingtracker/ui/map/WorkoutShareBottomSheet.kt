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

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager.TimeFilteredPhoto
import com.atrainingtracker.trainingtracker.helpers.PhotoTrackOverlayComposer
import com.atrainingtracker.trainingtracker.helpers.saveAndShare
import com.atrainingtracker.trainingtracker.ui.aftermath.RadarTrafficSummaryFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3-tier cascade tiers for workout photo selection (REQ-UI-447.6 / ATT-5656):
 * 1. [ATTACHED]: Photos already attached to the workout.
 * 2. [TIME_WINDOW]: Photos taken during the workout time window.
 */
enum class PhotoCascadeTier {
    ATTACHED,
    TIME_WINDOW
}

/**
 * Bottom sheet allowing athletes to select their preferred workout share mode:
 * - Option A: Classic Map & Analytics composite snapshot (Header + Map + Profile + Analytics + Footer)
 * - Option B: Photo with GPS track silhouette, key stats, and branding overlay with 3-tier cascade
 *   (1. Attached photos -> 2. Time-window photos -> 3. All photos) (REQ-UI-447 / ATT-30, ATT-5656)
 *
 * @param workoutData The workout data containing telemetry, stats, and GPS polyline.
 * @param onDismissRequest Invoked when the bottom sheet is dismissed.
 * @param onSelectMapSnapshot Invoked when the athlete chooses Option A (classic snapshot).
 * @param attachedPhotos Optional list of photos attached to the workout (for ATT-43 integration).
 * @param initialPhotoUri Optional photo URI to immediately open in preview mode.
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

    var activePhotoTier by remember { mutableStateOf<PhotoCascadeTier?>(null) }
    var timeFilteredPhotos by remember { mutableStateOf<List<TimeFilteredPhoto>>(emptyList()) }

    val startMs = remember(workoutData.id) { workoutData.startTimeS * 1000L }
    val endMs = remember(workoutData.id) { (workoutData.startTimeS + workoutData.totalTimeSec) * 1000L }

    val composePhoto: (Uri) -> Unit = { uri ->
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
                activePhotoTier = null
            } else {
                Toast.makeText(context, R.string.share_error_photo_load, Toast.LENGTH_SHORT).show()
            }
            isComposing = false
        }
    }

    LaunchedEffect(initialPhotoUri) {
        if (initialPhotoUri != null && composedBitmap == null) {
            composePhoto(initialPhotoUri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            composePhoto(uri)
        }
    }

    val launchSystemPicker: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    var pendingSuccess by remember { mutableStateOf<((List<TimeFilteredPhoto>) -> Unit)?>(null) }
    var pendingFallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            scope.launch {
                val matching = withContext(Dispatchers.IO) {
                    if (startMs > 0L && endMs >= startMs) {
                        WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs)
                    } else emptyList()
                }
                pendingSuccess?.invoke(matching)
                pendingSuccess = null
                pendingFallback = null
            }
        } else {
            pendingFallback?.invoke()
            pendingSuccess = null
            pendingFallback = null
        }
    }

    fun checkPermissionAndLoadTimePhotos(
        onSuccess: (List<TimeFilteredPhoto>) -> Unit,
        onFallback: () -> Unit
    ) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            scope.launch {
                val matching = withContext(Dispatchers.IO) {
                    if (startMs > 0L && endMs >= startMs) {
                        WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs)
                    } else emptyList()
                }
                onSuccess(matching)
            }
        } else {
            pendingSuccess = onSuccess
            pendingFallback = onFallback
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val requestTimePhotosAndAdvance: () -> Unit = {
        checkPermissionAndLoadTimePhotos(
            onSuccess = { photos ->
                if (photos.isNotEmpty()) {
                    timeFilteredPhotos = photos
                    activePhotoTier = PhotoCascadeTier.TIME_WINDOW
                } else {
                    launchSystemPicker()
                }
            },
            onFallback = {
                launchSystemPicker()
            }
        )
    }

    val startCascade: () -> Unit = {
        if (attachedPhotos.isNotEmpty()) {
            activePhotoTier = PhotoCascadeTier.ATTACHED
        } else {
            requestTimePhotosAndAdvance()
        }
    }

    AppModalBottomSheet(
        title = stringResource(
            if (composedBitmap != null) R.string.share_preview_title
            else if (activePhotoTier == PhotoCascadeTier.ATTACHED) R.string.workout_photos_title
            else if (activePhotoTier == PhotoCascadeTier.TIME_WINDOW) R.string.workout_photos_time_picker_title
            else R.string.share_workout_title
        ),
        icon = Icons.Default.Share,
        scrollable = activePhotoTier == null, // Prevent nested scroll exception with LazyVerticalGrid (Sec 5.8)
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
                            val shareSummaryText = RadarTrafficSummaryFormatter.format(workoutData.radarTrafficSummary, context)
                            saveAndShare(context, bitmapToShare, "workout_photo_${workoutData.id}.png", shareSummaryText)
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
                        composedBitmap = null
                        startCascade()
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
        } else if (activePhotoTier == PhotoCascadeTier.ATTACHED) {
            // Tier 1: Attached Photos (REQ-UI-447.6 / ATT-5656)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.share_photos_attached_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (attachedPhotos.size <= 3) 120.dp else 240.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(attachedPhotos) { uri ->
                            UriThumbnailGridItem(
                                uri = uri,
                                onClick = { composePhoto(uri) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Rule 73 stacked buttons:
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { requestTimePhotosAndAdvance() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.share_photos_show_time_window))
                    }

                    OutlinedButton(
                        onClick = { launchSystemPicker() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.workout_photos_show_all))
                    }
                }
            }
        } else if (activePhotoTier == PhotoCascadeTier.TIME_WINDOW) {
            // Tier 2: Time-Window Photos (REQ-UI-447.6 / ATT-5656)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.workout_photos_time_picker_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (timeFilteredPhotos.size <= 3) 120.dp else 240.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(timeFilteredPhotos, key = { it.id }) { photo ->
                            TimeFilteredThumbnailGridItem(
                                photo = photo,
                                onClick = { composePhoto(photo.contentUri) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Rule 73 stacked buttons:
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (attachedPhotos.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { activePhotoTier = PhotoCascadeTier.ATTACHED },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.share_photos_show_attached))
                        }
                    }

                    OutlinedButton(
                        onClick = { launchSystemPicker() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.workout_photos_show_all))
                    }
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
                            startCascade()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun UriThumbnailGridItem(
    uri: Uri,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(uri) { mutableStateOf(true) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            PhotoTrackOverlayComposer.decodeSampledBitmap(context, uri, maxDim = 240)
        }
        isLoading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeFilteredThumbnailGridItem(
    photo: TimeFilteredPhoto,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(photo.contentUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.contentUri) { mutableStateOf(true) }

    val formattedTime = remember(photo.dateTakenMs) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(photo.dateTakenMs))
    }

    LaunchedEffect(photo.contentUri) {
        bitmap = withContext(Dispatchers.IO) {
            PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.contentUri, maxDim = 240)
        }
        isLoading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Time badge (bottom full-width scrim)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
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
