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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.photos.PhotoThumbnailSize
import com.atrainingtracker.trainingtracker.ui.settings.tuning.SubordinateTuningToggleItem

/**
 * Reusable 3-way [PhotoThumbnailSize] segmented button selector (REQ-UI-414.6 / ATT-5474).
 */
@Composable
fun PhotoThumbnailSizeSelector(
    label: String,
    selectedSize: PhotoThumbnailSize,
    onSizeSelected: (PhotoThumbnailSize) -> Unit
) {
    val photoSizes = listOf(
        PhotoThumbnailSize.SMALL,
        PhotoThumbnailSize.MEDIUM,
        PhotoThumbnailSize.LARGE
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        photoSizes.forEachIndexed { index, size ->
            val selected = selectedSize == size
            SegmentedButton(
                selected = selected,
                onClick = { onSizeSelected(size) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = photoSizes.size),
                icon = {},
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(size.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Subordinate photo settings controls (Show Add-Button toggle & Thumbnail size selector)
 * for Workout Summary Cards and Workout Details views (REQ-UI-414.6 / ATT-5474).
 */
@Composable
fun PhotoCardSubSettings(
    showAddButton: Boolean,
    onShowAddButtonChange: (Boolean) -> Unit,
    thumbnailSize: PhotoThumbnailSize,
    onThumbnailSizeChange: (PhotoThumbnailSize) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SubordinateTuningToggleItem(
            title = stringResource(R.string.settings_workout_photos_show_add_button),
            isChecked = showAddButton,
            onCheckedChange = onShowAddButtonChange
        )
        PhotoThumbnailSizeSelector(
            label = stringResource(R.string.settings_workout_photos_thumbnail_size),
            selectedSize = thumbnailSize,
            onSizeSelected = onThumbnailSizeChange
        )
    }
}

/**
 * Summary cards subordinate photo settings block (REQ-UI-414.6 / TST-UI-377.5).
 */
@Composable
fun SummaryPhotoSubSettings(
    prefs: WorkoutCardSectionPreferences,
    onPrefsChange: (WorkoutCardSectionPreferences) -> Unit
) {
    if (prefs.showPhotos) {
        PhotoCardSubSettings(
            showAddButton = prefs.showPhotoAddButton,
            onShowAddButtonChange = { onPrefsChange(prefs.copy(showPhotoAddButton = it)) },
            thumbnailSize = prefs.photoThumbnailSize,
            onThumbnailSizeChange = { onPrefsChange(prefs.copy(photoThumbnailSize = it)) }
        )
    }
}

/**
 * Detail view subordinate photo settings block (REQ-UI-414.6 / TST-UI-377.5 / ATT-5655).
 * Tab configuration is the sole authority for section inclusion; photo card sub-settings
 * (add button toggle, thumbnail size) are unconditionally available.
 */
@Composable
fun DetailPhotoSubSettings(
    prefs: WorkoutDetailPreferences,
    onPrefsChange: (WorkoutDetailPreferences) -> Unit
) {
    PhotoCardSubSettings(
        showAddButton = prefs.showPhotoAddButton,
        onShowAddButtonChange = { onPrefsChange(prefs.copy(showPhotoAddButton = it)) },
        thumbnailSize = prefs.photoThumbnailSize,
        onThumbnailSizeChange = { onPrefsChange(prefs.copy(photoThumbnailSize = it)) }
    )
}
