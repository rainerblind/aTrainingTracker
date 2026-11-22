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

        val accumulated = summariesDbManager.getAccumulatedSensorTypes(workoutId)
        assertTrue("HRV_ARTIFACT_RATE must be persisted", accumulated.contains(SensorType.HRV_ARTIFACT_RATE))
        assertTrue("HRV_RMSSD must be persisted when computed", accumulated.contains(SensorType.HRV_RMSSD))
        assertTrue("HRV_DFA_A1 must be persisted when computed", accumulated.contains(SensorType.HRV_DFA_A1))
        assertTrue("HRV_SDNN must be persisted when computed", accumulated.contains(SensorType.HRV_SDNN))
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

    @Test
    fun recomputeWorkout_conditionalAccumulatedSensors_omitsUncomputedMetrics() = runBlocking {
        // TST-TRK-084.4: High-artifact / sparse beat series yields empty rmssdValues, empty dfaValues, null sessionSdnn
        val baseFileName = "2026-11-17_hrv_sparse"
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
            for (sec in 0 until 20) {
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

        // 15 gap-flagged RR intervals -> 100% artifact rate (>5%), 0 valid RMSSD pairs, <32 DFA beats
        val rrList = mutableListOf<WorkoutRrInterval>()
        var beatTs = startTs
        for (idx in 0 until 15) {
            val rr = 800f
            rrList.add(
                WorkoutRrInterval(
                    workoutId = workoutId,
                    beatIndex = idx,
                    beatTimestampMs = beatTs,
                    rrMs = rr,
                    artifactFlag = com.atrainingtracker.banalservice.devices.RrBeatEvent.ARTIFACT_GAP,
                    gapBeats = 0
                )
            )
            beatTs += rr.toLong()
        }
        WorkoutRrRepository.getInstance(context).insertIntervals(rrList)

        val success = HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation should complete and write HRV_ARTIFACT_RATE", success)

        val accumulated = summariesDbManager.getAccumulatedSensorTypes(workoutId)
        assertEquals(setOf(SensorType.HRV_ARTIFACT_RATE), accumulated)
    }

    @Test
    fun recomputeWorkout_sortsInterleavedBeatsBeforeFilteringAndRejectsNegativeDfaAlpha1() = runBlocking {
        // TST-TRK-085.3, TST-TRK-085.4:
        // Segment A (0..159s): interleaved out-of-order timestamps with <= 5% artifacts so DfaCalculator IS invoked and yields positive DFA a1.
        // Segment B (160..259s): heavy unflagged multi-second RR gaps (5196.29 ms, 6377.93 ms) gated to NULL.
        val baseFileName = "2026-11-18_hrv_interleaved"
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
            for (sec in 0 until 260) {
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
        var idx = 0
        // Segment A: smooth physiological transition from ~650 ms (beats 0..95) -> ramp (96..115) -> ~1050 ms (116..209),
        // with small physiological variation so DfaCalculator produces finite positive DFA a1 (~1.0+).
        // Include a genuine -430 ms backward timestamp jump relative to previous beat (ts[49] - 430 ms) at beat 50.
        // Bidirectional interleaving in persisted beat_index order:
        // - Interleave 15 late ~1050 ms beats (150..164, t~110..125s) into early [0..89] (every 5th non-extracted beat), AND
        // - Interleave 15 early ~650 ms beats (15..29, t~5..14s) into late [116..205] (every 5th non-extracted beat).
        // When sorted at HrvPostWorkoutRecomputer.kt:99: smooth transitions, <= 5% artifacts across all windows in 0..159s -> nonNullDfaCount >= 120.
        // If L99 sort were omitted: early 650 ms beats flagged at t~5..14s poison sec=0..74 (>18% art) and late 1050 ms beats flagged at t~110..125s poison sec=50..185 (>20% art) -> nonNullDfaCount == 0.
        var beatTimeMs = startTs - 5_000L
        val segmentABeats = mutableListOf<Pair<Long, Float>>()
        var prevTs = beatTimeMs
        for (i in 0 until 210) {
            val baseRr = when {
                i < 96 -> 650.0
                i < 116 -> 650.0 + (i - 95) * 20.0
                else -> 1050.0
            }
            val rr = (baseRr + (i % 3 - 1) * 4.0).toFloat()
            val ts = if (i == 50) prevTs - 430L else beatTimeMs
            segmentABeats.add(ts to rr)
            prevTs = beatTimeMs
            beatTimeMs += rr.toLong()
        }
        val earlyExtracted = (15 until 30).toList()
        val lateExtracted = (150 until 165).toList()
        val extractedSet = (earlyExtracted + lateExtracted).toSet()
        var earlyCursor = 0
        var lateCursor = 0
        var earlySlotCounter = 0
        var lateSlotCounter = 0
        for (i in 0 until segmentABeats.size) {
            if (i in extractedSet) continue
            val b = segmentABeats[i]
            rrList.add(WorkoutRrInterval(workoutId = workoutId, beatIndex = idx++, beatTimestampMs = b.first, rrMs = b.second, artifactFlag = 0, gapBeats = 0))
            if (i < 90) {
                earlySlotCounter++
                if (earlySlotCounter % 5 == 0 && lateCursor < lateExtracted.size) {
                    val lateBeat = segmentABeats[lateExtracted[lateCursor++]]
                    rrList.add(WorkoutRrInterval(workoutId = workoutId, beatIndex = idx++, beatTimestampMs = lateBeat.first, rrMs = lateBeat.second, artifactFlag = 0, gapBeats = 0))
                }
            } else if (i in 116..205) {
                lateSlotCounter++
                if (lateSlotCounter % 5 == 0 && earlyCursor < earlyExtracted.size) {
                    val earlyBeat = segmentABeats[earlyExtracted[earlyCursor++]]
                    rrList.add(WorkoutRrInterval(workoutId = workoutId, beatIndex = idx++, beatTimestampMs = earlyBeat.first, rrMs = earlyBeat.second, artifactFlag = 0, gapBeats = 0))
                }
            }
        }
        while (lateCursor < lateExtracted.size) {
            val lateBeat = segmentABeats[lateExtracted[lateCursor++]]
            rrList.add(WorkoutRrInterval(workoutId = workoutId, beatIndex = idx++, beatTimestampMs = lateBeat.first, rrMs = lateBeat.second, artifactFlag = 0, gapBeats = 0))
        }
        while (earlyCursor < earlyExtracted.size) {
            val earlyBeat = segmentABeats[earlyExtracted[earlyCursor++]]
            rrList.add(WorkoutRrInterval(workoutId = workoutId, beatIndex = idx++, beatTimestampMs = earlyBeat.first, rrMs = earlyBeat.second, artifactFlag = 0, gapBeats = 0))
        }

        // Segment B: unflagged multi-second gaps in tail [195s..259s] (keeping sec=0..134 centered windows clean)
        for (sec in 195 until 260) {
            val ts = startTs + sec * 1000L
            val rr = when (sec) {
                200, 220, 240 -> 5196.29f
                230 -> 6377.93f
                else -> 800f
            }
            rrList.add(WorkoutRrInterval(workoutId = workoutId, beatIndex = idx++, beatTimestampMs = ts, rrMs = rr, artifactFlag = 0, gapBeats = 0))
        }
        WorkoutRrRepository.getInstance(context).insertIntervals(rrList)

        val success = HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        assertTrue("Recomputation must succeed cleanly on interleaved/gapped streams", success)

        var nonNullDfaCount = 0
        var nullDfaCount = 0
        var midWindowValidCount = 0
        var rowIndex = 0
        samplesDb.query(tableName, arrayOf(SensorType.HRV_DFA_A1.name), null, null, null, null, "${WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.C_ID} ASC").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.isNull(0)) {
                    nullDfaCount++
                } else {
                    nonNullDfaCount++
                    val dfa = cursor.getDouble(0)
                    assertTrue("HRV_DFA_A1 must never be <= 0.0 (was $dfa)", dfa > 0.0 && dfa.isFinite())
                    if (rowIndex in 60..125) {
                        midWindowValidCount++
                    }
                }
                rowIndex++
            }
        }
        assertTrue("Pre-sorted bidirectional interleaved Segment A must compute >= 120 valid positive HRV_DFA_A1 rows (was $nonNullDfaCount)", nonNullDfaCount >= 120)
        assertTrue("Centered window sec 60..125 must contain valid positive HRV_DFA_A1 rows (was $midWindowValidCount)", midWindowValidCount > 0)
        assertTrue("Heavy unflagged gap tail must suppress HRV_DFA_A1 to NULL (was $nullDfaCount)", nullDfaCount > 0)
    }

    @Test
    fun recomputeWorkout_persistsArtifactFlagsIdempotentlyAndResetsAfterCleanBackfill_TST_HRV_048() = runBlocking {
        // REQ-HRV-048 (REQ-HRV-048.2) / TST-HRV-048 (TST-HRV-048.2)
        val baseFileName = "2026-11-20_hrv_flag_persist"
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
            for (sec in 0 until 80) {
                val cv = ContentValues().apply {
                    put("time", sdf.format(java.util.Date(startTs + sec * 1000L)))
                    put(SensorType.HR.name, 155.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        val rrList = mutableListOf<WorkoutRrInterval>()
        var beatTs = startTs
        for (idx in 0 until 180) {
            val rr = when (idx) {
                40 -> 275.39f  // extra/short outlier
                90 -> 779.30f  // missed beat (~2x 385 ms)
                140 -> 5196.29f // unflagged multi-second gap
                else -> (385.0 + (idx % 3 - 1) * 3.0).toFloat()
            }
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

        assertTrue(HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId))

        val firstPass = rrDbManager.getIntervalsForWorkout(workoutId)
        assertTrue("275.39 ms outlier must persist non-zero artifactFlag", firstPass[40].artifactFlag != 0)
        assertEquals(0, firstPass[40].gapBeats)
        assertTrue("779.30 ms missed beat must persist non-zero artifactFlag", firstPass[90].artifactFlag != 0)
        assertEquals(1, firstPass[90].gapBeats)
        assertEquals(com.atrainingtracker.banalservice.devices.RrBeatEvent.ARTIFACT_GAP, firstPass[140].artifactFlag)
        assertTrue("5196.29 ms gap must persist gapBeats >= 5", firstPass[140].gapBeats >= 5)
        assertEquals(0, firstPass[10].artifactFlag)
        assertEquals(0, firstPass[10].gapBeats)

        // Second idempotent pass must produce identical flags without mutating normal beats
        assertTrue(HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId))
        val secondPass = rrDbManager.getIntervalsForWorkout(workoutId)
        assertEquals(firstPass.map { it.artifactFlag to it.gapBeats }, secondPass.map { it.artifactFlag to it.gapBeats })

        // Clean backfill replacing outliers with clean 385 ms RR while retaining stale flags must reset them to (0, 0)
        rrDbManager.insertIntervals(
            listOf(
                firstPass[40].copy(rrMs = 385f),
                firstPass[90].copy(rrMs = 385f)
            )
        )
        assertTrue(HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId))
        val afterBackfill = rrDbManager.getIntervalsForWorkout(workoutId)
        assertEquals(0, afterBackfill[40].artifactFlag)
        assertEquals(0, afterBackfill[40].gapBeats)
        assertEquals(0, afterBackfill[90].artifactFlag)
        assertEquals(0, afterBackfill[90].gapBeats)
    }

    @Test
    fun recomputeWorkout_andComputeSessionSdnn_honorClampedArtifactGateThreshold_TST_HRV_048() = runBlocking {
        // REQ-HRV-048 (REQ-HRV-048.4) / TST-HRV-048 (TST-HRV-048.2)
        // 100 beats with 4 artifacts (4.0%): passes at 5.0% (and clamped 9.0%->5.0%), gated null at 3.5% & clamped 1.0%->3.0%
        val beatsWith4Pct = List(100) { idx ->
            FilteredRrBeat(
                beatTimestampMs = 1000L + idx * 800L,
                rawRrMs = 800.0 + (idx % 4) * 10.0,
                correctedRrMs = 800.0 + (idx % 4) * 10.0,
                artifactFlag = if (idx < 4) 1 else 0,
                isArtifact = idx < 4
            )
        }
        assertNotNull(HrvPostWorkoutRecomputer.computeSessionSdnn(beatsWith4Pct, 5.0))
        assertNotNull(HrvPostWorkoutRecomputer.computeSessionSdnn(beatsWith4Pct, 9.0)) // clamps to 5.0
        assertNotNull(HrvPostWorkoutRecomputer.computeSessionSdnn(beatsWith4Pct, Double.NaN)) // fallback 5.0
        assertNull(HrvPostWorkoutRecomputer.computeSessionSdnn(beatsWith4Pct, 3.5))
        assertNull(HrvPostWorkoutRecomputer.computeSessionSdnn(beatsWith4Pct, 1.0)) // clamps to 3.0

        // Full recomputeWorkout gate check: 100 beats with 4 spaced ARTIFACT_GAP beats (~4.0%)
        val baseFileName = "2026-11-20_hrv_gate_clamp"
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
            for (sec in 0 until 80) {
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

        val rrList = List(100) { idx ->
            val isGap = idx == 15 || idx == 35 || idx == 55 || idx == 75
            WorkoutRrInterval(
                workoutId = workoutId,
                beatIndex = idx,
                beatTimestampMs = startTs + idx * 800L,
                rrMs = (800.0 + 30.0 * kotlin.math.sin(2.0 * Math.PI * idx / 10.0)).toFloat(),
                artifactFlag = if (isGap) com.atrainingtracker.banalservice.devices.RrBeatEvent.ARTIFACT_GAP else 0,
                gapBeats = 0
            )
        }
        rrDbManager.insertIntervals(rrList)

        // At 3.0% gate, 4.0% artifacts must suppress HRV_DFA_A1 to NULL
        assertTrue(HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId, artifactGateThresholdPercent = 3.0))
        var dfaCountAt3 = 0
        samplesDb.query(tableName, arrayOf(SensorType.HRV_DFA_A1.name), null, null, null, null, null).use { c ->
            while (c.moveToNext()) if (!c.isNull(0)) dfaCountAt3++
        }
        assertEquals("4% artifacts must be suppressed when gate threshold is 3.0%", 0, dfaCountAt3)

        // At 5.0% gate, 4.0% artifacts must compute positive HRV_DFA_A1
        assertTrue(HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId, artifactGateThresholdPercent = 5.0))
        var dfaCountAt5 = 0
        samplesDb.query(tableName, arrayOf(SensorType.HRV_DFA_A1.name), null, null, null, null, null).use { c ->
            while (c.moveToNext()) if (!c.isNull(0)) dfaCountAt5++
        }
        assertTrue("4% artifacts must compute HRV_DFA_A1 when gate threshold is 5.0%", dfaCountAt5 > 0)
    }

    @Test
    fun recomputeWorkout_phaseAlignedReconciliationWithLive180sContext_TST_HRV_048() = runBlocking {
        // REQ-HRV-048 (REQ-HRV-048.3, REQ-HRV-048.4) / TST-HRV-048 (TST-HRV-048.3)
        val baseFileName = "2026-11-20_hrv_phase_align"
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
            for (sec in 0 until 210) {
                val cv = ContentValues().apply {
                    put("time", sdf.format(java.util.Date(startTs + sec * 1000L)))
                    put(SensorType.HR.name, 150.0)
                }
                samplesDb.insert(tableName, null, cv)
            }
            samplesDb.setTransactionSuccessful()
        } finally {
            samplesDb.endTransaction()
        }

        // 500 steady-state beats (~400 ms = 200 s) with physiological harmonic variation
        val rrEvents = mutableListOf<com.atrainingtracker.banalservice.devices.RrBeatEvent>()
        val rrEntities = mutableListOf<WorkoutRrInterval>()
        var ts = startTs
        for (i in 0 until 500) {
            val rr = 400.0 + 18.0 * kotlin.math.sin(2.0 * Math.PI * i / 16.0) + 8.0 * kotlin.math.sin(2.0 * Math.PI * i / 64.0)
            rrEvents.add(
                com.atrainingtracker.banalservice.devices.RrBeatEvent(
                    beatTimestampMs = ts,
                    rrMs = rr,
                    rawCounter = i,
                    protocol = com.atrainingtracker.banalservice.devices.ProtocolType.BLE
                )
            )
            rrEntities.add(
                WorkoutRrInterval(
                    workoutId = workoutId,
                    beatIndex = i,
                    beatTimestampMs = ts,
                    rrMs = rr.toFloat(),
                    artifactFlag = 0,
                    gapBeats = 0
                )
            )
            ts += rr.toLong()
        }
        rrDbManager.insertIntervals(rrEntities)
        assertTrue(HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId, artifactGateThresholdPercent = 5.0))

        // Query centered recompute at t = 120s (centered window [60s..180s])
        var centeredDfaAt120: Double? = null
        var centeredArtAt120: Double? = null
        samplesDb.query(
            tableName,
            arrayOf(SensorType.HRV_DFA_A1.name, SensorType.HRV_ARTIFACT_RATE.name),
            "${WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.C_ID} = ?",
            arrayOf("121"), // 0-indexed sec=120 is row 121
            null, null, null
        ).use { c ->
            if (c.moveToFirst()) {
                if (!c.isNull(0)) centeredDfaAt120 = c.getDouble(0)
                if (!c.isNull(1)) centeredArtAt120 = c.getDouble(1)
            }
        }
        assertNotNull("Centered DFA a1 at t=120s must be computed", centeredDfaAt120)
        assertEquals("Clean steady-state centered artifact rate must be 0%", 0.0, centeredArtAt120!!, 0.01)

        // Compare against live HRVMetricsDevice at phase-aligned T = 180s (causal window [60s..180s] with 0..60s prior context)
        val liveNow = startTs + 180_000L
        val sensorManager = io.mockk.mockk<com.atrainingtracker.banalservice.sensor.MySensorManager>(relaxed = true)
        val liveDevice = object : com.atrainingtracker.banalservice.devices.hrv.HRVMetricsDevice(
            context = context,
            mySensorManager = sensorManager,
            timeProvider = { liveNow },
            startScheduler = false,
            artifactGateThresholdPercent = 5.0
        ) {
            override fun notifySensorsChanged() {}
        }
        for (ev in rrEvents) {
            if (ev.beatTimestampMs <= liveNow) {
                liveDevice.onRrBeat(ev)
            }
        }
        liveDevice.calculateMetrics()
        val liveDfaAt180 = liveDevice.getHrvDfaA1Sensor().sensorData.value
        val liveArtAt180 = liveDevice.getHrvArtifactRateSensor().sensorData.value
        liveDevice.shutDown()

        assertNotNull("Live DFA a1 at T=180s must be computed", liveDfaAt180)
        assertEquals("Phase-aligned live artifact rate must match centered 0%", 0.0, liveArtAt180!!, 0.01)
        assertTrue(
            "Phase-aligned live(180s) vs centered(120s) DFA a1 must reconcile closely (live=$liveDfaAt180, centered=$centeredDfaAt120)",
            kotlin.math.abs(liveDfaAt180!! - centeredDfaAt120!!) <= 0.05
        )
    }

    /**
     * TST-MIG-058.2: Verifies static java.time UTC parsing across all supported formats,
     * trimming, fractional seconds, ISO offsets, invalid inputs, and DST spring-forward immunity.
     */
    @Test
    fun parseTimestampMs_supportsAllUtcFormatsTrimFractionalAndDstWithoutShift_TST_MIG_058_2() {
        val originalTz = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
        try {
            val expectedBase = java.time.Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()
            assertEquals(expectedBase, HrvPostWorkoutRecomputer.parseTimestampMs("2026-11-10 12:00:00"))
            assertEquals(expectedBase + 250L, HrvPostWorkoutRecomputer.parseTimestampMs("  2026-11-10 12:00:00.250  "))
            assertEquals(expectedBase, HrvPostWorkoutRecomputer.parseTimestampMs("2026-11-10T12:00:00"))
            assertEquals(expectedBase, HrvPostWorkoutRecomputer.parseTimestampMs("2026-11-10T12:00:00Z"))
            assertEquals(expectedBase + 500L, HrvPostWorkoutRecomputer.parseTimestampMs("2026-11-10T14:00:00.500+02:00"))
            assertEquals(expectedBase, HrvPostWorkoutRecomputer.parseTimestampMs("2026-11-10_120000"))

            // DST spring-forward wall time in Europe/Berlin must remain exact UTC with zero +1h shift
            val dstExpected = java.time.Instant.parse("2026-03-29T02:30:00Z").toEpochMilli()
            assertEquals(dstExpected, HrvPostWorkoutRecomputer.parseTimestampMs("2026-03-29 02:30:00"))

            assertNull(HrvPostWorkoutRecomputer.parseTimestampMs(null))
            assertNull(HrvPostWorkoutRecomputer.parseTimestampMs(""))
            assertNull(HrvPostWorkoutRecomputer.parseTimestampMs("   "))
            assertNull(HrvPostWorkoutRecomputer.parseTimestampMs("not-a-timestamp"))
        } finally {
            java.util.TimeZone.setDefault(originalTz)
        }
    }
}
