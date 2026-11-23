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

package com.atrainingtracker.trainingtracker.ui.settings.zones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitDarkTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView

/**
 * Shared live theme-aware [SensorFieldView] preview tile card used across HR/Power
 * ([SettingsScreenContent]) and HRV DFA a1 ([HrvDfaA1SettingsContent]) zone configuration tabs
 * (REQ-UI-2802 / ATT-8130).
 */
@Composable
fun ZoneLivePreviewTileCard(
    fieldState: SensorFieldState,
    modifier: Modifier = Modifier
) {
    val isSystemDark = isSystemInDarkTheme()
    val cockpitThemeMode = remember {
        try {
            TrainingApplication.getCockpitThemeMode()
        } catch (_: Exception) {
            CockpitThemeMode.SYSTEM
        }
    }
    val isCockpitDark = resolveEffectiveCockpitDarkTheme(cockpitThemeMode, isSystemDark)

    Box(
        modifier = resolveZonePreviewContainerModifier(modifier)
    ) {
        ATrainingTrackerTheme(
            darkTheme = isCockpitDark,
            amoled = isCockpitDark,
            setWindowColors = false
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                SensorFieldView(
                    fieldState = fieldState,
                    modifier = Modifier.fillMaxSize(),
                    screenMode = ScreenMode.TRACKING
                )
            }
        }
    }
}

/**
 * Modifier applied to the container wrapping the live preview [SensorFieldView] in [SettingsScreenContent].
 *
 * Applies [IntrinsicSize.Min] bounding to prevent vertical collapsing under scrollable parent containers
 * (`REQ-UI-441` / `ATT-5071`, Rule 67, Design Guideline 5.14).
 */
fun resolveZonePreviewContainerModifier(baseModifier: Modifier = Modifier): Modifier =
    baseModifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .padding(vertical = 4.dp)


/**
 * Compact zone-tinted bordered selector pill for the live zone preview row (`REQ-UI-384` / Rule 38).
 * Replaces Material 3 `FilterChip` + `leadingIcon` so that all 5 (HR) or 7 (Cycling Power) zones
 * render on a single line without vertical character wrapping (`"Z\n1"`..`"Z\n7"`).
 */
@Composable
fun ZonePreviewPill(
    label: String,
    zoneColor: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(8.dp),
        color = zoneColor.copy(alpha = if (selected) 0.28f else 0.12f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) zoneColor else zoneColor.copy(alpha = 0.6f)
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ZoneDisplayToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


@Composable
fun ZoneRow(
    zoneLabel: String,
    minValue: Int,
    maxValue: Int,
    onMinChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit,
    minEnabled: Boolean = true,
    maxEnabled: Boolean = true,
    minAllowed: Int? = null,
    maxAllowed: Int? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface
) {
    Card(
        // We use the containerColor with low alpha for the card body
        colors = CardDefaults.cardColors(
            containerColor = containerColor.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        // Row with IntrinsicSize.Min allows the Spacer to match the Column height
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {

            // 1. The Indicator Strip (Solid Color)
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(containerColor)
            )

            // 2. The Content
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = zoneLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Min Field
                    Box(modifier = Modifier.weight(1f)) {
                        IntegerInputField(
                            label = stringResource(R.string.label_min),
                            currentValue = minValue,
                            onValueChange = onMinChange,
                            enabled = minEnabled
                        )
                    }
                    // Max Field
                    Box(modifier = Modifier.weight(1f)) {
                        IntegerInputField(
                            label = stringResource(R.string.label_max),
                            currentValue = maxValue,
                            onValueChange = onMaxChange,
                            enabled = maxEnabled,
                            minAllowed = minAllowed,
                            maxAllowed = maxAllowed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IntegerInputField(
    label: String,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    minAllowed: Int? = null,
    maxAllowed: Int? = null
) {
    var textState by remember(currentValue) { mutableStateOf(currentValue.toString()) }
    val isOutOfRange = (minAllowed != null && currentValue < minAllowed) || (maxAllowed != null && currentValue > maxAllowed)

    OutlinedTextField(
        value = if (enabled) textState else "--",
        onValueChange = { input ->
            if (input.all { it.isDigit() }) {
                textState = input
                if (input.isNotEmpty()) {
                    try {
                        onValueChange(input.toInt())
                    } catch (e: NumberFormatException) { }
                } else {
                    onValueChange(0)
                }
            }
        },
        label = { Text(label) },
        enabled = enabled,
        readOnly = !enabled,
        isError = enabled && isOutOfRange,
        supportingText = if (enabled && isOutOfRange && minAllowed != null && maxAllowed != null) {
            {
                Text(
                    text = stringResource(R.string.zone_calc_error_monotonicity, minAllowed, maxAllowed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

