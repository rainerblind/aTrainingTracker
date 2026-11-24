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

import android.content.Context
import android.database.Cursor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.google.android.gms.maps.model.LatLng
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import android.util.Log
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Architectural contract test verifying database, DTO, and snapshot mapping symmetry (ATT-2387 / Rule 20).
 * Enforces that all cursor columns mapped in single-item [WorkoutDataMapper.fromCursor]
 * are symmetrically extracted into [WorkoutDataMapper.RawCursorSnapshot] and forwarded
 * into [WorkoutData] via [WorkoutDataMapper.fromSnapshot] and [WorkoutDataMapper.fromCursor] batch.
 */
// Linked test specs: TST-DAT-016, TST-UI-365, TST-LOG-001, TST-LOG-002
class WorkoutDataMappingSymmetryTest {

    private val context = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportTypeDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaUploadDb = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockClusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private lateinit var mapper: WorkoutDataMapper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(LapsDatabaseManager::class)
        val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb
        every { mockLapsDb.getLaps(any()) } returns emptyList()

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        every { mockClusterDb.getClusterNameById(any()) } returns "Test Cluster"

        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypeDb.getUIName(any()) } returns "Cycling"
        every { mockSportTypeDb.getStravaName(any()) } returns "Ride"
        every { mockEquipmentDb.getEquipmentNameFromId(any()) } returns "Road Bike"

        SettingsDataStore.setInstanceForTesting(mockk(relaxed = true))

        mapper = WorkoutDataMapper(
            context = context,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportTypeDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaUploadDb,
            knownLocationsDatabaseManager = null
        )
    }

    @org.junit.After
    fun tearDown() {
        SettingsDataStore.setInstanceForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        unmockkStatic(LapsDatabaseManager::class)
        unmockkStatic(TrainingApplication::class)
        unmockkStatic(Log::class)
    }

    private fun createMockCursor(sourceString: String? = "TCX", raceInt: Int = 1, epicInt: Int = 0): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)

        val columns = mapOf(
            WorkoutSummaries.C_ID to 0,
            WorkoutSummaries.SPORT_ID to 1,
            WorkoutSummaries.EQUIPMENT_ID to 2,
            WorkoutSummaries.TIME_START to 3,
            WorkoutSummaries.FILE_BASE_NAME to 4,
            WorkoutSummaries.DISTANCE_TOTAL_m to 5,
            WorkoutSummaries.MAP_POLYLINE to 6,
            WorkoutSummaries.TIME_ACTIVE_s to 7,
            WorkoutSummaries.TIME_TOTAL_s to 8,
            WorkoutSummaries.SPEED_AVERAGE_mps to 9,
            WorkoutSummaries.ASCENDING to 10,
            WorkoutSummaries.DESCENDING to 11,
            WorkoutSummaries.ALTITUDE_STREAM to 12,
            WorkoutSummaries.DISTANCE_STREAM to 13,
            WorkoutSummaries.WORKOUT_NAME to 14,
            WorkoutSummaries.CLUSTER_ID to 15,
            WorkoutSummaries.FINISHED to 16,
            WorkoutSummaries.COMMUTE to 17,
            WorkoutSummaries.TRAINER to 18,
            WorkoutSummaries.UPLOAD_TO_STRAVA to 19,
            WorkoutSummaries.BOUND_MIN_LAT to 20,
            WorkoutSummaries.BOUND_MIN_LNG to 21,
            WorkoutSummaries.BOUND_MAX_LAT to 22,
            WorkoutSummaries.BOUND_MAX_LNG to 23,
            WorkoutSummaries.DESCRIPTION to 24,
            WorkoutSummaries.GOAL to 25,
            WorkoutSummaries.METHOD to 26,
            WorkoutSummaries.RACE to 27,
            WorkoutSummaries.SOURCE to 28,
            WorkoutSummaries.EPIC to 29
        )

        columns.forEach { (colName, index) ->
            every { cursor.getColumnIndexOrThrow(colName) } returns index
            every { cursor.getColumnIndex(colName) } returns index
        }

        every { cursor.getLong(0) } returns 5023L
        every { cursor.getLong(1) } returns 2L
        every { cursor.getLong(2) } returns 10L
        every { cursor.getString(3) } returns "2026-09-09 10:02:00"
        every { cursor.getString(4) } returns "2026-09-09-10-02-00"
        every { cursor.getDouble(5) } returns 5220.0
        every { cursor.getString(6) } returns "k~g_I~...polyline"
        every { cursor.getLong(7) } returns 1811L
        every { cursor.getLong(8) } returns 1811L
        every { cursor.getDouble(9) } returns 2.88
        every { cursor.getLong(10) } returns 53L
        every { cursor.getLong(11) } returns 200L
        every { cursor.getString(12) } returns null
        every { cursor.getString(13) } returns null
        every { cursor.getString(14) } returns "Runter in die Stadt #1"
        every { cursor.getLong(15) } returns 42L
        every { cursor.getInt(16) } returns 1
        every { cursor.getInt(17) } returns 1
        every { cursor.getInt(18) } returns 0
        every { cursor.getInt(19) } returns 1
        every { cursor.getInt(27) } returns raceInt
        every { cursor.getString(28) } returns sourceString
        every { cursor.isNull(29) } returns false
        every { cursor.getInt(29) } returns epicInt

        return cursor
    }

    @Test
    fun testRule20_rawCursorSnapshot_declaresSourceProperty() {
        val snapshotFields = WorkoutDataMapper.RawCursorSnapshot::class.java.declaredFields.map { it.name }
        assertTrue("RawCursorSnapshot must declare 'source' field (Rule 20)", snapshotFields.contains("source"))
        assertTrue("RawCursorSnapshot must declare 'race' field", snapshotFields.contains("race"))
        assertTrue("RawCursorSnapshot must declare 'epic' field (REQ-DAT-050.2)", snapshotFields.contains("epic"))
        assertTrue("RawCursorSnapshot must declare 'workoutId' field", snapshotFields.contains("workoutId"))
    }

    @Test
    fun testRule20_workoutData_and_rawCursorSnapshot_sourceTypeParity() {
        val snapshotSourceField = WorkoutDataMapper.RawCursorSnapshot::class.java.getDeclaredField("source")
        val workoutDataSourceField = WorkoutData::class.java.getDeclaredField("source")

        assertEquals(WorkoutSource::class.java, snapshotSourceField.type)
        assertEquals(snapshotSourceField.type, workoutDataSourceField.type)
    }

    @Test
    fun testRule20_singleAndBatchMapping_yieldIdenticalProvenanceAndFlags() {
        val cursor = createMockCursor(sourceString = "TCX", raceInt = 1)
        val singleData = mapper.fromCursor(cursor)

        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )

        // 1. Batch via fromCursor(cursor, batch)
        val batchData = mapper.fromCursor(cursor, batchMetadata)

        // 2. Snapshot via readCursorSnapshot + fromSnapshot
        val snapshot = mapper.readCursorSnapshot(cursor)
        val snapshotData = mapper.fromSnapshot(snapshot, batchMetadata)

        // Verify Source symmetry across all paths
        assertEquals("Single fromCursor source", WorkoutSource.TCX, singleData.source)
        assertEquals("Batch fromCursor source", WorkoutSource.TCX, batchData.source)
        assertEquals("Snapshot source", WorkoutSource.TCX, snapshot.source)
        assertEquals("fromSnapshot source", WorkoutSource.TCX, snapshotData.source)

        // Verify HeaderData Source propagation
        assertEquals("Single HeaderData source", WorkoutSource.TCX, singleData.headerData.source)
        assertEquals("Batch HeaderData source", WorkoutSource.TCX, batchData.headerData.source)
        assertEquals("Snapshot HeaderData source", WorkoutSource.TCX, snapshotData.headerData.source)

        // Verify Race and other primitive flags symmetry
        assertEquals("Race flag single vs batch", singleData.race, batchData.race)
        assertEquals("Race flag single vs snapshot", singleData.race, snapshotData.race)
        assertEquals("Epic flag single vs batch", singleData.epic, batchData.epic)
        assertEquals("Epic flag single vs snapshot", singleData.epic, snapshotData.epic)
        assertEquals("Commute flag single vs batch", singleData.commute, batchData.commute)
        assertEquals("Trainer flag single vs batch", singleData.trainer, batchData.trainer)
        assertEquals("Finished flag single vs batch", singleData.finished, batchData.finished)
    }

    @Test
    fun testRule20_epicFlagSymmetryAndNullMissingFallback_tstDat050_2() {
        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )

        // Case 1: epic = 1 with race = 1 -> true across all 3 mapping paths
        val epicTrueCursor = createMockCursor(sourceString = "FIT", raceInt = 1, epicInt = 1)
        val singleTrue = mapper.fromCursor(epicTrueCursor)
        val batchTrue = mapper.fromCursor(epicTrueCursor, batchMetadata)
        val snapTrue = mapper.readCursorSnapshot(epicTrueCursor)
        val fromSnapTrue = mapper.fromSnapshot(snapTrue, batchMetadata)

        assertTrue(singleTrue.epic)
        assertTrue(batchTrue.epic)
        assertTrue(snapTrue.epic)
        assertTrue(fromSnapTrue.epic)
        assertTrue(singleTrue.race)

        // Case 2: epic column missing (-1) or NULL -> safely defaults to false
        val missingColCursor = createMockCursor(sourceString = "TRACKED", raceInt = 0, epicInt = 1)
        every { missingColCursor.getColumnIndex(WorkoutSummaries.EPIC) } returns -1
        assertFalse(mapper.fromCursor(missingColCursor).epic)
        assertFalse(mapper.fromCursor(missingColCursor, batchMetadata).epic)

        val nullColCursor = createMockCursor(sourceString = "TRACKED", raceInt = 0, epicInt = 1)
        every { nullColCursor.isNull(29) } returns true
        assertFalse(mapper.fromCursor(nullColCursor).epic)
        assertFalse(mapper.fromCursor(nullColCursor, batchMetadata).epic)
    }

    @Test
    fun testRule20_wPrimeBalSummary_symmetryAcrossSingleBatchAndSnapshot() {
        val cursor = createMockCursor(sourceString = "FIT", raceInt = 0)
        val extremaList = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                5023L,
                com.atrainingtracker.banalservice.sensor.SensorType.W_PRIME_BAL,
                com.atrainingtracker.trainingtracker.database.ExtremaType.MIN,
                1.5,
                null
            ),
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                5023L,
                com.atrainingtracker.banalservice.sensor.SensorType.POWER,
                com.atrainingtracker.trainingtracker.database.ExtremaType.AVG,
                200.0,
                null
            )
        )
        every { mockSummariesDb.getExtremaForWorkouts(listOf(5023L)) } returns extremaList

        val singleData = mapper.fromCursor(cursor)
        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(5023L to extremaList),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )
        val batchData = mapper.fromCursor(cursor, batchMetadata)
        val snapshot = mapper.readCursorSnapshot(cursor)
        val snapshotData = mapper.fromSnapshot(snapshot, batchMetadata)

        assertEquals(1.5, singleData.minWPrimeBalKj!!, 1e-6)
        assertEquals(singleData.minWPrimeBalKj, batchData.minWPrimeBalKj)
        assertEquals(singleData.minWPrimeBalKj, snapshotData.minWPrimeBalKj)
        assertEquals(singleData.minWPrimeBalPct, batchData.minWPrimeBalPct)
        assertEquals(singleData.minWPrimeBalPct, snapshotData.minWPrimeBalPct)
        assertEquals(singleData.isEmptyTankWarning, batchData.isEmptyTankWarning)
        assertEquals(singleData.isEmptyTankWarning, snapshotData.isEmptyTankWarning)
        assertEquals(singleData.detailsData.minWPrimeBalKj, batchData.detailsData.minWPrimeBalKj)
        assertEquals(singleData.detailsData.minWPrimeBalKj, snapshotData.detailsData.minWPrimeBalKj)
    }

    @Test
    fun testRule20_wPrimeBalSummary_noPowerData_suppressesWPrimeBalSymmetrically() {
        // [TST-TRK-031.6] / REQ-TRK-038.5 (Rule 20 & Rule 52)
        // Given legacy SQLite extrema for W_PRIME_BAL, but zero power data
        val cursor = createMockCursor(sourceString = "FIT", raceInt = 0)
        val extremaList = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                5023L,
                com.atrainingtracker.banalservice.sensor.SensorType.W_PRIME_BAL,
                com.atrainingtracker.trainingtracker.database.ExtremaType.MIN,
                20.0,
                null
            )
        )
        every { mockSummariesDb.getExtremaForWorkouts(listOf(5023L)) } returns extremaList

        val singleData = mapper.fromCursor(cursor)
        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(5023L to extremaList),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )
        val batchData = mapper.fromCursor(cursor, batchMetadata)
        val snapshot = mapper.readCursorSnapshot(cursor)
        val snapshotData = mapper.fromSnapshot(snapshot, batchMetadata)

        // Then all pathways MUST symmetrically suppress minWPrimeBal (null)
        org.junit.Assert.assertNull("Single minWPrimeBalKj must be null", singleData.minWPrimeBalKj)
        org.junit.Assert.assertNull("Batch minWPrimeBalKj must be null", batchData.minWPrimeBalKj)
        org.junit.Assert.assertNull("Snapshot minWPrimeBalKj must be null", snapshotData.minWPrimeBalKj)
        org.junit.Assert.assertNull("Single minWPrimeBalPct must be null", singleData.minWPrimeBalPct)
        org.junit.Assert.assertNull("Batch minWPrimeBalPct must be null", batchData.minWPrimeBalPct)
        org.junit.Assert.assertNull("Snapshot minWPrimeBalPct must be null", snapshotData.minWPrimeBalPct)
        org.junit.Assert.assertFalse("Single isEmptyTankWarning must be false", singleData.isEmptyTankWarning)
        org.junit.Assert.assertFalse("Batch isEmptyTankWarning must be false", batchData.isEmptyTankWarning)
        org.junit.Assert.assertFalse("Snapshot isEmptyTankWarning must be false", snapshotData.isEmptyTankWarning)
        org.junit.Assert.assertNull("DetailsData minWPrimeBalKj must be null", singleData.detailsData.minWPrimeBalKj)
        org.junit.Assert.assertNull("DetailsData minWPrimeBalKj must be null", batchData.detailsData.minWPrimeBalKj)
        org.junit.Assert.assertNull("DetailsData minWPrimeBalKj must be null", snapshotData.detailsData.minWPrimeBalKj)
    }

    @Test
    fun `fromCursor and fromSnapshot map Efficiency Factor and Aerobic Decoupling symmetrically (REQ-UI-400_3)`() {
        val cursor = createMockCursor(sourceString = "FIT", raceInt = 0)
        val extremaList = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                5023L,
                com.atrainingtracker.banalservice.sensor.SensorType.POWER,
                com.atrainingtracker.trainingtracker.database.ExtremaType.EFFICIENCY_FACTOR,
                1.65,
                null
            ),
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                5023L,
                com.atrainingtracker.banalservice.sensor.SensorType.POWER,
                com.atrainingtracker.trainingtracker.database.ExtremaType.AEROBIC_DECOUPLING,
                3.8,
                null
            )
        )
        every { mockSummariesDb.getExtremaForWorkouts(listOf(5023L)) } returns extremaList

        val singleData = mapper.fromCursor(cursor)
        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(5023L to extremaList),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )
        val batchData = mapper.fromCursor(cursor, batchMetadata)
        val snapshot = mapper.readCursorSnapshot(cursor)
        val snapshotData = mapper.fromSnapshot(snapshot, batchMetadata)

        assertEquals(1.65, singleData.efficiencyFactor!!, 1e-6)
        assertEquals(3.8, singleData.aerobicDecouplingPct!!, 1e-6)
        assertEquals(true, singleData.isPowerBasedEfficiency)
        assertEquals(false, singleData.isAerobicDecouplingHighDrift)

        assertEquals(singleData.efficiencyFactor, batchData.efficiencyFactor)
        assertEquals(singleData.efficiencyFactor, snapshotData.efficiencyFactor)
        assertEquals(singleData.aerobicDecouplingPct, batchData.aerobicDecouplingPct)
        assertEquals(singleData.aerobicDecouplingPct, snapshotData.aerobicDecouplingPct)
        assertEquals(singleData.isPowerBasedEfficiency, batchData.isPowerBasedEfficiency)
        assertEquals(singleData.isPowerBasedEfficiency, snapshotData.isPowerBasedEfficiency)
        assertEquals(singleData.isAerobicDecouplingHighDrift, batchData.isAerobicDecouplingHighDrift)
        assertEquals(singleData.isAerobicDecouplingHighDrift, snapshotData.isAerobicDecouplingHighDrift)
        assertEquals(singleData.detailsData.efficiencyFactor, snapshotData.detailsData.efficiencyFactor)
        assertEquals(singleData.detailsData.aerobicDecouplingPct, snapshotData.detailsData.aerobicDecouplingPct)
    }

    @Test
    fun `fromCursor and fromSnapshot map Power Training Metrics symmetrically and prioritize END over AVG (REQ-UI-402_1)`() {
        val cursor = createMockCursor(sourceString = "FIT", raceInt = 1)
        val sensorPower = com.atrainingtracker.banalservice.sensor.SensorType.POWER
        val sensorNp = com.atrainingtracker.banalservice.sensor.SensorType.NORMALIZED_POWER
        val sensorIf = com.atrainingtracker.banalservice.sensor.SensorType.INTENSITY_FACTOR
        val sensorTss = com.atrainingtracker.banalservice.sensor.SensorType.TSS
        val sensorWork = com.atrainingtracker.banalservice.sensor.SensorType.WORK_KJ
        val sensorPtw = com.atrainingtracker.banalservice.sensor.SensorType.POWER_TO_WEIGHT
        val extAvg = com.atrainingtracker.trainingtracker.database.ExtremaType.AVG
        val extMax = com.atrainingtracker.trainingtracker.database.ExtremaType.MAX
        val extEnd = com.atrainingtracker.trainingtracker.database.ExtremaType.END

        val extremaList = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorPower, extAvg, 200.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorPower, extMax, 600.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorNp, extAvg, 190.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorNp, extEnd, 210.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorIf, extAvg, 0.76, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorIf, extEnd, 0.84, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorTss, extAvg, 40.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorTss, extEnd, 75.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorWork, extAvg, 350.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorWork, extEnd, 720.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorPtw, extAvg, 2.80, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, sensorPtw, extMax, 8.00, null)
        )
        every { mockSummariesDb.getExtremaForWorkouts(listOf(5023L)) } returns extremaList

        val singleData = mapper.fromCursor(cursor)
        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(5023L to extremaList),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )
        val batchData = mapper.fromCursor(cursor, batchMetadata)
        val snapshot = mapper.readCursorSnapshot(cursor)
        val snapshotData = mapper.fromSnapshot(snapshot, batchMetadata)

        // Prioritizes END over AVG and computes VI = 210 / 200 = 1.05
        assertEquals(210.0, singleData.normalizedPowerWatts!!, 1e-6)
        assertEquals(0.84, singleData.intensityFactor!!, 1e-6)
        assertEquals(75.0, singleData.tss!!, 1e-6)
        assertEquals(720.0, singleData.workKj!!, 1e-6)
        assertEquals(2.80, singleData.avgPowerToWeight!!, 1e-6)
        assertEquals(8.00, singleData.maxPowerToWeight!!, 1e-6)
        assertEquals(1.05, singleData.variabilityIndex!!, 1e-6)

        // Rule 20 symmetry across single, batch, snapshot, and detailsData
        assertEquals(singleData.normalizedPowerWatts, batchData.normalizedPowerWatts)
        assertEquals(singleData.normalizedPowerWatts, snapshotData.normalizedPowerWatts)
        assertEquals(singleData.intensityFactor, batchData.intensityFactor)
        assertEquals(singleData.intensityFactor, snapshotData.intensityFactor)
        assertEquals(singleData.tss, batchData.tss)
        assertEquals(singleData.tss, snapshotData.tss)
        assertEquals(singleData.workKj, batchData.workKj)
        assertEquals(singleData.workKj, snapshotData.workKj)
        assertEquals(singleData.avgPowerToWeight, batchData.avgPowerToWeight)
        assertEquals(singleData.avgPowerToWeight, snapshotData.avgPowerToWeight)
        assertEquals(singleData.maxPowerToWeight, batchData.maxPowerToWeight)
        assertEquals(singleData.maxPowerToWeight, snapshotData.maxPowerToWeight)
        assertEquals(singleData.variabilityIndex, batchData.variabilityIndex)
        assertEquals(singleData.variabilityIndex, snapshotData.variabilityIndex)

        assertEquals(singleData.detailsData.normalizedPowerWatts, snapshotData.detailsData.normalizedPowerWatts)
        assertEquals(singleData.detailsData.intensityFactor, snapshotData.detailsData.intensityFactor)
        assertEquals(singleData.detailsData.tss, snapshotData.detailsData.tss)
        assertEquals(singleData.detailsData.workKj, snapshotData.detailsData.workKj)
        assertEquals(singleData.detailsData.avgPowerToWeight, snapshotData.detailsData.avgPowerToWeight)
        assertEquals(singleData.detailsData.variabilityIndex, snapshotData.detailsData.variabilityIndex)

        // Verify fallback derivation when only avgPowerWatts, activeTimeSec, FTP, and weight exist
        val fallbackSummary = mapper.resolvePowerTrainingSummary(
            npEnd = null, npAvg = null,
            ifEnd = null, ifAvg = null,
            tssEnd = null, tssAvg = null, tssMax = null,
            workEnd = null, workAvg = null, workMax = null,
            ptwAvg = null, ptwMax = null,
            avgPowerWatts = 250.0,
            maxPowerWatts = 500.0,
            activeTimeSec = 3600L,
            startTimeS = 1_700_000_000L,
            effectiveFtpWatts = 250.0,
            effectiveWeightKg = 75.0
        )
        assertEquals(250.0, fallbackSummary.normalizedPowerWatts!!, 1e-6)
        assertEquals(1.0, fallbackSummary.intensityFactor!!, 1e-6)
        assertEquals(100.0, fallbackSummary.tss!!, 1e-6)
        assertEquals(900.0, fallbackSummary.workKj!!, 1e-6)
        assertEquals(250.0 / 75.0, fallbackSummary.avgPowerToWeight!!, 1e-6)
        assertEquals(500.0 / 75.0, fallbackSummary.maxPowerToWeight!!, 1e-6)
    }

    @Test
    fun testTST_DAT_028_1_expandedBatchMetadata_and_nonReconcilingMode_zeroDbWrites() {
        val cursor = createMockCursor(sourceString = "TCX", raceInt = 1)
        val snapshot = mapper.readCursorSnapshot(cursor)

        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = mapOf("2026-09-09-10-02-00" to "StravaJSON"),
            clusterNames = mapOf(42L to "Morning Loops"),
            laps = emptyMap(),
            sportTypes = mapOf(
                2L to WorkoutDataMapper.SportTypeInfo(
                    bSportType = BSportType.RUN,
                    sportName = "Trail Run",
                    stravaSportName = "Run"
                )
            ),
            equipmentNames = mapOf(10L to "Speedcross 5"),
            locationNames = mapOf(com.google.android.gms.maps.model.LatLng(48.0, 11.5) to "Home Trail"),
            athleteMetrics = mapOf(
                5023L to WorkoutDataMapper.AthleteMetricsInfo(
                    effectiveFtpWatts = 280.0,
                    effectiveWeightKg = 72.0
                )
            )
        )

        // 1. Map via fromSnapshot with reconcile = false
        val mappedData = mapper.fromSnapshot(snapshot, batchMetadata, reconcile = false)

        // Verify batch pre-fetched metadata was consumed without querying single-item DBs
        assertEquals("Trail Run", mappedData.sportName)
        assertEquals(BSportType.RUN, mappedData.bSportType)
        assertEquals("Run", mappedData.stravaSportName)
        assertEquals("Speedcross 5", mappedData.equipmentName)
        assertEquals("Morning Loops", mappedData.clusterName)

        // Verify zero SQLite writes were executed on extrema database during mapping
        io.mockk.verify(exactly = 0) {
            mockSummariesDb.updateExtremaValue(any(), any(), any(), any(), any())
        }
    }

    /**
     * TST-LOG-001 & TST-LOG-002 (REQ-UI-011): Running workouts derive both Speed and Pace
     * extrema rows from the single SPEED_mps source, and Pace maintains strict mathematical
     * ordering (Min column = fastest / numerically smallest time per km; Max column = slowest).
     */
    @Test
    fun testRunningPaceExtrema_derivesBothSpeedAndPaceWithNumericalOrdering_TST_LOG_001_TST_LOG_002() {
        val speedUnitsId = com.atrainingtracker.trainingtracker.MyHelper.getUnitsId(SensorType.SPEED_mps)
        val paceUnitsId = com.atrainingtracker.trainingtracker.MyHelper.getUnitsId(SensorType.PACE_spm)
        every { context.getString(SensorType.SPEED_mps.shortNameId) } returns "Speed"
        every { context.getString(SensorType.PACE_spm.shortNameId) } returns "Pace"
        every { context.getString(speedUnitsId) } returns "km/h"
        every { context.getString(paceUnitsId) } returns "min/km"

        val cursor = createMockCursor(sourceString = "TRACKER", raceInt = 0)
        val snapshot = mapper.readCursorSnapshot(cursor)

        val slowestSpeedPos = LatLng(48.10, 11.50)
        val fastestSpeedPos = LatLng(48.20, 11.60)
        val speedExtrema = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                snapshot.workoutId, SensorType.SPEED_mps, ExtremaType.MIN, 2.5, slowestSpeedPos
            ),
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                snapshot.workoutId, SensorType.SPEED_mps, ExtremaType.AVG, 10.0 / 3.0, null
            ),
            WorkoutSummariesDatabaseManager.ExtremaRecord(
                snapshot.workoutId, SensorType.SPEED_mps, ExtremaType.MAX, 5.0, fastestSpeedPos
            )
        )

        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(snapshot.workoutId to speedExtrema),
            stravaData = emptyMap(),
            clusterNames = emptyMap(),
            laps = emptyMap(),
            sportTypes = mapOf(
                snapshot.sportId to WorkoutDataMapper.SportTypeInfo(
                    bSportType = BSportType.RUN,
                    sportName = "Running",
                    stravaSportName = "Run"
                )
            )
        )

        val mapped = mapper.fromSnapshot(snapshot, batchMetadata, reconcile = false)
        val speedRow = mapped.extremaRows.firstOrNull { it.sensorLabel == "Speed" }
        val paceRow = mapped.extremaRows.firstOrNull { it.sensorLabel == "Pace" }

        assertNotNull("Running activity must emit Speed extrema row (TST-LOG-002)", speedRow)
        assertNotNull("Running activity must emit derived Pace extrema row from SPEED_mps (TST-LOG-002)", paceRow)

        // Pace values at 5.0 m/s (3:20/km), 3.333 m/s (5:00/km), 2.5 m/s (6:40/km)
        val expectedFastestPace = SensorType.PACE_spm.myFormatter.format(1.0 / 5.0)
        val expectedAvgPace = SensorType.PACE_spm.myFormatter.format(1.0 / (10.0 / 3.0))
        val expectedSlowestPace = SensorType.PACE_spm.myFormatter.format(1.0 / 2.5)

        assertEquals("Numerical minimum Pace (fastest) must populate minValue (TST-LOG-001)", expectedFastestPace, paceRow!!.minValue)
        assertEquals(expectedAvgPace, paceRow.avgValue)
        assertEquals("Numerical maximum Pace (slowest) must populate maxValue (TST-LOG-001)", expectedSlowestPace, paceRow.maxValue)
        assertEquals(fastestSpeedPos, paceRow.minLatLng)
        assertEquals(slowestSpeedPos, paceRow.maxLatLng)

        fun paceSeconds(formatted: String): Int {
            val parts = formatted.split(":")
            return parts[0].toInt() * 60 + parts[1].toInt()
        }
        assertTrue(
            "Pace min <= avg <= max numerically",
            paceSeconds(paceRow.minValue!!) <= paceSeconds(paceRow.avgValue!!) &&
                paceSeconds(paceRow.avgValue!!) <= paceSeconds(paceRow.maxValue!!)
        )
    }

    @Test
    fun testFromCursor_delegatesToFromSnapshotWithReconcileAndFullBatchMetadata_TST_DAT_041_5() {
        val cursor = createMockCursor(sourceString = "FIT", raceInt = 1)
        val startPos = LatLng(48.10, 11.50)
        val endPos = LatLng(48.20, 11.60)
        val extremaList = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, SensorType.LATITUDE, ExtremaType.START, 0.0, startPos),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, SensorType.LATITUDE, ExtremaType.END, 0.0, endPos),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, SensorType.ALTITUDE, ExtremaType.MIN, 500.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(5023L, SensorType.ALTITUDE, ExtremaType.MAX, 650.0, null)
        )
        every { mockSummariesDb.getExtremaForWorkouts(listOf(5023L)) } returns extremaList
        every { mockStravaUploadDb.getStravaActivityData("2026-09-09-10-02-00") } returns "{\"id\":99}"
        val lap = LapData(1L, 5023L, 1L, null, 600, 2000.0, 3.33)
        every { LapsDatabaseManager.getInstance(any()).getLaps(5023L) } returns listOf(lap)

        val single = mapper.fromCursor(cursor)
        val snapshot = mapper.readCursorSnapshot(cursor)
        val explicitBatch = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(5023L to extremaList),
            stravaData = mapOf("2026-09-09-10-02-00" to "{\"id\":99}"),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = mapOf(5023L to listOf(lap))
        )
        val viaSnapshot = mapper.fromSnapshot(snapshot, explicitBatch, reconcile = true)

        assertEquals(viaSnapshot.id, single.id)
        assertEquals(viaSnapshot.stravaActivityData, single.stravaActivityData)
        assertEquals(viaSnapshot.clusterName, single.clusterName)
        assertEquals(viaSnapshot.laps, single.laps)
        assertEquals(viaSnapshot.minAltitude, single.minAltitude)
        assertEquals(viaSnapshot.maxAltitude, single.maxAltitude)
        assertEquals(viaSnapshot.startLatLng, single.startLatLng)
        assertEquals(viaSnapshot.endLatLng, single.endLatLng)
        assertEquals(viaSnapshot.sportName, single.sportName)
        assertEquals(viaSnapshot.equipmentName, single.equipmentName)
    }
}
