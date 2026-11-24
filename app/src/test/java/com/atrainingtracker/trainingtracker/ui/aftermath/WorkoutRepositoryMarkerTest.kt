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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.IntentFilter
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.atrainingtracker.trainingtracker.ui.map.RadarPassColorCategory
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying start and finish marker title annotations with favorite location names
 * in [WorkoutRepository.getWorkoutMarkers] (ATT-1400 / REQ-UI-184 / TST-UI-137.3).
 */
class WorkoutRepositoryMarkerTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockSamplesSqlite = mockk<SQLiteDatabase>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    private val startPos = LatLng(48.137, 11.576)
    private val endPos = LatLng(48.140, 11.580)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.getExtremaValue(any(), any(), any()) } returns null
        every { mockSummariesDb.getExtremaPosition(any(), any(), any()) } returns null

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { WorkoutSamplesDatabaseManager.getTableName(any()) } answers { firstArg() }
        every { mockSamplesDb.getExtremaPosition(any(), any(), any(), any()) } returns null
        every { mockSamplesDb.database } returns mockSamplesSqlite

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(ExportStatusDataProvider::class)
        every { anyConstructed<ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        every { mockApplication.getString(R.string.Start) } returns "Start"
        every { mockApplication.getString(R.string.Stop) } returns "Stop"
        every { mockApplication.getString(R.string.radar_vehicle_size_car) } returns "Car"
        every { mockApplication.getString(R.string.radar_vehicle_size_truck) } returns "Heavy Vehicle (Truck/Bus)"
        every { mockApplication.getString(R.string.radar_marker_pass_title, *anyVararg()) } answers {
            val varargs = args[1] as Array<*>
            "Vehicle Pass (${varargs[0]} km/h)"
        }
        every { mockApplication.getString(R.string.radar_marker_high_threat_title, *anyVararg()) } answers {
            val varargs = args[1] as Array<*>
            "High-Threat Pass (${varargs[0]} km/h)"
        }
        every { mockApplication.getString(R.string.radar_marker_close_pass_title, *anyVararg()) } answers {
            val varargs = args[1] as Array<*>
            "Close Pass (${varargs[0]}m) • ${varargs[1]} • ${varargs[2]} km/h"
        }

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkStatic(WorkoutSummariesDatabaseManager::class)
        unmockkStatic(com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager::class)
        unmockkAll()
    }

    private fun createWorkoutData(
        startLatLng: LatLng? = null,
        endLatLng: LatLng? = null,
        startLocationName: String? = null,
        endLocationName: String? = null,
        radarTrafficSummary: RadarTrafficSummary? = null
    ): WorkoutData {
        return WorkoutData(
            id = 1L,
            finished = true,
            fileBaseName = "2026-09-29-10-00-00",
            workoutName = "Test Workout",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-29",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 5.5,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = startLatLng,
            endLatLng = endLatLng,
            startLocationName = startLocationName,
            endLocationName = endLocationName,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            radarTrafficSummary = radarTrafficSummary
        )
    }

    @Test
    fun testWorkoutMarkers_withLocations_formatsLocationTitles() = runBlocking {
        val workoutData = createWorkoutData(
            startLatLng = startPos,
            endLatLng = endPos,
            startLocationName = "Zuhause",
            endLocationName = "Büro"
        )

        val markers = repository.getWorkoutMarkers(workoutData)

        val startMarker = markers.find { it.iconResId == R.drawable.control_start }
        val stopMarker = markers.find { it.iconResId == R.drawable.control_stop }

        assertNotNull("Start marker must be present", startMarker)
        assertEquals("Start: Zuhause", startMarker?.title)

        assertNotNull("Stop marker must be present", stopMarker)
        assertEquals("Stop: Büro", stopMarker?.title)
    }

    @Test
    fun testWorkoutMarkers_withoutLocations_defaultsToStandardTitles() = runBlocking {
        val workoutData = createWorkoutData(
            startLatLng = startPos,
            endLatLng = endPos,
            startLocationName = null,
            endLocationName = null
        )

        val markers = repository.getWorkoutMarkers(workoutData)

        val startMarker = markers.find { it.iconResId == R.drawable.control_start }
        val stopMarker = markers.find { it.iconResId == R.drawable.control_stop }

        assertNotNull("Start marker must be present", startMarker)
        assertEquals("Start", startMarker?.title)

        assertNotNull("Stop marker must be present", stopMarker)
        assertEquals("Stop", stopMarker?.title)
    }

    @Test
    fun testWorkoutMarkers_withoutCoordinates_omitsMarkers() = runBlocking {
        val workoutData = createWorkoutData(
            startLatLng = null,
            endLatLng = null
        )

        val markers = repository.getWorkoutMarkers(workoutData)

        val startMarker = markers.find { it.iconResId == R.drawable.control_start }
        val stopMarker = markers.find { it.iconResId == R.drawable.control_stop }

        assertNull("Start marker must be omitted when start coordinate is null", startMarker)
        assertNull("Stop marker must be omitted when end coordinate is null", stopMarker)
    }

    /**
     * TST-RAD-006 (ATT-8735): Verifies radar pass markers emit compact car/truck icon metadata,
     * centered anchor, priority closing-speed/close-pass color categories, unique monotonic passIndex,
     * and per-pass delta `radarLargeVehicleCount` detection (cumulative count must not mislabel
     * subsequent cars as trucks).
     */
    @Test
    fun testWorkoutMarkers_radarPasses_colorPriorityDeltaLargeVehicleAndCenteredCarIcons() = runBlocking {
        data class RadarSampleRow(
            val lat: Double,
            val lng: Double,
            val vehCount: Int,
            val largeVehCount: Int?,
            val approachMps: Double,
            val passingDistM: Double?
        )

        val rows = listOf(
            // Pass 1: Low speed car (< 7 m/s) -> Green car
            RadarSampleRow(48.1371, 11.5761, vehCount = 1, largeVehCount = 0, approachMps = 5.0, passingDistM = 2.2),
            // Pass 2: Moderate speed car (10 m/s) -> Amber car
            RadarSampleRow(48.1375, 11.5765, vehCount = 2, largeVehCount = 0, approachMps = 10.0, passingDistM = 1.9),
            // Pass 3: High speed car (16 m/s >= 15 m/s) -> Red car
            RadarSampleRow(48.1380, 11.5770, vehCount = 3, largeVehCount = 0, approachMps = 16.0, passingDistM = 2.0),
            // Pass 4: Close pass TRUCK (largeVehCount increments 0 -> 1, passingDist 1.1m < 1.5m) -> Brown truck
            RadarSampleRow(48.1385, 11.5775, vehCount = 4, largeVehCount = 1, approachMps = 18.0, passingDistM = 1.1),
            // Pass 5: Close pass CAR AFTER truck (cumulative largeVehCount stays 1!) -> MUST be Brown CAR, not truck!
            RadarSampleRow(48.1390, 11.5780, vehCount = 5, largeVehCount = 1, approachMps = 9.0, passingDistM = 1.2)
        )

        val cursor = buildRadarSamplesCursor(rows.map { r ->
            mapOf(
                SensorType.LATITUDE.name to r.lat,
                SensorType.LONGITUDE.name to r.lng,
                SensorType.RADAR_VEHICLE_COUNT.name to r.vehCount,
                SensorType.RADAR_LARGE_VEHICLE_COUNT.name to r.largeVehCount,
                SensorType.RADAR_APPROACH_SPEED_mps.name to r.approachMps,
                SensorType.RADAR_PASSING_DISTANCE_m.name to r.passingDistM
            )
        })
        every { mockSamplesSqlite.query(any(), any(), any(), any(), any(), any(), any()) } returns cursor

        val workoutData = createWorkoutData(
            startLatLng = startPos,
            endLatLng = endPos,
            radarTrafficSummary = RadarTrafficSummary(totalVehicles = 5)
        )

        val markers = repository.getWorkoutMarkers(workoutData)
        val radarMarkers = markers.filter { it.radarPass != null }

        assertEquals("Expected 5 radar pass markers", 5, radarMarkers.size)
        assertTrue("No radar marker should use legacy bike_radar pin", radarMarkers.none { it.iconResId == R.drawable.bike_radar })
        assertTrue("All radar markers must be centered at Offset(0.5, 0.5)", radarMarkers.all { it.anchor == Offset(0.5f, 0.5f) })
        assertEquals(listOf(1, 2, 3, 4, 5), radarMarkers.map { it.radarPass!!.passIndex })

        // Pass 1: Green Car
        assertEquals(RadarPassColorCategory.LOW_SPEED, radarMarkers[0].radarPass!!.colorCategory)
        assertFalse(radarMarkers[0].radarPass!!.isLargeVehicle)
        assertEquals(R.drawable.ic_radar_map_car, radarMarkers[0].iconResId)

        // Pass 2: Amber Car
        assertEquals(RadarPassColorCategory.MODERATE_SPEED, radarMarkers[1].radarPass!!.colorCategory)
        assertFalse(radarMarkers[1].radarPass!!.isLargeVehicle)
        assertEquals(R.drawable.ic_radar_map_car, radarMarkers[1].iconResId)

        // Pass 3: Red Car
        assertEquals(RadarPassColorCategory.HIGH_SPEED, radarMarkers[2].radarPass!!.colorCategory)
        assertFalse(radarMarkers[2].radarPass!!.isLargeVehicle)
        assertEquals(R.drawable.ic_radar_map_car, radarMarkers[2].iconResId)

        // Pass 4: Brown Truck (delta largeVehCount 0 -> 1)
        assertEquals(RadarPassColorCategory.CLOSE_PASS, radarMarkers[3].radarPass!!.colorCategory)
        assertTrue("Pass 4 must detect truck via delta largeVehCount", radarMarkers[3].radarPass!!.isLargeVehicle)
        assertEquals(R.drawable.ic_radar_map_truck, radarMarkers[3].iconResId)
        assertTrue(radarMarkers[3].title!!.contains("Heavy Vehicle (Truck/Bus)"))

        // Pass 5: Brown Car (cumulative largeVehCount remained 1 -> delta is 0 -> MUST NOT be truck!)
        assertEquals(RadarPassColorCategory.CLOSE_PASS, radarMarkers[4].radarPass!!.colorCategory)
        assertFalse("Pass 5 must be car when cumulative radarLargeVehicleCount did not increase", radarMarkers[4].radarPass!!.isLargeVehicle)
        assertEquals(R.drawable.ic_radar_map_car, radarMarkers[4].iconResId)
        assertTrue(radarMarkers[4].title!!.contains("Car"))
        assertFalse(radarMarkers[4].title!!.contains("Truck"))
    }

    private fun buildRadarSamplesCursor(rows: List<Map<String, Any?>>): Cursor {
        val columns = listOf(
            SensorType.LATITUDE.name,
            SensorType.LONGITUDE.name,
            SensorType.RADAR_VEHICLE_COUNT.name,
            SensorType.RADAR_LARGE_VEHICLE_COUNT.name,
            SensorType.RADAR_APPROACH_SPEED_mps.name,
            SensorType.RADAR_PASSING_DISTANCE_m.name
        )
        var rowIndex = -1
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.getColumnIndex(any()) } answers { columns.indexOf(firstArg<String>()) }
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val idx = columns.indexOf(firstArg<String>())
            if (idx >= 0) idx else throw IllegalArgumentException("Column not found: ${firstArg<String>()}")
        }
        every { cursor.moveToNext() } answers {
            rowIndex++
            rowIndex < rows.size
        }
        every { cursor.isNull(any()) } answers {
            val col = columns.getOrNull(firstArg<Int>()) ?: return@answers true
            rows[rowIndex][col] == null
        }
        every { cursor.getDouble(any()) } answers {
            val col = columns[firstArg<Int>()]
            (rows[rowIndex][col] as Number).toDouble()
        }
        every { cursor.getInt(any()) } answers {
            val col = columns[firstArg<Int>()]
            (rows[rowIndex][col] as Number).toInt()
        }
        return cursor
    }
}
