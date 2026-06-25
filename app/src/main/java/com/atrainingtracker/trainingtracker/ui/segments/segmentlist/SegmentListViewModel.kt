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

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.util.BaseMappableListViewModel
import com.atrainingtracker.trainingtracker.ui.util.MappableSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.text.lowercase

enum class SegmentSortOrder(@StringRes override val labelResId: Int) : MappableSortOrder {
    DISTANCE_TO_USER(R.string.sort_closest),
    CLIMB_CATEGORY(R.string.sort_climb_category),
    TOTAL_ELEVATION_GAIN(R.string.sort_elevation_gain),
    AVERAGE_GRADE(R.string.sort_average_grade),
    SEGMENT_DISTANCE(R.string.sort_length),
    NAME(R.string.sort_name)
}

class SegmentListViewModel(
    application: Application,
    private val segmentsRepository: SegmentsRepository,
    private val banalServiceRepository: BANALServiceRepository
) : BaseMappableListViewModel<SegmentWithPath, SegmentSortOrder>(application, SegmentSortOrder.DISTANCE_TO_USER, SegmentSortOrder.DISTANCE_TO_USER) {

    val connectedToStrava = segmentsRepository.connectedToStrava

    override val isLocationAvailable: StateFlow<Boolean> = banalServiceRepository.currentLocation
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Reactive sorted list
    val segmentsWithPath: StateFlow<List<SegmentWithPath>> = combine(
        segmentsRepository.allSegmentsWithPath,
        _sortOrder,
        banalServiceRepository.currentLocation // Directly observing the BANALService source
    ) { segments, order, location ->
        when (order) {
            SegmentSortOrder.NAME ->
                segments.sortedBy { it.summary.name.lowercase() }

            SegmentSortOrder.CLIMB_CATEGORY ->
                segments.sortedWith(
                    compareByDescending<SegmentWithPath> { it.summary.climbCategory_raw }
                        .thenByDescending { it.summary.elevationGain_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.TOTAL_ELEVATION_GAIN ->
                segments.sortedWith(
                    compareByDescending<SegmentWithPath> { it.summary.elevationGain_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.AVERAGE_GRADE ->
                segments.sortedWith(
                    compareByDescending<SegmentWithPath> { it.summary.averageGrade_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.SEGMENT_DISTANCE ->
                segments.sortedWith(
                    compareByDescending<SegmentWithPath> { it.summary.distance_raw }
                        .thenBy { it.summary.name.lowercase() }
                )

            SegmentSortOrder.DISTANCE_TO_USER -> {
                if (location == null) {
                    segments.sortedBy { it.summary.name.lowercase() }
                } else {
                    segments.sortedBy { segment ->
                        val startPoint = segment.path.firstOrNull()
                        if (startPoint != null) {
                            calculateDistance(
                                location.latitude, location.longitude,
                                startPoint.latLng.latitude, startPoint.latLng.longitude
                            )
                        } else {
                            Float.MAX_VALUE
                        }
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )


    val refreshingSports: StateFlow<Set<BSportType>> = segmentsRepository.refreshingSports

    fun onRefresh(sport: BSportType) {
        viewModelScope.launch {
            segmentsRepository.syncStarredSegments(sport)
        }
    }

    class SegmentListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

        private val segmentsRepository = SegmentsRepository.getInstance(context)
        private val banalServiceRepository = BANALServiceRepository.getInstance(context)

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SegmentListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SegmentListViewModel(context.applicationContext as Application, segmentsRepository, banalServiceRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
