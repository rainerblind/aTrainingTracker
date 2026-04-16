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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreen
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelFactory
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.GridActions

@Composable
fun TrackingTabGridContent(
    tabViewId: Long,
    screenMode: ScreenMode
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

    val uiState by viewModel.uiState.collectAsState()
    val editingFieldId by viewModel.editingFieldId.collectAsState()
    val pendingAddition by viewModel.pendingAddition.collectAsState()

    // Map the UI state and interactions to the SensorGridScreen
    SensorGridScreen(
        state = uiState,
        screenMode = screenMode,
        gridActions = GridActions {
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
        },
        // Pass flows from your repository/service
        currentLocationFlow = viewModel.banalServiceRepository.currentLocation,
        liveSegments = viewModel.activeLiveSegments,
        mapViewModel = TODO()
    )

    // Handle Dialogs (Edit field / Add sensor)
    if (editingFieldId != null) {
        EditSensorFieldDialog(
            fieldId = editingFieldId!!,
            onDismiss = { viewModel.onCancelEdit() },
            onSave = { updatedField -> viewModel.onSaveSensorField(updatedField) },
            title = TODO(),
            viewModel = TODO(),
            onDismissRequest = TODO()
        )
    }

    if (pendingAddition != null) {
        AddSensorDialog(
            position = pendingAddition!!,
            onDismiss = { viewModel.onCancelAdd() },
            onConfirm = { sensorType -> viewModel.onConfirmAddSensor(sensorType) }
        )
    }
}