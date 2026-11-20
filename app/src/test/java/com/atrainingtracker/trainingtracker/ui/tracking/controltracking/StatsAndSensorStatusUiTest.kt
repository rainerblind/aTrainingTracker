/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsSummaryBlock
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Behavioral Robolectric Compose UI tests for [StatsSummaryBlock] and [SensorStatus]:
 * - `TST-UI-046` (`REQ-UI-046`): StatsSummaryBlock Time/Distance/Ascent row visual alignment.
 * - `TST-UI-048` (`REQ-UI-048`): SensorStatus renders compact ~22dp icons with zero text labels.
 * - `TST-UI-049` (`REQ-UI-049`): Tapping a SensorStatus icon opens [SensorSourceDialog].
 * - `TST-UI-050` (`REQ-UI-050`): SensorSourceDialog displays aligned `DeviceIdentityBlock` & `DeviceStatusRow`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class StatsAndSensorStatusUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun statsSummaryBlock_alignsMetricsHorizontallyAndInvokesClick_TST_UI_046() {
        // TST-UI-046
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stats = StatsData(
            primaryTitle = "All Time",
            secondaryTitle = "All Time",
            totalWorkouts = 12,
            totalDistanceWithUnits = "340 km",
            timeWithUnits = "12:30:00",
            totalAscentWithUnits = "2400 m"
        )
        var clickedStats: StatsData? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    StatsSummaryBlock(stats = stats, onStatsClick = { clickedStats = it })
                }
            }
        }
        composeTestRule.waitForIdle()

        val timeBounds = composeTestRule.onNodeWithText(context.getString(R.string.stats_time)).getBoundsInRoot()
        val distBounds = composeTestRule.onNodeWithText(context.getString(R.string.stats_distance)).getBoundsInRoot()
        val ascentBounds = composeTestRule.onNodeWithText(context.getString(R.string.stats_ascent)).getBoundsInRoot()

        assertTrue("Time and Distance labels align vertically", abs((timeBounds.top - distBounds.top).value) <= 2f)
        assertTrue("Distance and Ascent labels align vertically", abs((distBounds.top - ascentBounds.top).value) <= 2f)

        composeTestRule.onNodeWithText("340 km").performClick()
        assertEquals(stats, clickedStats)
    }

    @Test
    fun sensorStatus_rendersIconsOnly_andOpensSensorSourceDialogWithIdentityOnTap_TST_UI_048_049_050() {
        // TST-UI-048, TST-UI-049, TST-UI-050
        val mockPro = mockk<ProFeatureManager>(relaxed = true).apply {
            every { effectiveTier } returns MutableStateFlow(SupporterTier.FREE)
        }
        mockkObject(ProFeatureManager.Companion)
        every { ProFeatureManager.getInstance(any()) } returns mockPro

        val hrDevice = DeviceUiData(
            id = 5L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = "Now",
            manufacturer = "Garmin",
            deviceName = "HRM-Pro",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 90,
            deviceTypeIconRes = R.drawable.ic_heart_rate,
            batteryStatusIconRes = R.drawable.ic_battery_full,
            onEquipmentResId = R.string.devices_on_equipment_text,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = true,
            mainValue = "148 bpm",
            allValues = emptyList()
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    Column {
                        SensorStatus(
                            activeSensors = setOf(SensorType.HR, SensorType.SPEED_mps),
                            sourceMapping = mapOf(SensorType.HR to 5L),
                            allDevices = listOf(hrDevice)
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // TST-UI-048: Compact sensor icons without text labels in SensorStatus row
        val hrIconNode = composeTestRule.onNodeWithContentDescription(SensorType.HR.name).assertIsDisplayed()
        val iconBounds = hrIconNode.getBoundsInRoot()
        assertTrue("Sensor icon height must stay <= 30.dp (compact header)", (iconBounds.bottom - iconBounds.top).value <= 30f)
        composeTestRule.onAllNodesWithText("HRM-Pro").fetchSemanticsNodes().also {
            assertTrue("SensorStatus header must not render text device names", it.isEmpty())
        }

        // TST-UI-049 & TST-UI-050: Tapping icon opens SensorSourceDialog displaying DeviceIdentityBlock + DeviceStatusRow
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedStatus = "90% (${context.getString(R.string.devices_available)})"
        hrIconNode.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("HRM-Pro").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(expectedStatus).onFirst().assertIsDisplayed()
    }
}
