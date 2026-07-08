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

package com.atrainingtracker.banalservice.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData

/**
 * A reusable technical row that displays battery status and connection/last seen information.
 */
@Composable
fun DeviceStatusRow(
    device: DeviceUiData,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    alpha: Float = TTAlpha.Medium
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // 1. Battery Identity
        Icon(
            painter = painterResource(id = device.batteryStatusIconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(4.dp))

        val batteryText = if (device.batteryPercentage >= 0) "${device.batteryPercentage}%" else stringResource(R.string.devices_unknown)

        // 2. State Information in Brackets
        val relativeTime = getRelativeLastSeen(device.lastSeen)
        val stateText = if (device.isConnected) {
            stringResource(R.string.devices_available)
        } else if (relativeTime.isNotEmpty()) {
            relativeTime
        } else {
            stringResource(R.string.devices_not_connected)
        }

        Text(
            text = "$batteryText ($stateText)",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
    }
}

/**
 * Technical helper to calculate the relative time string for sensor availability.
 */
private fun getRelativeLastSeen(lastSeen: String?): String {
    if (lastSeen == null) return ""

    return try {
        // Attempt to parse using the default date/time instance
        val date = java.text.DateFormat.getDateTimeInstance().parse(lastSeen)
        if (date != null) {
            android.text.format.DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                android.text.format.DateUtils.DAY_IN_MILLIS,
                android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } else {
            ""
        }
    } catch (e: Exception) {
        // Fallback for short special strings or parse errors
        if (lastSeen.length < 10) lastSeen else lastSeen.split(" ").firstOrNull() ?: ""
    }
}
