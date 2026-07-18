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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelFactory

@Composable
fun TrackingTabGridContent(
    tabViewId: Long,
    screenMode: ScreenMode,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: throw IllegalStateException("Context must be a ComponentActivity")

    // We use a unique key for each tab so that each tab gets its own ViewModel instance
    // but the ViewModel survives orientation changes.
    val viewModel: TrackingViewModel = viewModel(
        factory = TrackingViewModelFactory(activity.application, tabViewId),
        key = "tab_$tabViewId"
    )

    // Sync the local ViewModel screen mode with the parent's mode (ATT-245)
    LaunchedEffect(screenMode) {
        viewModel.updateScreenMode(screenMode)
    }

    val uiState by viewModel.uiState.collectAsState()
    val editingFieldId by viewModel.editingFieldId.collectAsState()
    val pendingAddition by viewModel.pendingAddition.collectAsState()
    val gridActions = object : GridActions {
        override fun onEditField(fieldState: SensorFieldState) {
            viewModel.onEditField(fieldState)
        }

        override fun onDeleteField(fieldState: SensorFieldState) {
            viewModel.onDeleteSensorField(fieldState.sensorFieldId)
        }

        override fun onAddRow(beforeRow: Int) {
            viewModel.onAddRow(beforeRow)
        }

        override fun onAddCol(atRow: Int, beforeCol: Int) {
            viewModel.onAddCol(atRow, beforeCol)
        }
    }

    // Map the UI state and interactions to the SensorGridScreen
    SensorGridScreen(
        state = uiState,
        screenMode = screenMode,
        gridActions = gridActions,
        currentLocationFlow = viewModel.banalServiceRepository.currentLocation,
        liveSegments = viewModel.activeLiveSegments,
    )

    val currentActivityType by viewModel.activityType.collectAsState()

    // Handle Dialogs (Edit field / Add sensor)
    if (editingFieldId != null) {
        // 1. We need the activityType and tabViewId from the existing tab ViewModel
        // Note: 'viewModel' here refers to the TrackingViewModel initialized at the top of this file

        // 2. Initialize the specialized ViewModel using the complex Factory
        val editViewModel: EditSensorFieldViewModel = viewModel(
            factory = EditSensorFieldViewModelFactory(
                application = activity.application,
                trackingViewsRepository = viewModel.trackingViewsRepository, // Use repo from main VM
                banalServiceRepository = viewModel.banalServiceRepository,   // Use repo from main VM
                activityType = currentActivityType,
                sensorFieldId = editingFieldId!!,
                tabViewId = tabViewId,
                rowNr = -1, // Not used for editing existing fields
                colNr = -1  // Not used for editing existing fields
            ),
            key = "edit_field_$editingFieldId"
        )

        // 3. Show the Dialog
        EditSensorFieldDialog(
            title = stringResource(R.string.edit_field),
            viewModel = editViewModel,
            onDismissRequest = { viewModel.onDismissEditDialog() }
        )
    }

    pendingAddition?.let { params ->
        EditSensorFieldDialog(
            title = stringResource(R.string.add_sensor),
            viewModel = viewModel(
                factory = EditSensorFieldViewModelFactory(
                    application = activity.application,
                    trackingViewsRepository = viewModel.trackingViewsRepository,
                    banalServiceRepository = viewModel.banalServiceRepository,
                    activityType = currentActivityType,
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
}