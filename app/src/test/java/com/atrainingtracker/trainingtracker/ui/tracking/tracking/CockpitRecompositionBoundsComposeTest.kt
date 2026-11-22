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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.location.Location
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.batterysaver.BatterySaverController
import com.atrainingtracker.trainingtracker.batterysaver.DisplayBrightnessMode
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.ObserveAutoBrightnessTelemetry
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.ObserveRouteSelectorLocation
import com.google.android.gms.maps.model.LatLng
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification suite for `ATT-8126` (`REQ-UI-2800.4`, `TST-UI-2800.4`):
 * - `rememberTrackingGridActions(viewModel)` preserves strict referential equality (`===`) across 1-Hz recompositions
 * - `ObserveAutoBrightnessTelemetry` skips upstream collection in `DisplayBrightnessMode.SYSTEM`/`CUSTOM`,
 *   forwards 1-Hz telemetry strictly in `AUTO`, and causes 0 host recompositions
 * - `ObserveRouteSelectorLocation` forwards 1-Hz GPS updates inside `LaunchedEffect` with 0 host recompositions
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class CockpitRecompositionBoundsComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testRememberTrackingGridActions_preservesReferentialEqualityAcrossRecompositions_tstUi2800_4() {
        val mockVm = mockk<TrackingViewModel>(relaxed = true)
        var tick by mutableIntStateOf(0)
        val capturedActions = mutableListOf<GridActions>()

        composeRule.setContent {
            ATrainingTrackerTheme {
                val actions = rememberTrackingGridActions(mockVm)
                capturedActions.add(actions)
                Text("Tick $tick")
            }
        }
        composeRule.waitForIdle()

        repeat(5) { idx ->
            composeRule.runOnIdle { tick = idx + 1 }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("Tick 5").assertIsDisplayed()
        assertEquals(6, capturedActions.size)
        val first = capturedActions.first()
        capturedActions.forEach { current ->
            assertSame("gridActions must remain referentially identical across 1-Hz recompositions", first, current)
        }
    }

    @Test
    fun testAutoBrightnessAndLocationObservers_zeroHostRecompositionsAndStrictModeGating_tstUi2800_4() {
        val sensorDataFlow = MutableStateFlow<List<FilteredSensorData<*>>>(emptyList())
        val locationFlow = MutableStateFlow<LatLng?>(null)
        val bearingFlow = MutableStateFlow<Double?>(90.0)
        val speedFlow = MutableStateFlow<Double?>(8.0)
        val batterySaver = mockk<BatterySaverController>(relaxed = true)
        val receivedLocations = mutableListOf<Location>()

        var mode by mutableStateOf(DisplayBrightnessMode.SYSTEM)
        var hostRecompositions = 0

        composeRule.setContent {
            ATrainingTrackerTheme {
                SideEffect { hostRecompositions++ }
                ObserveRouteSelectorLocation(
                    currentLocationFlow = locationFlow,
                    currentBearingFlow = bearingFlow,
                    currentSpeedFlow = speedFlow,
                    onLocationChanged = { receivedLocations.add(it) }
                )
                ObserveAutoBrightnessTelemetry(
                    brightnessMode = mode,
                    activityType = ActivityType.BIKE_POWER,
                    tuningConfig = TuningConfig(),
                    allFilteredSensorDataFlow = sensorDataFlow,
                    batterySaverController = batterySaver
                )
                Text("Mode ${mode.name}")
            }
        }
        composeRule.waitForIdle()

        // 1. In SYSTEM mode: zero upstream subscription on allFilteredSensorDataFlow, 0 updateTelemetry calls, 0 host recompositions
        assertEquals(0, sensorDataFlow.subscriptionCount.value)
        val baselineSystem = hostRecompositions
        repeat(5) { idx ->
            composeRule.runOnIdle {
                sensorDataFlow.value = listOf(
                    FilteredSensorData(SensorType.SPEED_mps, 6.0 + idx, "${6 + idx}", null, FilterType.INSTANTANEOUS, 1.0)
                )
                locationFlow.value = LatLng(48.1 + idx * 0.001, 11.5)
            }
            composeRule.waitForIdle()
        }
        assertEquals("1-Hz ticks must NOT recompose root host in SYSTEM mode", baselineSystem, hostRecompositions)
        assertEquals(5, receivedLocations.size)
        verify(exactly = 0) { batterySaver.updateTelemetry(any(), any()) }

        // 2. Switch to CUSTOM mode: still zero upstream subscription & 0 updateTelemetry calls
        composeRule.runOnIdle { mode = DisplayBrightnessMode.CUSTOM }
        composeRule.waitForIdle()
        assertEquals(0, sensorDataFlow.subscriptionCount.value)
        val baselineCustom = hostRecompositions
        repeat(3) { idx ->
            composeRule.runOnIdle {
                sensorDataFlow.value = listOf(
                    FilteredSensorData(SensorType.SPEED_mps, 12.0 + idx, "${12 + idx}", null, FilterType.INSTANTANEOUS, 1.0)
                )
            }
            composeRule.waitForIdle()
        }
        assertEquals("1-Hz ticks must NOT recompose root host in CUSTOM mode", baselineCustom, hostRecompositions)
        verify(exactly = 0) { batterySaver.updateTelemetry(any(), any()) }

        // 3. Switch to AUTO mode: subscribes once, forwards 5 distinct ticks, still 0 extra host recompositions!
        composeRule.runOnIdle { mode = DisplayBrightnessMode.AUTO }
        composeRule.waitForIdle()
        assertEquals(1, sensorDataFlow.subscriptionCount.value)
        val baselineAuto = hostRecompositions
        repeat(4) { idx ->
            composeRule.runOnIdle {
                sensorDataFlow.value = listOf(
                    FilteredSensorData(SensorType.SPEED_mps, 20.0 + idx, "${20 + idx}", null, FilterType.INSTANTANEOUS, 1.0)
                )
            }
            composeRule.waitForIdle()
        }
        assertEquals("1-Hz ticks must NOT recompose root host in AUTO mode", baselineAuto, hostRecompositions)
        // 1 initial replay on entering AUTO + 4 distinct emissions = 5 calls
        verify(exactly = 5) { batterySaver.updateTelemetry(any(), any()) }
    }
}
