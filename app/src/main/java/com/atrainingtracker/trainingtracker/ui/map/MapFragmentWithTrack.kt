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

package com.atrainingtracker.trainingtracker.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.trainingtracker.ui.segments.SegmentSummaryHeader
import com.atrainingtracker.trainingtracker.ui.segments.SimpleSegmentOnMapScreen
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import com.atrainingtracker.banalservice.BSportType

/**
 * Fragment that displays the map with current track and segments.
 * This version specifically disables auto-rotation and auto-follow
 * to allow the user to browse the map freely.
 */
class MapFragmentWithTrack : Fragment() {

    companion object {
        const val TAG = "MapFragmentWithTrack"

        // Following your project's pattern of providing a newInstance method
        @JvmStatic
        fun newInstance(): MapFragmentWithTrack {
            return MapFragmentWithTrack()
        }
    }

    private val viewModel: MapFragmentWithTrackViewModel by viewModels()

    /*
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // Observe the mapState which has bearing/speed/follow disabled
                    val mapState by viewModel.mapState.collectAsStateWithLifecycle()

                    ATrainingTrackerMap(
                        mapState = mapState,
                        currentLocationFlow = viewModel.currentLocation,
                        modifier = Modifier.fillMaxSize(),
                        onSegmentClick = { }  //TODO: show segment details as BottomSheetScaffold
                    )
                }
            }
        }
    }
     */

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {

                    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
                    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
                    val liveSegments by viewModel.liveSegments.collectAsStateWithLifecycle()

                    // The ID of the segment currently being "peeked"
                    var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }

                    val scaffoldState = rememberBottomSheetScaffoldState(
                        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
                    )

                    // Effect: When a user clicks a new segment, ensure the sheet is at least "Partially Expanded" (Peeked)
                    LaunchedEffect(selectedSegmentId) {
                        if (selectedSegmentId != null) {
                            scaffoldState.bottomSheetState.partialExpand()
                        } else {
                            scaffoldState.bottomSheetState.hide()
                        }
                    }

                    BottomSheetScaffold(
                        scaffoldState = scaffoldState,
                        sheetPeekHeight = if (selectedSegmentId != null) 210.dp else 0.dp,
                        sheetDragHandle = {
                            // Subtle small drag handle
                            Surface(
                                modifier = Modifier.statusBarsPadding(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            ) {
                                Box(
                                    Modifier.size(width = 32.dp, height = 3.dp)
                                )
                            }
                        },
                        sheetContent = {
                            // --- THE SHEET CONTENT: The Entire SimpleSegmentOnMapScreen ---
                            val selectedSegment = mapState.segments.find { it.id == selectedSegmentId }

                            // Transform the single selected LiveSegment into a MapState for the Detail Screen
                            val detailMapState = remember(selectedSegment) {
                                MapState(
                                    segments = if (selectedSegment != null) listOf(selectedSegment) else emptyList(),
                                    bSportType = selectedSegment?.bSportType ?: BSportType.UNKNOWN,
                                    isFollowMeEnabled = false
                                )
                            }

                            SimpleSegmentOnMapScreen(
                                segmentSummary = liveSegments.find { it.summary.stravaId == selectedSegmentId }?.summary,
                                mapState = detailMapState,
                                modifier = Modifier
                            )
                        }
                    ) { innerPadding ->
                        // --- THE MAIN BODY: The Track Map ---
                        ATrainingTrackerMap(
                            mapState = mapState,
                            currentLocationFlow = MutableStateFlow(currentLocation),
                            modifier = Modifier.fillMaxSize(),
                            onSegmentClick = { id ->
                                selectedSegmentId = id
                            }
                        )
                    }

                    // Handle system back button to close the peek
                    BackHandler(enabled = selectedSegmentId != null) {
                        selectedSegmentId = null
                    }
                }
            }
        }
    }

}