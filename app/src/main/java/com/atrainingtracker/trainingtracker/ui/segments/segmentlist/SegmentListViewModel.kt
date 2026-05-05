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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.text.lowercase

enum class SegmentSortOrder(@StringRes val labelResId: Int) {
    DISTANCE_TO_USER(R.string.sort_closest),
    CLIMB_CATEGORY(R.string.sort_climb_category),
    TOTAL_ELEVATION_GAIN(R.string.sort_elevation_gain),
    AVERAGE_GRADE(R.string.sort_average_grade),
    SEGMENT_DISTANCE(R.string.sort_segment_length),
    NAME(R.string.sort_name)
}

class SegmentListViewModel(
    private val segmentsRepository: SegmentsRepository,
    private val banalServiceRepository: BANALServiceRepository
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SegmentSortOrder.DISTANCE_TO_USER)
    val sortOrder = _sortOrder.asStateFlow()

    private var lastScrolledOrder: SegmentSortOrder? = null

    fun shouldScrollToTop(currentOrder: SegmentSortOrder): Boolean {
        Log.i("SegmentListViewModel", "shouldScroll(currentOrder=$currentOrder), lastScrolledOrder=$lastScrolledOrder")
        if (lastScrolledOrder != currentOrder) {
            lastScrolledOrder = currentOrder
            return true
        }
        return false
    }

    // Reactive sorted list
    val liveSegments: StateFlow<List<LiveSegment>> = combine(
        segmentsRepository.liveSegments,
        _sortOrder,
        banalServiceRepository.currentLocation // Directly observing the BANALService source
    ) { segments, order, location ->
        when (order) {
            SegmentSortOrder.NAME ->
                segments.sortedBy { it.summary.name.lowercase() }

            SegmentSortOrder.CLIMB_CATEGORY ->
                segments.sortedWith(
                    compareByDescending<LiveSegment> { it.summary.climbCategory_raw }
                        .thenByDescending { it.summary.elevationGain_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.TOTAL_ELEVATION_GAIN ->
                segments.sortedWith(
                    compareByDescending<LiveSegment> { it.summary.elevationGain_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.AVERAGE_GRADE ->
                segments.sortedWith(
                    compareByDescending<LiveSegment> { it.summary.averageGrade_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.SEGMENT_DISTANCE ->
                segments.sortedWith(
                    compareByDescending<LiveSegment> { it.summary.distance_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.DISTANCE_TO_USER -> {
                if (location == null) {
                    segments.sortedBy { it.summary.name.lowercase() }
                } else {
                    segments.sortedBy { segment ->
                        calculateDistance(
                            location.latitude, location.longitude,
                            segment.path[0].latLng.latitude, segment.path[0].latLng.longitude
                        )
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSortOrder(order: SegmentSortOrder) {
        _sortOrder.value = order
    }

    private fun calculateDistance(uLat: Double, uLon: Double, sLat: Double, sLon: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(uLat, uLon, sLat, sLon, results)
        return results[0]
    }

    val refreshingSports: StateFlow<Set<BSportType>> = segmentsRepository.refreshingSports

    fun onRefresh(sport: BSportType) {
        viewModelScope.launch {
            segmentsRepository.syncStarredSegments(sport)
        }
    }

    class SegmentListViewModelFactory(context: Context) : ViewModelProvider.Factory {

        private val segmentsRepository = SegmentsRepository.getInstance(context)
        private val banalServiceRepository = BANALServiceRepository.getInstance(context)

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SegmentListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SegmentListViewModel(segmentsRepository, banalServiceRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}