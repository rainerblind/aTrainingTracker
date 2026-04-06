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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow

class SimpleSegmentOnMapFragment : Fragment() {

    companion object {
        const val TAG = "SimpleSegmentOnMapFragment"

        @JvmStatic
        fun newInstance(segmentId: Long): SimpleSegmentOnMapFragment {
            return SimpleSegmentOnMapFragment().apply {
                arguments = Bundle().apply {
                    putLong(SegmentsDatabaseManager.Segments.STRAVA_SEGMENT_ID, segmentId)
                }
            }
        }
    }


    // Initialize the ViewModel
    private val viewModel: SimpleSegmentMapViewModel by viewModels()
    private var segmentId: Long = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extract segment ID from arguments
        segmentId = arguments?.getLong(SegmentsDatabaseManager.Segments.STRAVA_SEGMENT_ID) ?: -1

        Log.i(TAG, "onCreate: segmentId=$segmentId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // 3. Observe the state from ViewModel
                    val mapState by viewModel.mapState.collectAsState()

                    // Static view: we don't need live GPS updates here
                    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

                    ATrainingTrackerMap(
                        mapState = mapState,
                        mapViewModel = viewModel,
                        currentLocationFlow = noLocation,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 4. Trigger data load
        if (segmentId != -1L) {
            viewModel.loadSegment(segmentId)
        }
    }
}