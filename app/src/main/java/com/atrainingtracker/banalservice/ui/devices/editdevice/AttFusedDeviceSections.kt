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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.AttFusedAltitudePreferences
import com.atrainingtracker.banalservice.devices.AttFusedPreferences
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Splits a localized toggle label of the form `"Primary Title (Detail)"` (supporting both ASCII
 * and full-width parentheses) into a concise title and secondary subtitle so long compound labels
 * never crowd or collide with the trailing Material 3 [Switch] (`ATT-8112`).
 */
internal fun splitToggleTitleAndSubtitle(rawLabel: String): Pair<String, String?> {
    val trimmed = rawLabel.trim()
    val closeChar = when {
        trimmed.endsWith(")") -> ')'
        trimmed.endsWith("）") -> '）'
        else -> return trimmed to null
    }
    val openChar = if (closeChar == ')') '(' else '（'
    val openIdx = trimmed.lastIndexOf(openChar)
    if (openIdx <= 0) return trimmed to null
    val main = trimmed.substring(0, openIdx).trim()
    val detail = trimmed.substring(openIdx + 1, trimmed.length - 1).trim()
    return if (main.isNotEmpty() && detail.isNotEmpty()) main to detail else trimmed to null
}

/**
 * Display model for a single guarded source toggle inside [AttFusedSourcesCard] (`ATT-8112`).
 */
internal data class AttFusedSourceToggleItem(
    val title: String,
    val subtitle: String? = null,
    val checked: Boolean,
    val enabled: Boolean,
    val disabledHint: String,
    val switchTestTag: String? = null,
    val onCheckedChange: (Boolean) -> Unit
)

/**
 * Educational information card and guarded input-source toggles for ATT Odometry Fusion
 * (`REQ-SEN-028.3`, `REQ-SEN-028.4`, `REQ-UI-2799.2`, `ATT-7751`, `ATT-8125`, `ATT-8112`).
 * Toggle changes are staged in [EditDeviceViewModel] draft state and persisted strictly on Save.
 */
@Composable
internal fun AttFusedConfigSection(viewModel: EditDeviceViewModel) {
    val context = LocalContext.current
    val draftState by viewModel.attFusedDraft.collectAsStateWithLifecycle()
    val effectiveDraft = draftState ?: remember {
        AttFusedDraftConfig(
            gnssEnabled = AttFusedPreferences.isGnssEnabled(context),
            wheelEnabled = AttFusedPreferences.isWheelEnabled(context),
            gyroEnabled = AttFusedPreferences.isGyroEnabled(context),
            autoCalibEnabled = AttFusedPreferences.isAutoCalibEnabled(context)
        )
    }

    val gnssAvailable = remember { AttFusedPreferences.isGnssAvailable(context) }
    val wheelAvailable = remember { AttFusedPreferences.isWheelAvailable(context) }
    val gyroAvailable = remember { AttFusedPreferences.isGyroAvailable(context) }
    val autoCalibAvailable = remember { AttFusedPreferences.isAutoCalibAvailable(context) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AttFusedEducationCard(
            howItWorksText = stringResource(R.string.device_att_fused_how_it_works),
            prosConsText = stringResource(R.string.device_att_fused_pros_cons)
        )

        AttFusedOdometrySourcesCard(
            draft = effectiveDraft,
            gnssAvailable = gnssAvailable,
            wheelAvailable = wheelAvailable,
            gyroAvailable = gyroAvailable,
            autoCalibAvailable = autoCalibAvailable,
            onGnssChanged = { enabled -> viewModel.onAttFusedConfigChanged { it.copy(gnssEnabled = enabled) } },
            onWheelChanged = { enabled -> viewModel.onAttFusedConfigChanged { it.copy(wheelEnabled = enabled) } },
            onGyroChanged = { enabled -> viewModel.onAttFusedConfigChanged { it.copy(gyroEnabled = enabled) } },
            onAutoCalibChanged = { enabled -> viewModel.onAttFusedConfigChanged { it.copy(autoCalibEnabled = enabled) } }
        )
    }
}

/**
 * Stateless M3 card rendering the 4 guarded input-source toggles for ATT Odometry Fusion (`ATT-8112`).
 */
