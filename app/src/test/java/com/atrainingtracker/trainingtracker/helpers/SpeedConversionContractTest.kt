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

package com.atrainingtracker.trainingtracker.helpers

import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricUtils
import com.google.android.gms.maps.model.LatLng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * TST-UI-328 / REQ-UI-365:
 * Validates canonical speed unit conversion formulas, round-trip fidelity,
 * formatter delegation, and absence of duplicate magic constants.
 */
class SpeedConversionContractTest {

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    @Test
    fun testMetricConversionRoundTrip() {
        val originalMps = 10.0
        val kmh = MyHelper.mps2userUnit(originalMps, MyUnits.METRIC)
        assertEquals(36.0, kmh, 1e-6)

        val convertedBackMps = MyHelper.UserUnit2mps(kmh, MyUnits.METRIC)
        assertEquals(originalMps, convertedBackMps, 1e-6)
    }

    @Test
    fun testImperialConversionRoundTrip() {
        val originalMps = 10.0
        val mph = MyHelper.mps2userUnit(originalMps, MyUnits.IMPERIAL)
        val expectedMph = 10.0 * 2.23693629
        assertEquals(expectedMph, mph, 1e-5)

        val convertedBackMps = MyHelper.UserUnit2mps(mph, MyUnits.IMPERIAL)
        assertEquals(originalMps, convertedBackMps, 1e-5)
    }

    @Test
    fun testSpeedFormatterDelegatesToMyHelper() {
        val speedFormatter = SpeedFormatter()
        val mps = 8.5

        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val formattedMetric = speedFormatter.format(mps)
        val expectedMetric = String.format(Locale.getDefault(), "%.1f", MyHelper.mps2userUnit(mps, MyUnits.METRIC))
        assertEquals(expectedMetric, formattedMetric)

        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val formattedImperial = speedFormatter.format(mps)
        val expectedImperial = String.format(Locale.getDefault(), "%.1f", MyHelper.mps2userUnit(mps, MyUnits.IMPERIAL))
        assertEquals(expectedImperial, formattedImperial)
    }

    @Test
    fun testTelemetryMetricUtilsSpeedExtractionAndFormatting() {
        val point = PathPoint(
            distance = 1000.0,
            latLng = LatLng(48.0, 11.0),
            altitude = 500.0,
            timeSec = 300L,
            speedMps = 10.0
        )

        // Metric extraction
        val extractedMetric = TelemetryMetricUtils.extractMetricValue(
            point = point,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.METRIC
        )
        assertNotNull(extractedMetric)
        assertEquals(36.0, extractedMetric!!, 1e-6)

        // Imperial extraction
        val extractedImperial = TelemetryMetricUtils.extractMetricValue(
            point = point,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.IMPERIAL
        )
        assertNotNull(extractedImperial)
        assertEquals(10.0 * 2.23693629, extractedImperial!!, 1e-5)

        // Speed formatting
        val speedFormatter = SpeedFormatter()
        val paceFormatter = PaceFormatter()

        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val formattedMetric = TelemetryMetricUtils.formatValue(
            value = 36.0,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter
        )
        val expectedMetricFormatted = speedFormatter.format_with_units(10.0)
        assertEquals(expectedMetricFormatted, formattedMetric)

        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val formattedImperial = TelemetryMetricUtils.formatValue(
            value = 10.0 * 2.23693629,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.IMPERIAL,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter
        )
        val expectedImperialFormatted = speedFormatter.format_with_units(10.0)
        assertEquals(expectedImperialFormatted, formattedImperial)
    }

    @Test
    fun testEditSportTypeSpeedFormattingAndParsing() {
        val originalLocale = Locale.getDefault()
        try {
            val mps = 10.0
            TrainingApplication.setUnitForTesting(MyUnits.METRIC)

            // Regional formatting: US uses dot, Germany uses comma
            Locale.setDefault(Locale.US)
            val formattedUs = String.format(Locale.getDefault(), "%.2f", MyHelper.mps2userUnit(mps))
            assertEquals("36.00", formattedUs)

            Locale.setDefault(Locale.GERMANY)
            val formattedDe = String.format(Locale.getDefault(), "%.2f", MyHelper.mps2userUnit(mps))
            assertEquals("36,00", formattedDe)

            // Input parsing: Comma vs Dot
            val commaInput = "36,5"
            val dotInput = "36.5"
            val parsedComma = commaInput.replace(',', '.').toDoubleOrNull()
            val parsedDot = dotInput.replace(',', '.').toDoubleOrNull()
            assertNotNull(parsedComma)
            assertNotNull(parsedDot)
            assertEquals(parsedComma, parsedDot)
            assertEquals(36.5, parsedComma!!, 1e-6)

            val mpsFromComma = MyHelper.UserUnit2mps(commaInput.replace(',', '.').toDoubleOrNull() ?: 0.0, MyUnits.METRIC)
            val mpsFromDot = MyHelper.UserUnit2mps(dotInput.replace(',', '.').toDoubleOrNull() ?: 0.0, MyUnits.METRIC)
            assertEquals(mpsFromDot, mpsFromComma, 1e-6)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testNoHardcodedMagicSpeedConversionConstantsInMainCodebase() {
        val mainSrcDir = when {
            File("app/src/main/java").exists() -> File("app/src/main/java")
            File("src/main/java").exists() -> File("src/main/java")
            else -> File("../app/src/main/java")
        }
        assertTrue("Main source dir must exist at ${mainSrcDir.absolutePath}", mainSrcDir.exists())

        val violatingFiles = mutableListOf<String>()
        val regex = Regex("""(?<![\w.])2\.2369\d*|(?<![\w.])2\.237\b""")

        mainSrcDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { !it.name.equals("MyHelper.java") }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        // Exclude comments
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                            if (regex.containsMatchIn(line)) {
                                violatingFiles.add("${file.name}:${index + 1}: $trimmed")
                            }
                        }
                    }
                }
            }

        assertTrue(
            "Found hardcoded speed conversion constants outside MyHelper.java:\n" +
                violatingFiles.joinToString("\n"),
            violatingFiles.isEmpty()
        )
    }
}
