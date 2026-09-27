/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.display

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication

import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.batterysaver.DisplayBrightnessMode
import kotlin.math.roundToInt

@Composable
fun DisplaySettingsDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: (() -> Unit)? = null
) {
    var currentOptions by remember { 
        mutableStateOf(TrainingApplication.getDisplayOptions().toSet())
    }
    var currentThemeMode by remember {
        mutableStateOf(TrainingApplication.getCockpitThemeMode())
    }
    var currentBrightnessMode by remember {
        mutableStateOf(TrainingApplication.getDisplayBrightnessMode())
    }
    var currentCustomBrightness by remember {
        mutableStateOf(TrainingApplication.getCustomDisplayBrightness())
    }
    
    AppBottomSheetContent(
        title = stringResource(R.string.Display),
        icon = Icons.Default.DisplaySettings,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    TrainingApplication.setDisplayOptions(currentOptions)
                    TrainingApplication.setCockpitThemeMode(currentThemeMode)
                    TrainingApplication.setDisplayBrightnessMode(currentBrightnessMode)
                    TrainingApplication.setCustomDisplayBrightness(currentCustomBrightness)
                    onSettingsChanged?.invoke()
                    onDismiss()
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DisplayOptionToggle(
                    label = stringResource(R.string.forcePortrait),
                    isChecked = currentOptions.contains("forcePortrait"),
                    onCheckedChange = { checked ->
                        currentOptions = if (checked) currentOptions + "forcePortrait" else currentOptions - "forcePortrait"
                    }
                )
                DisplayOptionToggle(
                    label = stringResource(R.string.prefsKeepScreenOnTitle),
                    isChecked = currentOptions.contains("keepScreenOn"),
                    onCheckedChange = { checked ->
                        currentOptions = if (checked) currentOptions + "keepScreenOn" else currentOptions - "keepScreenOn"
                    }
                )
                DisplayOptionToggle(
                    label = stringResource(R.string.prefsNoUnlockingTitle),
                    isChecked = currentOptions.contains("noUnlocking"),
                    onCheckedChange = { checked ->
                        currentOptions = if (checked) currentOptions + "noUnlocking" else currentOptions - "noUnlocking"
                    }
                )
            }

            HorizontalDivider()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_category_savings),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_category_savings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cockpit_theme_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.cockpit_theme_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = currentThemeMode == CockpitThemeMode.SYSTEM,
                            onClick = { currentThemeMode = CockpitThemeMode.SYSTEM },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { SegmentedButtonDefaults.Icon(active = currentThemeMode == CockpitThemeMode.SYSTEM) }
                        ) {
                            Text(stringResource(R.string.cockpit_theme_system))
                        }
                        SegmentedButton(
                            selected = currentThemeMode == CockpitThemeMode.ALWAYS_DARK,
                            onClick = { currentThemeMode = CockpitThemeMode.ALWAYS_DARK },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { SegmentedButtonDefaults.Icon(active = currentThemeMode == CockpitThemeMode.ALWAYS_DARK) }
                        ) {
                            Text(stringResource(R.string.cockpit_theme_always_dark))
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.display_brightness_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val brightnessDesc = when (currentBrightnessMode) {
                        DisplayBrightnessMode.SYSTEM -> stringResource(R.string.display_brightness_system_desc)
                        DisplayBrightnessMode.AUTO -> stringResource(R.string.display_brightness_auto_desc)
                        DisplayBrightnessMode.CUSTOM -> stringResource(R.string.display_brightness_custom_desc)
                    }
                    Text(
                        text = brightnessDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = currentBrightnessMode == DisplayBrightnessMode.SYSTEM,
                            onClick = { currentBrightnessMode = DisplayBrightnessMode.SYSTEM },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = { SegmentedButtonDefaults.Icon(active = currentBrightnessMode == DisplayBrightnessMode.SYSTEM) }
                        ) {
                            Text(stringResource(R.string.display_brightness_system))
                        }
                        SegmentedButton(
                            selected = currentBrightnessMode == DisplayBrightnessMode.AUTO,
                            onClick = { currentBrightnessMode = DisplayBrightnessMode.AUTO },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = { SegmentedButtonDefaults.Icon(active = currentBrightnessMode == DisplayBrightnessMode.AUTO) }
                        ) {
                            Text(stringResource(R.string.display_brightness_auto))
                        }
                        SegmentedButton(
                            selected = currentBrightnessMode == DisplayBrightnessMode.CUSTOM,
                            onClick = { currentBrightnessMode = DisplayBrightnessMode.CUSTOM },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = { SegmentedButtonDefaults.Icon(active = currentBrightnessMode == DisplayBrightnessMode.CUSTOM) }
                        ) {
                            Text(stringResource(R.string.display_brightness_custom))
                        }
                    }

                    if (currentBrightnessMode == DisplayBrightnessMode.CUSTOM) {
                        val percent = (currentCustomBrightness * 100f).roundToInt()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.display_brightness_slider_format, percent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = currentCustomBrightness,
                                onValueChange = { currentCustomBrightness = it },
                                valueRange = 0.05f..1.0f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayOptionToggle(
    label: String, 
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.7f)
        )
    }
}
