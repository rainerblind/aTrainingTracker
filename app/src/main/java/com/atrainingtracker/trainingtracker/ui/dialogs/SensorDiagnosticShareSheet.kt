/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.dialogs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.exporter.SensorDiagnosticBundlePackager
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import kotlinx.coroutines.launch

/**
 * Material 3 diagnostic bundle share sheet for Polar raw sensor telemetry (REQ-EXP-044.2, REQ-EXP-044.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDiagnosticShareSheet(
    fileBaseName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var includeGps by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var isPackaging by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        title = stringResource(R.string.diagnostic_share_sheet_title),
        icon = Icons.Default.BugReport,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        SensorDiagnosticShareSheetContent(
            fileBaseName = fileBaseName,
            includeGps = includeGps,
            onIncludeGpsChange = { includeGps = it },
            notes = notes,
            onNotesChange = { notes = it },
            isPackaging = isPackaging,
            onShareClick = {
                if (isPackaging) return@SensorDiagnosticShareSheetContent
                isPackaging = true
                coroutineScope.launch {
                    val result = SensorDiagnosticBundlePackager.createBundle(
                        context = context,
                        fileBaseName = fileBaseName,
                        includeGps = includeGps,
                        symptomNotes = notes
                    )
                    isPackaging = false
                    result.fold(
                        onSuccess = { zipFile ->
                            shareDiagnosticBundle(context, zipFile, fileBaseName, notes)
                            onDismiss()
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Failed to create bundle: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        )
    }
}

/**
 * Stateless content presentation for [SensorDiagnosticShareSheet], facilitating visual unit testing (TST-EXP-044.5).
 */
@Composable
fun SensorDiagnosticShareSheetContent(
    fileBaseName: String,
    includeGps: Boolean,
    onIncludeGpsChange: (Boolean) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    isPackaging: Boolean,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("sensor_diagnostic_share_sheet_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.diagnostic_share_sheet_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.diagnostic_include_gps_location),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.diagnostic_include_gps_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includeGps,
                    onCheckedChange = onIncludeGpsChange,
                    modifier = Modifier.testTag("diagnostic_gps_switch")
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.diagnostic_symptom_notes)) },
            placeholder = { Text(stringResource(R.string.diagnostic_symptom_notes_placeholder)) },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("diagnostic_notes_input")
        )

        if (isPackaging) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.diagnostic_bundle_packaging),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Button(
                onClick = onShareClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostic_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.diagnostic_share_button))
            }
        }
    }
}

private fun shareDiagnosticBundle(
    context: Context,
    zipFile: java.io.File,
    fileBaseName: String,
    notes: String
) {
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, zipFile)

    val bodyText = buildString {
        append("Sensor Diagnostic Bundle for $fileBaseName\n\n")
        if (notes.isNotBlank()) {
            append("Symptom Notes:\n$notes\n\n")
        }
        append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\n")
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_EMAIL, arrayOf("rainer.blind@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, "aTrainingTracker Sensor Diagnostic Bundle: $fileBaseName")
        putExtra(Intent.EXTRA_TEXT, bodyText)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.diagnostic_share_button)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
