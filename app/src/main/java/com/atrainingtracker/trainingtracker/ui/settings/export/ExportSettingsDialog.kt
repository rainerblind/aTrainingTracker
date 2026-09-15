/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.export

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication

import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSettingsDialog(
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(
        title = stringResource(R.string.prefs_Export),
        icon = Icons.Default.Upload,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.Confirm(
                onConfirm = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExportOptionToggle(
                label = "TCX",
                initialValue = TrainingApplication.exportToTCX(),
                onCheckedChange = { TrainingApplication.setExportToTCX(it) }
            )
            ExportOptionToggle(
                label = "GPX",
                initialValue = TrainingApplication.exportToGPX(),
                onCheckedChange = { TrainingApplication.setExportToGPX(it) }
            )
            ExportOptionToggle(
                label = "Golden Cheetah JSON",
                initialValue = TrainingApplication.exportToGCJson(),
                onCheckedChange = { TrainingApplication.setExportToGCJson(it) }
            )
            ExportOptionToggle(
                label = "CSV",
                initialValue = TrainingApplication.exportToCSV(),
                onCheckedChange = { TrainingApplication.setExportToCSV(it) }
            )
        }
    }
}

@Composable
private fun ExportOptionToggle(
    label: String,
    initialValue: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = isChecked,
            onCheckedChange = {
                isChecked = it
                onCheckedChange(it)
            },
            modifier = Modifier.scale(0.7f)
        )
    }
}
