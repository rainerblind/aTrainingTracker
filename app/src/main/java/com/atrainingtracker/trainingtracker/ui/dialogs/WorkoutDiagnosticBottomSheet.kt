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

package com.atrainingtracker.trainingtracker.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.tracker.DiagnosticCategory
import com.atrainingtracker.trainingtracker.tracker.WorkoutRecordingErrorCollector
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

object WorkoutDiagnosticTestTags {
    const val SHEET = "workout_diagnostic_bottom_sheet"
    const val EXPLANATION = "workout_diagnostic_explanation"
    const val DETAILS_TOGGLE = "workout_diagnostic_details_toggle"
    const val COPY_BUTTON = "workout_diagnostic_copy_button"
    const val EMAIL_BUTTON = "workout_diagnostic_email_button"
    const val CLEAR_BUTTON = "workout_diagnostic_clear_button"
    const val CONFIRM_CLEAR = "workout_diagnostic_confirm_clear"
    const val BANNER = "workout_diagnostic_banner"
    const val BADGE = "workout_diagnostic_badge"
    const val CARD = "workout_diagnostic_card"
}

/**
 * Actionable Material 3 diagnostic bottom sheet surfacing caught workout exceptions (REQ-TRK-053.4).
 *
 * Features:
 * 1. Plain-language categorization (Storage Full, Sample Write, Finalization, Generic).
 * 2. Collapsible technical details with Copy-to-Clipboard.
 * 3. 1-Tap email bug report to Rainer Blind with pre-filled device metadata and stack traces.
 * 4. Confirmation-gated warning deletion resetting `RECORDING_ERRORS` to null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDiagnosticBottomSheet(
    recordingErrorsJson: String,
    workoutName: String,
    sportName: String,
    onClearWarning: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSendEmailReport: ((subject: String, body: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDetails by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val entries = remember(recordingErrorsJson) {
        WorkoutRecordingErrorCollector.fromJson(recordingErrorsJson)
    }
    val category = remember(entries) {
        WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(entries)
    }

    val explanationText = when (category) {
        DiagnosticCategory.STORAGE_FULL -> stringResource(R.string.workout_diagnostic_explanation_storage_full)
        DiagnosticCategory.SAMPLE_WRITE -> stringResource(R.string.workout_diagnostic_explanation_sample_write)
        DiagnosticCategory.FINALIZE -> stringResource(R.string.workout_diagnostic_explanation_finalize)
        DiagnosticCategory.GENERIC -> stringResource(R.string.workout_diagnostic_explanation_generic)
    }

    val emailSubject = stringResource(R.string.workout_diagnostic_email_subject, BuildConfig.VERSION_NAME)
    val emailBody = remember(workoutName, sportName, recordingErrorsJson) {
        WorkoutRecordingErrorCollector.formatEmailBody(workoutName, sportName, recordingErrorsJson)
    }

    val handleSendEmail: () -> Unit = {
        if (onSendEmailReport != null) {
            onSendEmailReport(emailSubject, emailBody)
        } else {
            launchEmailIntent(context, emailSubject, emailBody)
        }
    }

    AppModalBottomSheet(
        title = stringResource(R.string.workout_diagnostic_dialog_title),
        icon = Icons.Default.WarningAmber,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(WorkoutDiagnosticTestTags.SHEET),
        actions = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Plain-Language Explanation Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutDiagnosticTestTags.EXPLANATION)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = explanationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 2. Expandable Technical Details Section
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDetails = !showDetails }
                            .testTag(WorkoutDiagnosticTestTags.DETAILS_TOGGLE),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.workout_diagnostic_technical_details_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(emailBody))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.workout_diagnostic_copied_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag(WorkoutDiagnosticTestTags.COPY_BUTTON)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.workout_diagnostic_action_copy),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = showDetails) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = emailBody,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. Primary Action: Send Email Bug Report
            Button(
                onClick = handleSendEmail,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutDiagnosticTestTags.EMAIL_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.workout_diagnostic_action_send_email))
            }

            // 4. Secondary Action: Clear Warning
            OutlinedButton(
                onClick = { showConfirmClearDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutDiagnosticTestTags.CLEAR_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.workout_diagnostic_action_clear_warning),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text(stringResource(R.string.workout_diagnostic_clear_confirm_title)) },
            text = { Text(stringResource(R.string.workout_diagnostic_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmClearDialog = false
                        onClearWarning()
                        onDismiss()
                    },
                    modifier = Modifier.testTag(WorkoutDiagnosticTestTags.CONFIRM_CLEAR)
                ) {
                    Text(
                        stringResource(R.string.workout_diagnostic_action_clear_warning),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

private fun launchEmailIntent(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:rainer.blind@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No email app found to send report.", Toast.LENGTH_SHORT).show()
    }
}
