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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.util.MappableSortOrder

/**
 * Defines the sorting dimensions available for Favorite Tracks / Workout Clusters (ATT-761).
 *
 * Adheres to [MappableSortOrder] for UI consistency across Routes, Segments, and Clusters.
 */
enum class ClusterSortOrder(@StringRes override val labelResId: Int) : MappableSortOrder {
    RECORDINGS(R.string.filter_section_recordings),
    DISTANCE_TO_USER(R.string.sort_closest),
    DISTANCE(R.string.sort_length),
    NAME(R.string.sort_name)
}