@Composable
internal fun AttFusedOdometrySourcesCard(
    draft: AttFusedDraftConfig,
    gnssAvailable: Boolean,
    wheelAvailable: Boolean,
    gyroAvailable: Boolean,
    autoCalibAvailable: Boolean,
    onGnssChanged: (Boolean) -> Unit,
    onWheelChanged: (Boolean) -> Unit,
    onGyroChanged: (Boolean) -> Unit,
    onAutoCalibChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        AttFusedSourceToggleItem(
            title = stringResource(R.string.device_att_fused_toggle_gnss),
            checked = draft.gnssEnabled,
            enabled = gnssAvailable,
            disabledHint = stringResource(R.string.device_att_fused_toggle_gnss_hint_disabled),
            switchTestTag = "att_fused_odo_switch_gnss",
            onCheckedChange = onGnssChanged
        ),
        AttFusedSourceToggleItem(
            title = stringResource(R.string.device_att_fused_toggle_wheel),
            checked = draft.wheelEnabled,
            enabled = wheelAvailable,
            disabledHint = stringResource(R.string.device_att_fused_toggle_wheel_hint_disabled),
            switchTestTag = "att_fused_odo_switch_wheel",
            onCheckedChange = onWheelChanged
        ),
        AttFusedSourceToggleItem(
            title = stringResource(R.string.device_att_fused_toggle_gyro),
            checked = draft.gyroEnabled,
            enabled = gyroAvailable,
            disabledHint = stringResource(R.string.device_att_fused_toggle_gyro_hint_disabled),
            switchTestTag = "att_fused_odo_switch_gyro",
            onCheckedChange = onGyroChanged
        ),
        AttFusedSourceToggleItem(
            title = stringResource(R.string.device_att_fused_toggle_autocalib),
            checked = draft.autoCalibEnabled,
            enabled = autoCalibAvailable,
            disabledHint = stringResource(R.string.device_att_fused_toggle_autocalib_hint_disabled),
            switchTestTag = "att_fused_odo_switch_autocalib",
            onCheckedChange = onAutoCalibChanged
        )
    )
    AttFusedSourcesCard(
        items = items,
        modifier = modifier.testTag("att_fused_odometry_sources_card")
    )
}

/**
 * Educational information card and guarded input-source toggles for ATT Altitude & Slope Fusion
 * (`REQ-FIL-016.5`, `REQ-UI-2799.2`, `ATT-6279`, `ATT-8125`, `ATT-8112`).
 * Toggle changes are staged in [EditDeviceViewModel] draft state and persisted strictly on Save.
 */
@Composable
internal fun AttFusedAltitudeConfigSection(viewModel: EditDeviceViewModel) {
    val context = LocalContext.current
    val draftState by viewModel.attFusedAltitudeDraft.collectAsStateWithLifecycle()
    val effectiveDraft = draftState ?: remember {
        AttFusedAltitudeDraftConfig(
            barometerEnabled = AttFusedAltitudePreferences.isBarometerEnabled(context),
            demEnabled = AttFusedAltitudePreferences.isDemEnabled(context),
            gpsEnabled = AttFusedAltitudePreferences.isGpsEnabled(context),
            speedEnabled = AttFusedAltitudePreferences.isSpeedEnabled(context)
        )
    }

    val baroAvailable = remember { AttFusedAltitudePreferences.isBarometerAvailable(context) }
    val demAvailable = remember { AttFusedAltitudePreferences.isDemAvailable(context) }
    val gpsAvailable = remember { AttFusedAltitudePreferences.isGpsAvailable(context) }
    val speedAvailable = remember { AttFusedAltitudePreferences.isSpeedAvailable(context) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AttFusedEducationCard(
            howItWorksText = stringResource(R.string.device_att_fused_alt_how_it_works),
            prosConsText = stringResource(R.string.device_att_fused_alt_pros_cons)
        )

        AttFusedAltitudeSourcesCard(
            draft = effectiveDraft,
            baroAvailable = baroAvailable,
            demAvailable = demAvailable,
            gpsAvailable = gpsAvailable,
            speedAvailable = speedAvailable,
            onBaroChanged = { enabled -> viewModel.onAttFusedAltitudeConfigChanged { it.copy(barometerEnabled = enabled) } },
            onDemChanged = { enabled -> viewModel.onAttFusedAltitudeConfigChanged { it.copy(demEnabled = enabled) } },
            onGpsChanged = { enabled -> viewModel.onAttFusedAltitudeConfigChanged { it.copy(gpsEnabled = enabled) } },
            onSpeedChanged = { enabled -> viewModel.onAttFusedAltitudeConfigChanged { it.copy(speedEnabled = enabled) } }
        )
    }
}

