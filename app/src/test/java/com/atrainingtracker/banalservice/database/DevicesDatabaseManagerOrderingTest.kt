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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.testutil.MockCursorFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit verification suite for sport-specific sensor ordering in [DevicesDatabaseManager.getSensorsForSportType]
 * and location devices initialization ordering in [DevicesDatabaseManager.DevicesDbHelper.addLocationDevicesToDb]
 * (REQ-UI-283, TST-UI-243.3, ATT-2464, REQ-TRK-024.6, TST-TRK-016.4, ATT-4375).
 */
@RunWith(RobolectricTestRunner::class)
class DevicesDatabaseManagerOrderingTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetSensorsForSportType_bike_returnsPrioritizedOrder() {
        // Cursor simulates alphabetical query return from SQLite
        val cursor = MockCursorFactory.create(
            columns = listOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE
            ),
            rows = listOf(
                listOf(1L, "Garmin HRM-Dual", "HRM"),
                listOf(2L, "Garmin Tempe", "ENVIRONMENT"),
                listOf(3L, "Stages Power", "BIKE_POWER"),
                listOf(4L, "Wahoo SPEED", "BIKE_SPEED")
            )
        )

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                any<String>(),
                isNull(),
                isNull(),
                isNull(),
                any<String>()
            )
        } returns cursor

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.getSensorsForSportType(BSportType.BIKE)

        assertEquals(4, result.size)
        // Bike priority: Power (3) -> Speed (4) -> HRM (1) -> Environment (2)
        assertEquals(listOf(3L, 4L, 1L, 2L), result.map { it.id })
        assertEquals(listOf("Stages Power", "Wahoo SPEED", "Garmin HRM-Dual", "Garmin Tempe"), result.map { it.name })
        assertEquals(DeviceType.BIKE_POWER, result[0].deviceType)
        assertEquals(DeviceType.BIKE_SPEED, result[1].deviceType)
        assertEquals(DeviceType.HRM, result[2].deviceType)
        assertEquals(DeviceType.ENVIRONMENT, result[3].deviceType)
    }

    @Test
    fun testGetSensorsForSportType_run_returnsPrioritizedOrder() {
        // Cursor simulates alphabetical query return from SQLite
        val cursor = MockCursorFactory.create(
            columns = listOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE
            ),
            rows = listOf(
                listOf(10L, "Garmin HRM-Dual", "HRM"),
                listOf(20L, "Garmin Tempe", "ENVIRONMENT"),
                listOf(30L, "Stryd Footpod", "RUN_SPEED")
            )
        )

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                any<String>(),
                isNull(),
                isNull(),
                isNull(),
                any<String>()
            )
        } returns cursor

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.getSensorsForSportType(BSportType.RUN)

        assertEquals(3, result.size)
        // Run priority: Footpod/Run speed (30) -> HRM (10) -> Environment (20)
        assertEquals(listOf(30L, 10L, 20L), result.map { it.id })
        assertEquals(listOf("Stryd Footpod", "Garmin HRM-Dual", "Garmin Tempe"), result.map { it.name })
        assertEquals(DeviceType.RUN_SPEED, result[0].deviceType)
        assertEquals(DeviceType.HRM, result[1].deviceType)
        assertEquals(DeviceType.ENVIRONMENT, result[2].deviceType)
    }

    /**
     * `TST-TRK-016.4`: Verifies that [DevicesDatabaseManager.DevicesDbHelper.addLocationDevicesToDb]
     * inserts location devices in sequence GPS (_id=1, paired=false), GOOGLE_FUSED (_id=2, paired=true),
     * and NETWORK (_id=3, paired=false) when Google Play Services is available (REQ-TRK-024.6, ATT-5491).
     */
    @Test
    fun testAddLocationDevicesToDb_whenPlayServicesAvailable_pairsGoogleFusedAndUnpairsGps() {
        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS

        mockkStatic(com.atrainingtracker.banalservice.helpers.HavePressureSensor::class)
        every { com.atrainingtracker.banalservice.helpers.HavePressureSensor.havePressureSensor(any()) } returns false

        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getString(any()) } returns "MockDevice"

        val db = SQLiteDatabase.create(null)
        val helper = DevicesDatabaseManager.DevicesDbHelper(testContext)

        helper.onCreate(db)

        val cursor = db.query(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            arrayOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                DevicesDatabaseManager.DevicesDbHelper.PAIRED
            ),
            "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL} = ?",
            arrayOf(Protocol.SMARTPHONE.name),
            null,
            null,
            "${DevicesDatabaseManager.DevicesDbHelper.C_ID} ASC"
        )

        val devices = mutableListOf<Triple<Long, String, Int>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val type = it.getString(1)
                val paired = it.getInt(2)
                devices.add(Triple(id, type, paired))
            }
        }

        assertTrue(devices.size >= 3)
        assertEquals(1L, devices[0].first)
        assertEquals(DeviceType.SPEED_AND_LOCATION_GPS.name, devices[0].second)
        assertEquals(0, devices[0].third) // Unpaired when Play Services is available

        assertEquals(2L, devices[1].first)
        assertEquals(DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED.name, devices[1].second)
        assertEquals(1, devices[1].third) // Paired by default when Play Services is available (REQ-TRK-024.6, ATT-5491)

        assertEquals(3L, devices[2].first)
        assertEquals(DeviceType.ALTITUDE_FROM_POSITION.name, devices[2].second)
        assertEquals(0, devices[2].third)
    }

    /**
     * `TST-TRK-016.4`: Verifies that [DevicesDatabaseManager.DevicesDbHelper.addLocationDevicesToDb]
     * falls back to pairing GPS (_id=1, paired=true) and unpairing GOOGLE_FUSED (_id=2, paired=false)
     * when Google Play Services is unavailable (de-Googled fallback) (REQ-TRK-024.6, ATT-5491).
     */
    @Test
    fun testAddLocationDevicesToDb_whenPlayServicesUnavailable_pairsGpsAndUnpairsGoogleFused() {
        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SERVICE_MISSING

        mockkStatic(com.atrainingtracker.banalservice.helpers.HavePressureSensor::class)
        every { com.atrainingtracker.banalservice.helpers.HavePressureSensor.havePressureSensor(any()) } returns false

        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getString(any()) } returns "MockDevice"

        val db = SQLiteDatabase.create(null)
        val helper = DevicesDatabaseManager.DevicesDbHelper(testContext)

        helper.onCreate(db)

        val cursor = db.query(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            arrayOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                DevicesDatabaseManager.DevicesDbHelper.PAIRED
            ),
            "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL} = ?",
            arrayOf(Protocol.SMARTPHONE.name),
            null,
            null,
            "${DevicesDatabaseManager.DevicesDbHelper.C_ID} ASC"
        )

        val devices = mutableListOf<Triple<Long, String, Int>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val type = it.getString(1)
                val paired = it.getInt(2)
                devices.add(Triple(id, type, paired))
            }
        }

        assertTrue(devices.size >= 3)
        assertEquals(1L, devices[0].first)
        assertEquals(DeviceType.SPEED_AND_LOCATION_GPS.name, devices[0].second)
        assertEquals(1, devices[0].third) // Paired on de-Googled fallback

        assertEquals(2L, devices[1].first)
        assertEquals(DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED.name, devices[1].second)
        assertEquals(0, devices[1].third) // Unpaired when Play Services is missing

        assertEquals(3L, devices[2].first)
        assertEquals(DeviceType.ALTITUDE_FROM_POSITION.name, devices[2].second)
        assertEquals(0, devices[2].third)
    }

    /**
     * `TST-SEN-027.2`: Verifies that [DevicesDatabaseManager.DevicesDbHelper.onUpgrade]
     * at version 10 executes [migrateRemoveSpeedAndLocationNetwork] and cleanly removes
     * any obsolete SPEED_AND_LOCATION_NETWORK rows from existing databases (REQ-SEN-027.2, ATT-5492).
     */
    @Test
    fun testDevicesDbUpgrade_v9ToV10_removesSpeedAndLocationNetworkRow() {
        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS

        mockkStatic(com.atrainingtracker.banalservice.helpers.HavePressureSensor::class)
        every { com.atrainingtracker.banalservice.helpers.HavePressureSensor.havePressureSensor(any()) } returns false

        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getString(any()) } returns "MockDevice"

        val db = SQLiteDatabase.create(null)
        val helper = DevicesDatabaseManager.DevicesDbHelper(testContext)

        helper.onCreate(db)

        // Insert a legacy SPEED_AND_LOCATION_NETWORK row manually
        val legacyRow = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL, Protocol.SMARTPHONE.name)
            put(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE, "SPEED_AND_LOCATION_NETWORK")
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, 0)
            put(DevicesDatabaseManager.DevicesDbHelper.NAME, "Legacy Network Location")
        }
        val insertedId = db.insert(DevicesDatabaseManager.DevicesDbHelper.DEVICES, null, legacyRow)
        assertTrue(insertedId > 0)

        // Upgrade from v9 to v10
        helper.onUpgrade(db, 9, 10)

        // Assert obsolete row is deleted
        val cursor = db.query(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            arrayOf(DevicesDatabaseManager.DevicesDbHelper.C_ID),
            "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE} = ?",
            arrayOf("SPEED_AND_LOCATION_NETWORK"),
            null,
            null,
            null
        )
        cursor.use {
            assertEquals(0, it.count)
        }
    }
}
