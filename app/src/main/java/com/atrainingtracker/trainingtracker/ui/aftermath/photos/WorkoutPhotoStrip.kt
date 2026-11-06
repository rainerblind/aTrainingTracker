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

package com.atrainingtracker.trainingtracker.ui.aftermath.photos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoEntry
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import com.atrainingtracker.trainingtracker.helpers.PhotoTrackOverlayComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Horizontal strip displaying workout photo thumbnails and an "Add Photos" trigger (REQ-UI-448.1 / REQ-UI-455 / ATT-43 / ATT-5475).
 */
@Composable
fun WorkoutPhotoStrip(
    photos: List<WorkoutPhotoEntry>,
    onPhotoClick: (WorkoutPhotoEntry) -> Unit,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    modifier: Modifier = Modifier,
    showAddButton: Boolean = true,
    thumbnailSize: PhotoThumbnailSize = PhotoThumbnailSize.MEDIUM,
    startTimeMs: Long = 0L,
    endTimeMs: Long = 0L
) {
    if (photos.isEmpty() && !showAddButton) {
        return
    }

    val context = LocalContext.current
    var timeFilteredPhotos by remember { mutableStateOf<List<WorkoutPhotoManager.TimeFilteredPhoto>?>(null) }
    var showTimePickerSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddPhotos(uris)
        }
    }

    val launchSystemPicker: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val tryQueryAndShowTimePhotos: () -> Unit = {
        if (startTimeMs > 0L && endTimeMs >= startTimeMs) {
            val matching = WorkoutPhotoManager.queryWorkoutTimePhotos(
                context = context,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
            if (matching.isNotEmpty()) {
                timeFilteredPhotos = matching
                showTimePickerSheet = true
            } else {
                launchSystemPicker()
            }
        } else {
            launchSystemPicker()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            tryQueryAndShowTimePhotos()
        } else {
            // Permission denied -> seamless fallback to system picker (REQ-UI-455.3)
            launchSystemPicker()
        }
    }

    val onAddTileClick: () -> Unit = {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            tryQueryAndShowTimePhotos()
        } else {
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

    if (showTimePickerSheet && timeFilteredPhotos != null) {
        WorkoutTimePhotoPickerSheet(
            photos = timeFilteredPhotos!!,
            onAddSelectedPhotos = { uris ->
                showTimePickerSheet = false
                if (uris.isNotEmpty()) {
                    onAddPhotos(uris)
                }
            },
            onShowAllPhotos = {
                showTimePickerSheet = false
                launchSystemPicker()
            },
            onDismissRequest = {
                showTimePickerSheet = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.workout_photos_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (photos.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${photos.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Add Photos" Tile
            if (showAddButton) {
                item(key = "add_photo_tile") {
                    OutlinedCard(
                        onClick = onAddTileClick,
                        modifier = Modifier.size(thumbnailSize.dpSize.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.workout_photos_add),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (thumbnailSize == PhotoThumbnailSize.SMALL) 20.dp else 26.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.workout_photos_add),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Photo Thumbnail Items
            items(photos, key = { it.id }) { photo ->
                PhotoThumbnailCard(
                    photo = photo,
                    onClick = { onPhotoClick(photo) },
                    thumbnailSize = thumbnailSize
                )
            }
        }
    }
}

@Composable
private fun PhotoThumbnailCard(
    photo: WorkoutPhotoEntry,
    onClick: () -> Unit,
    thumbnailSize: PhotoThumbnailSize = PhotoThumbnailSize.MEDIUM
) {
    val context = LocalContext.current
    var bitmap by remember(photo.filePath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.filePath) { mutableStateOf(true) }

    LaunchedEffect(photo.filePath) {
        bitmap = withContext(Dispatchers.IO) {
            PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = 200)
        }
        isLoading = false
    }

    Card(
        modifier = Modifier
            .size(thumbnailSize.dpSize.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = photo.caption ?: stringResource(R.string.workout_photos_marker_title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