/**
 * Stateless M3 card rendering the 4 guarded input-source toggles for ATT Altitude & Slope Fusion
 * with clean title/subtitle separation preventing long localized labels from colliding with
 * switches (`ATT-8112`).
 */
@Composable
internal fun AttFusedAltitudeSourcesCard(
    draft: AttFusedAltitudeDraftConfig,
    baroAvailable: Boolean,
    demAvailable: Boolean,
    gpsAvailable: Boolean,
    speedAvailable: Boolean,
    onBaroChanged: (Boolean) -> Unit,
    onDemChanged: (Boolean) -> Unit,
    onGpsChanged: (Boolean) -> Unit,
    onSpeedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val (baroTitle, baroSub) = splitToggleTitleAndSubtitle(stringResource(R.string.device_att_fused_alt_toggle_baro))
    val (demTitle, demSub) = splitToggleTitleAndSubtitle(stringResource(R.string.device_att_fused_alt_toggle_dem))
    val (gpsTitle, gpsSub) = splitToggleTitleAndSubtitle(stringResource(R.string.device_att_fused_alt_toggle_gps))
    val (speedTitle, speedSub) = splitToggleTitleAndSubtitle(stringResource(R.string.device_att_fused_alt_toggle_speed))

    val items = listOf(
        AttFusedSourceToggleItem(
            title = baroTitle,
            subtitle = baroSub,
            checked = draft.barometerEnabled,
            enabled = baroAvailable,
            disabledHint = stringResource(R.string.device_att_fused_alt_toggle_baro_hint_disabled),
            switchTestTag = "att_fused_alt_switch_baro",
            onCheckedChange = onBaroChanged
        ),
        AttFusedSourceToggleItem(
            title = demTitle,
            subtitle = demSub,
            checked = draft.demEnabled,
            enabled = demAvailable,
            disabledHint = stringResource(R.string.device_att_fused_alt_toggle_dem_hint_disabled),
            switchTestTag = "att_fused_alt_switch_dem",
            onCheckedChange = onDemChanged
        ),
        AttFusedSourceToggleItem(
            title = gpsTitle,
            subtitle = gpsSub,
            checked = draft.gpsEnabled,
            enabled = gpsAvailable,
            disabledHint = stringResource(R.string.device_att_fused_alt_toggle_gps_hint_disabled),
            switchTestTag = "att_fused_alt_switch_gps",
            onCheckedChange = onGpsChanged
        ),
        AttFusedSourceToggleItem(
            title = speedTitle,
            subtitle = speedSub,
            checked = draft.speedEnabled,
            enabled = speedAvailable,
            disabledHint = stringResource(R.string.device_att_fused_alt_toggle_speed_hint_disabled),
            switchTestTag = "att_fused_alt_switch_speed",
            onCheckedChange = onSpeedChanged
        )
    )
    AttFusedSourcesCard(
        items = items,
        modifier = modifier.testTag("att_fused_altitude_sources_card")
    )
}

