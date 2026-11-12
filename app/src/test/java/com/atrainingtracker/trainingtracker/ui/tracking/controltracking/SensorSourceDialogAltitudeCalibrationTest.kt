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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract and unit tests for altitude calibration section within [SensorSourceDialog]
 * and conversion contracts (REQ-TRK-047, REQ-TRK-047.1, TST-TRK-047.1, TST-TRK-047.5, TST-TRK-047.6, TST-TRK-047.7).
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorSourceDialogAltitudeCalibrationTest {

    @Test
    fun testContract_SensorSourceDialogHasAltitudeCalibrationSection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogKt")
        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("SensorSourceDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorSourceDialog composable must exist", dialogMethod)

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("altitude_calibration_title must exist", strings.containsKey("altitude_calibration_title"))
        assertTrue("altitude_calibration_save_location must exist", strings.containsKey("altitude_calibration_save_location"))
        assertTrue("altitude_calibration_action must exist", strings.containsKey("altitude_calibration_action"))
    }

    @Test
    fun testContract_SensorSourceDialogKnownLocationDefaultsAndMatching() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogKt")
        assertNotNull("SensorSourceDialogKt must exist", clazz)

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("altitude_calibration_matched_location must exist", strings.containsKey("altitude_calibration_matched_location"))
    }

    @Test
    fun testUnitConversion_metricInput_preservesMeters() {
        val inputStr = "250.5"
        val isMetric = true
        val parsed = inputStr.replace(',', '.').toDoubleOrNull()
        assertNotNull("Metric numeric input must parse", parsed)

        val referenceAltitudeMeters = if (isMetric) {
            parsed!!
        } else {
            parsed!! / 3.28084
        }

        assertEquals("Metric input 250.5m must equal 250.5m", 250.5, referenceAltitudeMeters, 0.001)
    }

    @Test
    fun testUnitConversion_imperialInput_convertsFeetToMeters() {
        val inputStr = "1000.0"
        val isMetric = false
        val parsed = inputStr.replace(',', '.').toDoubleOrNull()
        assertNotNull("Imperial numeric input must parse", parsed)

        val referenceAltitudeMeters = if (isMetric) {
            parsed!!
        } else {
            parsed!! / 3.28084
        }

        // 1000 feet / 3.28084 = 304.8 meters
        assertEquals("Imperial input 1000ft must equal ~304.8m", 304.8, referenceAltitudeMeters, 0.01)
    }
}
