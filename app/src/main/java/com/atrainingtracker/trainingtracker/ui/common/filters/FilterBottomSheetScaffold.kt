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

package com.atrainingtracker.trainingtracker.ui.common.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Reusable Material 3 modal bottom sheet scaffold for multi-dimensional filtering.
 *
 * Provides a standardized structural layout across workouts and routes:
 * 1. Top header row with title text and close dismiss button.
 * 2. Top horizontal divider.
 * 3. Scrollable content body with standardized horizontal padding and vertical spacing.
 * 4. Bottom horizontal divider.
 * 5. Dedicated bottom action bar with full-width split "Reset all" and "OK" buttons,
 *    anchored flush to the bottom edge with [navigationBarsPadding].
 *
 * @param title Localized headline for the filter sheet.
 * @param onDismissRequest Callback invoked when the sheet is dismissed via gesture or close button.
 * @param onClearAll Callback invoked when the "Reset all" action button is tapped.
 * @param onApply Callback invoked when the "OK" / "Apply" action button is tapped.
 * @param modifier Optional modifier applied to the root column.
 * @param content Composable slot containing the filter dimension controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheetScaffold(
    title: String,
    onDismissRequest: () -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Bar: Title and Close dismiss button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.Cancel)
                    )
                }
            }

            HorizontalDivider()

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()

            // Dedicated Bottom Action Bar: Reset all & Apply
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.filter_clear_all))
                }

                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.OK))
                }
            }
        }
    }
}
