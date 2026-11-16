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

package com.atrainingtracker.banalservice.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Devices.db v13 migration, polarH10OfflineRecording column addition,
 * and DatabaseSchemaRegistry registration (REQ-SET-104.2, TST-SET-106.4, ATT-7762).
 */
class DevicesDatabaseManagerMigrationV13Test {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testDbVersionIsAtLeast13() {
        assertTrue(
            "DevicesDbHelper.DB_VERSION must be >= 13",
            DevicesDatabaseManager.DevicesDbHelper.DB_VERSION >= 13
        )
    }

    @Test
    fun testDatabaseSchemaRegistryMatchesDbVersion() {
        val maxVersion = DatabaseSchemaRegistry.getMaxSupportedVersion("Devices.db")
        assertNotNull("Devices.db must be registered in DatabaseSchemaRegistry", maxVersion)
        assertEquals(
            "DatabaseSchemaRegistry max supported version must match DB_VERSION",
            DevicesDatabaseManager.DevicesDbHelper.DB_VERSION,
            maxVersion
        )
    }

    @Test
    fun testOnUpgradeFrom12To13ExecutesAlterTableWithDefault1() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val helper = DevicesDatabaseManager.DevicesDbHelper(mockContext)

        helper.onUpgrade(mockDb, 12, 13)

        verify {
            mockDb.execSQL(match { sql ->
                sql.contains("ALTER TABLE Devices4 ADD COLUMN polarH10OfflineRecording int DEFAULT 1")
            })
        }
    }

    @Test
    fun testPolarH10OfflineRecordingConstant() {
        assertEquals("polarH10OfflineRecording", DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING)
    }
}
