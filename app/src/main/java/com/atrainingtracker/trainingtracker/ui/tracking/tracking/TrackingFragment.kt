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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.MapViewModel
import com.atrainingtracker.trainingtracker.ui.map.TrackingMapViewModelFactory
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelFactory

class TrackingFragment : Fragment() {

    private lateinit var viewModel: TrackingViewModel
    private val mapViewModel: MapViewModel by activityViewModels {
        TrackingMapViewModelFactory(requireActivity().application)
    }

    private var tabViewId: Long = -1L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.i(TAG, "onCreate")

        // Get the viewId from the fragment's arguments
        arguments?.let {
            tabViewId = it.getLong(ARG_TAB_VIEW_ID) ?: 0
        }

        // Create the ViewModel using our custom factory
        val factory = TrackingViewModelFactory(requireActivity().application, tabViewId)
        viewModel = ViewModelProvider(this, factory)[TrackingViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (DEBUG) Log.i(TAG, "onCreateView")

        // Create a ComposeView and set its content
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    val activityType by viewModel.activityType.collectAsState()

                    val screenMode by viewModel.screenMode.collectAsState()

                    val editingFieldId by viewModel.editingFieldId.collectAsState()
                    val pendingAddition by viewModel.pendingAddition.collectAsState()

                    // If editingFieldId is not null, the Dialog is added to the UI composition.
                    editingFieldId?.let { fieldId ->
                        EditSensorFieldDialog(
                            title = context.getString(R.string.edit_field),
                            viewModel = viewModel(
                                factory = EditSensorFieldViewModelFactory(
                                    application = requireActivity().application,
                                    sensorFieldId = fieldId,
                                    activityType = activityType,
                                    trackingViewsRepository = viewModel.trackingViewsRepository,
                                    banalServiceRepository = viewModel.banalServiceRepository,
                                    tabViewId = tabViewId,
                                    rowNr = -1,
                                    colNr = -1
                                ),
                                // Crucial: Use the fieldId as a key so a new ViewModel
                                // is created if you switch from editing field A to field B
                                key = fieldId.toString()
                            ),
                            onDismissRequest = {
                                // Tell the TrackingViewModel to reset the state to null
                                viewModel.onDismissEditDialog()
                            }
                        )
                    }

                    // Show the EditSensorFieldDialog to add a new sensor field
                    pendingAddition?.let { params ->
                        Log.i(TAG, "pendingAddition: $params ($tabViewId)")
                        EditSensorFieldDialog(
                            title = context.getString(R.string.add_sensor),
                            viewModel = viewModel(
                                factory = EditSensorFieldViewModelFactory(
                                    application = requireActivity().application,
                                    trackingViewsRepository = viewModel.trackingViewsRepository,
                                    banalServiceRepository = viewModel.banalServiceRepository,
                                    activityType = activityType,
                                    sensorFieldId = -1L, // Signal NEW mode
                                    tabViewId = tabViewId,
                                    rowNr = params.row,
                                    colNr = params.col,
                                ),
                                key = "$tabViewId, ${params.row}, ${params.col}"
                            ),
                            onDismissRequest = { viewModel.onDismissAddition() }
                        )
                    }

                    // Create the GridActions object
                    val gridActions = object : GridActions {
                        override fun onEditField(fieldState: SensorFieldState) {
                            // simply forward this to the viewModel to show edit dialog
                            viewModel.onEditField(fieldState)
                        }
                        override fun onDeleteField(fieldState: SensorFieldState) {
                            viewModel.onDeleteSensorField(fieldState.sensorFieldId)
                        }
                        override fun onAddRow(beforeRow: Int) {
                            Log.i(TAG, "onAddRow($beforeRow)")
                            viewModel.onAddRow(beforeRow)
                        }
                        override fun onAddCol(atRow: Int, beforeCol: Int) {
                            Log.i(TAG, "onAddCol($atRow, $beforeCol)")
                            viewModel.onAddCol(atRow, beforeCol)
                        }
                    }

                    // 6. Pass the current screenMode to the SensorGridScreen
                    SensorGridScreen(
                        state = uiState,
                        mapViewModel = mapViewModel,
                        screenMode = screenMode,
                        gridActions = gridActions, // Pass the actions object
                        currentLocationFlow = viewModel.banalServiceRepository.currentLocation,
                        liveSegments = viewModel.activeLiveSegments
                    )
                }
            }
        }
    }

    companion object {
        private const val DEBUG = true
        private const val TAG = "TrackingFragment"
        private const val ARG_TAB_VIEW_ID = "tab_view_id"
        /**
         * A factory method to create a new instance of this fragment
         * with the required viewId.
         */
        @JvmStatic
        fun newInstance(tabViewId: Long) =
            TrackingFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TAB_VIEW_ID, tabViewId)
                }
            }
    }
}