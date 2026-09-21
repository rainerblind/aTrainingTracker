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

import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions

@Composable
fun ExportSettingsDialog(
    onDismiss: () -> Unit
) {
    var exportTcx by remember { mutableStateOf(TrainingApplication.exportToTCX()) }
    var exportGpx by remember { mutableStateOf(TrainingApplication.exportToGPX()) }
    var exportGcJson by remember { mutableStateOf(TrainingApplication.exportToGCJson()) }
    var exportCsv by remember { mutableStateOf(TrainingApplication.exportToCSV()) }

    AppBottomSheetContent(
        title = stringResource(R.string.prefs_Export),
        icon = Icons.Default.Upload,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    TrainingApplication.setExportToTCX(exportTcx)
                    TrainingApplication.setExportToGPX(exportGpx)
                    TrainingApplication.setExportToGCJson(exportGcJson)
                    TrainingApplication.setExportToCSV(exportCsv)
                    onDismiss()
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExportOptionToggle(
                label = "TCX",
                isChecked = exportTcx,
                onCheckedChange = { exportTcx = it }
            )
            ExportOptionToggle(
                label = "GPX",
                isChecked = exportGpx,
                onCheckedChange = { exportGpx = it }
            )
            ExportOptionToggle(
                label = "Golden Cheetah JSON",
                isChecked = exportGcJson,
                onCheckedChange = { exportGcJson = it }
            )
            ExportOptionToggle(
                label = "CSV",
                isChecked = exportCsv,
                onCheckedChange = { exportCsv = it }
            )
        }
    }
}

@Composable
private fun ExportOptionToggle(
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
