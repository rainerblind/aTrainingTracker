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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModel

const val TEST_TAG_ZONE_ONBOARDING_BANNER = "zone_onboarding_banner"
const val TEST_TAG_ZONE_ONBOARDING_TEXT = "zone_onboarding_text"
const val TEST_TAG_ZONE_ONBOARDING_DISMISS = "zone_onboarding_dismiss"

/**
 * Contextual onboarding banner displayed at the top of ZoneSettingsScreen when the user is redirected
 * after pairing an unconfigured physiological sensor (REQ-UI-374.4).
 *
 * @param promptReason Reason identifier ("hr_paired" or "pwr_paired")
 * @param onDismiss Callback invoked when the user dismisses the guidance banner
 * @param modifier Optional modifier
 */
@Composable
fun ZoneOnboardingBanner(
    promptReason: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messageText = when (promptReason) {
        DeviceListViewModel.PROMPT_REASON_HR_PAIRED -> stringResource(R.string.zone_onboarding_hr_banner)
        DeviceListViewModel.PROMPT_REASON_PWR_PAIRED -> stringResource(R.string.zone_onboarding_pwr_banner)
        else -> return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_ZONE_ONBOARDING_BANNER),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TEST_TAG_ZONE_ONBOARDING_TEXT)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(TEST_TAG_ZONE_ONBOARDING_DISMISS)
                ) {
                    Text(
                        text = stringResource(R.string.zone_onboarding_dismiss),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
