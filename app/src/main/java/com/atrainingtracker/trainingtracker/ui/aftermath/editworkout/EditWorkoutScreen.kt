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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterSelectionDialog
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutScreen(
    viewModel: EditWorkoutViewModel,
    onBack: () -> Unit
) {
    // Observe LiveData from ViewModel
    val workoutData by viewModel.workoutData.collectAsState()
    val sportTypes by viewModel.sportTypeNames.observeAsState(emptyList())
    val equipmentNames by viewModel.equipmentNames.observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_workout)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveChanges()
                            onBack()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Workout Name with Cluster Suggestions
            val suggestions by viewModel.clusterSuggestions.collectAsState()
            var showSuggestions by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = workoutData?.workoutName ?: "",
                    onValueChange = { viewModel.updateWorkoutName(it) },
                    label = { Text(stringResource(R.string.hint_workout_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (suggestions.isNotEmpty()) {
                            IconButton(onClick = { showSuggestions = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.my_locations),
                                    contentDescription = stringResource(R.string.cluster_suggestions_content_desc),
                                    tint = if (suggestions.any { it.second < 1.0 }) MaterialTheme.colorScheme.primary 
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )

                if (showSuggestions) {
                    WorkoutClusterSelectionDialog(
                        title = stringResource(R.string.edit_workout_name),
                        candidates = suggestions,
                        onSelect = { viewModel.applyClusterIdentity(it) },
                        onDismiss = { showSuggestions = false },
                        sportNameResolver = { viewModel.getSportName(it) },
                        bSportTypeResolver = { viewModel.getBSportType(it) }
                    )
                }
            }

            // 2. Spinners (Sport & Equipment)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = stringResource(R.string.Sport),
                    options = sportTypes,
                    selectedOption = viewModel.suggestedSportTypeName,
                    onOptionSelected = { viewModel.updateSportName(it) },
                    modifier = Modifier.weight(1f),
                    stayOpenOn = setOf(viewModel.allSportTypes)
                )
                DropdownSelector(
                    label = stringResource(R.string.Equipment),
                    options = equipmentNames,
                    selectedOption = viewModel.suggestedEquipmentName ?: viewModel.noEquipment,
                    onOptionSelected = { viewModel.updateEquipmentName(it) },
                    modifier = Modifier.weight(1f),
                    stayOpenOn = setOf(viewModel.allEquipment, viewModel.allShoes, viewModel.allBikes)
                )
            }

            // 3. Checkboxes (Commute / Trainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = workoutData?.commute ?: false,
                    onCheckedChange = { viewModel.updateIsCommute(it) }
                )
                Text(stringResource(R.string.commute))
                Spacer(Modifier.width(16.dp))
                Checkbox(
                    checked = workoutData?.trainer ?: false,
                    onCheckedChange = { viewModel.updateIsTrainer(it) }
                )
                Text(stringResource(R.string.trainer_general))
            }

            // 3.5 Workout individual upload to Strava
            if (TrainingApplication.uploadToCommunity(FileFormat.STRAVA)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val uploadStatus = workoutData?.uploadToStrava ?: -1
                    val stravaMappingAvailable = workoutData?.stravaSportName != null

                    val isChecked = if (stravaMappingAvailable) {
                        when (uploadStatus) {
                            1 -> true
                            0 -> false
                            else -> true
                        }
                    } else false

                    Icon(
                        painter = painterResource(R.drawable.logo_square_strava),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (stravaMappingAvailable) Color.Unspecified else Color.Gray
                    )

                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { viewModel.updateUploadToStrava(it) },
                        enabled = stravaMappingAvailable
                    )
                    Text(
                        text = stringResource(R.string.stravaUpload),
                        color = if (stravaMappingAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // 4. Description
            OutlinedTextField(
                value = workoutData?.description ?: "",
                onValueChange = { newValue -> viewModel.updateDescription(newDescription = newValue) },
                label = { Text(stringResource(R.string.hint_workout_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // 5. Goal
            OutlinedTextField(
                value = workoutData?.goal ?: "",
                onValueChange = { newValue -> viewModel.updateGoal(newGoal = newValue) },
                label = { Text(stringResource(R.string.hint_workout_goal)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 6. Method
            OutlinedTextField(
                value = workoutData?.method ?: "",
                onValueChange = { newValue -> viewModel.updateMethod(newMethod = newValue) },
                label = { Text(stringResource(R.string.hint_workout_method)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
