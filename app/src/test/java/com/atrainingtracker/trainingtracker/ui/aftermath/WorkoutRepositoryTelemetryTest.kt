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
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit verification for [WorkoutRepository.getWorkoutTelemetryPoints] (REQ-UI-235 / TST-UI-194.1 / ATT-2006).
 */
class WorkoutRepositoryTelemetryTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockSqliteDb = mockk<SQLiteDatabase>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { mockSamplesDb.database } returns mockSqliteDb

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testExtractTelemetryPoints_whenNoTableOrBaseName_returnsEmptyList() = runBlocking {
        every { mockSummariesDb.getBaseFileName(1L) } returns null
        val resultNullBase = repository.getWorkoutTelemetryPoints(1L)
        assertTrue(resultNullBase.isEmpty())

        every { mockSummariesDb.getBaseFileName(2L) } returns "2026-10-02-12-00-00"
        every { mockSamplesDb.existsTable("2026-10-02-12-00-00") } returns false
        val resultNoTable = repository.getWorkoutTelemetryPoints(2L)
        assertTrue(resultNoTable.isEmpty())
    }

    @Test
    fun testExtractTelemetryPoints_whenNoGpsCoordinates_returnsValidTimeDomainPoints() = runBlocking {
        val baseFileName = "2026-10-02-12-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(100L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            SensorType.POWER.name,
            SensorType.SPEED_mps.name
        )
        val rows = listOf(
            listOf(0L, 120, 180, 5.0),
            listOf(1L, 122, 185, 5.2),
            listOf(2L, 125, 190, 5.1)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(100L)
        assertEquals(3, points.size)
        assertEquals(0L, points[0].timeSec)
        assertEquals(120, points[0].hr)
        assertEquals(180, points[0].power)
        assertEquals(5.0, points[0].speedMps ?: 0.0, 0.001)
        assertEquals(0.0, points[0].distance, 0.0)
        assertEquals(LatLng(0.0, 0.0), points[0].latLng)
    }

    @Test
    fun testExtractTelemetryPoints_populatesHeartRateAndPower_withZeroDistance() = runBlocking {
        val baseFileName = "2026-10-02-13-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(200L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            SensorType.POWER.name
        )
        val rows = listOf(
            listOf(10L, 145, 220),
            listOf(20L, 150, 230)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(200L)
        assertEquals(2, points.size)
        assertEquals(145, points[0].hr)
        assertEquals(220, points[0].power)
        assertEquals(0.0, points[0].distance, 0.0)
        assertEquals(LatLng(0.0, 0.0), points[0].latLng)
        assertNull(points[0].altitude)
    }

    @Test
    fun testExtractTelemetryPoints_decimatesWhenOver800Points() = runBlocking {
        val baseFileName = "2026-10-02-14-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(300L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name
        )
        // 1600 samples
        val rows = (0 until 1600).map { i ->
            listOf(i.toLong(), 100 + (i % 60))
        }
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(300L)
        assertTrue("Points size should be <= 801, was ${points.size}", points.size <= 801)
        assertEquals("First point must be preserved", 0L, points.first().timeSec)
        assertEquals("Last point must be preserved", 1599L, points.last().timeSec)
    }

    @Test
    fun testExtractTelemetryPoints_populatesAltitude_whenPresentInSamplesDb() = runBlocking {
        val baseFileName = "2026-10-02-15-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(400L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            SensorType.ALTITUDE.name
        )
        val rows = listOf(
            listOf(0L, 130, 450.5),
            listOf(1L, 132, 452.0),
            listOf(2L, 135, 455.2)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(400L)
        assertEquals(3, points.size)
        assertEquals(450.5, points[0].altitude!!, 0.001)
        assertEquals(452.0, points[1].altitude!!, 0.001)
        assertEquals(455.2, points[2].altitude!!, 0.001)
    }

    @Test
    fun testExtractTelemetryPoints_ingestsPointsWithOnlyAltitude_whenHrPowerSpeedAbsent() = runBlocking {
        val baseFileName = "2026-10-02-16-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(500L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.ALTITUDE.name
        )
        val rows = listOf(
            listOf(0L, 510.0),
            listOf(10L, 515.5)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(500L)
        assertEquals(2, points.size)
        assertEquals(0L, points[0].timeSec)
        assertEquals(510.0, points[0].altitude!!, 0.001)
        assertNull(points[0].hr)
        assertNull(points[0].power)
        assertNull(points[0].speedMps)
        assertEquals(10L, points[1].timeSec)
        assertEquals(515.5, points[1].altitude!!, 0.001)
    }

    @Test
    fun testExtractTelemetryPoints_retainsSeaLevelZeroAltitude_withoutHrPowerSpeed() = runBlocking {
        val baseFileName = "2026-10-02-19-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(800L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val columns = listOf(
            SensorType.TIME_ACTIVE.name,
            SensorType.ALTITUDE.name
        )
        val rows = listOf(
            listOf(0L, 0.0),
            listOf(10L, 0.0)
        )
        val cursor = MockCursorFactory.create(columns, rows)
        every { mockSqliteDb.query(tableName, null, null, null, null, null, null) } returns cursor

        val points = repository.getWorkoutTelemetryPoints(800L)
        assertEquals(2, points.size)
        assertEquals(0L, points[0].timeSec)
        assertEquals(0.0, points[0].altitude!!, 0.001)
        assertEquals(10L, points[1].timeSec)
        assertEquals(0.0, points[1].altitude!!, 0.001)
    }

    @Test
    fun testGetWorkoutTrackPoints_usesExplicitColumnProjection() = runBlocking {
        val baseFileName = "2026-10-02-17-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(600L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val pragmaCursor = MockCursorFactory.create(
            listOf("name"),
            listOf(
                listOf(TrackType.BEST.latitudeColumn),
                listOf(TrackType.BEST.longitudeColumn),
                listOf(SensorType.ALTITUDE.name),
                listOf(SensorType.DISTANCE_m.name),
                listOf(SensorType.TIME_ACTIVE.name)
            )
        )
        every { mockSqliteDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns pragmaCursor

        val dataCursor = MockCursorFactory.create(
            listOf(
                TrackType.BEST.latitudeColumn,
                TrackType.BEST.longitudeColumn,
                SensorType.ALTITUDE.name,
                SensorType.DISTANCE_m.name,
                SensorType.TIME_ACTIVE.name
            ),
            listOf(
                listOf(48.137, 11.575, 520.0, 100.0, 10L),
                listOf(48.138, 11.576, 522.0, 200.0, 20L)
            )
        )
        val capturedProjection = slot<Array<String>>()
        every { mockSqliteDb.query(tableName, capture(capturedProjection), null, null, null, null, null) } returns dataCursor

        val points = repository.getWorkoutTrackPoints(600L, TrackType.BEST)
        assertEquals(2, points.size)
        assertEquals(48.137, points[0].latLng.latitude, 0.0001)
        assertEquals(11.575, points[0].latLng.longitude, 0.0001)
        assertTrue(capturedProjection.captured.contains(WorkoutRepository.quoteSqlIdentifier(TrackType.BEST.latitudeColumn)))
        assertTrue(capturedProjection.captured.contains(WorkoutRepository.quoteSqlIdentifier(TrackType.BEST.longitudeColumn)))
    }

    @Test
    fun testLoadWorkoutDetailTelemetry_singlePassConsolidation() = runBlocking {
        val baseFileName = "2026-10-02-18-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(700L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val pragmaCursor = MockCursorFactory.create(
            listOf("name"),
            listOf(
                listOf(TrackType.BEST.latitudeColumn),
                listOf(TrackType.BEST.longitudeColumn),
                listOf(SensorType.ALTITUDE.name),
                listOf(SensorType.DISTANCE_m.name),
                listOf(SensorType.TIME_ACTIVE.name),
                listOf(SensorType.HR.name),
                listOf(SensorType.POWER.name)
            )
        )
        every { mockSqliteDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns pragmaCursor

        val dataCursor = MockCursorFactory.create(
            listOf(
                TrackType.BEST.latitudeColumn,
                TrackType.BEST.longitudeColumn,
                SensorType.ALTITUDE.name,
                SensorType.DISTANCE_m.name,
                SensorType.TIME_ACTIVE.name,
                SensorType.HR.name,
                SensorType.POWER.name
            ),
            listOf(
                listOf(48.137, 11.575, 520.0, 100.0, 10L, 140, 200),
                listOf(48.138, 11.576, 522.0, 200.0, 20L, 150, 250)
            )
        )
        every { mockSqliteDb.query(tableName, any(), null, null, null, null, null) } returns dataCursor

        val telemetry = repository.loadWorkoutDetailTelemetry(700L, com.atrainingtracker.banalservice.BSportType.BIKE)
        assertEquals(1, telemetry.tracks.size)
        assertTrue(telemetry.tracks.containsKey(TrackType.BEST))
        assertEquals(2, telemetry.tracks[TrackType.BEST]?.size)
        assertNotNull(telemetry.hrZoneDistribution)
        assertNotNull(telemetry.powerZoneDistribution)
    }

    /**
     * TST-SEN-025.8: loadWorkoutDetailTelemetry must extract null altitude as null (not 0.0)
     * so that missing altitude rows are interpolated by ElevationSmoothingMath.
     */
    @Test
    fun testLoadWorkoutDetailTelemetry_mapsNullAltitudeToNullRatherThanZero() = runBlocking {
        val baseFileName = "2026-11-03-06-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(800L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val pragmaCursor = MockCursorFactory.create(
            listOf("name"),
            listOf(
                listOf(TrackType.BEST.latitudeColumn),
                listOf(TrackType.BEST.longitudeColumn),
                listOf(SensorType.ALTITUDE.name),
                listOf(SensorType.DISTANCE_m.name),
                listOf(SensorType.TIME_ACTIVE.name)
            )
        )
        every { mockSqliteDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns pragmaCursor

        // Row 1 has null altitude (GPS fix present, but ALTITUDE sensor not yet resolved)
        // Row 2 has resolved altitude 435.0
        val dataCursor = MockCursorFactory.create(
            listOf(
                TrackType.BEST.latitudeColumn,
                TrackType.BEST.longitudeColumn,
                SensorType.ALTITUDE.name,
                SensorType.DISTANCE_m.name,
                SensorType.TIME_ACTIVE.name
            ),
            listOf(
                listOf(48.656, 9.054, null, 10.0, 1L),
                listOf(48.657, 9.055, 435.0, 20.0, 2L)
            )
        )
        every { mockSqliteDb.query(tableName, any(), null, null, null, null, null) } returns dataCursor

        val telemetry = repository.loadWorkoutDetailTelemetry(800L, com.atrainingtracker.banalservice.BSportType.BIKE)
        val bestTrack = telemetry.tracks[TrackType.BEST]
        assertNotNull(bestTrack)
        assertEquals(2, bestTrack!!.size)

        // Row 1 MUST have null altitude (not 0.0) so ElevationSmoothingMath can interpolate cleanly (REQ-SEN-025.8, ATT-5312)
        assertNull(bestTrack[0].altitude)
        assertEquals(435.0, bestTrack[1].altitude!!, 1e-4)
    }

    /**
     * TST-PER-050.1: Strict named-source column discovery ignores canonical ProxySensor + 1 named column,
     * discovers >= 2 distinct named sources (including dotted device names), and preserves pre-decimation meanValue.
     */
    @Test
    fun testDiscoverMultiSourceOverlayColumns_enforcesStrictTwoNamedSourcesAndDottedNames() {
        // TST-PER-050.1
        val singleNamedCols = listOf(
            SensorType.HR.name,
            "${SensorType.HR.name} (Polar H10)",
            SensorType.POWER.name,
            "${SensorType.POWER.name} (KICKR v5.0)",
            "${SensorType.POWER.name} (Assioma Duo)",
            "${SensorType.CADENCE.name} (Assioma)",
            "${SensorType.CADENCE.name} (KICKR)"
        )

        assertEquals(
            listOf("Polar H10" to "${SensorType.HR.name} (Polar H10)"),
            WorkoutRepository.discoverNamedSourceColumns(singleNamedCols, SensorType.HR)
        )

        val discovered = WorkoutRepository.discoverMultiSourceOverlayColumns(singleNamedCols)
        assertFalse(
            "1 named + canonical ProxySensor HR must not produce multi-sensor overlay",
            discovered.containsKey(TelemetryMetricType.HEART_RATE)
        )
        assertEquals(
            listOf(
                "Assioma Duo" to "${SensorType.POWER.name} (Assioma Duo)",
                "KICKR v5.0" to "${SensorType.POWER.name} (KICKR v5.0)"
            ),
            discovered[TelemetryMetricType.POWER]
        )
        assertEquals(
            listOf(
                "Assioma" to "${SensorType.CADENCE.name} (Assioma)",
                "KICKR" to "${SensorType.CADENCE.name} (KICKR)"
            ),
            discovered[TelemetryMetricType.CADENCE]
        )
    }

    @Test
    fun testLoadTracklessWorkoutTelemetry_extractsCadencePreDecimationMeansAndDottedColumns() = runBlocking {
        // TST-PER-050.1
        val baseFileName = "2026-11-16-07-30-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(901L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val rowCount = 1000
        val rows = (0 until rowCount).map { idx ->
            val hrPolar = if (idx < 50) 100 else 200
            val hrWatch = 150
            val kickrPower = if (idx < 500) 200 else 300
            val assiomaPower = 240
            val cadA = 85 + (idx % 5)
            val cadB = 87 + (idx % 5)
            listOf<Any?>(
                idx * 10.0,
                idx.toLong(),
                hrPolar,
                hrPolar,
                hrWatch,
                kickrPower,
                kickrPower,
                assiomaPower,
                cadA,
                cadA,
                cadB
            )
        }
        val dataCursor = MockCursorFactory.create(
            listOf(
                SensorType.DISTANCE_m.name,
                SensorType.TIME_ACTIVE.name,
                SensorType.HR.name,
                "${SensorType.HR.name} (Polar H10)",
                "${SensorType.HR.name} (Optical Watch)",
                SensorType.POWER.name,
                "${SensorType.POWER.name} (KICKR v5.0)",
                "${SensorType.POWER.name} (Assioma Duo)",
                SensorType.CADENCE.name,
                "${SensorType.CADENCE.name} (Assioma)",
                "${SensorType.CADENCE.name} (KICKR)"
            ),
            rows
        )
        every { mockSqliteDb.query(tableName, any(), null, null, null, null, null) } returns dataCursor

        val (points, overlays) = repository.loadTracklessWorkoutTelemetry(901L)
        assertTrue("Expected decimated points <= 800, was ${points.size}", points.size in 500..800)
        assertTrue(points.all { (it.cadence ?: 0) >= 85 })

        val hrOverlays = overlays[TelemetryMetricType.HEART_RATE].orEmpty()
        assertEquals(2, hrOverlays.size)
        val polarSeries = hrOverlays.first { it.sourceName == "Polar H10" }
        assertTrue(polarSeries.points.size <= 800)
        // Pre-decimation mean of 50*100 + 950*200 over 1000 rows = 195.0 exactly
        assertEquals(195.0, polarSeries.meanValue, 1e-6)

        val powerOverlays = overlays[TelemetryMetricType.POWER].orEmpty()
        assertEquals(listOf("Assioma Duo", "KICKR v5.0"), powerOverlays.map { it.sourceName })
        assertEquals(250.0, powerOverlays.first { it.sourceName == "KICKR v5.0" }.meanValue, 1e-6)

        val cadenceOverlays = overlays[TelemetryMetricType.CADENCE].orEmpty()
        assertEquals(listOf("Assioma", "KICKR"), cadenceOverlays.map { it.sourceName })
    }

    @Test
    fun testLoadWorkoutDetailTelemetry_gpsExtractsCadenceAndMultiSensorOverlays() = runBlocking {
        // TST-PER-050.1
        val baseFileName = "2026-11-16-08-00-00"
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        every { mockSummariesDb.getBaseFileName(902L) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val cols = listOf(
            TrackType.BEST.latitudeColumn,
            TrackType.BEST.longitudeColumn,
            SensorType.DISTANCE_m.name,
            SensorType.TIME_ACTIVE.name,
            SensorType.HR.name,
            "${SensorType.HR.name} (Chest Strap)",
            "${SensorType.HR.name} (Optical)",
            SensorType.POWER.name,
            "${SensorType.POWER.name} (Single Power)",
            SensorType.CADENCE.name,
            "${SensorType.CADENCE.name} (Pedal)",
            "${SensorType.CADENCE.name} (Hub)"
        )
        val pragmaCursor = MockCursorFactory.create(listOf("name"), cols.map { listOf(it) })
        every { mockSqliteDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns pragmaCursor

        val dataCursor = MockCursorFactory.create(
            cols,
            listOf(
                listOf(48.10, 11.50, 0.0, 0L, 140, 140, 134, 210, 210, 88, 88, 86),
                listOf(48.11, 11.51, 50.0, 10L, 150, 150, 146, 230, 230, 92, 92, 90)
            )
        )
        every { mockSqliteDb.query(tableName, any(), null, null, null, null, null) } returns dataCursor

        val telemetry = repository.loadWorkoutDetailTelemetry(902L, com.atrainingtracker.banalservice.BSportType.BIKE)
        val bestTrack = telemetry.tracks[TrackType.BEST].orEmpty()
        assertEquals(2, bestTrack.size)
        assertEquals(88, bestTrack[0].cadence)
        assertEquals(92, bestTrack[1].cadence)

        assertEquals(2, telemetry.multiSensorOverlays[TelemetryMetricType.HEART_RATE]?.size)
        assertEquals(2, telemetry.multiSensorOverlays[TelemetryMetricType.CADENCE]?.size)
        assertFalse(
            "Single named POWER column must not emit an overlay",
            telemetry.multiSensorOverlays.containsKey(TelemetryMetricType.POWER)
        )
    }
}
