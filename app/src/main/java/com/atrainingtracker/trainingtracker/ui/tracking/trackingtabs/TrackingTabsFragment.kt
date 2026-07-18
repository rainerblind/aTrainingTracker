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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode

/**
 * Fragment that hosts the tabbed tracking interface.
 * Now migrated to Jetpack Compose using HorizontalPager for better performance and
 * seamless Map integration.
 */
class TrackingTabsFragment : Fragment() {

    private val viewModel: TrackingTabsViewModel by viewModels {
        TrackingTabsViewModelFactory(requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle arguments as per original implementation
        arguments?.let {
            val activityTypeName = it.getString(ARG_ACTIVITY_TYPE)
            if (activityTypeName != null) {
                viewModel.setExplicitActivityType(ActivityType.valueOf(activityTypeName))
                viewModel.setScreenMode(ScreenMode.CONFIGURATION)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    TrackingTabsScreen(
                        trackingTabsViewModel = viewModel,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Removed: viewModel.setScreenMode(ScreenMode.TRACKING)
        // This was causing race conditions when replacing the fragment with another 
        // TrackingTabsFragment instance in CONFIGURATION mode. (ATT-245)
    }

    companion object {
        val DEBUG = TrainingApplication.getDebug(true)
        const val TAG = "TrackingTabsFragment"
        const val ARG_ACTIVITY_TYPE = "arg_activity_type"

        @JvmStatic
        fun newInstance(activityType: ActivityType): TrackingTabsFragment {
            return TrackingTabsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ACTIVITY_TYPE, activityType.name)
                }
            }
        }

        @JvmStatic
        fun newInstance(): TrackingTabsFragment {
            return TrackingTabsFragment()
        }
    }
}