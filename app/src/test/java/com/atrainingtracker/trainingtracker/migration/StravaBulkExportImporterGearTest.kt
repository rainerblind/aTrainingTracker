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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests verifying that StravaBulkExportImporter passes null for stravaId
 * during gear synchronization, preventing StravaId name pollution (REQ-SET-098.3, TST-SET-098.2, ATT-6215).
 */
class StravaBulkExportImporterGearTest {

    private lateinit var mockContext: Context
    private lateinit var mockEquipmentDb: EquipmentDbHelper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockContext = mockk(relaxed = true)
        mockEquipmentDb = mockk(relaxed = true)

        mockkStatic(EquipmentDbHelper::class)
        every { EquipmentDbHelper.getInstance(any()) } returns mockEquipmentDb
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSyncEquipmentFromArchive_passesNullStravaIdForBikesAndShoes() = runBlocking {
        every { mockEquipmentDb.getEquipmentId(any()) } returns -1L

        val capturedStravaIds = mutableListOf<String?>()
        val capturedNames = mutableListOf<String>()

        every {
            mockEquipmentDb.addOrUpdateStravaGear(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            capturedStravaIds.add(firstArg())
            capturedNames.add(secondArg())
            100L
        }

        val tempZip = File.createTempFile("strava_gear_test", ".zip")
        try {
            val bikesCsv = """
                Bike Name,Brand,Model,Weight,Primary
                Endurance,Canyon,Endurace AL 7.0,8.4,true
            """.trimIndent()

            val shoesCsv = """
                Shoe Name,Brand,Model,Primary
                Brooks Pure Flow,Brooks,Pure Flow 4,true
            """.trimIndent()

            ZipOutputStream(FileOutputStream(tempZip)).use { zip ->
                zip.putNextEntry(ZipEntry("bikes.csv"))
                zip.write(bikesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("shoes.csv"))
                zip.write(shoesCsv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Minimal activities.csv
                zip.putNextEntry(ZipEntry("activities.csv"))
                zip.write("Activity ID,Filename\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            StravaBulkExportImporter.importArchive(mockContext, tempZip, null)

            assertEquals(2, capturedNames.size)
            assertEquals("Endurance", capturedNames[0])
            assertNull("Bike StravaId must be null, not plaintext name", capturedStravaIds[0])

            assertEquals("Brooks Pure Flow", capturedNames[1])
            assertNull("Shoe StravaId must be null, not plaintext name", capturedStravaIds[1])
        } finally {
            tempZip.delete()
        }
    }
}
