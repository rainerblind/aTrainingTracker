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

package com.atrainingtracker.trainingtracker.ui.settings.trackingtabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource

/**
 * Modal Bottom Sheet presenting the Multi-Select Copy Tracking Tab dialog (REQ-UI-439 / ATT-5078).
 *
 * Allows athletes to select one or more target [ActivityType] profiles to clone an existing tracking tab into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyTrackingTabDialog(
    sourceActivityType: ActivityType,
    onConfirm: (List<ActivityType>) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetDesign.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = BottomSheetDesign.SheetTonalElevation,
        dragHandle = null,
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        CopyTrackingTabContent(
            sourceActivityType = sourceActivityType,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

/**
 * Reusable content layout for [CopyTrackingTabDialog], backed by [AppBottomSheetContent].
 */
@Composable
fun CopyTrackingTabContent(
    sourceActivityType: ActivityType,
    onConfirm: (List<ActivityType>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTypes by remember { mutableStateOf(emptySet<ActivityType>()) }

    AppBottomSheetContent(
        title = stringResource(R.string.copy_tab_title),
        icon = Icons.Default.ContentCopy,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = { onConfirm(selectedTypes.toList()) },
                onCancel = onDismiss,
                saveText = stringResource(R.string.copy_tab_action),
                saveEnabled = selectedTypes.isNotEmpty()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ActivityType.values().forEach { type ->
                val isChecked = selectedTypes.contains(type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTypes = if (isChecked) {
                                selectedTypes - type
                            } else {
                                selectedTypes + type
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = safePainterResource(id = type.logoId, fallback = Icons.Default.FitnessCenter),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(id = type.titleId),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selectedTypes = if (checked) {
                                selectedTypes + type
                            } else {
                                selectedTypes - type
                            }
                        }
                    )
                }
            }
        }
    }
}
