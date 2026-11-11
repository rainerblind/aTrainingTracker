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

package com.atrainingtracker.trainingtracker.database.hrv

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.hrv.FilteredRrBeat
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Arrays

/**
 * TST-PER-042.3: Unit & contract tests for HrvPostWorkoutRecomputer centered-window recomputation
 * and bias-free rMSSD evaluation (REQ-PER-034.3).
 */
@RunWith(RobolectricTestRunner::class)
class HrvPostWorkoutRecomputerTest {

    private lateinit var context: Context
    private lateinit var rrDbManager: WorkoutRrDatabaseManager
    private lateinit var samplesDbManager: WorkoutSamplesDatabaseManager
    private lateinit var summariesDbManager: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutRrDatabaseManager.resetForTesting(null)
        rrDbManager = WorkoutRrDatabaseManager.getInstance(context)
        samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        rrDbManager.deleteAll()
    }

    private fun createWorkout(baseFileName: String): Long {
        val cv = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
            put(WorkoutSummaries.TIME_START, "2026-11-10 12:00:00")
            put(WorkoutSummaries.TIME_TOTAL_s, 120.0)
            put(WorkoutSummaries.SPORT_ID, 1L)
        }
        return summariesDbManager.database.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
    }

    @After
    fun tearDown() {
        rrDbManager.deleteAll()
        WorkoutRrDatabaseManager.resetForTesting(null)
    }

    @Test
    fun computeBiasFreeRmssd_returnsNullWhenInsufficientBeatsOrPairs() {
        assertNull(HrvPostWorkoutRecomputer.computeBiasFreeRmssd(emptyList()))
        assertNull(HrvPostWorkoutRecomputer.computeBiasFreeRmssd(listOf(
            FilteredRrBeat(1000L, 800.0, 800.0, 0, false)
        )))
    }

    @Test
    fun computeBiasFreeRmssd_calculatesExpectedRmsDifference_excludingArtifactAdjacentPairs() {
        // 5 normal beats: intervals = [800, 850, 820, 880, 810]
        // successive differences = [850-800=50, 820-850=-30, 880-820=60, 810-880=-70]
        // sum squared diffs = 2500 + 900 + 3600 + 4900 = 11900
        // mean squared diff = 11900 / 4 = 2975
        // expected rmssd = sqrt(2975) = 54.5435
        val beats = listOf(
            FilteredRrBeat(1000L, 800.0, 800.0, 0, false),
            FilteredRrBeat(1800L, 850.0, 850.0, 0, false),
            FilteredRrBeat(2650L, 820.0, 820.0, 0, false),
            FilteredRrBeat(3470L, 880.0, 880.0, 0, false),
            FilteredRrBeat(4350L, 810.0, 810.0, 0, false)
        )

        val rmssd = HrvPostWorkoutRecomputer.computeBiasFreeRmssd(beats)
        assertNotNull(rmssd)
        assertEquals(kotlin.math.sqrt(2975.0), rmssd!!, 0.01)
    }

    @Test
    fun recomputeWorkout_populatesCenteredHrvMetrics_andExtrema() = runBlocking {
        val baseFileName = "2026-11-10_120000"
        val workoutId = createWorkout(baseFileName)

        // Create sample table with SensorType columns
        samplesDbManager.createNewTable(baseFileName, Arrays.asList(*SensorType.values()))

        val startTs = 1_700_000_000_000L

        // Insert 120 seconds of 1-Hz samples
        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 120) {
                val cv = ContentValues().apply {
                    put("time", sdf.format(java.util.Date(startTs + sec * 1000L)))
                    put(SensorType.HR.name, 75.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        // Insert ~150 RR intervals (approx 800ms each)
        val rrList = mutableListOf<WorkoutRrInterval>()
        var beatTs = startTs - 10000L // 10s pre-workout
        for (idx in 0 until 150) {
            val rr = if (idx % 2 == 0) 800f else 820f
            rrList.add(
                WorkoutRrInterval(
                    workoutId = workoutId,
                    beatIndex = idx,
                    beatTimestampMs = beatTs,
                    rrMs = rr,
                    artifactFlag = 0,
                    gapBeats = 0
                )
            )
            beatTs += rr.toLong()
        }
        rrDbManager.insertIntervals(rrList)

        // Run recompute
        val success = HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation should succeed", success)

        // Verify that sample rows have populated HRV_RMSSD
        var nonNullRmssdCount = 0
        samplesDb.query(tableName, arrayOf(SensorType.HRV_RMSSD.name, SensorType.HRV_ARTIFACT_RATE.name), null, null, null, null, null).use { cursor ->
            val rmssdCol = cursor.getColumnIndexOrThrow(SensorType.HRV_RMSSD.name)
            val artCol = cursor.getColumnIndexOrThrow(SensorType.HRV_ARTIFACT_RATE.name)
            while (cursor.moveToNext()) {
                if (!cursor.isNull(rmssdCol)) {
                    nonNullRmssdCount++
                    val rmssd = cursor.getDouble(rmssdCol)
                    assertTrue(rmssd > 0.0)
                }
                assertFalse("Artifact rate should be non-null", cursor.isNull(artCol))
            }
        }
        assertTrue("Should have non-null rMSSD values in centered window", nonNullRmssdCount > 0)
    }

    @Test
    fun recomputeWorkout_returnsFalseWhenNoRrData() = runBlocking {
        val baseFileName = "2026-11-10_130000"
        val workoutId = createWorkout(baseFileName)

        val success = HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertFalse("Should return false when workout has zero RR intervals", success)
    }

    @Test
    fun testAnalyzeSignalQualityHelper() {
        val beats = List(40) { index ->
            FilteredRrBeat(
                beatTimestampMs = 1000L + index * 800L,
                rawRrMs = 800.0 + (index % 4) * 10.0,
                correctedRrMs = 800.0 + (index % 4) * 10.0,
                artifactFlag = 0
            )
        }

        val quality = HrvPostWorkoutRecomputer.analyzeSignalQuality(beats)
        assertEquals(HrvSignalQualityStatus.VALID, quality.status)
        assertEquals(40, quality.beatCount)
    }

    @Test
    fun computeSessionSdnn_returnsNullWhenInsufficientBeatsOrHighArtifactRate() {
        assertNull("Empty beats must return null", HrvPostWorkoutRecomputer.computeSessionSdnn(emptyList()))
        assertNull(
            "Single beat must return null",
            HrvPostWorkoutRecomputer.computeSessionSdnn(
                listOf(FilteredRrBeat(1000L, 800.0, 800.0, 0, false))
            )
        )

        // 100 beats with 6 artifacts (6% > 5% gate threshold)
        val beatsWithHighArtifacts = List(100) { idx ->
            FilteredRrBeat(
                beatTimestampMs = 1000L + idx * 800L,
                rawRrMs = 800.0,
                correctedRrMs = 800.0,
                artifactFlag = if (idx < 6) 1 else 0,
                isArtifact = idx < 6
            )
        }
        assertNull(
            "Artifact rate > 5% must gate SDNN computation to null",
            HrvPostWorkoutRecomputer.computeSessionSdnn(beatsWithHighArtifacts)
        )
    }

    @Test
    fun computeSessionSdnn_calculatesUnbiasedSampleStandardDeviation() {
        // 5 valid non-artifact beats: [800, 820, 810, 830, 840]
        // mean = 820.0
        // sum squared deviations = (-20)^2 + 0^2 + (-10)^2 + 10^2 + 20^2 = 400 + 0 + 100 + 100 + 400 = 1000.0
        // Bessel's sample variance = 1000 / (5 - 1) = 250.0
        // expected SDNN = sqrt(250.0) ~= 15.811388
        val beats = listOf(
            FilteredRrBeat(1000L, 800.0, 800.0, 0, false),
            FilteredRrBeat(1800L, 820.0, 820.0, 0, false),
            FilteredRrBeat(2620L, 810.0, 810.0, 0, false),
            FilteredRrBeat(3430L, 830.0, 830.0, 0, false),
            FilteredRrBeat(4260L, 840.0, 840.0, 0, false)
        )

        val sdnn = HrvPostWorkoutRecomputer.computeSessionSdnn(beats)
        assertNotNull("SDNN must not be null for valid beats", sdnn)
        assertEquals(kotlin.math.sqrt(250.0), sdnn!!, 0.001)
    }

    @Test
    fun recomputeWorkout_uses5sGrid_stepLatchesMetricsAndPersistsSessionSdnn() = runBlocking {
        val baseFileName = "2026-11-10_140000"
        val workoutId = createWorkout(baseFileName)

        samplesDbManager.createNewTable(baseFileName, java.util.Arrays.asList(*SensorType.values()))

        val startTs = 1_700_000_000_000L
        val samplesDb = samplesDbManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        samplesDb.beginTransaction()
        try {
            for (sec in 0 until 60) {
                val cv = ContentValues().apply {
                    put("time", sdf.format(java.util.Date(startTs + sec * 1000L)))
                    put(SensorType.HR.name, 75.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val rrList = mutableListOf<WorkoutRrInterval>()
        var beatTs = startTs - 5000L
        for (idx in 0 until 80) {
            val rr = 800f + (idx % 3) * 20f
            rrList.add(
                WorkoutRrInterval(
                    workoutId = workoutId,
                    beatIndex = idx,
                    beatTimestampMs = beatTs,
                    rrMs = rr,
                    artifactFlag = 0,
                    gapBeats = 0
                )
            )
            beatTs += rr.toLong()
        }
        WorkoutRrRepository.getInstance(context).insertIntervals(rrList)

        val success = HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation must succeed", success)

        // Verify session SDNN persisted in WorkoutSummaries.db
        val extremaRecords = summariesDbManager.getExtremaForWorkouts(listOf(workoutId))
        val sdnnRecord = extremaRecords.find { it.sensorType == SensorType.HRV_SDNN && it.extremaType == ExtremaType.AVG }
        assertNotNull("Session SDNN extrema record must be persisted", sdnnRecord)
        assertTrue("Session SDNN value must be > 0", sdnnRecord!!.value > 0.0)

        // Verify step-latching: consecutive intermediate 1-Hz samples within a 5s block share the same rMSSD
        val rmssdValues = mutableListOf<Double>()
        samplesDb.query(
            tableName,
            arrayOf(SensorType.HRV_RMSSD.name),
            null, null, null, null,
            "${WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.C_ID} ASC"
        ).use { cursor ->
            val col = cursor.getColumnIndexOrThrow(SensorType.HRV_RMSSD.name)
            while (cursor.moveToNext()) {
                if (!cursor.isNull(col)) {
                    rmssdValues.add(cursor.getDouble(col))
                }
            }
        }
        assertTrue("Must have non-empty rMSSD samples", rmssdValues.size >= 5)
        // Check that sample 0 and sample 1 (within first 5s) share identical latched value
        assertEquals("Sample 1 must latch value from sample 0 on 5s grid", rmssdValues[0], rmssdValues[1], 0.0001)
    }
}
