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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared container composable for live tracking bottom sheets (Segments & Climbs) in the cockpit (REQ-UI-366, ATT-3364).
 *
 * Encapsulates the canonical sheet contouring, shape, surface container background, and
 * click-to-expand gesture logic when in partially expanded state:
 * 1. Standard [BottomSheetDesign.SheetShape] and `.sheetContour()`.
 * 2. Unbroken [MaterialTheme.colorScheme.surface] background.
 * 3. Wraps content height to prevent vertical viewport overdraw (REQ-UI-270).
 * 4. Tap gesture to expand when in [isPartiallyExpanded] state.
 *
 * @param onExpand Invoked when the sheet is clicked in partially expanded state to request full expansion.
 * @param isPartiallyExpanded Whether the parent scaffold state is currently partially expanded.
 * @param modifier Optional modifier applied to the outer container.
 * @param content Composable slot for the sheet body (e.g. [LiveSegmentSheet] or [LiveClimbSheet]).
 */
@Composable
fun LiveTrackingSheetContainer(
    onExpand: () -> Unit,
    isPartiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .sheetContour()
            .background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)
            .then(
                if (isPartiallyExpanded) {
                    Modifier.clickable(onClick = onExpand)
                } else Modifier
            )
    ) {
        content()
    }
}
