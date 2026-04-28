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
import androidx.activity.result.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SegmentListViewModel(
    private val repository: SegmentsRepository
) : ViewModel() {

    // 1. Directly expose the repository's StateFlow
    // We use stateIn to make it lifecycle-aware for Compose
    val liveSegments: StateFlow<List<LiveSegment>> = repository.liveSegments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Set containing the sport types currently being refreshed
    private val _refreshingSports = MutableStateFlow<Set<BSportType>>(emptySet())

    // Helper function for the UI to check state
    fun isRefreshing(sport: BSportType): Boolean = _refreshingSports.value.contains(sport)

    fun onRefresh(sport: BSportType) {
        viewModelScope.launch {
            _refreshingSports.update { it + sport } // Add sport to refreshing set

            try {
                // TODO: update Segments
            } finally {
                _refreshingSports.update { it - sport } // Remove sport when done
            }
        }
    }


    fun onSegmentClick(stravaId: Long) {
        // TODO: Handle navigation to details
    }

    class SegmentListViewModelFactory(context: Context) : ViewModelProvider.Factory {
        private val repository = SegmentsRepository.getInstance(context)
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SegmentListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SegmentListViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}