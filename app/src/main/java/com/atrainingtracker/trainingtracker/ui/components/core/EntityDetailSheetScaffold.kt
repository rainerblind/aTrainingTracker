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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.atrainingtracker.R

/**
 * Shared Material 3 modal bottom sheet scaffold for entity detail popups (REQ-UI-333, ATT-3053).
 *
 * Encapsulates the canonical full-height modal bottom sheet container and floating dismiss overlay
 * used across Climb, Segment, and Route popups:
 * 1. Standard [BottomSheetDesign.SheetShape] and [BottomSheetDesign.SheetTonalElevation].
 * 2. Suppressed drag handle to maximize vertical screen real estate for map and elevation viewports.
 * 3. Semi-transparent elevated circular close button anchored at [Alignment.TopEnd].
 *
 * @param onDismiss Invoked when the user dismisses the sheet via close button, downward swipe, or back gesture.
 * @param modifier Optional modifier applied to the root container.
 * @param content Content slot composed inside a full-size [BoxScope].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailSheetScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetDesign.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = BottomSheetDesign.SheetTonalElevation,
        dragHandle = null,
        modifier = modifier.fillMaxHeight()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            // Top-right dismiss close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .zIndex(10f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shadowElevation = 2.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.Cancel),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
