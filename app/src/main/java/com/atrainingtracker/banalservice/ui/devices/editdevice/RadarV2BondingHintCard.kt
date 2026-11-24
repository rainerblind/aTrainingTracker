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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.RadarBondActionResult
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.DarkOnWarningContainer
import com.atrainingtracker.trainingtracker.ui.theme.DarkWarning
import com.atrainingtracker.trainingtracker.ui.theme.DarkWarningAction
import com.atrainingtracker.trainingtracker.ui.theme.DarkWarningContainer
import com.atrainingtracker.trainingtracker.ui.theme.LightOnWarningContainer
import com.atrainingtracker.trainingtracker.ui.theme.LightWarning
import com.atrainingtracker.trainingtracker.ui.theme.LightWarningAction
import com.atrainingtracker.trainingtracker.ui.theme.LightWarningBorder
import com.atrainingtracker.trainingtracker.ui.theme.LightWarningContainer

/**
 * Determines whether [RadarV2BondingHintCard] should be displayed for a sensor inside
 * [EditDeviceDialog] (`REQ-CON-061.3`).
 *
 * Returns `true` strictly for BLE [DeviceType.BIKE_RADAR] devices when V2 bonding is required and,
 * if both MAC addresses are non-blank, their trimmed case-insensitive values match.
 */
internal fun shouldShowRadarV2BondingHint(
    deviceType: DeviceType,
    protocol: Protocol,
    isV2BondingRequired: Boolean,
    bondingDeviceAddress: String?,
    deviceMacAddress: String?
): Boolean {
    if (deviceType != DeviceType.BIKE_RADAR || protocol != Protocol.BLUETOOTH_LE) {
        return false
    }
    if (!isV2BondingRequired) {
        return false
    }
    val normBonding = bondingDeviceAddress?.trim().orEmpty()
    val normDevice = deviceMacAddress?.trim().orEmpty()
    if (normBonding.isNotEmpty() && normDevice.isNotEmpty()) {
        return normBonding.equals(normDevice, ignoreCase = true)
    }
    return true
}

/**
 * Inline Material 3 warning/action card rendered inside [EditDeviceDialog] when a V2-capable BLE
 * cycling radar fell back to legacy V1 due to missing Android Bluetooth bonding (`REQ-CON-061.3`).
 */
@Composable
internal fun RadarV2BondingHintCard(
    actionResult: RadarBondActionResult?,
    onPairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (isDark) DarkWarningContainer else LightWarningContainer
    val contentColor = if (isDark) DarkOnWarningContainer else LightOnWarningContainer
    val borderColor = if (isDark) DarkWarning else LightWarningBorder
    val iconColor = if (isDark) DarkWarning else LightWarning
    val buttonContainerColor = if (isDark) DarkWarningAction else LightWarningAction
    val buttonContentColor = if (isDark) DarkWarningContainer else LightWarningContainer

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("RadarV2BondingHintCard"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconColor
                )
                Text(
                    text = stringResource(R.string.radar_v2_bonding_hint_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(R.string.radar_v2_bonding_hint_desc),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )

            Button(
                onClick = onPairClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("RadarV2BondingPairButton"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainerColor,
                    contentColor = buttonContentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.radar_v2_bonding_pair_action),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (actionResult == RadarBondActionResult.BONDING_INITIATED) {
                Text(
                    text = stringResource(R.string.radar_v2_bonding_initiated_hint),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.testTag("RadarV2BondingInitiatedHint")
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Radar V2 Bonding Card Light")
@Composable
private fun RadarV2BondingHintCardLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarV2BondingHintCard(
                actionResult = null,
                onPairClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Radar V2 Bonding Card Dark (Initiated)")
@Composable
private fun RadarV2BondingHintCardDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarV2BondingHintCard(
                actionResult = RadarBondActionResult.BONDING_INITIATED,
                onPairClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Radar V2 Bonding Card AMOLED")
@Composable
private fun RadarV2BondingHintCardAmoledPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarV2BondingHintCard(
                actionResult = null,
                onPairClick = {}
            )
        }
    }
}
