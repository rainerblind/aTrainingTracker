/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.fragments.preferences

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

    AlertDialog(
        onDismissRequest = onDismiss,
        // Standard Material 3 dialog icon & title
        icon = { Icon(Icons.Default.SquareFoot, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { 
            Text(
                text = stringResource(R.string.prefsUnitsTitle),
                style = MaterialTheme.typography.headlineSmall
            ) 
        },
        text = {
            // Remove fillMaxWidth() to allow the dialog container to hug the content more tightly.
            Column(
                modifier = Modifier.wrapContentWidth(),
                verticalArrangement = Arrangement.Center
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
        },
        confirmButton = {
            TextButton(onClick = {
                sharedPreferences.edit().putString(key, selectedUnit.name).apply()
                onDismiss()
            }) {
                Text(stringResource(R.string.Done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.Cancel))
            }
        }
    )
}
