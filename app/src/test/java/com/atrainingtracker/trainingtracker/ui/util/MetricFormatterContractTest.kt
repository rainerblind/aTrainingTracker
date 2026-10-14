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

package com.atrainingtracker.trainingtracker.ui.util

import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.routes.ReturnNavigationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Architectural and unit contract tests verifying centralized metric distance and altitude formatting
 * across navigation and telemetry cards, enforcing Rule 36 and Imperial unit parity (REQ-UI-364 / TST-UI-327).
 */
class MetricFormatterContractTest {

    private val distanceFormatter = DistanceFormatter()
    private val altitudeFormatter = AltitudeFormatter()

    @Before
    fun setUp() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
    }

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    @Test
    fun metricFormatting_formatsDistanceAndAltitudeProperly() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)

        // Sub-kilometer
        assertEquals("450 m", distanceFormatter.format_with_units(450.0))

        // Multi-kilometer
        val dist12k = distanceFormatter.format_with_units(12400.0)
        assertTrue("Distance should end with km: $dist12k", dist12k.endsWith("km"))
        assertTrue("Distance should format ~12.40: $dist12k", dist12k.startsWith("12.4") || dist12k.startsWith("12,4"))

        // Altitude
        assertEquals("420 m", altitudeFormatter.format_with_units(420.0))
        assertEquals("0 m", altitudeFormatter.format_with_units(0.0))
    }

    @Test
    fun imperialFormatting_formatsDistanceInFeetAndMilesAndElevationInFeet() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)

        // Sub-kilometer (~450m -> ~1476 ft)
        val shortDist = distanceFormatter.format_with_units(450.0)
        assertTrue("Short distance in Imperial should use ft: $shortDist", shortDist.endsWith("ft"))
        assertFalse("Imperial must not contain 'm': $shortDist", shortDist.contains(" m"))

        // Long distance (12400m -> ~7.71 miles)
        val longDist = distanceFormatter.format_with_units(12400.0)
        assertTrue("Long distance in Imperial should use mile: $longDist", longDist.endsWith("mile"))
        assertFalse("Imperial must not contain 'km': $longDist", longDist.contains("km"))

        // Elevation (420m -> ~1378 ft)
        val ele = altitudeFormatter.format_with_units(420.0)
        assertTrue("Elevation in Imperial should use ft: $ele", ele.endsWith("ft"))
        assertFalse("Imperial elevation must not contain 'm': $ele", ele.endsWith("m"))
    }

    @Test
    fun returnNavigationState_formatsDistanceAndClimbWithUnits() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val metricState = ReturnNavigationState(
            isActive = true,
            remainingDistanceMeters = 5400.0,
            remainingElevationGainMeters = 210.0
        )
        assertTrue(metricState.formattedRemainingDistance.endsWith("km"))
        assertEquals("+210 m", metricState.formattedRemainingClimb)

        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val imperialState = ReturnNavigationState(
            isActive = true,
            remainingDistanceMeters = 5400.0,
            remainingElevationGainMeters = 210.0
        )
        assertTrue("Imperial remaining distance must end with mile: ${imperialState.formattedRemainingDistance}",
            imperialState.formattedRemainingDistance.endsWith("mile"))
        assertTrue("Imperial remaining climb must end with ft: ${imperialState.formattedRemainingClimb}",
            imperialState.formattedRemainingClimb.endsWith("ft"))
        assertFalse(imperialState.formattedRemainingDistance.contains("km"))
        assertFalse(imperialState.formattedRemainingClimb.endsWith("m"))
    }

    @Test
    fun headlessNullSafety_formattersDoNotThrowNpeWhenApplicationContextIsNull() {
        TrainingApplication.setUnitForTesting(null)

        val dist = distanceFormatter.format_with_units(1000.0)
        assertNotNull(dist)
        assertTrue(dist != "--")

        val alt = altitudeFormatter.format_with_units(150.0)
        assertNotNull(alt)
        assertTrue(alt != "--")
    }

    @Test
    fun sourceFiles_doNotContainHardcodedMetricTemplateStrings() {
        val filesToCheck = listOf(
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationState.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt"
        )

        for (relPath in filesToCheck) {
            val file = resolveFile(relPath)
            assertTrue("File must exist: $relPath", file.exists())
            val text = file.readText()

            // Assert absence of raw templates
            assertFalse("File $relPath must not contain '\"%.1f km\"'", text.contains("\"%.1f km\""))
            assertFalse("File $relPath must not contain '\"%.2f km\"'", text.contains("\"%.2f km\""))
            assertFalse("File $relPath must not contain '\${distanceRemaining} m'", text.contains("\${distanceRemaining} m"))
            assertFalse("File $relPath must not contain '+\${branch.totalElevationMeters.roundToInt()} m'",
                text.contains("+\${branch.totalElevationMeters.roundToInt()} m"))
            assertFalse("File $relPath must not contain '+\$climb m'", text.contains("+\$climb m"))
        }
    }

    private fun resolveFile(relPath: String): File {
        val candidates = listOf(
            File(relPath),
            File("../$relPath"),
            File("../../$relPath")
        )
        return candidates.firstOrNull { it.exists() } ?: File(relPath)
    }
}
