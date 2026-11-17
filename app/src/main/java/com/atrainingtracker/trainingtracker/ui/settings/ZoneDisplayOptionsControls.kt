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

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsSnapshot
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Target athletic zone profiles that support configurable [ZoneDisplayOptions]
 * across `ZonesSettingsActivity`, sensor tile configuration, and tracking tabs configuration
 * (`REQ-UI-2783`, `ATT-8498`).
 */
enum class ZoneProfileTarget(@param:StringRes val labelResId: Int) {
    HR_RUN(R.string.tab_hr_run),
    HR_BIKE(R.string.tab_hr_bike),
    PWR_BIKE(R.string.tab_pwr_bike),
    HRV_DFA_A1(R.string.tab_hrv_dfa_a1)
}

/**
 * Maps a `(sensorType, sportType)` pair 1:1 with `TrackingViewModel.resolveDisplayOptions`
 * to its backing [ZoneProfileTarget], or returns `null` when zone display highlighting
 * does not apply (`REQ-UI-2783.2`).
 */
fun resolveZoneProfileTarget(
    sensorType: SensorType?,
    sportType: BSportType
): ZoneProfileTarget? = when (sensorType) {
    SensorType.HRV_DFA_A1 -> ZoneProfileTarget.HRV_DFA_A1
    SensorType.HR -> when (sportType) {
        BSportType.RUN -> ZoneProfileTarget.HR_RUN
        BSportType.BIKE -> ZoneProfileTarget.HR_BIKE
        else -> null
    }
    SensorType.POWER -> when (sportType) {
        BSportType.BIKE -> ZoneProfileTarget.PWR_BIKE
        else -> null
    }
    else -> null
}

/**
 * Returns the default [ZoneProfileTarget] to pre-select when configuring tracking tabs
 * for [activityType] (`REQ-UI-2783.3`).
 */
fun defaultZoneProfileTarget(activityType: ActivityType): ZoneProfileTarget = when (activityType) {
    ActivityType.RUN_SPEED_AND_CADENCE -> ZoneProfileTarget.HR_RUN
    ActivityType.BIKE_SPEED_AND_CADENCE -> ZoneProfileTarget.HR_BIKE
    ActivityType.BIKE_POWER -> ZoneProfileTarget.PWR_BIKE
    ActivityType.GENERIC_HR -> ZoneProfileTarget.HRV_DFA_A1
}

/**
 * Immutable draft container holding uncommitted [ZoneDisplayOptions] edits across all four
 * zone profiles (`REQ-UI-2783.2`, `REQ-UI-2783.3`).
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
     * [sourceTarget] to all four zone profiles while preserving `hrv.showGauge` (`REQ-UI-2783`).
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

    /** Persists all four profile display option sets atomically to [dataStore] without altering thresholds. */
    suspend fun persistTo(dataStore: SettingsDataStore) {
        dataStore.saveAllZoneDisplayOptions(
            hrRun = hrRun,
            hrBike = hrBike,
            pwrBike = pwrBike,
            hrv = hrv
        )
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

        /** Persists a single [target] profile's [options] to [dataStore] without altering thresholds. */
        suspend fun persistTarget(
            dataStore: SettingsDataStore,
            target: ZoneProfileTarget,
            options: ZoneDisplayOptions
        ) {
            when (target) {
                ZoneProfileTarget.HR_RUN -> dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.HR_RUN, options)
                ZoneProfileTarget.HR_BIKE -> dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.HR_BIKE, options)
                ZoneProfileTarget.PWR_BIKE -> dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.PWR_BIKE, options)
                ZoneProfileTarget.HRV_DFA_A1 -> dataStore.saveHrvZoneDisplayOptions(options)
            }
        }
    }
}

/**
 * Shared [ZoneDisplayOptions] toggle list supporting host-specific row styling via [toggleRow]
 * (`ZoneDisplayToggleRow` in `ZonesSettingsActivity`, `ZoneDisplaySwitchRow` in tile/tab editors)
 * (`REQ-UI-2783`).
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

/**
 * Compact switch row used inside [ZoneDisplayConfigSection] for tile & tracking-tab editors.
 */
@Composable
fun ZoneDisplaySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

/**
 * 4-profile segmented selector styled per `docs/design_guidelines.md §1 & §5.6` with compact
 * padding, suppressed checkmark icons, and single-line ellipsized labels so German translations
 * fit cleanly without clipping or wrapping (`REQ-UI-2783.3`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDisplayProfileSegmentedSelector(
    selectedProfile: ZoneProfileTarget,
    onProfileSelected: (ZoneProfileTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = ZoneProfileTarget.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        profiles.forEachIndexed { index, target ->
            SegmentedButton(
                selected = selectedProfile == target,
                onClick = { onProfileSelected(target) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = profiles.size),
                icon = {},
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(target.labelResId),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Reusable [ZoneDisplayOptions] configuration panel shared by `EditSensorFieldDialog` (single
 * tile-scoped profile when [onProfileSelected] is `null`) and `SensorGridScreen`
 * `ScreenMode.CONFIGURATION` (all 4 profiles selectable when [onProfileSelected] is non-null)
 * (`REQ-UI-2783.2`, `REQ-UI-2783.3`).
 */
@Composable
fun ZoneDisplayConfigSection(
    selectedProfile: ZoneProfileTarget,
    displayOptions: ZoneDisplayOptions,
    onUpdateDisplayOptions: (ZoneDisplayOptions) -> Unit,
    onApplyToAll: () -> Unit,
    onProfileSelected: ((ZoneProfileTarget) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.zone_display_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onProfileSelected == null) {
                    Text(
                        text = stringResource(selectedProfile.labelResId),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (onProfileSelected != null) {
                ZoneDisplayProfileSegmentedSelector(
                    selectedProfile = selectedProfile,
                    onProfileSelected = onProfileSelected
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ZoneDisplayOptionsToggleList(
                    displayOptions = displayOptions,
                    onUpdateDisplayOptions = onUpdateDisplayOptions,
                    showGaugeToggle = (selectedProfile == ZoneProfileTarget.HRV_DFA_A1),
                    dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ) { label, checked, onCheckedChange ->
                    ZoneDisplaySwitchRow(
                        label = label,
                        checked = checked,
                        onCheckedChange = onCheckedChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            OutlinedButton(
                onClick = onApplyToAll,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.zone_display_apply_all),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "ZoneDisplayConfigSection Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ZoneDisplayConfigSectionLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(12.dp)) {
            ZoneDisplayConfigSection(
                selectedProfile = ZoneProfileTarget.HRV_DFA_A1,
                displayOptions = ZoneDisplayOptions(showGauge = true),
                onUpdateDisplayOptions = {},
                onApplyToAll = {},
                onProfileSelected = {}
            )
        }
    }
}

@Preview(name = "ZoneDisplayConfigSection Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ZoneDisplayConfigSectionDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(12.dp)) {
            ZoneDisplayConfigSection(
                selectedProfile = ZoneProfileTarget.HR_BIKE,
                displayOptions = ZoneDisplayOptions(),
                onUpdateDisplayOptions = {},
                onApplyToAll = {},
                onProfileSelected = null
            )
        }
    }
}
