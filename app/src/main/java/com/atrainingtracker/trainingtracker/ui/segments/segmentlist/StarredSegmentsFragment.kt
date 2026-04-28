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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    // Note: We need to observe the set of refreshing sports
                    // Since it's a StateFlow, we collect it here.
                    // We might need to add 'val refreshingSports' to the ViewModel if not exposed

                    SegmentsTabsScreen(
                        liveSegments = segments,
                        // Accessing the state from the ViewModel
                        isRefreshing = { sport -> viewModel.isRefreshing(sport) },
                        onRefresh = { sport -> viewModel.onRefresh(sport) },
                        onSegmentClick = { id ->
                            viewModel.onSegmentClick(id)
                            // Add navigation logic here if needed
                        }
                    )
                }
            }
        }
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