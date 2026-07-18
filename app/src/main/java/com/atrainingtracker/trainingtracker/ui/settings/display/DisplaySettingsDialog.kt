/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.display

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication

@Composable
fun DisplaySettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DisplaySettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.Display),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DisplayOptionToggle(
                    label = stringResource(R.string.forcePortrait),
                    prefValue = "forcePortrait",
                    sharedPreferences = sharedPreferences
                )
                DisplayOptionToggle(
                    label = stringResource(R.string.prefsKeepScreenOnTitle),
                    prefValue = "keepScreenOn",
                    sharedPreferences = sharedPreferences
                )
                DisplayOptionToggle(
                    label = stringResource(R.string.prefsNoUnlockingTitle),
                    prefValue = "noUnlocking",
                    sharedPreferences = sharedPreferences
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.Done))
            }
        }
    )
}

@Composable
private fun DisplayOptionToggle(
    label: String, 
    prefValue: String,
    sharedPreferences: SharedPreferences
) {
    val key = TrainingApplication.SP_DISPLAY_OPTIONS
    
    var currentOptions by remember { 
        mutableStateOf(sharedPreferences.getStringSet(key, emptySet()) ?: emptySet())
    }
    
    val isChecked = currentOptions.contains(prefValue)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = isChecked,
            onCheckedChange = { checked ->
                val newSet = currentOptions.toMutableSet()
                if (checked) newSet.add(prefValue) else newSet.remove(prefValue)
                sharedPreferences.edit().putStringSet(key, newSet).apply()
                currentOptions = newSet
            },
            modifier = Modifier.scale(0.7f)
        )
    }
}
