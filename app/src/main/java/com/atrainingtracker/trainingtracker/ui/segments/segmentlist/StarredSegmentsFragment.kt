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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogFragment
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.toMapSegment
import com.atrainingtracker.trainingtracker.ui.segments.SegmentOnMapScreen
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class StarredSegmentsFragment : Fragment() {

    private lateinit var viewModel: SegmentListViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(
            this,
            SegmentListViewModel.SegmentListViewModelFactory(requireContext())
        ).get(SegmentListViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    StarredSegmentsScreen(
                        viewModel = viewModel,
                        onConnectToStrava = { startStravaSettingsDialog() }
                    )
                }
            }
        }
    }

    fun startStravaSettingsDialog() {
        Log.i(TAG, "startStravaSettingsDialog()")
        StravaSettingsDialogFragment.newInstance().show(parentFragmentManager, StravaSettingsDialogFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ATT-735: Clear active filters when navigating away from segments view (unless rotating screen)
        if (activity?.isChangingConfigurations != true) {
            viewModel.clearFilterCriteria()
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