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

package com.atrainingtracker.trainingtracker.ui.components.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

@Composable
fun ExportStatus(
    exportStatuses: List<ExportStatusGroupData>,
    modifier: Modifier = Modifier
) {
    val activeExports = exportStatuses.filter { it.hasContent }

    if (activeExports.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.export_status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                activeExports.forEach { statusGroup ->
                    ExportStatusGroup(data = statusGroup)
                }
            }
        }
    }
}

/*
 * Export Status for one export type (File, Dropbox, Community)
 */
@Composable
fun ExportStatusGroup(
    data: ExportStatusGroupData,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        ExportDetailsDialog(
            data = data,
            onDismiss = { showDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Title (e.g., "Dropbox") - Matches Body2 style
        Text(
            text = data.groupTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Status Lines - Replicates the visibility="gone" behavior
        StatusLine(text = data.waitingLine)
        StatusLine(text = data.runningLine)
        StatusLine(text = data.succeededLine)
        StatusLine(text = data.failedLine)
    }
}

@Composable
fun ExportDetailsDialog(
    data: ExportStatusGroupData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = data.groupTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.details.forEach { detail ->
                    Column {
                        Text(
                            text = "${detail.formatName}: ${detail.status}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!detail.answer.isNullOrBlank()) {
                            Text(
                                text = detail.answer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.OK))
            }
        }
    )
}

@Composable
private fun StatusLine(text: String?) {
    if (!text.isNullOrBlank()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun PreviewExportStatusGroup() {
    MaterialTheme {
        Column(Modifier.padding(16.dp)) {
            ExportStatusGroup(
                data = ExportStatusGroupData(
                    groupTitle = "Dropbox",
                    waitingLine = "⏳ waiting: TCX, GPX",
                    runningLine = "📤 Uploading: FIT",
                    succeededLine = "✅ Succeeded: CSV",
                    failedLine = "❌ Failed: KML",
                    hasContent = true,
                    details = listOf(
                        ExportDetail("TCX", "WAITING", null),
                        ExportDetail("GPX", "WAITING", null),
                        ExportDetail("FIT", "PROCESSING", "Uploading to Dropbox..."),
                        ExportDetail("CSV", "FINISHED_SUCCESS", "Successfully uploaded"),
                        ExportDetail("KML", "FINISHED_FAILED", "Network error: 404")
                    )
                )
            )

            ExportStatusGroup(
                data = ExportStatusGroupData(
                    groupTitle = "Local File",
                    succeededLine = "✅ Succeeded: GPX",
                    hasContent = true,
                    details = listOf(
                        ExportDetail("GPX", "FINISHED_SUCCESS", "Stored in /Documents/aTrainingTracker")
                    )
                )
            )
        }
    }
}