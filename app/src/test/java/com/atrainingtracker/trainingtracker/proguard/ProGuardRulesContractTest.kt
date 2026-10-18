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

package com.atrainingtracker.trainingtracker.proguard

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates ProGuard rules composition (REQ-BLD-001, TST-BLD-001) and domain enum reflection safety.
 */
class ProGuardRulesContractTest {

    private fun findProGuardFile(): File {
        val candidates = listOf(
            File("app/proguard-rules.pro"),
            File("proguard-rules.pro"),
            File("../app/proguard-rules.pro")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate app/proguard-rules.pro in working directory: " + File(".").absolutePath)
    }

    @Test
    fun testProGuardRules_composeBlanketKeepRemoved() {
        val content = findProGuardFile().readText()
        assertFalse(
            "Blanket -keep class androidx.compose.** must be removed so R8 can optimize and tree-shake Compose (REQ-BLD-001.2)",
            content.contains("-keep class androidx.compose.**")
        )
    }

    @Test
    fun testProGuardRules_requiredRulesPresent() {
        val content = findProGuardFile().readText()

        assertTrue(
            "Must preserve domain enum values and valueOf resolution (REQ-BLD-001.3)",
            content.contains("-keepclassmembers enum com.atrainingtracker.**")
        )
        assertTrue(
            "Must keep Garmin FIT SDK classes (REQ-BLD-001.4)",
            content.contains("-keep class com.garmin.fit.**")
        )
        assertTrue(
            "Must keep ANT+ SDK classes (REQ-BLD-001.4)",
            content.contains("-keep class com.dsi.ant.**")
        )
        assertTrue(
            "Must keep PebbleKit classes (REQ-BLD-001.4)",
            content.contains("-keep class com.getpebble.android.kit.**")
        )
        assertTrue(
            "Must keep Dropbox SDK classes (REQ-BLD-001.4)",
            content.contains("-keep class com.dropbox.core.**")
        )
        assertTrue(
            "Must keep GPX parser classes (REQ-BLD-001.4)",
            content.contains("-keep class io.ticofab.androidgpxparser.**")
        )
        assertTrue(
            "Must preserve ViewModel reflective constructors (REQ-BLD-001.5)",
            content.contains("class * extends androidx.lifecycle.ViewModel")
        )
        assertTrue(
            "Must preserve SourceFile and LineNumberTable for deobfuscated stacktraces (REQ-BLD-001.5)",
            content.contains("-keepattributes SourceFile,LineNumberTable")
        )
        assertTrue(
            "Must rename source file attribute for clean mapping (REQ-BLD-001.5)",
            content.contains("-renamesourcefileattribute SourceFile")
        )
    }

    @Test
    fun testDomainEnums_reflectionAndSerializationSafety() {
        // Verify SensorType reflection
        val sensorType = java.lang.Enum.valueOf(SensorType::class.java, "HR")
        assertEquals(SensorType.HR, sensorType)
        assertNotNull(SensorType.values())

        // Verify DeviceType reflection
        val deviceType = java.lang.Enum.valueOf(DeviceType::class.java, "HRM")
        assertEquals(DeviceType.HRM, deviceType)

        // Verify BSportType reflection
        val bSport = java.lang.Enum.valueOf(BSportType::class.java, "BIKE")
        assertEquals(BSportType.BIKE, bSport)

        // Verify FileFormat reflection
        val fileFormat = java.lang.Enum.valueOf(FileFormat::class.java, "GC")
        assertEquals(FileFormat.GC, fileFormat)

        // Verify ExportType reflection
        val exportType = java.lang.Enum.valueOf(ExportType::class.java, "FILE")
        assertEquals(ExportType.FILE, exportType)

        // Verify ExportStatus reflection
        val exportStatus = java.lang.Enum.valueOf(ExportStatus::class.java, "WAITING")
        assertEquals(ExportStatus.WAITING, exportStatus)

        // Verify ZoneType reflection
        val zoneType = java.lang.Enum.valueOf(ZoneType::class.java, "HR_BIKE")
        assertEquals(ZoneType.HR_BIKE, zoneType)

        // Verify WorkoutSource reflection
        val workoutSource = java.lang.Enum.valueOf(WorkoutSource::class.java, "TRACKED")
        assertEquals(WorkoutSource.TRACKED, workoutSource)

        // Verify ExtremaType reflection
        val extremaType = java.lang.Enum.valueOf(ExtremaType::class.java, "MAX")
        assertEquals(ExtremaType.MAX, extremaType)
    }
}
