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

package com.atrainingtracker.trainingtracker.ui.segments

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.map.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SimpleSegmentMapViewModel(application: Application) : MapViewModel(application) {

    // Repository instance
    private val segmentsRepository = SegmentsRepository.getInstance(application)

    private val _mapState = MutableStateFlow(MapState(isFollowMeEnabled = false))
    val mapState = _mapState.asStateFlow()

    /**
     * Loads a single segment into the map state using the Repository.
     */
    fun loadSegment(segmentId: Long, showStartAndFinishText: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            // Get the segment from our new repository (uses in-memory cache if available)
            val segment = segmentsRepository.getSegmentById(segmentId)

            withContext(Dispatchers.Main) {
                _mapState.value = _mapState.value.copy(
                    // We copy the segment to apply the visibility flag for markers/text
                    segments = segment?.let {
                        listOf(it.copy(showStartAndFinishText = showStartAndFinishText))
                    } ?: emptyList(),
                    isFollowMeEnabled = false
                )
            }
        }
    }
}