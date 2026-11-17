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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsDraft
import com.atrainingtracker.trainingtracker.ui.settings.ZoneDisplayOptionsToggleList
import com.atrainingtracker.trainingtracker.ui.settings.ZoneProfileTarget
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSegmentedButtonRow
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningToggleItem
import com.atrainingtracker.trainingtracker.ui.tracking.HrvZoneIndicator
import com.atrainingtracker.trainingtracker.ui.tracking.ZoneGaugeState

/**
 * Sample telemetry metrics and optional HRV gauge state rendered inside the Cockpit
 * & Tiles live preview tile for the active [ZoneProfileTarget] (`REQ-UI-2783.3`).
 */
internal data class ZonePreviewTileMetrics(
    val value: String,
    val unit: String,
    val gaugeState: ZoneGaugeState?
)

@Composable
internal fun resolveZonePreviewTileMetrics(
    profile: ZoneProfileTarget,
    options: ZoneDisplayOptions
): ZonePreviewTileMetrics {
    return when (profile) {
        ZoneProfileTarget.HR_RUN, ZoneProfileTarget.HR_BIKE -> ZonePreviewTileMetrics("148", "bpm", null)
        ZoneProfileTarget.PWR_BIKE -> ZonePreviewTileMetrics("245", "W", null)
        ZoneProfileTarget.HRV_DFA_A1 -> {
            val gauge = if (options.showGauge) {
                HrvZoneIndicator.calculateZoneGaugeState(
                    alpha1 = 0.82,
                    t1 = 0.75f,
                    t2 = 0.50f,
                    zoneLabels = listOf(
                        stringResource(R.string.hrv_preview_pill_aerobic),
                        stringResource(R.string.hrv_preview_pill_transition),
                        stringResource(R.string.hrv_preview_pill_anaerobic)
                    )
                )
            } else {
                null
            }
            ZonePreviewTileMetrics("0.82", "α1", gauge)
        }
    }
}

/**
 * Profile-scoped Zone Display subsection embedded inside [CockpitTypographySection]
 * (`REQ-UI-2783.1`, `REQ-UI-262.5`). Uses strictly `TuningSegmentedButtonRow`,
 * `TuningToggleItem`, `OutlinedButton`, and `HorizontalDivider`.
 */
@Composable
fun CockpitZoneDisplaySubsection(
    selectedProfile: ZoneProfileTarget,
    onProfileSelected: (ZoneProfileTarget) -> Unit,
    zoneDisplayDraft: ZoneDisplayOptionsDraft,
    onZoneDisplayDraftChange: (ZoneDisplayOptionsDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeOptions = zoneDisplayDraft.optionsFor(selectedProfile)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TuningSegmentedButtonRow(
            title = stringResource(R.string.zone_display_title),
            options = ZoneProfileTarget.entries,
            selectedOption = selectedProfile,
            onOptionSelected = onProfileSelected,
            labelProvider = { target -> stringResource(target.labelResId) }
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            ZoneDisplayOptionsToggleList(
                displayOptions = activeOptions,
                onUpdateDisplayOptions = { updated ->
                    onZoneDisplayDraftChange(zoneDisplayDraft.withUpdated(selectedProfile, updated))
                },
                showGaugeToggle = (selectedProfile == ZoneProfileTarget.HRV_DFA_A1),
                dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ) { label, checked, onCheckedChange ->
                TuningToggleItem(
                    title = label,
                    isChecked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        }

        OutlinedButton(
            onClick = {
                onZoneDisplayDraftChange(zoneDisplayDraft.applyToAllFrom(selectedProfile))
            },
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

/**
 * Reusable exposed dropdown selector for Cockpit typography & style options (`REQ-UI-262`, `REQ-UI-277.3`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> CockpitDropdownSelector(
    title: String,
    options: List<T>,
    selectedOption: T,
    selectedLabel: String,
    onOptionSelected: (T) -> Unit,
    itemLabel: @Composable (T) -> String,
    textStyle: TextStyle = LocalTextStyle.current,
    itemFontFamily: (T) -> FontFamily? = { null },
    itemFontWeight: (T) -> FontWeight? = { null }
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                textStyle = textStyle
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = itemLabel(option),
                                fontFamily = itemFontFamily(option),
                                fontWeight = itemFontWeight(option)
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
