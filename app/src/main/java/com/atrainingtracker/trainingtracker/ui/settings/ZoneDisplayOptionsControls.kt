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

package com.atrainingtracker.trainingtracker.ui.settings

import androidx.annotation.StringRes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsSnapshot
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions

/**
 * Target athletic zone profiles that support configurable [ZoneDisplayOptions]
 * in both `ZonesSettingsActivity` and Cockpit & Tiles tuning (`REQ-UI-2783`, `ATT-8498`).
 */
enum class ZoneProfileTarget(@param:StringRes val labelResId: Int) {
    HR_RUN(R.string.tab_hr_run),
    HR_BIKE(R.string.tab_hr_bike),
    PWR_BIKE(R.string.tab_pwr_bike),
    HRV_DFA_A1(R.string.tab_hrv_dfa_a1)
}

/**
 * Immutable draft container holding uncommitted [ZoneDisplayOptions] edits across all four
 * zone profiles inside `AdvancedTuningScreen` and `AdvancedTuningDialog` (`REQ-UI-2783.3`).
 */
data class ZoneDisplayOptionsDraft(
    val hrRun: ZoneDisplayOptions = ZoneDisplayOptions(),
    val hrBike: ZoneDisplayOptions = ZoneDisplayOptions(),
    val pwrBike: ZoneDisplayOptions = ZoneDisplayOptions(),
    val hrv: ZoneDisplayOptions = ZoneDisplayOptions()
) {
    /** Returns the [ZoneDisplayOptions] for [target]. */
    fun optionsFor(target: ZoneProfileTarget): ZoneDisplayOptions = when (target) {
        ZoneProfileTarget.HR_RUN -> hrRun
        ZoneProfileTarget.HR_BIKE -> hrBike
        ZoneProfileTarget.PWR_BIKE -> pwrBike
        ZoneProfileTarget.HRV_DFA_A1 -> hrv
    }

    /** Returns a copy of this draft with [target] replaced by [updated]. */
    fun withUpdated(target: ZoneProfileTarget, updated: ZoneDisplayOptions): ZoneDisplayOptionsDraft =
        when (target) {
            ZoneProfileTarget.HR_RUN -> copy(hrRun = updated)
            ZoneProfileTarget.HR_BIKE -> copy(hrBike = updated)
            ZoneProfileTarget.PWR_BIKE -> copy(pwrBike = updated)
            ZoneProfileTarget.HRV_DFA_A1 -> copy(hrv = updated)
        }

    /**
     * Copies `showBackground`, `showLeftBar`, `showRightBar`, and `showTextColor` from
     * [sourceTarget] to all four zone profiles while preserving `hrv.showGauge` (`REQ-UI-2783.1`).
     */
    fun applyToAllFrom(sourceTarget: ZoneProfileTarget): ZoneDisplayOptionsDraft {
        val source = optionsFor(sourceTarget)
        val sharedFlags = ZoneDisplayOptions(
            showBackground = source.showBackground,
            showLeftBar = source.showLeftBar,
            showRightBar = source.showRightBar,
            showTextColor = source.showTextColor,
            showGauge = false
        )
        return ZoneDisplayOptionsDraft(
            hrRun = sharedFlags,
            hrBike = sharedFlags,
            pwrBike = sharedFlags,
            hrv = sharedFlags.copy(showGauge = hrv.showGauge)
        )
    }

    /** Persists all four profile display option sets to [dataStore] without altering thresholds. */
    suspend fun persistTo(dataStore: SettingsDataStore) {
        dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.HR_RUN, hrRun)
        dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.HR_BIKE, hrBike)
        dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.PWR_BIKE, pwrBike)
        dataStore.saveHrvZoneDisplayOptions(hrv)
    }

    companion object {
        /** Hydrates a [ZoneDisplayOptionsDraft] from an immutable [SettingsSnapshot]. */
        fun fromSnapshot(snapshot: SettingsSnapshot): ZoneDisplayOptionsDraft =
            ZoneDisplayOptionsDraft(
                hrRun = snapshot.hrRunDisplayOptions,
                hrBike = snapshot.hrBikeDisplayOptions,
                pwrBike = snapshot.pwrBikeDisplayOptions,
                hrv = snapshot.hrvDisplayOptions
            )
    }
}

/**
 * Shared [ZoneDisplayOptions] toggle list supporting host-specific row styling via [toggleRow]
 * (`ZoneDisplayToggleRow` in `ZonesSettingsActivity`, `TuningToggleItem` in tuning categories)
 * (`REQ-UI-2783.2`).
 */
@Composable
fun ZoneDisplayOptionsToggleList(
    displayOptions: ZoneDisplayOptions,
    onUpdateDisplayOptions: (ZoneDisplayOptions) -> Unit,
    showGaugeToggle: Boolean = false,
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    toggleRow: @Composable (label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) -> Unit
) {
    toggleRow(
        stringResource(R.string.zone_display_background),
        displayOptions.showBackground
    ) { onUpdateDisplayOptions(displayOptions.copy(showBackground = it)) }
    HorizontalDivider(color = dividerColor)

    toggleRow(
        stringResource(R.string.zone_display_left_bar),
        displayOptions.showLeftBar
    ) { onUpdateDisplayOptions(displayOptions.copy(showLeftBar = it)) }
    HorizontalDivider(color = dividerColor)

    toggleRow(
        stringResource(R.string.zone_display_right_bar),
        displayOptions.showRightBar
    ) { onUpdateDisplayOptions(displayOptions.copy(showRightBar = it)) }
    HorizontalDivider(color = dividerColor)

    toggleRow(
        stringResource(R.string.zone_display_text_color),
        displayOptions.showTextColor
    ) { onUpdateDisplayOptions(displayOptions.copy(showTextColor = it)) }

    if (showGaugeToggle) {
        HorizontalDivider(color = dividerColor)
        toggleRow(
            stringResource(R.string.zone_display_gauge),
            displayOptions.showGauge
        ) { onUpdateDisplayOptions(displayOptions.copy(showGauge = it)) }
    }
}
