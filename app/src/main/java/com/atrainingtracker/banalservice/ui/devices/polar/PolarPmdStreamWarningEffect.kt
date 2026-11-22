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

package com.atrainingtracker.banalservice.ui.devices.polar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarPmdStreamCoordinator
import kotlinx.coroutines.flow.collectLatest

/**
 * Shared Compose effect observing [PolarPmdStreamCoordinator.streamWarning] and presenting a
 * localized Material 3 Snackbar when Polar Live-IMU PMD streaming remains blocked after bounded
 * `ERROR_ALREADY_IN_STATE` recovery retries are exhausted (`ATT-9161`, `REQ-CON-053.3`).
 */
@Composable
fun PolarPmdStreamWarningEffect(snackbarHostState: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(snackbarHostState) {
        PolarPmdStreamCoordinator.streamWarning.collectLatest { warning ->
            if (warning == null) return@collectLatest
            val message = resources.getString(
                R.string.polar_live_imu_stream_blocked_warning,
                warning.deviceName,
                warning.measurementType.name
            )
            try {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
            } finally {
                PolarPmdStreamCoordinator.consumeStreamWarning(warning.polarId)
            }
        }
    }
}
