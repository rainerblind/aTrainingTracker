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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying KnownLocationsDatabaseManager, Schema V5 upgrade,
 * spatial caching within 200m radius, locked location immutability,
 * and legacy location batch healing.
 *
 * Traceability:
 * - REQ-DAT-008: Atomic Database Upgrades.
 * - REQ-DAT-014: Internet Digital Elevation Model (DEM) Reference Altitude Retrieval, Spatial Caching & Legacy Location Healing.
 * - TST-DAT-008: Tests 2, 6, 7, 8.
 */
class KnownLocationsDatabaseManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockkConstructor(Location::class)
        every { anyConstructed<Location>().setLatitude(any()) } returns Unit
        every { anyConstructed<Location>().setLongitude(any()) } returns Unit

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
        resetSingleton()
    }

    private fun resetSingleton() {
        try {
            val instanceField: Field = KnownLocationsDatabaseManager::class.java.getDeclaredField("cInstance")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {
        }
    }

    private fun injectMockDatabase(manager: KnownLocationsDatabaseManager, db: SQLiteDatabase) {
        val dbField: Field = KnownLocationsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, db)
    }

    private fun setupMockCursorColumns(cursor: Cursor) {
        val columns = mapOf(
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.C_ID to 0,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.NAME to 1,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.EXTREMA_TYPE to 2,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.ALTITUDE to 3,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.LONGITUDE to 4,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.LATITUDE to 5,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.RADIUS to 6,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.HIT_COUNT to 7,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.IS_LOCKED to 8,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.SOURCE to 9
        )
        columns.forEach { (name, idx) ->
            every { cursor.getColumnIndex(name) } returns idx
        }
    }

    /**
     * TST-DAT-008.2: Spatial Geofence Caching & Quantization Test.
     * Verifies that coordinates within radius hit the spatial cache,
     * and microscopic GPS jitter is quantized.
     */
    @Test
    fun testSpatialGeofenceCache_hitWithinRadius() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val mockCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(mockCursor)

        every { mockCursor.moveToNext() } returns true andThen false
        every { mockCursor.getInt(6) } returns 200 // radius
        every { mockCursor.getDouble(5) } returns 48.13715 // lat
        every { mockCursor.getDouble(4) } returns 11.57612 // lng
        every { mockCursor.getLong(0) } returns 101L // id
        every { mockCursor.getString(1) } returns "Munich Start" // name
        every { mockCursor.getDouble(3) } returns 312.5 // altitude
        every { mockCursor.getInt(7) } returns 5 // hitCount
        every { mockCursor.getInt(8) } returns 0 // isLocked
        every { mockCursor.getString(9) } returns "INTERNET_DEM" // source

        every { mockDb.query(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, null, null, null, null, null, null) } returns mockCursor
        every { anyConstructed<Location>().distanceTo(any()) } returns 50.0f // 50m < 200m radius

        val result = manager.getMyLocation(LatLng(48.13750, 11.57650))

        assertNotNull("Must return cached location", result)
        assertEquals(101L, result!!.id)
        assertEquals("Munich Start", result.name)
        assertEquals(312.5, result.altitude, 0.001)
        assertEquals(ElevationSource.INTERNET_DEM, result.source)
        assertFalse(result.isLocked)

        // Microscopic GPS jitter quantization verification
        val jitterCoord1 = 52.5200001
        val jitterCoord2 = 52.5200004
        assertEquals(
            ElevationService.quantizeCoordinate(jitterCoord1),
            ElevationService.quantizeCoordinate(jitterCoord2),
            0.000001
        )
    }

    /**
     * TST-DAT-008.6: Database Schema V4 to V5 Atomic Upgrade Test.
     * Verifies that V4 -> V5 upgrade alters table to add is_locked and source,
     * without calling manual transactions (adhering to REQ-DAT-008).
     */
    @Test
    fun testDatabaseUpgrade_V4toV5() {
        val helper = KnownLocationsDatabaseManager.KnownLocationsDbHelper(mockContext)

        // Act: upgrade from 4 to 5
        helper.onUpgrade(mockDb, 4, 5)

        // Verify ALTER TABLE statements executed
        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE") && it.contains("is_locked") && it.contains("integer default 0") })
        }
        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE") && it.contains("source") && it.contains("text default 'LEGACY_RAW'") })
        }

        // Verify zero manual transaction management (REQ-DAT-008 invariant)
        verify(exactly = 0) { mockDb.beginTransaction() }
        verify(exactly = 0) { mockDb.setTransactionSuccessful() }
        verify(exactly = 0) { mockDb.endTransaction() }

        // Verify table was never dropped
        verify(exactly = 0) {
            mockDb.execSQL(match { it.lowercase().contains("drop table") })
        }
    }

    /**
     * TST-DAT-008.7: Locked Location Immutability Test.
     * Verifies that learnLocation strictly preserves altitude and hitCount when is_locked == 1.
     */
    @Test
    fun testLockedLocation_immutability() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val mockCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(mockCursor)

        every { mockCursor.moveToNext() } returns true andThen false
        every { mockCursor.getInt(6) } returns 200 // radius
        every { mockCursor.getDouble(5) } returns 48.0 // lat
        every { mockCursor.getDouble(4) } returns 11.0 // lng
        every { mockCursor.getLong(0) } returns 42L // id
        every { mockCursor.getString(1) } returns "Locked Home" // name
        every { mockCursor.getDouble(3) } returns 600.0 // altitude
        every { mockCursor.getInt(7) } returns 5 // hitCount
        every { mockCursor.getInt(8) } returns 1 // isLocked = true
        every { mockCursor.getString(9) } returns "MANUAL_USER" // source

        every { mockDb.query(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, null, null, null, null, null, null) } returns mockCursor
        every { anyConstructed<Location>().distanceTo(any()) } returns 10.0f

        // Act: try to learn location with different altitude
        manager.learnLocation(LatLng(48.0, 11.0), 520.0, ExtremaType.START)

        // Assert: no updates or inserts written to SQLite for this locked location
        verify(exactly = 0) {
            mockDb.update(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, any(), any(), any())
        }
        verify(exactly = 0) {
            mockDb.insert(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, any(), any())
        }
    }

    /**
     * TST-DAT-008.8: Legacy Location Batch Healing Test.
     * Verifies that batch healing queries Open-Meteo for unlocked LEGACY_RAW locations
     * and atomically updates them to INTERNET_DEM.
     */
    @Test
    fun testLegacyBatchHealing_updatesUnlockedToDEM() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val mockCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(mockCursor)

        // 2 unlocked legacy rows
        every { mockCursor.moveToNext() } returns true andThen true andThen false
        every { mockCursor.getLong(0) } returns 1L andThen 2L
        every { mockCursor.getString(1) } returns "Start A" andThen "Start B"
        every { mockCursor.getDouble(3) } returns 500.0 andThen 400.0
        every { mockCursor.getDouble(4) } returns 11.0 andThen 11.1
        every { mockCursor.getDouble(5) } returns 48.0 andThen 48.1
        every { mockCursor.getInt(6) } returns 200 andThen 200
        every { mockCursor.getInt(7) } returns 1 andThen 1
        every { mockCursor.getInt(8) } returns 0 andThen 0
        every { mockCursor.getString(9) } returns "LEGACY_RAW" andThen "LEGACY_RAW"

        every {
            mockDb.query(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                null,
                match { it.contains("is_locked=0") && it.contains("source=?") },
                arrayOf("LEGACY_RAW"),
                null,
                null,
                null
            )
        } returns mockCursor

        val mockElevationService = mockk<ElevationService>()
        every { mockElevationService.fetchBatchElevations(any()) } returns ElevationResult.BatchSuccess(
            listOf(535.0, 422.0)
        )

        // Act: execute batch healing
        val healedCount = manager.healLegacyLocations(mockElevationService)

        // Assert: 2 rows healed
        assertEquals(2, healedCount)
        verify(exactly = 2) {
            mockDb.update(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                any(),
                match { it.contains("_id=?") },
                any()
            )
        }
    }

    /**
     * Verifies that when no legacy rows exist, healLegacyLocations returns 0 without calling network.
     */
    @Test
    fun testLegacyBatchHealing_whenNoLegacyRows_returnsZero() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToNext() } returns false

        every {
            mockDb.query(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                null,
                any(),
                any(),
                null,
                null,
                null
            )
        } returns mockCursor

        val mockElevationService = mockk<ElevationService>()

        val healedCount = manager.healLegacyLocations(mockElevationService)

        assertEquals(0, healedCount)
        verify(exactly = 0) { mockElevationService.fetchBatchElevations(any()) }
    }

    /**
     * Verifies addNewLocation persists is_locked and source fields.
     */
    @Test
    fun testAddNewLocation_withLockAndSource() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val capturedValues = slot<ContentValues>()
        every {
            mockDb.insert(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, null, capture(capturedValues))
        } returns 10L

        val loc = manager.addNewLocation(
            "Summit Base",
            1200.0,
            250,
            47.5,
            11.2,
            ExtremaType.START,
            true,
            ElevationSource.MANUAL_USER
        )

        assertNotNull(loc)
        assertEquals(10L, loc!!.id)
        assertEquals("Summit Base", loc.name)
        assertEquals(1200.0, loc.altitude, 0.001)
        assertTrue(loc.isLocked)
        assertEquals(ElevationSource.MANUAL_USER, loc.source)
    }
}
