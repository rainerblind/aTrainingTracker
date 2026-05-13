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

package com.atrainingtracker.trainingtracker.ui.routes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Fragment that hosts the tabbed Route list.
 * Integrates with the existing Navigation Drawer via MainActivityWithNavigation.
 */
class RoutesFragment : Fragment() {

    private lateinit var viewModel: RoutesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize the ViewModel
        viewModel = ViewModelProvider(this).get(RoutesViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // This calls the Tabbed screen we discussed,
                    // which uses BSportType.BIKE, RUN, and UNKNOWN tabs.
                    RouteTabbedScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure data is fresh when returning to this screen
        viewModel.refresh()
    }

    companion object {
        const val TAG = "RoutesFragment"

        @JvmStatic
        fun newInstance(): RoutesFragment {
            return RoutesFragment()
        }
    }
}