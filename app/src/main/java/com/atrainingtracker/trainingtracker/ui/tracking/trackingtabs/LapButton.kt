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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import androidx.compose.animation.core.copy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.color
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlin.text.uppercase

@Composable
fun LapButton(
    modifier: Modifier = Modifier,
    trackingMode: TrackingMode,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isTracking = trackingMode == TrackingMode.TRACKING

    // Logic: 1.0f when tracking, 0.5f when not (Ghosted)
    val alpha = if (isTracking) 1.0f else 0.5f

    Button(
        onClick = onClick,
        enabled = isTracking, // Button is only clickable in Tracking mode
        modifier = modifier
            .height(56.dp)
            .width(124.dp)
            .alpha(alpha),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            // Active Colors
            containerColor = colorResource(R.color.color_primary),
            contentColor = colorResource(R.color.color_on_primary),
            // "Ghosted" / Disabled Colors
            disabledContainerColor = colorResource(R.color.lap_button_disabled_background),
            disabledContentColor = colorResource(R.color.lap_button_disabled_text)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isTracking) 6.dp else 0.dp)
    ) {
        Text(
            text = stringResource(R.string.Lap).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}


@Preview(showBackground = true, name = "Lap Button States")
@Composable
fun PreviewLapButton() {
    ATrainingTrackerTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ACTIVE STATE (Tracking)
            Text("Active (Tracking Mode)", style = MaterialTheme.typography.labelSmall)
            LapButton(
                trackingMode = TrackingMode.TRACKING,
                onClick = {}
            )

            // 2. GHOSTED STATE (Stopped/Paused)
            Text("Ghosted (Stopped/Paused)", style = MaterialTheme.typography.labelSmall)
            LapButton(
                trackingMode = TrackingMode.PAUSED,
                onClick = {}
            )
        }
    }
}