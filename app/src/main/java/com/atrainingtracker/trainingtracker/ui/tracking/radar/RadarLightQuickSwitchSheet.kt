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

package com.atrainingtracker.trainingtracker.ui.tracking.radar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.resolveLightVisuals

/**
 * Modal quick-switch sheet for Garmin Varia BLE radar tail light mode selection
 * and audio snooze (REQ-CON-031.3, REQ-TRK-064.1).
 *
 * Triggered by tapping the top status badge on [RadarEdgeOverlay]. Dynamically prunes
 * flashing modes when StVZO-compliant hardware is connected, and provides 1-tap audio snooze
 * actions across all radar devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarLightQuickSwitchSheet(
    currentMode: RadarLightMode,
    availableModes: List<RadarLightMode>,
    isStvzo: Boolean,
    isAutoMode: Boolean = true,
    isLightControlSupported: Boolean = true,
    snoozeState: RadarAudioSnoozeState = RadarAudioSnoozeState.INACTIVE,
    onModeSelected: (RadarLightMode) -> Unit,
    onAutoSelected: () -> Unit = {},
    onSnoozeSelected: (RadarAudioSnoozeMode) -> Unit = {},
    onResumeAudio: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = if (isLightControlSupported) {
        stringResource(R.string.radar_light_sheet_title)
    } else {
        stringResource(R.string.radar_snooze_title)
    }

    AppModalBottomSheet(
        title = titleText,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("RadarLightQuickSwitchSheet")
    ) {
        RadarLightQuickSwitchSheetContent(
            currentMode = currentMode,
            availableModes = availableModes,
            isStvzo = isStvzo,
            isAutoMode = isAutoMode,
            isLightControlSupported = isLightControlSupported,
            snoozeState = snoozeState,
            onModeSelected = onModeSelected,
            onAutoSelected = onAutoSelected,
            onSnoozeSelected = onSnoozeSelected,
            onResumeAudio = onResumeAudio,
            onDismiss = onDismiss
        )
    }
}

/**
 * Visual content for [RadarLightQuickSwitchSheet] (REQ-CON-031.3).
 */
