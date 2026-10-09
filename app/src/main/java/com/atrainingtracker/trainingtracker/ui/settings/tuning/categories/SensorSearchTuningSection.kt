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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import kotlin.math.roundToInt

/**
 * Encapsulated state and preferences persistence for Sensor Search tuning (ATT-2780).
 */
data class SensorSearchPreferences(
    val numberOfSearchTries: Int = 3,
    val startSearchWhenAppStarts: Boolean = true,
    val startSearchWhenResumeFromPaused: Boolean = true,
    val startSearchWhenUserChangesSport: Boolean = true,
    val startSearchWhenTrackingStarts: Boolean = false,
    val searchOnlyForSportSpecificDevices: Boolean = true,
    val changeSportWhenDeviceGetsLost: Boolean = true
) {
    companion object {
        const val DEFAULT_NUMBER_OF_SEARCH_TRIES = 3

        fun load(context: Context): SensorSearchPreferences {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return SensorSearchPreferences(
                numberOfSearchTries = prefs.getInt(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT, DEFAULT_NUMBER_OF_SEARCH_TRIES),
                startSearchWhenAppStarts = prefs.getBoolean("startSearchWhenAppStarts", true),
                startSearchWhenResumeFromPaused = prefs.getBoolean("startSearchWhenResumeFromPaused", true),
                startSearchWhenUserChangesSport = prefs.getBoolean("startSearchWhenUserChangesSport", true),
                startSearchWhenTrackingStarts = prefs.getBoolean("startSearchWhenTrackingStarts", false),
                searchOnlyForSportSpecificDevices = prefs.getBoolean("searchOnlyForSportSpecificDevices", true),
                changeSportWhenDeviceGetsLost = prefs.getBoolean("changeSportWhenDeviceGetsLost", true)
            )
        }

        fun save(context: Context, prefs: SensorSearchPreferences) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT, prefs.numberOfSearchTries)
                .putBoolean("startSearchWhenAppStarts", prefs.startSearchWhenAppStarts)
                .putBoolean("startSearchWhenResumeFromPaused", prefs.startSearchWhenResumeFromPaused)
                .putBoolean("startSearchWhenUserChangesSport", prefs.startSearchWhenUserChangesSport)
                .putBoolean("startSearchWhenTrackingStarts", prefs.startSearchWhenTrackingStarts)
                .putBoolean("searchOnlyForSportSpecificDevices", prefs.searchOnlyForSportSpecificDevices)
                .putBoolean("changeSportWhenDeviceGetsLost", prefs.changeSportWhenDeviceGetsLost)
                .apply()
        }

        fun reset(context: Context) {
            save(context, SensorSearchPreferences())
        }
    }
}

/**
 * Sensor Search & Behavior tuning category composable (REQ-UI-2780).
 * Encapsulates search round attempts, auto-search triggers, and sensor discovery behaviors.
 */
@Composable
fun SensorSearchTuningSection(
    prefs: SensorSearchPreferences,
    onPrefsChange: (SensorSearchPreferences) -> Unit
) {
    SensorSearchTuningSection(
        numberOfSearchTries = prefs.numberOfSearchTries,
        onNumberOfSearchTriesChange = { onPrefsChange(prefs.copy(numberOfSearchTries = it)) },
        startSearchWhenAppStarts = prefs.startSearchWhenAppStarts,
        onStartSearchWhenAppStartsChange = { onPrefsChange(prefs.copy(startSearchWhenAppStarts = it)) },
        startSearchWhenResumeFromPaused = prefs.startSearchWhenResumeFromPaused,
        onStartSearchWhenResumeFromPausedChange = { onPrefsChange(prefs.copy(startSearchWhenResumeFromPaused = it)) },
        startSearchWhenUserChangesSport = prefs.startSearchWhenUserChangesSport,
        onStartSearchWhenUserChangesSportChange = { onPrefsChange(prefs.copy(startSearchWhenUserChangesSport = it)) },
        startSearchWhenTrackingStarts = prefs.startSearchWhenTrackingStarts,
        onStartSearchWhenTrackingStartsChange = { onPrefsChange(prefs.copy(startSearchWhenTrackingStarts = it)) },
        searchOnlyForSportSpecificDevices = prefs.searchOnlyForSportSpecificDevices,
        onSearchOnlyForSportSpecificDevicesChange = { onPrefsChange(prefs.copy(searchOnlyForSportSpecificDevices = it)) },
        changeSportWhenDeviceGetsLost = prefs.changeSportWhenDeviceGetsLost,
        onChangeSportWhenDeviceGetsLostChange = { onPrefsChange(prefs.copy(changeSportWhenDeviceGetsLost = it)) }
    )
}

