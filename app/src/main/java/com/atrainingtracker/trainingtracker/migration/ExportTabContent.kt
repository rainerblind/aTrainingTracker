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

package com.atrainingtracker.trainingtracker.migration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Composable rendering the Export tab (Tab 0) of [ImportBackupTabsScreen] (`REQ-MIG-059`).
 *
 * Exposes all local workout export file format toggles (TCX, GPX, Golden Cheetah JSON, CSV, FIT,
 * and Raw Sensor Data) with immediate persistence via [TrainingApplication].
 *
 * @param resumeTrigger Monotonic counter incremented when the host screen resumes or shared
 *   preferences mutate externally, keeping toggle states synchronized.
 */
@Composable
fun ExportTabContent(
    resumeTrigger: Int = 0
) {
    var exportTcx by remember { mutableStateOf(TrainingApplication.exportToTCX()) }
    var exportGpx by remember { mutableStateOf(TrainingApplication.exportToGPX()) }
    var exportGcJson by remember { mutableStateOf(TrainingApplication.exportToGCJson()) }
    var exportCsv by remember { mutableStateOf(TrainingApplication.exportToCSV()) }
    var exportFit by remember { mutableStateOf(TrainingApplication.exportToFIT()) }
    var exportRawSensor by remember { mutableStateOf(TrainingApplication.exportToRawSensor()) }

    LaunchedEffect(resumeTrigger) {
        exportTcx = TrainingApplication.exportToTCX()
        exportGpx = TrainingApplication.exportToGPX()
        exportGcJson = TrainingApplication.exportToGCJson()
        exportCsv = TrainingApplication.exportToCSV()
        exportFit = TrainingApplication.exportToFIT()
        exportRawSensor = TrainingApplication.exportToRawSensor()
    }

    ExportTabContentLayout(
        exportTcx = exportTcx,
        exportGpx = exportGpx,
        exportGcJson = exportGcJson,
        exportCsv = exportCsv,
        exportFit = exportFit,
        exportRawSensor = exportRawSensor,
        onExportTcxChange = {
            exportTcx = it
            TrainingApplication.setExportToTCX(it)
        },
        onExportGpxChange = {
            exportGpx = it
            TrainingApplication.setExportToGPX(it)
        },
        onExportGcJsonChange = {
            exportGcJson = it
            TrainingApplication.setExportToGCJson(it)
        },
        onExportCsvChange = {
            exportCsv = it
            TrainingApplication.setExportToCSV(it)
        },
        onExportFitChange = {
            exportFit = it
            TrainingApplication.setExportToFIT(it)
        },
        onExportRawSensorChange = {
            exportRawSensor = it
            TrainingApplication.setExportToRawSensor(it)
        }
    )
}

/**
 * Stateless layout implementation for [ExportTabContent] enabling previews and isolated UI tests.
 */
@Composable
fun ExportTabContentLayout(
    exportTcx: Boolean,
    exportGpx: Boolean,
    exportGcJson: Boolean,
    exportCsv: Boolean,
    exportFit: Boolean,
    exportRawSensor: Boolean,
    onExportTcxChange: (Boolean) -> Unit,
    onExportGpxChange: (Boolean) -> Unit,
    onExportGcJsonChange: (Boolean) -> Unit,
    onExportCsvChange: (Boolean) -> Unit,
    onExportFitChange: (Boolean) -> Unit,
    onExportRawSensorChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.prefs_Export),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.prefsExportSummary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                ExportOptionToggleRow(
                    label = "TCX",
                    isChecked = exportTcx,
                    onCheckedChange = onExportTcxChange
                )
                ExportOptionToggleRow(
                    label = "GPX",
                    isChecked = exportGpx,
                    onCheckedChange = onExportGpxChange
                )
                ExportOptionToggleRow(
                    label = "Golden Cheetah JSON",
                    isChecked = exportGcJson,
                    onCheckedChange = onExportGcJsonChange
                )
                ExportOptionToggleRow(
                    label = "CSV",
                    isChecked = exportCsv,
                    onCheckedChange = onExportCsvChange
                )
                ExportOptionToggleRow(
                    label = "FIT",
                    isChecked = exportFit,
                    onCheckedChange = onExportFitChange
                )
                ExportOptionToggleRow(
                    label = stringResource(R.string.file_format_raw_sensor),
                    isChecked = exportRawSensor,
                    onCheckedChange = onExportRawSensorChange
                )
            }
        }
    }
}

@Composable
private fun ExportOptionToggleRow(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Preview(showBackground = true, name = "Export Tab - Light")
@Composable
private fun ExportTabContentPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        ExportTabContentLayout(
            exportTcx = true,
            exportGpx = false,
            exportGcJson = false,
            exportCsv = false,
            exportFit = true,
            exportRawSensor = false,
            onExportTcxChange = {},
            onExportGpxChange = {},
            onExportGcJsonChange = {},
            onExportCsvChange = {},
            onExportFitChange = {},
            onExportRawSensorChange = {}
        )
    }
}

@Preview(showBackground = true, name = "Export Tab - Dark")
@Composable
private fun ExportTabContentPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        ExportTabContentLayout(
            exportTcx = true,
            exportGpx = true,
            exportGcJson = false,
            exportCsv = false,
            exportFit = true,
            exportRawSensor = true,
            onExportTcxChange = {},
            onExportGpxChange = {},
            onExportGcJsonChange = {},
            onExportCsvChange = {},
            onExportFitChange = {},
            onExportRawSensorChange = {}
        )
    }
}
