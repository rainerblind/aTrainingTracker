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

import android.content.Context
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarPmdStreamCoordinator
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI verification for [PolarPmdStreamWarningEffect] (`ATT-9161`, `REQ-CON-053.3`, `TST-CON-053.3`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolarPmdStreamWarningEffectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        PolarPmdStreamCoordinator.resetWarningsForTesting()
    }

    @After
    fun tearDown() {
        PolarPmdStreamCoordinator.resetWarningsForTesting()
    }

    @Test
    fun testPolarPmdStreamWarningEffect_displaysLocalizedSnackbarAndDismissesOnDisconnect_TST_CON_053_3() {
        // REQ-CON-053.3 / TST-CON-053.3
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = PolarPmdStreamCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            deviceId = 42L,
            deviceNameProvider = { "Polar Verity Sense 24825632" },
            polarIdProvider = { "24825632" },
            macAddress = "24:AC:AC:24:82:56",
            isConnectedProvider = { true },
            isShutDownProvider = { false },
            delayFn = {}
        )

        var hostStateRef: SnackbarHostState? = null
        composeTestRule.setContent {
            val hostState = remember { SnackbarHostState() }
            hostStateRef = hostState
            PolarPmdStreamWarningEffect(hostState)
            SnackbarHost(hostState = hostState)
        }

        // Exhaust PMD retries to emit warning
        runBlocking {
            coordinator.collectPmdWithAlreadyInStateRecovery(
                pmdType = PmdMeasurementType.GYRO,
                sessionId = "24825632",
                stopStreaming = {},
                streamFactory = {
                    flow<Int> {
                        throw BleControlPointCommandError(
                            "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
                            PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                        )
                    }
                },
                onSample = {}
            )
        }

        val expectedText = context.getString(
            R.string.polar_live_imu_stream_blocked_warning,
            "Polar Verity Sense 24825632",
            "GYRO"
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()

        // Disconnect clears warning -> collectLatest cancels active snackbar & consumes cleanly
        coordinator.resetSessionWarningState("24825632")
        composeTestRule.waitForIdle()
        assertNull("Warning state must be cleared on disconnect", PolarPmdStreamCoordinator.streamWarning.value)
        assertNull("Active snackbar must be dismissed on disconnect", hostStateRef?.currentSnackbarData)
    }
}
