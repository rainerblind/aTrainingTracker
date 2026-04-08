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

package com.atrainingtracker.trainingtracker.segments

import android.content.Context
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SegmentSummary(
    val stravaId: Long,
    val name: String,
    val bSportType: BSportType,
    val climbCategory: String,
    val prTime: String,
    val city: String,
    val distance: String,
    val averageGrade: String,
    val maxGrade: String,
    val elevationGain: String,
    val elevationMin: String,
    val elevationMax: String,
)

class SegmentsRepository private constructor(context: Context) {

    private val dbManager = SegmentsDatabaseManager.getInstance(context)

    // Repository scope for background loading
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache of segments
    private val _allSegments = MutableStateFlow<List<MapSegment>?>(null)

    init {
        // Load segments from DB into memory immediately upon creation
        repositoryScope.launch {
            val segments = dbManager.allSegments ?: emptyList()
            _allSegments.value = segments
        }
    }

    /**
     * Fetches all segments. If they haven't loaded yet, it waits for the background task.
     */
    suspend fun getAllSegments(): List<MapSegment> = withContext(Dispatchers.IO) {
        // Wait until the flow has a non-null value (meaning DB load finished)
        _allSegments.first { it != null } ?: emptyList()
    }

    /**
     * Fetches a specific segment by its ID from the in-memory cache.
     */
    suspend fun getSegmentById(segmentId: Long): MapSegment? = withContext(Dispatchers.IO) {
        // Ensure data is loaded before filtering
        val segments = _allSegments.first { it != null }
        segments?.find { it.id == segmentId }
    }

    /**
     * Fetches the summary details for a specific segment.
     */
    suspend fun getSegmentSummary(segmentId: Long): SegmentSummary? = withContext(Dispatchers.IO) {
        // This assumes your dbManager has a corresponding method to return this data class
        dbManager.getSegmentSummary(segmentId)
    }

    /**
     * Optional: Trigger a refresh if the user adds/edits segments
     */
    fun refreshSegments() {
        repositoryScope.launch {
            _allSegments.value = dbManager.allSegments ?: emptyList()
        }
    }

    companion object {
        @Volatile
        private var instance: SegmentsRepository? = null

        fun getInstance(context: Context): SegmentsRepository {
            return instance ?: synchronized(this) {
                instance ?: SegmentsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}