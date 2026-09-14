/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.units

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication

import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val key = TrainingApplication.SP_UNITS
    
    // Initial selection
    var selectedUnit by remember { 
        mutableStateOf(TrainingApplication.getUnit()) 
    }

    AppModalBottomSheet(
        title = stringResource(R.string.prefsUnitsTitle),
        icon = Icons.Default.SquareFoot,
        onDismissRequest = onDismiss,
        actions = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.Cancel))
            }
            Button(
                onClick = {
                    sharedPreferences.edit().putString(key, selectedUnit.name).apply()
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.Done))
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MyUnits.values().forEach { unit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth() 
                        .clickable { selectedUnit = unit }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (unit == selectedUnit),
                        onClick = { selectedUnit = unit }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(unit.nameId),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
