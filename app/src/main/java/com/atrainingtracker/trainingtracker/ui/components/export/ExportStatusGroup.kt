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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Renders the workout export status section with smart auto-collapse (`isExpanded = false` by default)
 * and inline expansion (`REQ-UI-407`):
 * - Summarizes all `isFullySucceeded` groups in a compact 1-line clickable header (`✅ File, Dropbox, Communities [▾]`).
 * - Automatically surfaces any `needsAttentionOrInProgress` groups (waiting, running, failed) immediately below the header when collapsed.
 * - Expands all active groups via `AnimatedVisibility` when the header row is tapped.
 */
@Composable
fun ExportStatus(
    exportStatuses: List<ExportStatusGroupData>,
    modifier: Modifier = Modifier
) {
    val activeExports = remember(exportStatuses) { exportStatuses.filter { it.hasContent } }

    if (activeExports.isNotEmpty()) {
        var isExpanded by remember { mutableStateOf(false) }
        val succeededSummary = remember(activeExports) { formatCollapsedSucceededSummary(activeExports) }
        val attentionGroups = remember(activeExports) { filterAttentionGroups(activeExports) }

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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.export_status),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!isExpanded && succeededSummary != null) {
                        Text(
                            text = succeededSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp, end = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.export_status),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // When collapsed, automatically show any waiting/uploading/failed groups immediately
                if (!isExpanded && attentionGroups.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        attentionGroups.forEach { statusGroup ->
                            ExportStatusGroup(
                                data = statusGroup,
                                showSucceededLine = false
                            )
                        }
                    }
                }

                // When expanded, show all active export groups in full
                AnimatedVisibility(visible = isExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeExports.forEach { statusGroup ->
                            ExportStatusGroup(
                                data = statusGroup,
                                showSucceededLine = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Export Status for one export type (File, Dropbox, Google Drive, Community).
 * Tapping the group opens [ExportDetailsDialog] (`REQ-UI-149`, `REQ-UI-407.2`).
 */
@Composable
fun ExportStatusGroup(
    data: ExportStatusGroupData,
    modifier: Modifier = Modifier,
    showSucceededLine: Boolean = true
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
        if (showSucceededLine) {
            StatusLine(text = data.succeededLine)
        }
        StatusLine(text = data.failedLine)
    }
}

/**
 * Modernized bottom sheet dialog for inspecting export format upload statuses and logs (REQ-UI-149, TST-UI-102).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDetailsDialog(
    data: ExportStatusGroupData,
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = data.groupTitle,
        icon = Icons.Default.Upload,
        actions = {
            AppDialogActions.Confirm(onConfirm = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            val hasFailures = remember(data.details) {
                data.details.any { it.status == "FINISHED_FAILED" }
            }
            if (hasFailures) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.crop_force_reexport))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
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
private fun PreviewExportStatusGroup() {
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