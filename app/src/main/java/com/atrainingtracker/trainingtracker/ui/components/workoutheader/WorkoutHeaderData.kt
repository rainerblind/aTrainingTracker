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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import com.atrainingtracker.banalservice.BSportType

/**
 * Presentation data model encapsulating header-level workout metadata (ATT-503).
 *
 * @property workoutName The display title of the workout session.
 * @property formattedDate Pre-formatted date string.
 * @property formattedTime Pre-formatted start time string.
 * @property startTimeS Epoch start timestamp in seconds.
 * @property bSportType Sport type classification for icon and styling.
 * @property sportName Localized name of the sport type.
 * @property equipmentName Linked equipment / gear name if assigned.
 * @property commute Flag indicating whether the activity was a commute.
 * @property trainer Flag indicating whether the session was on a stationary trainer.
 * @property uploadToStrava Strava upload sync status code.
 * @property stravaSportName Strava-specific sport name override.
 * @property clusterId Unique identifier of the linked [com.atrainingtracker.trainingtracker.database.WorkoutCluster].
 * @property clusterName Display name of the linked Workout Cluster.
 * @property finished Completion state of the workout recording.
 */
data class WorkoutHeaderData(
    val workoutName: String,    // TODO: Add Id?
    val formattedDate: String,
    val formattedTime: String,
    val startTimeS: Long,        // start time in seconds  // TODO: really necessary?
    val bSportType: BSportType,  // necessary to get the icon and the text for an indoor activity
    var sportName: String,
    var equipmentName: String?,
    var commute: Boolean,
    var trainer: Boolean,
    val uploadToStrava: Int,
    val stravaSportName: String? = null,
    val clusterId: Long = -1L,
    val clusterName: String? = null,
    val finished: Boolean
)