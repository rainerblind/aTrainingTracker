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
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.GpxImportActivity
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.atrainingtracker.trainingtracker.ui.map.toMapSegment
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

        // 1. Register the picker launcher
        val gpxPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val intent = android.content.Intent(requireContext(), GpxImportActivity::class.java).apply {
                    data = it
                }
                startActivity(intent)
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    RoutesScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure data is fresh when returning to this screen
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ATT-736: Clear active filters when navigating away from routes view (unless rotating screen)
        if (activity?.isChangingConfigurations != true) {
            viewModel.clearFilterCriteria()
        }
    }

    companion object {
        const val TAG = "RoutesFragment"

        @JvmStatic
        fun newInstance(): RoutesFragment {
            return RoutesFragment()
        }
    }
}