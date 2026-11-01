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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit verification suite for [ElevationCacheDatabaseManager] and [ElevationCacheDbHelper].
 *
 * Traceability:
 * - REQ-SEN-025.2, TST-SEN-025.2: Spatial database, quantized (lat_e5, lon_e5) lookup, nearest neighbor.
 * - REQ-SEN-025.3, TST-SEN-025.3: Route and segment batch seeding.
 * - REQ-SEN-025.4, TST-SEN-025.4: Isolation from KnownLocations.db.
 * - Rule 20: Symmetric cursor mappers.
 * - Rule 62: DatabaseSchemaRegistry integration.
 */
@RunWith(RobolectricTestRunner::class)
class ElevationCacheDatabaseManagerTest {

    private lateinit var context: Context
    private lateinit var cacheDb: ElevationCacheDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ElevationCacheDbHelper.DB_NAME)
        ElevationCacheDatabaseManager.resetForTesting(null)
        cacheDb = ElevationCacheDatabaseManager.getInstance(context)
    }

    @After
    fun tearDown() {
        cacheDb.close()
        context.deleteDatabase(ElevationCacheDbHelper.DB_NAME)
        ElevationCacheDatabaseManager.resetForTesting(null)
    }

    /**
     * TST-SEN-025.2 / Rule 62: DatabaseSchemaRegistry must reference ElevationCacheDbHelper.DB_VERSION.
     */
    @Test
    fun databaseSchemaRegistry_registersElevationCacheDbVersion() {
        val supportedVersion = DatabaseSchemaRegistry.getMaxSupportedVersion(ElevationCacheDbHelper.DB_NAME)
        assertNotNull(supportedVersion)
        assertEquals(ElevationCacheDbHelper.DB_VERSION, supportedVersion)
        assertTrue(DatabaseSchemaRegistry.isVersionSupported(ElevationCacheDbHelper.DB_NAME, 1))
    }

    /**
     * TST-SEN-025.2: Basic CRUD operations (put, get, count, clear).
     */
    @Test
    fun crudOperations_putAndGetElevation_clearsSuccessfully() {
        val lat = 48.13715
        val lon = 11.57612
        val alt = 520.5

        val rowId = cacheDb.putElevation(lat, lon, alt, "DEM_CACHE")
        assertTrue(rowId > 0)
        assertEquals(1L, cacheDb.getCacheCount())

        val retrievedAlt = cacheDb.getElevation(lat, lon)
        assertNotNull(retrievedAlt)
        assertEquals(alt, retrievedAlt!!, 1e-4)

        val deleted = cacheDb.clearCache()
        assertEquals(1, deleted)
        assertEquals(0L, cacheDb.getCacheCount())
        assertNull(cacheDb.getElevation(lat, lon))
    }

    /**
     * TST-SEN-025.2: Quantized coordinate lookup with spatial tolerance and nearest neighbor selection.
     */
    @Test
    fun spatialTolerance_findsNearestPointWithinTolerance_returnsNullOutside() {
        // Base point: E5 (4813715, 1157612)
        val baseLat = 48.13715
        val baseLon = 11.57612
        cacheDb.putElevation(baseLat, baseLon, 520.0, "BASE")

        // Point offset by 1 E5 (~1.1m)
        val nearLat = 48.13716
        val nearLon = 11.57613
        val nearAlt = cacheDb.getElevation(nearLat, nearLon, toleranceE5 = 2)
        assertNotNull(nearAlt)
        assertEquals(520.0, nearAlt!!, 1e-4)

        // Exact lookup (tolerance 0) fails for offset
        val exactFail = cacheDb.getElevation(nearLat, nearLon, toleranceE5 = 0)
        assertNull(exactFail)

        // Point offset by 5 E5 (~5.5m) exceeds tolerance of 2
        val farLat = 48.13720
        val farLon = 11.57612
        val farAlt = cacheDb.getElevation(farLat, farLon, toleranceE5 = 2)
        assertNull(farAlt)

        // Nearest neighbor test: add second point closer to target
        // Target: (4813717, 1157612)
        // Point 1 (base): (4813715, 1157612) -> dLat = 2, distSq = 4
        // Point 2 (closer): (4813716, 1157612) -> dLat = 1, distSq = 1
        cacheDb.putElevation(48.13716, 11.57612, 525.0, "CLOSER")
        val nearestAlt = cacheDb.getElevation(48.13717, 11.57612, toleranceE5 = 2)
        assertNotNull(nearestAlt)
        assertEquals(525.0, nearestAlt!!, 1e-4)
    }

    /**
     * Rule 20: Symmetric cursor mapping contract.
     */
    @Test
    fun mapperSymmetry_fromCursorAndToContentValues_preservesAllFields() {
        val original = ElevationCacheItem(
            id = 1L,
            latE5 = 4813715,
            lonE5 = 1157612,
            altitude = 519.8,
            source = "SYMMETRY_TEST",
            updatedAt = 1700000000000L
        )

        val cv = ElevationCacheItem.toContentValues(original)
        val db = cacheDb.database
        val rowId = db.insert(ElevationCacheDbHelper.TABLE_ELEVATION_CACHE, null, cv)
        assertTrue(rowId > 0)

        db.query(
            ElevationCacheDbHelper.TABLE_ELEVATION_CACHE,
            null,
            "${ElevationCacheDbHelper.COLUMN_ID} = ?",
            arrayOf(rowId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val mapped = ElevationCacheItem.fromCursor(cursor)
            assertEquals(rowId, mapped.id)
            assertEquals(original.latE5, mapped.latE5)
            assertEquals(original.lonE5, mapped.lonE5)
            assertEquals(original.altitude, mapped.altitude, 1e-4)
            assertEquals(original.source, mapped.source)
            assertEquals(original.updatedAt, mapped.updatedAt)

            cursor.moveToPosition(-1)
            val batch = ElevationCacheItem.fromCursorBatch(cursor)
            assertEquals(1, batch.size)
            assertEquals(mapped, batch[0])
        }
    }

    /**
     * TST-SEN-025.2: Unique compound coordinate index replaces duplicate entries upon conflict.
     */
    @Test
    fun uniqueConstraint_replacesExistingCoordinateOnConflict() {
        val lat = 48.13715
        val lon = 11.57612

        cacheDb.putElevation(lat, lon, 500.0, "FIRST")
        assertEquals(1L, cacheDb.getCacheCount())

        // Insert new altitude at identical coordinate
        cacheDb.putElevation(lat, lon, 525.0, "UPDATED")
        assertEquals(1L, cacheDb.getCacheCount()) // Count must not increase

        val alt = cacheDb.getElevation(lat, lon)
        assertEquals(525.0, alt!!, 1e-4)
    }

    /**
     * TST-SEN-025.3: Seeding cache from RoutesDatabaseManager route points.
     */
    @Test
    fun seedFromRoutes_importsRouteTrackpointsIntoCache() {
        context.deleteDatabase("Routes.db")
        val routesDb = RoutesDatabaseManager.getInstance(context)

        // Insert parent route to satisfy foreign key constraints
        val rDb = routesDb.getDatabase()
        val routeCv = ContentValues().apply {
            put(RoutesDatabaseManager.RouteContract.COLUMN_NAME, "Test Route")
            put(RoutesDatabaseManager.RouteContract.COLUMN_SPORT_TYPE, "BIKE")
            put(RoutesDatabaseManager.RouteContract.COLUMN_SOURCE, RouteSource.STRAVA.name)
            put(RoutesDatabaseManager.RouteContract.COLUMN_DISTANCE, 1000.0)
            put(RoutesDatabaseManager.RouteContract.COLUMN_ELEVATION_GAIN, 50.0)
        }
        val routeId = rDb.insert(RoutesDatabaseManager.RouteContract.TABLE_ROUTES, null, routeCv)
        assertTrue(routeId > 0)

        // Insert synthetic route points into route_points table
        val cv1 = ContentValues().apply {
            put(RoutesDatabaseManager.RouteContract.COLUMN_ROUTE_ID_FK, routeId)
            put(RoutesDatabaseManager.RouteContract.COLUMN_LAT, 47.99123)
            put(RoutesDatabaseManager.RouteContract.COLUMN_LNG, 11.45678)
            put(RoutesDatabaseManager.RouteContract.COLUMN_DIST_FROM_START, 0.0)
            put(RoutesDatabaseManager.RouteContract.COLUMN_ALTITUDE, 605.2)
        }
        val cv2 = ContentValues().apply {
            put(RoutesDatabaseManager.RouteContract.COLUMN_ROUTE_ID_FK, routeId)
            put(RoutesDatabaseManager.RouteContract.COLUMN_LAT, 47.99200)
            put(RoutesDatabaseManager.RouteContract.COLUMN_LNG, 11.45700)
            put(RoutesDatabaseManager.RouteContract.COLUMN_DIST_FROM_START, 100.0)
            put(RoutesDatabaseManager.RouteContract.COLUMN_ALTITUDE, 610.8)
        }
        val p1 = rDb.insert(RoutesDatabaseManager.RouteContract.TABLE_ROUTE_POINTS, null, cv1)
        val p2 = rDb.insert(RoutesDatabaseManager.RouteContract.TABLE_ROUTE_POINTS, null, cv2)
        assertTrue(p1 > 0)
        assertTrue(p2 > 0)

        val seededCount = cacheDb.seedFromRoutes(routesDb)
        assertEquals(2, seededCount)
        assertEquals(2L, cacheDb.getCacheCount())

        val elev1 = cacheDb.getElevation(47.99123, 11.45678)
        assertNotNull(elev1)
        assertEquals(605.2, elev1!!, 1e-4)

        context.deleteDatabase("Routes.db")
    }

    /**
     * TST-SEN-025.3: Seeding cache from SegmentsDatabaseManager segment streams.
     */
    @Test
    fun seedFromSegments_importsStreamPointsIntoCache() {
        context.deleteDatabase("Segments.db")
        val segmentsDb = SegmentsDatabaseManager.getInstance(context)

        val sDb = segmentsDb.database
        val cv1 = ContentValues().apply {
            put(SegmentsDatabaseManager.Segments.STRAVA_SEGMENT_ID, 12345L)
            put(SegmentsDatabaseManager.Segments.DISTANCE, 0.0)
            put(SegmentsDatabaseManager.Segments.LATITUDE, 46.50123)
            put(SegmentsDatabaseManager.Segments.LONGITUDE, 10.30456)
            put(SegmentsDatabaseManager.Segments.ALTITUDE, 1250.0)
        }
        sDb.insert(SegmentsDatabaseManager.Segments.TABLE_SEGMENT_STREAMS, null, cv1)

        val seededCount = cacheDb.seedFromSegments(segmentsDb)
        assertEquals(1, seededCount)
        assertEquals(1L, cacheDb.getCacheCount())

        val elev = cacheDb.getElevation(46.50123, 10.30456)
        assertNotNull(elev)
        assertEquals(1250.0, elev!!, 1e-4)

        context.deleteDatabase("Segments.db")
    }

    /**
     * TST-SEN-025.4: ElevationCacheDatabaseManager must strictly operate on ElevationCache.db
     * with zero writes or queries to KnownLocations.db.
     */
    @Test
    fun knownLocationsDecoupling_operatesWithZeroAccessToKnownLocationsDb() {
        // KnownLocations.db must not even be created by ElevationCacheDatabaseManager
        context.deleteDatabase("KnownLocations.db")

        cacheDb.putElevation(48.0, 11.0, 500.0, "DEM")
        val alt = cacheDb.getElevation(48.0, 11.0)
        assertEquals(500.0, alt!!, 1e-4)

        val knownLocDbFile = context.getDatabasePath("KnownLocations.db")
        assertTrue("ElevationCache must never touch or create KnownLocations.db", !knownLocDbFile.exists())
    }
}
