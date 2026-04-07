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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

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
    private val mapViewModel: MapViewModel by viewModels()


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
                        mapViewModel = mapViewModel,
                        currentLocationFlow = viewModel.currentLocation,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}