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

package com.atrainingtracker.trainingtracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import java.util.Locale

/**
 * Dialog allowing athlete body weight configuration conforming to REQ-TRK-019 and TST-TRK-011.
 * Supports metric (kg) and imperial (lbs) units with automatic conversion and boundary validation.
 */
@Composable
fun AthleteWeightDialog(
    initialWeightKg: Float,
    onDismiss: () -> Unit,
    onSaveWeightKg: (Float) -> Unit
) {
    val isImperial = TrainingApplication.getUnit() == MyUnits.IMPERIAL
    val unitLabel = if (isImperial) "lbs" else "kg"
    val minAllowed = if (isImperial) SettingsDataStore.MIN_ATHLETE_WEIGHT_LBS else SettingsDataStore.MIN_ATHLETE_WEIGHT_KG
    val maxAllowed = if (isImperial) SettingsDataStore.MAX_ATHLETE_WEIGHT_LBS else SettingsDataStore.MAX_ATHLETE_WEIGHT_KG

    var input by remember(initialWeightKg) {
        val initialDisplay = if (initialWeightKg > 0f) {
            val displayVal = if (isImperial) initialWeightKg * SettingsDataStore.LBS_PER_KG else initialWeightKg
            String.format(Locale.US, "%.1f", displayVal)
        } else ""
        mutableStateOf(initialDisplay)
    }

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.athlete_weight_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.athlete_weight_card_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = { newValue ->
                        input = newValue
                        isError = false
                    },
                    label = { Text("${stringResource(R.string.athlete_weight_label)} ($unitLabel)") },
                    supportingText = {
                        if (isError) {
                            Text(
                                text = "${stringResource(R.string.athlete_weight_invalid)} (${minAllowed.toInt()} - ${maxAllowed.toInt()} $unitLabel)",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("${minAllowed.toInt()} - ${maxAllowed.toInt()} $unitLabel")
                        }
                    },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = input.trim().replace(',', '.').toFloatOrNull()
                    if (parsed == null || parsed !in minAllowed..maxAllowed) {
                        isError = true
                    } else {
                        val savedKg = if (isImperial) parsed / SettingsDataStore.LBS_PER_KG else parsed
                        onSaveWeightKg(savedKg)
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(R.string.athlete_weight_save))
            }
        },
        dismissButton = {
            Row {
                if (initialWeightKg > 0f) {
                    TextButton(
                        onClick = {
                            onSaveWeightKg(0.0f)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.athlete_weight_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
