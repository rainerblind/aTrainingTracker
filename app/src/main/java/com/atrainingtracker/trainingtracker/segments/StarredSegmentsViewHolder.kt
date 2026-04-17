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

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.segments.SimpleSegmentMapViewModel
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class StarredSegmentViewHolder(
    val rowView: View,
    activity: Activity,
    private val onSegmentClick: (Long) -> Unit
) {
    var segmentId: Long = -1

    // Text Views
    val tvClimbCategory: TextView = rowView.findViewById(R.id.textViewCategoryChip)
    val tvName: TextView = rowView.findViewById(R.id.textViewSegmentName)
    val layoutPr: View = rowView.findViewById(R.id.layout_pr)
    val tvPrTime: TextView = rowView.findViewById(R.id.textViewPrTime)
    val tvCity: TextView = rowView.findViewById(R.id.textViewCity)
    val tvDistance: TextView = rowView.findViewById(R.id.textViewDistance)
    val tvAverageGrade: TextView = rowView.findViewById(R.id.textViewAvgGrade)
    val tvMaxGrade: TextView = rowView.findViewById(R.id.textViewMaxGrade)
    val tvElevationGain: TextView = rowView.findViewById(R.id.textViewElevationGain)
    val tvElevationMin: TextView = rowView.findViewById(R.id.textViewElevationMin)
    val tvElevationMax: TextView = rowView.findViewById(R.id.textViewElevationMax)

    val mapComposeView: ComposeView? = rowView.findViewById(R.id.map_compose_segment)
    val viewModel: SimpleSegmentMapViewModel = SimpleSegmentMapViewModel(activity.application)

    init {
        mapComposeView?.setContent {
            ATrainingTrackerTheme {
                val state by viewModel.mapState.collectAsState()

                ATrainingTrackerMap(
                    mapState = state,
                    currentLocationFlow = MutableStateFlow(null),
                    modifier = Modifier.fillMaxSize(),
                    onMapClick = {
                        if (segmentId != -1L) {
                            onSegmentClick(segmentId)
                        }
                    }
                )
            }
        }
    }

    fun bindSegment(id: Long) {
        this.segmentId = id
        // Trigger data load through the repository/cache
        viewModel.loadSegment(id, false)
    }
}