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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRouteScreen(
    routeSummary: RouteSummary,
    onSave: (RouteSummary) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(routeSummary.name) }
    var description by remember { mutableStateOf(routeSummary.description) }
    var selectedSport by remember { mutableStateOf(routeSummary.bSportType) }

    AppModalBottomSheet(
        title = stringResource(R.string.route_edit),
        icon = Icons.Default.Edit,
        onDismissRequest = onCancel,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    onSave(
                        routeSummary.copy(
                            name = name,
                            description = description,
                            bSportType = selectedSport
                        )
                    )
                },
                onCancel = onCancel,
                saveEnabled = name.isNotBlank()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Route Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Sport Type Selection
            Text(stringResource(R.string.route_sport_type), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { bSportType ->
                    FilterChip(
                        selected = selectedSport == bSportType,
                        onClick = { selectedSport = bSportType },
                        label = { Text(stringResource(bSportType.stringResId)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = bSportType.iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.route_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}