@Suppress("LongMethod")
@Composable
fun RadarLightQuickSwitchSheetContent(
    currentMode: RadarLightMode,
    availableModes: List<RadarLightMode>,
    isStvzo: Boolean,
    isAutoMode: Boolean = true,
    isLightControlSupported: Boolean = true,
    snoozeState: RadarAudioSnoozeState = RadarAudioSnoozeState.INACTIVE,
    onModeSelected: (RadarLightMode) -> Unit,
    onAutoSelected: () -> Unit = {},
    onSnoozeSelected: (RadarAudioSnoozeMode) -> Unit = {},
    onResumeAudio: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLightControlSupported) {
            if (isStvzo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TTColor.RadarDisconnectedAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.radar_light_stvzo_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Unified 2-column compact tile grid (REQ-CON-047.1, REQ-CON-047.2, REQ-CON-047.3):
            // Automatik first, followed by ascending-intensity physical modes.
            // When availableModes has an even count (e.g. StVZO: OFF, SOLID), Automatik spans row 1
            // and the modes fill row 2 evenly; when odd (5 modes), Automatik + 5 modes form 3 even 2-col rows.
            val activeAutoSubtitle = if (isAutoMode) {
                stringResource(R.string.radar_light_active_mode_format, stringResource(currentMode.titleResId))
            } else {
                null
            }
            val lightTiles = buildList {
                add(
                    LightTileSpec(
                        testTag = "RadarLightMode_AUTO",
                        label = stringResource(R.string.radar_light_control_mode_smart),
                        subtitle = activeAutoSubtitle,
                        icon = Icons.Default.AutoAwesome,
                        iconTint = TTColor.RadarLightBadgeAuto,
                        isSelected = isAutoMode,
                        onClick = onAutoSelected
                    )
                )
                availableModes.forEach { mode ->
                    val (modeIcon, modeColor) = resolveLightVisuals(mode)
                    add(
                        LightTileSpec(
                            testTag = "RadarLightMode_${mode.name}",
                            label = stringResource(mode.titleResId),
                            subtitle = null,
                            icon = modeIcon,
                            iconTint = modeColor,
                            isSelected = !isAutoMode && mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                    )
                }
            }

            if (availableModes.size % 2 == 0) {
                RadarQuickSwitchLightTile(
                    spec = lightTiles.first(),
                    modifier = Modifier.fillMaxWidth()
                )
                lightTiles.drop(1).chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { spec ->
                            RadarQuickSwitchLightTile(
                                spec = spec,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                lightTiles.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { spec ->
                            RadarQuickSwitchLightTile(
                                spec = spec,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        }

        // Audio Snooze Section (REQ-TRK-064.1, REQ-CON-047.1)
        Text(
            text = stringResource(R.string.radar_snooze_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )

        if (snoozeState.isSnoozed) {
            Button(
                onClick = {
                    onResumeAudio()
                    onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("RadarSnooze_Resume")
            ) {
                Text(text = stringResource(R.string.radar_snooze_resume))
            }
        }

        val snoozeOptions = listOf(
            Pair(RadarAudioSnoozeMode.SNOOZE_15M, R.string.radar_snooze_15m),
            Pair(RadarAudioSnoozeMode.SNOOZE_30M, R.string.radar_snooze_30m),
            Pair(RadarAudioSnoozeMode.FAST_ONLY, R.string.radar_snooze_fast_only),
            Pair(RadarAudioSnoozeMode.REST_OF_RIDE, R.string.radar_snooze_rest_of_ride)
        )

        snoozeOptions.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (mode, stringResId) ->
                    val isSelected = snoozeState.isSnoozed && snoozeState.mode == mode
                    RadarQuickSwitchSnoozeTile(
                        label = stringResource(stringResId),
                        isSelected = isSelected,
                        onClick = {
                            onSnoozeSelected(mode)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("RadarSnooze_${mode.name}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private data class LightTileSpec(
    val testTag: String,
    val label: String,
    val subtitle: String?,
    val icon: ImageVector,
    val iconTint: Color,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun RadarQuickSwitchLightTile(
    spec: LightTileSpec,
    modifier: Modifier = Modifier
) {
    val containerColor = if (spec.isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (spec.isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (spec.isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .testTag(spec.testTag)
            .semantics(mergeDescendants = true) { selected = spec.isSelected }
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton) { spec.onClick() },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (spec.isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TTColor.RadarLightContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = if (spec.isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (spec.subtitle != null) {
                    Text(
                        text = spec.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spec.isSelected) {
                            contentColor.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarQuickSwitchSnoozeTile(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) { selected = isSelected }
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton) { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(name = "RadarLightQuickSwitchSheet - Light", showBackground = true)
@Composable
private fun RadarLightQuickSwitchSheetPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            RadarLightQuickSwitchSheetContent(
                currentMode = RadarLightMode.DAY_FLASH,
                availableModes = RadarLightMode.getAvailableModes(false),
                isStvzo = false,
                isAutoMode = true,
                isLightControlSupported = true,
                onModeSelected = {},
                onAutoSelected = {}
            )
        }
    }
}

@Preview(
    name = "RadarLightQuickSwitchSheet - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun RadarLightQuickSwitchSheetPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            RadarLightQuickSwitchSheetContent(
                currentMode = RadarLightMode.SOLID,
                availableModes = RadarLightMode.getAvailableModes(false),
                isStvzo = false,
                isAutoMode = false,
                isLightControlSupported = true,
                onModeSelected = {},
                onAutoSelected = {}
            )
        }
    }
}

@Preview(name = "RadarLightQuickSwitchSheet - StVZO", showBackground = true)
@Composable
private fun RadarLightQuickSwitchSheetPreviewStvzo() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            RadarLightQuickSwitchSheetContent(
                currentMode = RadarLightMode.SOLID,
                availableModes = RadarLightMode.getAvailableModes(true),
                isStvzo = true,
                isAutoMode = true,
                isLightControlSupported = true,
                onModeSelected = {},
                onAutoSelected = {}
            )
        }
    }
}
