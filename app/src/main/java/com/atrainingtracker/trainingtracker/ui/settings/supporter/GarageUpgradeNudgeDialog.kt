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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.GarageLeague
import com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvent

/**
 * Humorous Material 3 N+1 Cyclist & Runner Garage Upgrade Dialog (REQ-SET-088.5, Rule 23).
 *
 * Displays dynamic fleet counts (bikes, shoes, sensors, power meters) and invites
 * the athlete to upgrade to the matching Supporter League with pre-selection.
 *
 * @param event The qualification event holding fleet statistics and target plan ID.
 * @param onUpgrade Callback triggered with the recommended plan ID to navigate to SupporterScreen.
 * @param onDismiss Callback triggered when the dialog is dismissed without upgrading.
 */
@Composable
fun GarageUpgradeNudgeDialog(
    event: GarageNudgeEvent,
    onUpgrade: (planId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDiamond = event.league == GarageLeague.DIAMOND

    val title = if (isDiamond) {
        stringResource(R.string.garage_detector_dialog_title_diamond)
    } else {
        stringResource(R.string.garage_detector_dialog_title_gold)
    }

    val currentTierName = stringResource(event.currentTier.titleRes)

    val message = if (isDiamond) {
        stringResource(
            R.string.garage_detector_dialog_message_diamond,
            event.stats.bikeCount,
            event.stats.shoeCount,
            event.stats.sensorCount,
            event.stats.powerMeterCount,
            currentTierName
        )
    } else {
        stringResource(
            R.string.garage_detector_dialog_message_gold,
            event.stats.bikeCount,
            event.stats.shoeCount,
            event.stats.sensorCount,
            event.stats.powerMeterCount,
            currentTierName
        )
    }

    val upgradeButtonText = if (isDiamond) {
        stringResource(R.string.garage_detector_action_upgrade_diamond)
    } else {
        stringResource(R.string.garage_detector_action_upgrade_gold)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isDiamond) Icons.Default.Diamond else Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = { onUpgrade(event.recommendedPlanId) }
            ) {
                Text(text = upgradeButtonText)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.garage_detector_action_dismiss))
            }
        }
    )
}

/**
 * Composable alias guaranteeing bidirectional name compatibility between specification and implementation.
 */
@Composable
fun GarageDetectorDialog(
    event: GarageNudgeEvent,
    onUpgrade: (planId: String) -> Unit,
    onDismiss: () -> Unit
) {
    GarageUpgradeNudgeDialog(event, onUpgrade, onDismiss)
}
