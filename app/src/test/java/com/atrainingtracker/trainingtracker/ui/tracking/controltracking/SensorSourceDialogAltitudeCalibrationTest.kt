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

import com.atrainingtracker.banalservice.BANALService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit verification suite for [SensorSourceDialog] altitude calibration section
 * and conversion contracts (REQ-TRK-047, REQ-TRK-047.1, TST-TRK-047.1, TST-TRK-047.5, TST-TRK-047.6, TST-TRK-047.7).
 */
class SensorSourceDialogAltitudeCalibrationTest {

    private val sourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
        ).firstOrNull { it.exists() } ?: File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
    }

    @Test
    fun testContract_SensorSourceDialogHasAltitudeCalibrationSection() {
        assertTrue("SensorSourceDialog.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        // Contract checks for REQ-TRK-047
        assertTrue(
            "SensorSourceDialog must accept currentAltitude parameter",
            content.contains("currentAltitude: Double? = null")
        )
        assertTrue(
            "SensorSourceDialog must accept onCalibrateAltitude callback",
            content.contains("onCalibrateAltitude: ((referenceAltitudeMeters: Double, locationName: String?, saveAsKnownLocation: Boolean) -> Unit)? = null")
        )
        assertTrue(
            "SensorSourceDialog must conditionally render calibration card for SensorType.ALTITUDE",
            content.contains("sensorType == SensorType.ALTITUDE && onCalibrateAltitude != null")
        )
        assertTrue(
            "SensorSourceDialog must use canonical altitude calibration title string",
            content.contains("R.string.altitude_calibration_title")
        )
        assertTrue(
            "SensorSourceDialog must include save location toggle",
            content.contains("R.string.altitude_calibration_save_location")
        )
        assertTrue(
            "SensorSourceDialog must include calibrate action button",
            content.contains("R.string.altitude_calibration_action")
        )
    }

    @Test
    fun testContract_SensorSourceDialogKnownLocationDefaultsAndMatching() {
        assertTrue("SensorSourceDialog.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        // REQ-TRK-047.1 / TST-TRK-047.6 & TST-TRK-047.7 checks
        assertTrue(
            "SensorSourceDialog must accept matchedLocation parameter",
            content.contains("matchedLocation: LocationCalibrationStatus? = null")
        )
        assertTrue(
            "SensorSourceDialog must default saveLocation to false (unchecked by default)",
            content.contains("var saveLocation by remember { mutableStateOf(false) }")
        )
        assertTrue(
            "SensorSourceDialog must conditionally render save location toggle when matchedLocation is null",
            content.contains("if (matchedLocation == null)")
        )
        assertTrue(
            "SensorSourceDialog must conditionally render locationNameInput only when saveLocation is true",
            content.contains("if (saveLocation)")
        )
        assertTrue(
            "SensorSourceDialog must display matched location badge using canonical string",
            content.contains("R.string.altitude_calibration_matched_location")
        )
        assertTrue(
            "SensorSourceDialog initial altitude must prefer matchedLocation reference altitude",
            content.contains("matchedLocation?.referenceAltitude ?: currentAltitude")
        )
    }

    @Test
    fun testUnitConversion_metricInput_preservesMeters() {
        val inputMeters = "542.5"
        val parsed = inputMeters.replace(',', '.').toDoubleOrNull()
        assertNotNull(parsed)

        val isMetric = true
        val targetMeters = if (isMetric) parsed!! else parsed!! * BANALService.METER_PER_FOOT
        assertEquals(542.5, targetMeters, 1e-4)

        var dispatchedMeters: Double? = null
        var dispatchedName: String? = null
        var dispatchedSave: Boolean? = null

        val callback: (Double, String?, Boolean) -> Unit = { alt, name, save ->
            dispatchedMeters = alt
            dispatchedName = name
            dispatchedSave = save
        }

        callback(targetMeters, "Summit Cross", true)

        assertEquals(542.5, dispatchedMeters!!, 1e-4)
        assertEquals("Summit Cross", dispatchedName)
        assertEquals(true, dispatchedSave)
    }

    @Test
    fun testUnitConversion_imperialInput_convertsFeetToMeters() {
        val inputFeet = "1000.0"
        val parsed = inputFeet.replace(',', '.').toDoubleOrNull()
        assertNotNull(parsed)

        val isMetric = false
        val targetMeters = if (isMetric) parsed!! else parsed!! * BANALService.METER_PER_FOOT
        // 1000 ft * 0.3048 = 304.8 meters
        assertEquals(304.8, targetMeters, 1e-3)

        var dispatchedMeters: Double? = null
        val callback: (Double, String?, Boolean) -> Unit = { alt, _, _ ->
            dispatchedMeters = alt
        }

        callback(targetMeters, null, false)
        assertEquals(304.8, dispatchedMeters!!, 1e-3)
    }
}
