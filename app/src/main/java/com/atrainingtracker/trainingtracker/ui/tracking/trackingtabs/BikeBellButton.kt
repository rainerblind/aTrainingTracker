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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.launch

/**
 * Floating Cockpit Bike Bell Button (REQ-UI-421.2).
 * Styled as a Material 3 ElevatedButton (height 72.dp, RoundedCornerShape(12.dp)) matching LapButton.
 * Features rotation shake animation on ring, tap for double-strike ("Ding-Ding!"),
 * and long-press for urgent continuous striker roll ("Brriiiing!").
 */
@Composable
fun BikeBellButton(
    modifier: Modifier = Modifier,
    trackingMode: TrackingMode = TrackingMode.TRACKING,
    onSingleRing: () -> Unit,
    onContinuousRingStart: () -> Unit = {},
    onContinuousRingStop: () -> Unit = {}
) {
    val isTracking = trackingMode == TrackingMode.TRACKING
    val coroutineScope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    fun triggerShakeAnimation() {
        coroutineScope.launch {
            rotation.snapTo(0f)
            rotation.animateTo(-15f, animationSpec = tween(40))
            rotation.animateTo(15f, animationSpec = tween(60))
            rotation.animateTo(-10f, animationSpec = tween(50))
            rotation.animateTo(10f, animationSpec = tween(50))
            rotation.animateTo(0f, animationSpec = tween(40))
        }
    }

    ElevatedButton(
        onClick = {
            if (isTracking) {
                triggerShakeAnimation()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSingleRing()
            }
        },
        enabled = isTracking,
        modifier = modifier
            .height(72.dp)
            .pointerInput(isTracking) {
                if (!isTracking) return@pointerInput
                var isLongPress = false
                detectTapGestures(
                    onPress = {
                        isLongPress = false
                        val released = tryAwaitRelease()
                        if (isLongPress) {
                            onContinuousRingStop()
                        }
                    },
                    onTap = {
                        triggerShakeAnimation()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSingleRing()
                    },
                    onLongPress = {
                        isLongPress = true
                        triggerShakeAnimation()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onContinuousRingStart()
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = colorResource(R.color.lap_button_disabled_background),
            disabledContentColor = colorResource(R.color.lap_button_disabled_text)
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = if (isTracking) 6.dp else 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(R.string.config_tracking__showBikeBell),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = rotation.value },
                tint = if (isTracking)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    colorResource(R.color.lap_button_disabled_text)
            )
        }
    }
}

@Preview(showBackground = true, name = "Bike Bell Button States")
@Composable
fun PreviewBikeBellButton() {
    ATrainingTrackerTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Active (Tracking Mode)", style = MaterialTheme.typography.labelSmall)
            BikeBellButton(
                trackingMode = TrackingMode.TRACKING,
                onSingleRing = {}
            )

            Text("Ghosted (Stopped/Paused)", style = MaterialTheme.typography.labelSmall)
            BikeBellButton(
                trackingMode = TrackingMode.PAUSED,
                onSingleRing = {}
            )
        }
    }
}
