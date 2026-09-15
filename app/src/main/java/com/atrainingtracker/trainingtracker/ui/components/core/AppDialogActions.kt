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

package com.atrainingtracker.trainingtracker.ui.components.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R

/**
 * Standardized action bar layouts for modal bottom sheets and dialogs.
 *
 * Enforces uniform button placement, spacing, and semantic labeling per REQ-UI-150:
 * - [SaveCancel]: For data-persisting dialogs. Cancel on left, primary "Save" ("Speichern") on right.
 * - [Confirm]: For informational or immediate-effect sheets. Full-width primary "OK".
 * - [CancelOnly]: For selection sheets. Full-width text "Cancel" ("Abbrechen").
 */
object AppDialogActions {

    /**
     * Standard action layout for data-storing sheets:
     * - Left: Secondary text button (Cancel / "Abbrechen")
     * - Right: Primary filled button (Save / "Speichern")
     */
    @Composable
    fun SaveCancel(
        onSave: () -> Unit,
        onCancel: () -> Unit,
        modifier: Modifier = Modifier,
        saveText: String = stringResource(R.string.save),
        cancelText: String = stringResource(R.string.Cancel),
        saveEnabled: Boolean = true
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(cancelText)
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled
            ) {
                Text(saveText)
            }
        }
    }

    /**
     * Standard action layout for informational or immediate preference sheets:
     * - Full-width primary filled button (OK / "OK")
     */
    @Composable
    fun Confirm(
        onConfirm: () -> Unit,
        modifier: Modifier = Modifier,
        confirmText: String = stringResource(android.R.string.ok)
    ) {
        Button(
            onClick = onConfirm,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(confirmText)
        }
    }

    /**
     * Standard action layout for selection sheets:
     * - Full-width text button (Cancel / "Abbrechen")
     */
    @Composable
    fun CancelOnly(
        onCancel: () -> Unit,
        modifier: Modifier = Modifier,
        cancelText: String = stringResource(R.string.Cancel)
    ) {
        TextButton(
            onClick = onCancel,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(cancelText)
        }
    }
}
