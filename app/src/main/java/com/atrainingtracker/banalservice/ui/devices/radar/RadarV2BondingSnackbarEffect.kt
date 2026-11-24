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

package com.atrainingtracker.banalservice.ui.devices.radar

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarBluetoothBondingHelper
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import kotlinx.coroutines.flow.collectLatest

private data class RadarV2BondingSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration
) : SnackbarVisuals

/**
 * Non-interrupting, dismissible one-shot-per-connection Snackbar effect alerting the cyclist when
 * a V2-capable BLE radar falls back to legacy V1 due to missing Android Bluetooth bonding
 * (`REQ-CON-061.3`).
 *
 * Checks [isAllowedToShow] strictly BEFORE consuming the repository prompt latch so active
 * `TRACKING`/`PAUSED` sessions never burn the one-shot prompt, and cancels/dismisses strictly
 * its own [RadarV2BondingSnackbarVisuals] snackbar if [isAllowedToShow] transitions to `false`
 * (e.g., `READY -> TRACKING`) without disturbing unrelated snackbars (such as battery alerts).
 */
@Composable
fun RadarV2BondingSnackbarEffect(
    snackbarHostState: SnackbarHostState,
    isAllowedToShow: Boolean,
    onPairAction: (Context, String?) -> Unit = { ctx, mac ->
        RadarBluetoothBondingHelper.requestBondOrOpenSettings(ctx, mac)
    }
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val currentOnPairAction by rememberUpdatedState(onPairAction)

    LaunchedEffect(snackbarHostState, isAllowedToShow) {
        if (!isAllowedToShow) {
            if (snackbarHostState.currentSnackbarData?.visuals is RadarV2BondingSnackbarVisuals) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            return@LaunchedEffect
        }
        RadarStateRepository.observeV2BondingStatus().collectLatest { status ->
            if (!status.isRequired) return@collectLatest
            if (!RadarStateRepository.consumeV2BondingPromptIfPending(status.deviceAddress)) {
                return@collectLatest
            }
            val message = resources.getString(R.string.radar_v2_bonding_snackbar_message)
            val actionLabel = resources.getString(R.string.radar_v2_bonding_pair_action_short)
            val result = snackbarHostState.showSnackbar(
                RadarV2BondingSnackbarVisuals(
                    message = message,
                    actionLabel = actionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentOnPairAction(context, status.deviceAddress)
            }
        }
    }
}
