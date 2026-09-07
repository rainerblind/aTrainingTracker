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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Reusable Jetpack Compose dialog for confirming bulk deletion of old workouts.
 *
 * Functional Description:
 * Presents a modal dialog asking the user to specify a retention threshold in days
 * (defaulting to [R.string.defaultDaysToKeep], which is 365 days). Enforces input
 * validation to ensure only non-negative integer values can be confirmed.
 *
 * @param onConfirm Callback invoked with the confirmed retention threshold in days.
 * @param onDismiss Callback invoked when the user cancels or dismisses the dialog.
 */
@Composable
fun DeleteOldWorkoutsDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultDays = stringResource(R.string.defaultDaysToKeep)
    var daysText by rememberSaveable { mutableStateOf(defaultDays) }

    val daysToKeep = daysText.trim().toIntOrNull()
    val isValid = daysToKeep != null && daysToKeep >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.deleteOldWorkouts),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.deleteWorkoutsThatAreOlderThanDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { newValue ->
                        // Allow only digit input or empty string during typing
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            daysText = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.workout_periods__days)) },
                    singleLine = true,
                    isError = !isValid && daysText.isNotEmpty(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    daysToKeep?.takeIf { it >= 0 }?.let { validDays ->
                        onConfirm(validDays)
                        onDismiss()
                    }
                },
                enabled = isValid
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.Cancel))
            }
        }
    )
}
