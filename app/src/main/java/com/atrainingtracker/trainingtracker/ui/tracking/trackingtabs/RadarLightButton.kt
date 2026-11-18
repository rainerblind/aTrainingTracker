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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.components.radar.resolveLightVisuals as resolveSharedLightVisuals
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating Cockpit Radar Tail Light Button (REQ-CON-043.2, Governance Rules 53 & 58).
 * Styled as a Material 3 ElevatedButton (height 72.dp, RoundedCornerShape(12.dp)) matching BikeBellButton.
 * Displays live physical radar tail light mode with semantic vector icons, colors, and an AUTO micro-badge.
 * Isolated gesture handling demuxes short tap (debounced cycleNextMode()) from long press (quick-switch sheet).
 */
@Composable
fun RadarLightButton(
    modifier: Modifier = Modifier,
    trackingMode: TrackingMode = TrackingMode.TRACKING,
    lightMode: RadarLightMode = RadarLightMode.OFF,
    isAutoMode: Boolean = true,
    isLightControlAvailable: Boolean = true,
    onShortTap: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val isAvailable = isLightControlAvailable &&
            trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)
    val haptic = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }

    var wasLongPressHandled by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource, isAvailable) {
        if (!isAvailable) return@LaunchedEffect
        var longPressJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    wasLongPressHandled = false
                    longPressJob?.cancel()
                    longPressJob = launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        wasLongPressHandled = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                }
                is PressInteraction.Release -> longPressJob?.cancel()
                is PressInteraction.Cancel -> longPressJob?.cancel()
            }
        }
    }

    val (modeIcon, modeColor) = resolveLightVisuals(lightMode)

    ElevatedButton(
        onClick = {
            if (isAvailable) {
                if (wasLongPressHandled) {
                    wasLongPressHandled = false
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShortTap()
                }
            }
        },
        enabled = isAvailable,
        interactionSource = interactionSource,
        modifier = modifier
            .height(72.dp)
            .testTag("RadarLightButton"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = TTColor.RadarLightContainer,
            contentColor = modeColor,
            disabledContainerColor = colorResource(R.color.lap_button_disabled_background),
            disabledContentColor = colorResource(R.color.lap_button_disabled_text)
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = if (isAvailable) 6.dp else 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        RadarLightButtonContent(
            modeIcon = modeIcon,
            modeColor = modeColor,
            lightMode = lightMode,
            isAvailable = isAvailable,
            isAutoMode = isAutoMode
        )
    }
}

@Composable
private fun RadarLightButtonContent(
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeColor: androidx.compose.ui.graphics.Color,
    lightMode: RadarLightMode,
    isAvailable: Boolean,
    isAutoMode: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = modeIcon,
            contentDescription = stringResource(
                R.string.radar_light_button_content_description,
                stringResource(lightMode.titleResId)
            ),
            modifier = Modifier.size(if (isAutoMode) 30.dp else 36.dp),
            tint = if (isAvailable) modeColor else colorResource(R.color.lap_button_disabled_text)
        )

        if (isAutoMode) {
            Spacer(modifier = Modifier.height(2.dp))
            @Suppress("DEPRECATION")
            Text(
                text = stringResource(R.string.radar_light_auto_badge),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = if (isAvailable) {
                    TTColor.RadarLightBadgeAuto
                } else {
                    colorResource(R.color.lap_button_disabled_text)
                },
                modifier = Modifier.testTag("RadarLightButton_AutoBadge")
            )
        }
    }
}

/**
 * Resolves the canonical semantic vector icon and contrast color for [lightMode] (`REQ-CON-043.2`, `REQ-CON-047.2`).
 */
internal fun resolveLightVisuals(lightMode: RadarLightMode): Pair<ImageVector, Color> =
    resolveSharedLightVisuals(lightMode)

@Preview(showBackground = true, name = "Radar Light Button States")
@Composable
private fun PreviewRadarLightButton() {
    ATrainingTrackerTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Auto (Day Flash)", style = MaterialTheme.typography.labelSmall)
            RadarLightButton(
                trackingMode = TrackingMode.TRACKING,
                lightMode = RadarLightMode.DAY_FLASH,
                isAutoMode = true,
                onShortTap = {}
            )

            Text("Manual (Solid)", style = MaterialTheme.typography.labelSmall)
            RadarLightButton(
                trackingMode = TrackingMode.TRACKING,
                lightMode = RadarLightMode.SOLID,
                isAutoMode = false,
                onShortTap = {}
            )

            Text("Off State", style = MaterialTheme.typography.labelSmall)
            RadarLightButton(
                trackingMode = TrackingMode.TRACKING,
                lightMode = RadarLightMode.OFF,
                isAutoMode = false,
                onShortTap = {}
            )

            Text("Disabled / Disconnected", style = MaterialTheme.typography.labelSmall)
            RadarLightButton(
                trackingMode = TrackingMode.IDLE,
                lightMode = RadarLightMode.OFF,
                isLightControlAvailable = false,
                onShortTap = {}
            )
        }
    }
}
