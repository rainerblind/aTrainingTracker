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

package com.atrainingtracker.trainingtracker.ui.components.radar

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.WbSunny
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
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Shared stateless 2-column compact radar tail light mode selector grid (`REQ-CON-047.2`, `REQ-CON-047.3`,
 * `REQ-CON-048.3`).
 *
 * Displays the optional StVZO compliance notice (`testTag("RadarLightStvzoNotice")`), the `Automatik` tile
 * (`testTag("RadarLightMode_AUTO")`, with optional live active-mode subtitle controlled by
 * [showActiveModeSubtitle]), and ascending-intensity manual [RadarLightMode] tiles
 * (`testTag("RadarLightMode_<MODE>")`) in a row-major 2-column grid.
 */
@Composable
fun RadarLightModeGrid(
    currentMode: RadarLightMode,
    availableModes: List<RadarLightMode>,
    isStvzo: Boolean,
    isAutoMode: Boolean,
    showActiveModeSubtitle: Boolean = true,
    onModeSelected: (RadarLightMode) -> Unit,
    onAutoSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isStvzo) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TTColor.RadarDisconnectedAmber.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("RadarLightStvzoNotice")
            ) {
                Text(
                    text = stringResource(R.string.radar_light_stvzo_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        val activeAutoSubtitle = if (isAutoMode && showActiveModeSubtitle) {
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

        // When availableModes has an even count (e.g. StVZO: OFF, SOLID), Automatik spans row 1
        // and physical modes fill row 2 evenly; when odd (5 modes), Automatik + 5 modes form 3 even 2-col rows.
        if (availableModes.size % 2 == 0) {
            RadarLightModeTile(
                spec = lightTiles.first(),
                modifier = Modifier.fillMaxWidth()
            )
            lightTiles.drop(1).chunked(2).forEach { pair ->
                RadarLightTileRow(pair = pair)
            }
        } else {
            lightTiles.chunked(2).forEach { pair ->
                RadarLightTileRow(pair = pair)
            }
        }
    }
}

/**
 * Resolves the canonical semantic vector icon and contrast color for [lightMode]
 * (`REQ-CON-043.2`, `REQ-CON-047.2`).
 */
fun resolveLightVisuals(lightMode: RadarLightMode): Pair<ImageVector, Color> = when (lightMode) {
    RadarLightMode.OFF -> Pair(Icons.Default.FlashlightOff, TTColor.RadarLightContentOff)
    RadarLightMode.SOLID -> Pair(Icons.Default.FlashlightOn, TTColor.RadarLightContentSolid)
    RadarLightMode.DAY_FLASH -> Pair(Icons.Default.WbSunny, TTColor.RadarLightContentDayFlash)
    RadarLightMode.NIGHT_FLASH -> Pair(Icons.Default.NightlightRound, TTColor.RadarLightContentNightFlash)
    RadarLightMode.PELOTON -> Pair(Icons.Default.Groups, TTColor.RadarLightContentPeloton)
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
private fun RadarLightTileRow(pair: List<LightTileSpec>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pair.forEach { spec ->
            RadarLightModeTile(
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

@Composable
private fun RadarLightModeTile(
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

@Preview(name = "RadarLightModeGrid - Light", showBackground = true)
@Composable
private fun RadarLightModeGridPreviewLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            RadarLightModeGrid(
                currentMode = RadarLightMode.DAY_FLASH,
                availableModes = RadarLightMode.getAvailableModes(false),
                isStvzo = false,
                isAutoMode = true,
                showActiveModeSubtitle = true,
                onModeSelected = {},
                onAutoSelected = {},
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Preview(
    name = "RadarLightModeGrid - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun RadarLightModeGridPreviewDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface {
            RadarLightModeGrid(
                currentMode = RadarLightMode.SOLID,
                availableModes = RadarLightMode.getAvailableModes(false),
                isStvzo = false,
                isAutoMode = false,
                showActiveModeSubtitle = false,
                onModeSelected = {},
                onAutoSelected = {},
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Preview(name = "RadarLightModeGrid - StVZO", showBackground = true)
@Composable
private fun RadarLightModeGridPreviewStvzo() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface {
            RadarLightModeGrid(
                currentMode = RadarLightMode.SOLID,
                availableModes = RadarLightMode.getAvailableModes(true),
                isStvzo = true,
                isAutoMode = true,
                showActiveModeSubtitle = true,
                onModeSelected = {},
                onAutoSelected = {},
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
