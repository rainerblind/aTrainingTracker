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

package com.atrainingtracker.trainingtracker.ui

import com.atrainingtracker.banalservice.ui.devices.devicedata.PairedRemoteDeviceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying database manager decoupling across Phase C recording
 * services and device connection management (REQ-TRK-066, TST-TRK-066.1).
 *
 * Asserts that live recording services and managers (TrackerService, BANALService, DeviceManager)
 * eliminate direct `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)` calls,
 * routing database operations cleanly through domain repositories.
 * Additionally verifies that DeviceManager eliminates direct raw Cursor queries and SQLite
 * table references, accessing paired remote devices exclusively via PairedRemoteDeviceDto.
 */
class RepositoryServiceCleanlinessContractTest {

    private fun resolveFile(relativePath: String): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val fullRel = "src" + "/main/" + relativePath
        val candidates = listOf(
            File(projectDir, fullRel),
            File(projectDir, "app/$fullRel"),
            File(projectDir, "../app/$fullRel")
        )
        return candidates.firstOrNull { it.exists() } ?: File(projectDir, "app/$fullRel")
    }

    private val directDbInstantiationPattern = Regex("""\b\w*(DatabaseManager|DbHelper)\s*\.\s*getInstance\s*\(""")

    @Test
    fun testTrackerService_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java")
        assertTrue("TrackerService.java must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "TrackerService.java must contain zero DatabaseManager/DbHelper getInstance references (REQ-TRK-066.1), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "TrackerService.java must route summary, lap, and live sample persistence through WorkoutRepository",
            content.contains("getWorkoutRepository()")
        )
        assertTrue(
            "TrackerService.java must route sport type lookups through SportTypesRepository",
            content.contains("getSportTypesRepository()")
        )
        assertTrue(
            "TrackerService.java must route active device recording through DeviceDataRepository",
            content.contains("getDeviceDataRepository()")
        )
        assertTrue(
            "TrackerService.java must route favorite location geocoding through KnownLocationsRepository",
            content.contains("getKnownLocationsRepository()")
        )
        assertTrue(
            "TrackerService.java must route beat-to-beat RR intervals through WorkoutRrRepository",
            content.contains("getWorkoutRrRepository()")
        )
    }

    @Test
    fun testBanalService_zeroDatabaseManagerInstantiation() {
        val file = resolveFile("java/com/atrainingtracker/banalservice/BANALService.java")
        assertTrue("BANALService.java must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "BANALService.java must contain zero DatabaseManager/DbHelper getInstance references (REQ-TRK-066.2), found: $matches",
            matches.isEmpty()
        )
        assertTrue(
            "BANALService.java must route sport queries through SportTypesRepository",
            content.contains("SportTypesRepository")
        )
    }

    @Test
    fun testDeviceManager_zeroDatabaseManagerInstantiationAndZeroCursorLeak() {
        val file = resolveFile("java/com/atrainingtracker/banalservice/devices/DeviceManager.java")
        assertTrue("DeviceManager.java must exist", file.exists())
        val content = file.readText()

        val matches = directDbInstantiationPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "DeviceManager.java must contain zero DatabaseManager/DbHelper getInstance references (REQ-TRK-066.3), found: $matches",
            matches.isEmpty()
        )
        assertFalse(
            "DeviceManager.java must not import or use Cursor (zero Cursor leaks)",
            content.contains("import android.database.Cursor;") || content.contains("Cursor cursor")
        )
        assertFalse(
            "DeviceManager.java must not import or use SQLiteDatabase",
            content.contains("import android.database.sqlite.SQLiteDatabase;") || content.contains("SQLiteDatabase db")
        )
        assertFalse(
            "DeviceManager.java must not directly reference DevicesDbHelper.DEVICES table",
            content.contains("DevicesDbHelper.DEVICES")
        )
        assertTrue(
            "DeviceManager.java must query paired devices via DeviceDataRepository.getPairedRemoteDevices",
            content.contains("getPairedRemoteDevices")
        )
    }

    @Test
    fun testPairedRemoteDeviceDto_contractValidation() {
        val dto = PairedRemoteDeviceDto(
            id = 42L,
            isPaired = true,
            deviceType = com.atrainingtracker.banalservice.devices.DeviceType.HRM,
            antDeviceNumber = 12345,
            btAddress = "00:11:22:33:44:55"
        )
        assertEquals(42L, dto.id)
        assertTrue(dto.isPaired)
        assertEquals(com.atrainingtracker.banalservice.devices.DeviceType.HRM, dto.deviceType)
        assertEquals(12345, dto.antDeviceNumber)
        assertEquals("00:11:22:33:44:55", dto.btAddress)
    }
}
