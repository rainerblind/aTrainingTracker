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

package com.atrainingtracker.trainingtracker.database

import android.net.Uri
import java.io.File

/**
 * Entity representing an athlete photo attached to a workout (REQ-DAT-038 / ATT-43).
 *
 * Persisted in `WorkoutSummaries.db` table `workout_photos`.
 */
data class WorkoutPhotoEntry @JvmOverloads constructor(
    val id: Long = 0L,
    val workoutId: Long,
    val filePath: String,
    val takenAtMs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val caption: String? = null
) {
    val file: File get() = File(filePath)
    val uri: Uri get() = Uri.fromFile(file)
}