@Composable
private fun AttFusedEducationCard(
    howItWorksText: String,
    prosConsText: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.device_altitude_explanation_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.device_altitude_how_it_works_header),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = howItWorksText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.device_altitude_pros_cons_header),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = prosConsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttFusedSourcesCard(
    items: List<AttFusedSourceToggleItem>,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.device_att_fused_sources_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            items.forEachIndexed { index, item ->
                AttFusedToggleRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    checked = item.checked,
                    enabled = item.enabled,
                    disabledHint = item.disabledHint,
                    switchTestTag = item.switchTestTag,
                    onCheckedChange = item.onCheckedChange
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Reusable guarded toggle row with two-tier typography (title + optional subtitle),
 * guaranteed `12.dp` spacing before the trailing Material 3 [Switch], and a cohesive tonal
 * prerequisite hint banner when [enabled] is `false` (`ATT-8112`).
 */
@Composable
internal fun AttFusedToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    disabledHint: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    switchTestTag: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }
            Switch(
                checked = checked && enabled,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
            )
        }
        if (!enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = disabledHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Returns `true` exclusively for the two virtual ATT Fused sensors that benefit from
 * post-workout Extended Rauch-Tung-Striebel (`ERTS`) smoothing (`REQ-FIL-017.5`, `ATT-8113`).
 */
internal fun shouldShowPostWorkoutErtsToggle(deviceType: DeviceType): Boolean =
    deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED ||
    deviceType == DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED

/**
 * Stateful section binding the Post-Workout ERTS smoothing switch to [EditDeviceViewModel]
 * draft/persisted state backed by `TuningPreferencesDataStore.KEY_POST_WORKOUT_ERTS_SMOOTHING`
 * (`REQ-FIL-017.5`, `REQ-UI-2799.2`, `ATT-8113`).
 */
@Composable
internal fun PostWorkoutErtsSmoothingSection(
    viewModel: EditDeviceViewModel,
    modifier: Modifier = Modifier
) {
    val persistedEnabled by viewModel.postWorkoutErtsPersisted.collectAsStateWithLifecycle()
    val draftEnabled by viewModel.postWorkoutErtsDraft.collectAsStateWithLifecycle()
    val effectiveEnabled = draftEnabled ?: persistedEnabled

    PostWorkoutErtsSmoothingCard(
        checked = effectiveEnabled,
        onCheckedChange = viewModel::onPostWorkoutErtsSmoothingChanged,
        modifier = modifier
    )
}

/**
 * Stateless Material 3 card exposing the global Post-Workout ERTS smoothing toggle inside
 * [EditDeviceDialog] for ATT Fused sensors, matching the shared preference in
 * `SensorsGpsFilterSection` (`REQ-FIL-017.5`, `ATT-8113`).
 */
@Composable
internal fun PostWorkoutErtsSmoothingCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("edit_device_post_workout_erts_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.tuning_post_workout_erts_smoothing_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag("edit_device_post_workout_erts_switch")
                )
            }
            Text(
                text = stringResource(R.string.tuning_post_workout_erts_smoothing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "ATT Fused Sources Light (DE)", locale = "de", showBackground = true)
@Composable
private fun AttFusedSourcesLightGermanPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AttFusedAltitudeSourcesCard(
                    draft = AttFusedAltitudeDraftConfig(barometerEnabled = true, demEnabled = false, gpsEnabled = true, speedEnabled = true),
                    baroAvailable = true,
                    demAvailable = false,
                    gpsAvailable = true,
                    speedAvailable = true,
                    onBaroChanged = {},
                    onDemChanged = {},
                    onGpsChanged = {},
                    onSpeedChanged = {}
                )
                AttFusedOdometrySourcesCard(
                    draft = AttFusedDraftConfig(gnssEnabled = true, wheelEnabled = false, gyroEnabled = true, autoCalibEnabled = false),
                    gnssAvailable = true,
                    wheelAvailable = false,
                    gyroAvailable = true,
                    autoCalibAvailable = false,
                    onGnssChanged = {},
                    onWheelChanged = {},
                    onGyroChanged = {},
                    onAutoCalibChanged = {}
                )
            }
        }
    }
}

@Preview(
    name = "ATT Fused Sources Dark (130% Font)",
    locale = "de",
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun AttFusedSourcesDarkLargeFontPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AttFusedAltitudeSourcesCard(
                    draft = AttFusedAltitudeDraftConfig(barometerEnabled = true, demEnabled = false, gpsEnabled = true, speedEnabled = true),
                    baroAvailable = true,
                    demAvailable = false,
                    gpsAvailable = true,
                    speedAvailable = true,
                    onBaroChanged = {},
                    onDemChanged = {},
                    onGpsChanged = {},
                    onSpeedChanged = {}
                )
                PostWorkoutErtsSmoothingCard(checked = true, onCheckedChange = {})
            }
        }
    }
}
