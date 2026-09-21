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

package com.atrainingtracker.trainingtracker.ui.settings.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetContent
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import kotlin.math.roundToInt

/**
 * Bottom sheet composable for configuring sensor search retry attempts, automatic search triggers,
 * and search behaviors.
 *
 * Architectural Role:
 * - Replaces the legacy full-screen [SearchSettingsFragment] outlier with an [AppBottomSheetContent] dialog.
 * - Provides search retry slider (1..5) with dynamic value badge.
 * - Configures automatic search triggers (App starts, resume from paused, sport changes, tracking starts).
 * - Configures search behaviors (sport-specific sensor filtering, sport changes on device lost).
 * - Integrates standard [AppDialogActions.SaveCancel] to stage modifications transactionally.
 *
 * @param onDismiss Callback invoked to dismiss the modal bottom sheet dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var numberOfSearchTries by remember {
        mutableIntStateOf(TrainingApplication.getNumberOfSearchTries())
    }
    var startSearchWhenAppStarts by remember {
        mutableStateOf(TrainingApplication.startSearchWhenAppStarts())
    }
    var startSearchWhenResumeFromPaused by remember {
        mutableStateOf(TrainingApplication.startSearchWhenResumeFromPaused())
    }
    var startSearchWhenUserChangesSport by remember {
        mutableStateOf(TrainingApplication.startSearchWhenUserChangesSport())
    }
    var startSearchWhenTrackingStarts by remember {
        mutableStateOf(TrainingApplication.startSearchWhenTrackingStarts())
    }
    var searchOnlyForSportSpecificDevices by remember {
        mutableStateOf(TrainingApplication.searchOnlyForSportSpecificDevices())
    }
    var changeSportWhenDeviceGetsLost by remember {
        mutableStateOf(TrainingApplication.changeSportWhenDeviceGetsLost())
    }

    AppBottomSheetContent(
        title = stringResource(R.string.Search_Settings),
        iconPainter = painterResource(id = R.drawable.ic_search),
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    prefs.edit()
                        .putInt(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT, numberOfSearchTries)
                        .putBoolean("startSearchWhenAppStarts", startSearchWhenAppStarts)
                        .putBoolean("startSearchWhenResumeFromPaused", startSearchWhenResumeFromPaused)
                        .putBoolean("startSearchWhenUserChangesSport", startSearchWhenUserChangesSport)
                        .putBoolean("startSearchWhenTrackingStarts", startSearchWhenTrackingStarts)
                        .putBoolean("searchOnlyForSportSpecificDevices", searchOnlyForSportSpecificDevices)
                        .putBoolean("changeSportWhenDeviceGetsLost", changeSportWhenDeviceGetsLost)
                        .apply()
                    onDismiss()
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Number of search tries
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.prefsNumberOfSearchTriesTitle),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = numberOfSearchTries.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Slider(
                    value = numberOfSearchTries.toFloat(),
                    onValueChange = { numberOfSearchTries = it.roundToInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            // Automatic Search Triggers Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.prefStartSearchTitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                SearchSettingToggleRow(
                    title = stringResource(R.string.prefsStartSearchWhenAppStartsTitle),
                    checked = startSearchWhenAppStarts,
                    onCheckedChange = { startSearchWhenAppStarts = it }
                )

                SearchSettingToggleRow(
                    title = stringResource(R.string.prefsStartSearchWhenResumeFromPausedTitle),
                    checked = startSearchWhenResumeFromPaused,
                    onCheckedChange = { startSearchWhenResumeFromPaused = it }
                )

                SearchSettingToggleRow(
                    title = stringResource(R.string.prefsStartSearchWhenUserChangesSportTitle),
                    checked = startSearchWhenUserChangesSport,
                    onCheckedChange = { startSearchWhenUserChangesSport = it }
                )

                SearchSettingToggleRow(
                    title = stringResource(R.string.prefsStartSearchWhenTrackingStartsTitle),
                    checked = startSearchWhenTrackingStarts,
                    onCheckedChange = { startSearchWhenTrackingStarts = it }
                )
            }

            HorizontalDivider()

            // Search Behavior Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.prefs_search_behavior_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                SearchSettingToggleRow(
                    title = stringResource(R.string.prefsSearchOnlyForSportSpecificDevicesTitle),
                    summary = stringResource(R.string.prefsSearchOnlyForSportSpecificDevicesSummary),
                    checked = searchOnlyForSportSpecificDevices,
                    onCheckedChange = { searchOnlyForSportSpecificDevices = it }
                )

                SearchSettingToggleRow(
                    title = stringResource(R.string.prefsChangeSportWhenDeviceGetsLostTitle),
                    summary = stringResource(R.string.prefsChangeSportWhenDeviceGetsLostSummary),
                    checked = changeSportWhenDeviceGetsLost,
                    onCheckedChange = { changeSportWhenDeviceGetsLost = it }
                )
            }
        }
    }
}

@Composable
private fun SearchSettingToggleRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}
