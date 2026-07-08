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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.collectLatest

class FrequentPathsFragment : Fragment() {

    companion object {
        const val TAG = "FrequentPathsFragment"
        fun newInstance() = FrequentPathsFragment()
    }

    private val viewModel: FrequentPathsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    val selectedCluster by viewModel.selectedCluster.collectAsState()
                    var isTuning by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        viewModel.recalculationFinished.collectLatest {
                            isTuning = false
                        }
                    }

                    when {
                        isTuning -> {
                            BackHandler { isTuning = false }
                            ClusterTuningScreen(
                                viewModel = viewModel,
                                onBack = { isTuning = false }
                            )
                        }
                        selectedCluster != null -> {
                            BackHandler { viewModel.selectCluster(null) }
                            FrequentPathHeatmapScreen(
                                cluster = selectedCluster!!,
                                viewModel = viewModel,
                                onBack = { viewModel.selectCluster(null) }
                            )
                        }
                        else -> {
                            FrequentPathsListScreen(
                                viewModel = viewModel,
                                onClusterClick = { viewModel.selectCluster(it) },
                                onTuneClick = { isTuning = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
