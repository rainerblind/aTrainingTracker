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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoEntry
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import com.atrainingtracker.trainingtracker.helpers.PhotoTrackOverlayComposer
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (showAddButton) {
                IconButton(
                    onClick = onAddTileClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.workout_photos_add),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (photos.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val availableWidth = (maxWidth - 32.dp).coerceAtLeast(100.dp)
                val dimensions = calculatePhotoItemDimensions(thumbnailSize, availableWidth)

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo Thumbnail Items
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumbnailCard(
                            photo = photo,
                            onClick = { onPhotoClick(photo) },
                            dimensions = dimensions
                        )
                    }
                }
            }
        }
    }
}

/**
 * Viewport-relative item dimensions for photo thumbnails (REQ-UI-414.6 / ATT-5655 / ATT-5811).
 */
data class PhotoItemDimensions(
    val width: Dp,
    val height: Dp
)

/**
 * Calculates responsive width and height for photo cards based on [thumbnailSize] and [availableWidth] (REQ-UI-414.6 / TST-UI-377.1 / ATT-5655).
 *
 * - [PhotoThumbnailSize.LARGE]: 1 full-width photo per row (`width = availableWidth`, `height = (availableWidth * 0.65f).coerceIn(180.dp, 260.dp)`).
 * - [PhotoThumbnailSize.MEDIUM]: 2 photos side-by-side (`width = (availableWidth - 8.dp) / 2`, square aspect ratio).
 * - [PhotoThumbnailSize.SMALL]: 3–4 photos side-by-side (`width = ((availableWidth - 24.dp) / 3.5f).coerceIn(60.dp, 90.dp)`, square aspect ratio).
 */
fun calculatePhotoItemDimensions(
    thumbnailSize: PhotoThumbnailSize,
    availableWidth: Dp
): PhotoItemDimensions {
    val safeWidth = availableWidth.coerceAtLeast(100.dp)
    return when (thumbnailSize) {
        PhotoThumbnailSize.LARGE -> {
            val width = safeWidth
            val height = (safeWidth * 0.65f).coerceIn(180.dp, 260.dp)
            PhotoItemDimensions(width, height)
        }
        PhotoThumbnailSize.MEDIUM -> {
            val width = (safeWidth - 8.dp) / 2
            PhotoItemDimensions(width, width)
        }
        PhotoThumbnailSize.SMALL -> {
            val width = ((safeWidth - 24.dp) / 3.5f).coerceIn(60.dp, 90.dp)
            PhotoItemDimensions(width, width)
        }
    }
}

/**
 * Calculates the dynamic downsample target resolution (max dimension in physical pixels)
 * for decoding a photo thumbnail based on its [dimensions] and device screen [density]
 * (REQ-UI-457.1 / REQ-UI-457.4 / ATT-5760).
 *
 * Clamped to `[256 .. 1200]` pixels to guarantee crisp rendering across SMALL, MEDIUM,
 * and LARGE sizes while strictly bounding memory consumption (< 4MB per bitmap) to prevent OOM.
 */
fun calculatePhotoDecodeMaxDim(
    dimensions: PhotoItemDimensions,
    density: Density
): Int {
    val maxDp = maxOf(dimensions.width, dimensions.height).value
    val targetPx = (maxDp * density.density).roundToInt()
    return targetPx.coerceIn(256, 1200)
}

@Composable
private fun PhotoThumbnailCard(
    photo: WorkoutPhotoEntry,
    onClick: () -> Unit,
    dimensions: PhotoItemDimensions
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetMaxDim = remember(dimensions, density) {
        calculatePhotoDecodeMaxDim(dimensions, density)
    }

    var bitmap by remember(photo.filePath, targetMaxDim) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.filePath, targetMaxDim) { mutableStateOf(true) }

    LaunchedEffect(photo.filePath, targetMaxDim) {
        bitmap = withContext(Dispatchers.IO) {
            PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = targetMaxDim)
        }
        isLoading = false
    }

    Card(
        modifier = Modifier
            .size(width = dimensions.width, height = dimensions.height)
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

@Preview(name = "Empty Photos Strip with Add Button - Light", showBackground = true)
@Composable
private fun WorkoutPhotoStripEmptyPreview() {
    ATrainingTrackerTheme {
        WorkoutPhotoStrip(
            photos = emptyList(),
            onPhotoClick = {},
            onAddPhotos = {},
            showAddButton = true
        )
    }
}

@Preview(name = "Empty Photos Strip with Add Button - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutPhotoStripEmptyDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        WorkoutPhotoStrip(
            photos = emptyList(),
            onPhotoClick = {},
            onAddPhotos = {},
            showAddButton = true
        )
    }
}
