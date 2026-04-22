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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Replaces export_status__group.xml
 */
@Composable
fun ExportStatusGroup(
    data: ExportStatusGroupData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                    hasContent = true
                )
            )

            ExportStatusGroup(
                data = ExportStatusGroupData(
                    groupTitle = "Local File",
                    succeededLine = "✅ Succeeded: GPX",
                    hasContent = true
                )
            )
        }
    }
}