@Composable
fun SensorSearchTuningSection(
    numberOfSearchTries: Int,
    onNumberOfSearchTriesChange: (Int) -> Unit,
    startSearchWhenAppStarts: Boolean,
    onStartSearchWhenAppStartsChange: (Boolean) -> Unit,
    startSearchWhenResumeFromPaused: Boolean,
    onStartSearchWhenResumeFromPausedChange: (Boolean) -> Unit,
    startSearchWhenUserChangesSport: Boolean,
    onStartSearchWhenUserChangesSportChange: (Boolean) -> Unit,
    startSearchWhenTrackingStarts: Boolean,
    onStartSearchWhenTrackingStartsChange: (Boolean) -> Unit,
    searchOnlyForSportSpecificDevices: Boolean,
    onSearchOnlyForSportSpecificDevicesChange: (Boolean) -> Unit,
    changeSportWhenDeviceGetsLost: Boolean,
    onChangeSportWhenDeviceGetsLostChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Number of search tries slider
        TuningSliderItem(
            title = stringResource(R.string.prefsNumberOfSearchTriesTitle),
            valueText = numberOfSearchTries.toString(),
            helperText = "",
            defaultText = stringResource(R.string.tuning_default_format, SensorSearchPreferences.DEFAULT_NUMBER_OF_SEARCH_TRIES.toString()),
            value = numberOfSearchTries.toFloat(),
            onValueChange = { onNumberOfSearchTriesChange(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Automatic search triggers
        Text(
            text = stringResource(R.string.prefStartSearchTitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        TuningToggleItem(
            title = stringResource(R.string.prefsStartSearchWhenAppStartsTitle),
            isChecked = startSearchWhenAppStarts,
            onCheckedChange = onStartSearchWhenAppStartsChange
        )

        TuningToggleItem(
            title = stringResource(R.string.prefsStartSearchWhenResumeFromPausedTitle),
            isChecked = startSearchWhenResumeFromPaused,
            onCheckedChange = onStartSearchWhenResumeFromPausedChange
        )

        TuningToggleItem(
            title = stringResource(R.string.prefsStartSearchWhenUserChangesSportTitle),
            isChecked = startSearchWhenUserChangesSport,
            onCheckedChange = onStartSearchWhenUserChangesSportChange
        )

        TuningToggleItem(
            title = stringResource(R.string.prefsStartSearchWhenTrackingStartsTitle),
            isChecked = startSearchWhenTrackingStarts,
            onCheckedChange = onStartSearchWhenTrackingStartsChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Search behavior
        Text(
            text = stringResource(R.string.prefs_search_behavior_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        TuningToggleItem(
            title = stringResource(R.string.prefsSearchOnlyForSportSpecificDevicesTitle),
            isChecked = searchOnlyForSportSpecificDevices,
            onCheckedChange = onSearchOnlyForSportSpecificDevicesChange,
            summary = stringResource(R.string.prefsSearchOnlyForSportSpecificDevicesSummary)
        )

        TuningToggleItem(
            title = stringResource(R.string.prefsChangeSportWhenDeviceGetsLostTitle),
            isChecked = changeSportWhenDeviceGetsLost,
            onCheckedChange = onChangeSportWhenDeviceGetsLostChange,
            summary = stringResource(R.string.prefsChangeSportWhenDeviceGetsLostSummary)
        )
    }
}
