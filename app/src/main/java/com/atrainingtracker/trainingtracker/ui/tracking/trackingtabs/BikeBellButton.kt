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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Floating Cockpit Bike Bell Button (REQ-UI-421.2, Governance Rules 53 & 58).
 * Styled as a Material 3 ElevatedButton (height 72.dp, RoundedCornerShape(12.dp)) matching LapButton.
 * Enabled across READY, TRACKING, and PAUSED modes so cyclists can test or ring the bell
 * while stationary or before pressing Start.
 * Features rotation shake animation on ring, continuous looping shake on press-and-hold,
 * haptic feedback, and isolated onClick tap handling without nested pointer input contention.
 */
@Composable
fun BikeBellButton(
    modifier: Modifier = Modifier,
    trackingMode: TrackingMode = TrackingMode.TRACKING,
    onSingleRing: () -> Unit,
    onContinuousRingStart: () -> Unit = {},
    onContinuousRingStop: () -> Unit = {}
) {
    val isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)
    val coroutineScope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }

    var isContinuousRingActive by remember { mutableStateOf(false) }
    var wasLongPressHandled by remember { mutableStateOf(false) }

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

    LaunchedEffect(interactionSource, isAvailable) {
        if (!isAvailable) {
            if (isContinuousRingActive) {
                isContinuousRingActive = false
                onContinuousRingStop()
                rotation.snapTo(0f)
            }
            return@LaunchedEffect
        }
        var longPressJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    wasLongPressHandled = false
                    longPressJob?.cancel()
                    longPressJob = launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        wasLongPressHandled = true
                        isContinuousRingActive = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onContinuousRingStart()
                        while (isActive) {
                            rotation.animateTo(-15f, animationSpec = tween(40))
                            rotation.animateTo(15f, animationSpec = tween(60))
                            rotation.animateTo(-10f, animationSpec = tween(50))
                            rotation.animateTo(10f, animationSpec = tween(50))
                        }
                    }
                }
                is PressInteraction.Release -> {
                    longPressJob?.cancel()
                    if (isContinuousRingActive) {
                        isContinuousRingActive = false
                        onContinuousRingStop()
                        coroutineScope.launch {
                            rotation.animateTo(0f, animationSpec = tween(40))
                        }
                    }
                }
                is PressInteraction.Cancel -> {
                    longPressJob?.cancel()
                    if (isContinuousRingActive) {
                        isContinuousRingActive = false
                        onContinuousRingStop()
                        coroutineScope.launch {
                            rotation.animateTo(0f, animationSpec = tween(40))
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isContinuousRingActive) {
                isContinuousRingActive = false
                onContinuousRingStop()
            }
        }
    }

    ElevatedButton(
        onClick = {
            if (isAvailable) {
                if (wasLongPressHandled) {
                    wasLongPressHandled = false
                } else {
                    triggerShakeAnimation()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSingleRing()
                }
            }
        },
        enabled = isAvailable,
        interactionSource = interactionSource,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = colorResource(R.color.lap_button_disabled_background),
            disabledContentColor = colorResource(R.color.lap_button_disabled_text)
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = if (isAvailable) 6.dp else 0.dp,
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
                tint = if (isAvailable)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    colorResource(R.color.lap_button_disabled_text)
            )
        }
    }
}

@Preview(showBackground = true, name = "Bike Bell Button States")
@Composable
private fun PreviewBikeBellButton() {
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

            Text("Active (Ready Mode)", style = MaterialTheme.typography.labelSmall)
            BikeBellButton(
                trackingMode = TrackingMode.READY,
                onSingleRing = {}
            )

            Text("Active (Paused Mode)", style = MaterialTheme.typography.labelSmall)
            BikeBellButton(
                trackingMode = TrackingMode.PAUSED,
                onSingleRing = {}
            )

            Text("Ghosted (Idle Mode)", style = MaterialTheme.typography.labelSmall)
            BikeBellButton(
                trackingMode = TrackingMode.IDLE,
                onSingleRing = {}
            )
        }
    }
}
