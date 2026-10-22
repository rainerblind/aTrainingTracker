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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared canonical internal layout structure for live tracking bottom sheets (Segments & Climbs) in the cockpit (REQ-UI-383, ATT-3576).
 *
 * Enforces a single authoritative vertical hierarchy:
 * 1. Unbroken [MaterialTheme.colorScheme.surface] container background wrapping content height.
 * 2. Header slot padded with `start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp`.
 * 3. Canonical 0.5.dp [HorizontalDivider] with 16.dp horizontal margin.
 * 4. Telemetry HUD metrics slot padded with `horizontal = 16.dp, vertical = 4.dp`.
 * 5. Full-width elevation profile slot without extra horizontal padding.
 *
 * @param headerContent Composable slot receiving pre-configured modifier for header content (e.g. title, badges).
 * @param telemetryHudContent Composable slot receiving pre-configured modifier for live telemetry HUD rows.
 * @param elevationContent Composable slot for the full-width elevation profile.
 * @param modifier Optional modifier applied to the outer layout container.
 */
@Composable
fun LiveTrackingEntitySheet(
    headerContent: @Composable (Modifier) -> Unit,
    telemetryHudContent: @Composable (Modifier) -> Unit,
    elevationContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        elevationContent()
    }
}
