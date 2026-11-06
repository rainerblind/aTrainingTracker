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

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Configurable thumbnail display sizes for workout photo strips (REQ-UI-414.6 / ATT-5474).
 *
 * - [SMALL]: 56dp compact thumbnail
 * - [MEDIUM]: 76dp standard thumbnail (default)
 * - [LARGE]: 104dp high-visibility thumbnail
 */
enum class PhotoThumbnailSize(
    val dpSize: Int,
    @StringRes val labelRes: Int
) {
    SMALL(56, R.string.settings_workout_photos_size_small),
    MEDIUM(76, R.string.settings_workout_photos_size_medium),
    LARGE(104, R.string.settings_workout_photos_size_large);

    companion object {
        val DEFAULT = MEDIUM
    }
}
