/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.content.SharedPreferences
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for background training zone bands, right-hand secondary
 * zone axis, scrubbing readout formatting, and padding alignment invariants (TST-UI-184 / REQ-UI-230 / ATT-1839).
 */
class TelemetryMetricGraphZoneTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val telemetryGraphFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    private var originalPrefs: Any? = null

    @Before
    fun setUp() {
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString(TrainingApplication.SP_UNITS, any()) } returns "METRIC"
        val field = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        field.isAccessible = true
        originalPrefs = field.get(null)
        field.set(null, mockPrefs)
    }

    @After
    fun tearDown() {
        val field = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        field.isAccessible = true
        field.set(null, originalPrefs)
    }

    @Test
    fun testScrubbingReadout_includesZoneTag_forHeartRate() {
        val speedFormatter = SpeedFormatter()
        val paceFormatter = PaceFormatter()
        val thresholds = HeartRateZoneThresholds(z1Max = 120, z2Max = 140, z3Max = 160, z4Max = 180)

        // Without thresholds: plain format
        val plainFormat = TelemetryMetricUtils.formatValue(
            value = 165.0,
            metricType = TelemetryMetricType.HEART_RATE,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter
        )
        assertEquals("165 bpm", plainFormat)

        // With thresholds: includes zone tag
        val zone1 = TelemetryMetricUtils.formatValue(
            value = 110.0,
            metricType = TelemetryMetricType.HEART_RATE,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            hrZoneThresholds = thresholds
        )
        assertEquals("110 bpm • Z1", zone1)

        val zone4 = TelemetryMetricUtils.formatValue(
            value = 165.0,
            metricType = TelemetryMetricType.HEART_RATE,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            hrZoneThresholds = thresholds
        )
        assertEquals("165 bpm • Z4", zone4)

        val zone5 = TelemetryMetricUtils.formatValue(
            value = 188.0,
            metricType = TelemetryMetricType.HEART_RATE,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            hrZoneThresholds = thresholds
        )
        assertEquals("188 bpm • Z5", zone5)
    }

    @Test
    fun testScrubbingReadout_includesZoneTag_forPower() {
        val speedFormatter = SpeedFormatter()
        val paceFormatter = PaceFormatter()
        val thresholds = PowerZoneThresholds(z1Max = 150, z2Max = 200, z3Max = 250, z4Max = 300)

        // Without thresholds: plain format
        val plainFormat = TelemetryMetricUtils.formatValue(
            value = 280.0,
            metricType = TelemetryMetricType.POWER,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter
        )
        assertEquals("280 W", plainFormat)

        // With thresholds: includes zone tag
        val zone3 = TelemetryMetricUtils.formatValue(
            value = 220.0,
            metricType = TelemetryMetricType.POWER,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            powerZoneThresholds = thresholds
        )
        assertEquals("220 W • Z3", zone3)

        val zone4 = TelemetryMetricUtils.formatValue(
            value = 280.0,
            metricType = TelemetryMetricType.POWER,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            powerZoneThresholds = thresholds
        )
        assertEquals("280 W • Z4", zone4)

        val zone5 = TelemetryMetricUtils.formatValue(
            value = 350.0,
            metricType = TelemetryMetricType.POWER,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            powerZoneThresholds = thresholds
        )
        assertEquals("350 W • Z5", zone5)
    }

    @Test
    fun testSpeedAndPaceGraphs_noZoneTags() {
        val speedFormatter = SpeedFormatter()
        val paceFormatter = PaceFormatter()
        val hrThresholds = HeartRateZoneThresholds(120, 140, 160, 180)
        val powerThresholds = PowerZoneThresholds(150, 200, 250, 300)

        val speedResult = TelemetryMetricUtils.formatValue(
            value = 25.0,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            hrZoneThresholds = hrThresholds,
            powerZoneThresholds = powerThresholds
        )
        assertFalse("Speed format must not contain zone indicator", speedResult.contains("• Z"))

        val paceResult = TelemetryMetricUtils.formatValue(
            value = 5.0,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            hrZoneThresholds = hrThresholds,
            powerZoneThresholds = powerThresholds
        )
        assertFalse("Pace format must not contain zone indicator", paceResult.contains("• Z"))
    }

    @Test
    fun testHeartRateAndPower_enableZoneBands_contract() {
        val hrThresholds = HeartRateZoneThresholds(120, 140, 160, 180)
        val hrBands = TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 100.0, 190.0)
        assertEquals(5, hrBands.size)

        val powerThresholds = PowerZoneThresholds(150, 200, 250, 300)
        val powerBands = TelemetryZoneMath.calculatePowerZoneBands(powerThresholds, 100.0, 350.0)
        assertEquals(5, powerBands.size)

        // Threshold boundary guidelines
        val hrDashes = TelemetryZoneMath.calculateThresholdDashes(
            hrThresholds.z1Max, hrThresholds.z2Max, hrThresholds.z3Max, hrThresholds.z4Max,
            100.0, 190.0
        )
        assertEquals(listOf(120.0, 140.0, 160.0, 180.0), hrDashes)
    }

    @Test
    fun testSpeedAndPace_doNotEnableZoneBands_contract() {
        // Contract verification in TelemetryMetricGraph.kt:
        // zoneBands should only be calculated for HEART_RATE and POWER
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryGraphFile.exists())
        val content = telemetryGraphFile.readText()

        // Verify zoneBands when clause
        assertTrue(content.contains("TelemetryMetricType.HEART_RATE ->"))
        assertTrue(content.contains("TelemetryMetricType.POWER ->"))
        assertTrue(content.contains("else -> emptyList()"))
    }

    @Test
    fun testPaddingInvariants_matchElevationProfile() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryGraphFile.exists())
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())

        val graphContent = telemetryGraphFile.readText()
        val profileContent = elevationProfileFile.readText()

        // 1. Both files must enforce 50.dp start padding and 25.dp end padding
        assertTrue(graphContent.contains("50.dp"))
        assertTrue(graphContent.contains("25.dp"))
        assertTrue(profileContent.contains("start = 50.dp, end = 25.dp"))

        // 2. Right-hand zone labels must be positioned within the 25.dp end margin
        assertTrue(
            "Right axis center must be positioned within the end padding margin",
            graphContent.contains("startPaddingPx + chartWidthPx + (endPaddingPx / 2f)")
        )

        // 3. Zone band alpha must be calibrated to 10%
        assertTrue(graphContent.contains("TelemetryZoneMath.ZONE_BAND_ALPHA"))
        assertEquals(0.10f, TelemetryZoneMath.ZONE_BAND_ALPHA, 0.001f)
    }

    @Test
    fun testElevationProfile_scrubbingBadge_includesZoneSupport() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify ScrubbingTelemetryBadge accepts zone thresholds and displays active zone
        assertTrue(content.contains("hrZoneThresholds: HeartRateZoneThresholds? = null"))
        assertTrue(content.contains("powerZoneThresholds: PowerZoneThresholds? = null"))
        assertTrue(content.contains("TelemetryZoneMath.determineHeartRateZone"))
        assertTrue(content.contains("TelemetryZoneMath.determinePowerZone"))
    }
}
