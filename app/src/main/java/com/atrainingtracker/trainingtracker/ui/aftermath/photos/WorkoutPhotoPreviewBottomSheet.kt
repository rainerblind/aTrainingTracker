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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoEntry
import com.atrainingtracker.trainingtracker.helpers.PhotoTrackOverlayComposer
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Preview bottom sheet displaying a full photo preview, capture timestamp, caption,
 * deletion action, and direct handoff to [WorkoutShareBottomSheet] (REQ-UI-448.2 / ATT-43).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPhotoPreviewBottomSheet(
    photo: WorkoutPhotoEntry,
    onDismissRequest: () -> Unit,
    onDeletePhoto: (WorkoutPhotoEntry) -> Unit,
    onShareOverlay: (WorkoutPhotoEntry) -> Unit
) {
    val context = LocalContext.current
    var fullBitmap by remember(photo.filePath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photo.filePath) { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(photo.filePath) {
        fullBitmap = withContext(Dispatchers.IO) {
            PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = 1200)
        }
        isLoading = false
    }

    AppModalBottomSheet(
        title = photo.caption?.takeIf { it.isNotBlank() } ?: stringResource(R.string.workout_photos_title),
        icon = Icons.Default.PhotoCamera,
        onDismissRequest = onDismissRequest,
        actions = {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.workout_photos_delete))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    onDismissRequest()
                    onShareOverlay(photo)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.workout_photos_share_overlay))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Photo Image Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (fullBitmap != null) {
                    val bmp = fullBitmap!!
                    val aspect = (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)).coerceIn(0.5f, 2.0f)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = photo.caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = stringResource(R.string.share_error_photo_load),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Timestamp & Metadata
            if (photo.takenAtMs != null && photo.takenAtMs > 0) {
                val formattedDate = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(photo.takenAtMs))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Caption if present
            if (!photo.caption.isNullOrBlank()) {
                Text(
                    text = photo.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.workout_photos_delete)) },
            text = { Text(stringResource(R.string.workout_photos_delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDismissRequest()
                        onDeletePhoto(photo)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
