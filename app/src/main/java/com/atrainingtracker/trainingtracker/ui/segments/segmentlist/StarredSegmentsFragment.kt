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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.fragments.preferences.StravaUploadFragment
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.segments.SegmentOnMapScreen
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class StarredSegmentsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {

                    // Initialize the existing SegmentListViewModel
                    val viewModel: SegmentListViewModel = viewModel(
                        factory = SegmentListViewModel.SegmentListViewModelFactory(requireContext())
                    )

                    val segments by viewModel.liveSegments.collectAsStateWithLifecycle()
                    val sortOrder by viewModel.sortOrder.collectAsState()
                    val refreshingSports by viewModel.refreshingSports.collectAsStateWithLifecycle()

                    val pagerState = rememberPagerState(pageCount = { 2 })
                    val bikeListState = rememberLazyListState()
                    val runListState = rememberLazyListState()

                    // 1. Manage local navigation state
                    var selectedSegmentId by rememberSaveable { mutableStateOf<Long?>(null) }

                    // 2. Logic to switch between List and Detail
                    if (selectedSegmentId == null) {
                        // SHOW LIST
                        SegmentsTabsScreen(
                            liveSegments = segments,
                            pagerState = pagerState,
                            bikeListState = bikeListState,
                            runListState = runListState,
                            isStravaConnected = viewModel.connectedToStrava,
                            onConnectToStrava = {
                                startStravaUploadFragment()
                            },
                            isRefreshing = { sport -> refreshingSports.contains(sport) },
                            onRefresh = { sport -> viewModel.onRefresh(sport) },
                            onSegmentClick = { id ->
                                selectedSegmentId = id
                            },
                            sortOrder = sortOrder,
                            scrollToTop = viewModel.shouldScrollToTop(sortOrder),
                            onSortOrderChange = { viewModel.setSortOrder(it) }
                        )
                    } else {
                        // SHOW DETAIL
                        // Deriving the specific segment from the list we already have
                        val selectedSegment = segments.find { it.summary.stravaId == selectedSegmentId }

                        if (selectedSegment != null) {

                            // Create MapState on the fly
                            val mapState = remember(selectedSegment) {
                                MapState(
                                    segments = listOf(
                                            MapSegment(
                                                id = selectedSegment.summary.stravaId,
                                                name = selectedSegment.summary.name,
                                                bSportType = selectedSegment.summary.bSportType,
                                                path = selectedSegment.path,
                                                showStartAndFinishText = false
                                        )
                                    ),
                                    bSportType = selectedSegment.summary.bSportType,
                                    isFollowMeEnabled = false
                                )
                            }

                            SegmentOnMapScreen(
                                segmentSummary = selectedSegment.summary,
                                mapState = mapState,
                                modifier = Modifier.statusBarsPadding()
                            )

                            // Handle Back Press to return to list
                            BackHandler {
                                selectedSegmentId = null
                            }
                        }
                    }
                }
            }
        }
    }

    fun startStravaUploadFragment() {
        Log.i(TAG, "startStravaUploadFragment()")
        // 1. Create the Strava fragment
        val fragment = StravaUploadFragment()

        // 2. Prepare arguments to tell the fragment it's being opened
        // as the Strava preference screen (matches MainActivity logic)
        val args = Bundle().apply {
            putString(
                androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT,
                TrainingApplication.PREFERENCE_SCREEN_STRAVA
            )
        }
        fragment.arguments = args

        // 3. Perform the transaction using the container ID from MainActivity
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment, TrainingApplication.PREFERENCE_SCREEN_STRAVA)
            .addToBackStack(null) // Allows user to press 'Back' to return to segments
            .commit()
    }

    companion object {
        // This provides the static TAG used in MainActivity's switch statement
        const val TAG = "StarredSegmentsFragment"

        // Standard pattern for creating new instances
        @JvmStatic
        fun newInstance(): StarredSegmentsFragment {
            return StarredSegmentsFragment()
        }
    }
}