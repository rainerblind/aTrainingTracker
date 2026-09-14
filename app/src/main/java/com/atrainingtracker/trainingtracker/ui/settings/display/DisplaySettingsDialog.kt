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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication

@Composable
fun DisplaySettingsDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: (() -> Unit)? = null
) {
    var currentOptions by remember { 
        mutableStateOf(HashSet(TrainingApplication.getDisplayOptions()))
    }
    
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
                    isChecked = currentOptions.contains("forcePortrait"),
                    onCheckedChange = { checked ->
                        val newSet = currentOptions.toMutableSet().apply {
                            if (checked) add("forcePortrait") else remove("forcePortrait")
                        }
                        TrainingApplication.setDisplayOptions(newSet)
                        currentOptions = HashSet(newSet)
                        onSettingsChanged?.invoke()
                    }
                )
                DisplayOptionToggle(
                    label = stringResource(R.string.prefsKeepScreenOnTitle),
                    isChecked = currentOptions.contains("keepScreenOn"),
                    onCheckedChange = { checked ->
                        val newSet = currentOptions.toMutableSet().apply {
                            if (checked) add("keepScreenOn") else remove("keepScreenOn")
                        }
                        TrainingApplication.setDisplayOptions(newSet)
                        currentOptions = HashSet(newSet)
                        onSettingsChanged?.invoke()
                    }
                )
                DisplayOptionToggle(
                    label = stringResource(R.string.prefsNoUnlockingTitle),
                    isChecked = currentOptions.contains("noUnlocking"),
                    onCheckedChange = { checked ->
                        val newSet = currentOptions.toMutableSet().apply {
                            if (checked) add("noUnlocking") else remove("noUnlocking")
                        }
                        TrainingApplication.setDisplayOptions(newSet)
                        currentOptions = HashSet(newSet)
                        onSettingsChanged?.invoke()
                    }
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
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.7f)
        )
    }
}
