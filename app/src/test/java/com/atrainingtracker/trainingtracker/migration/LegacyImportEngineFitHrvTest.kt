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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.hrv.RrBeatTimestampReconstructor
import com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParser
import com.garmin.fit.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.Date

/**
 * TST-PER-042.5: Automated unit tests for Garmin FIT HrvMesg parsing and RR extraction (REQ-PER-034.5).
 */
@RunWith(RobolectricTestRunner::class)
class LegacyImportEngineFitHrvTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = Files.createTempDirectory("fit_hrv_import_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun parseFitWithHrvMesg_extractsRawRrIntervalsCorrectly() {
        val fitFile = File(tempDir, "activity_with_hrv.fit")
        val encoder = FileEncoder(fitFile, Fit.ProtocolVersion.V2_0)

        val fileId = FileIdMesg()
        fileId.type = com.garmin.fit.File.ACTIVITY
        fileId.manufacturer = Manufacturer.GARMIN
        fileId.timeCreated = DateTime(Date(1696327200000L))
        encoder.write(fileId)

        val rec1 = RecordMesg()
        rec1.timestamp = DateTime(Date(1696327200000L))
        rec1.heartRate = 75.toShort()
        encoder.write(rec1)

        val hrv1 = HrvMesg()
        hrv1.setTime(0, 0.800f)
        hrv1.setTime(1, 0.820f)
        encoder.write(hrv1)

        val rec2 = RecordMesg()
        rec2.timestamp = DateTime(Date(1696327201000L))
        rec2.heartRate = 76.toShort()
        encoder.write(rec2)

        val hrv2 = HrvMesg()
        hrv2.setTime(0, 0.810f)
        encoder.write(hrv2)

        val session = SessionMesg()
        session.timestamp = DateTime(Date(1696327201000L))
        session.startTime = DateTime(Date(1696327200000L))
        session.totalElapsedTime = 2.0f
        session.sport = Sport.RUNNING
        session.subSport = SubSport.GENERIC
        encoder.write(session)

        val activity = ActivityMesg()
        activity.timestamp = DateTime(Date(1696327201000L))
        activity.numSessions = 1
        encoder.write(activity)

        encoder.close()

        val parseResult = FitWorkoutParser.parse(fitFile, context)
        assertTrue("Parsing FIT file must succeed", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals("Must extract exactly 3 RR intervals", 3, parsed.rawRrIntervals.size)
        assertEquals(800.0, parsed.rawRrIntervals[0], 0.01)
        assertEquals(820.0, parsed.rawRrIntervals[1], 0.01)
        assertEquals(810.0, parsed.rawRrIntervals[2], 0.01)
    }

    @Test
    fun parseFitWithoutHrvMesg_returnsEmptyRawRrIntervals() {
        val fitFile = File(tempDir, "activity_no_hrv.fit")
        val encoder = FileEncoder(fitFile, Fit.ProtocolVersion.V2_0)

        val fileId = FileIdMesg()
        fileId.type = com.garmin.fit.File.ACTIVITY
        fileId.timeCreated = DateTime(Date(1696327200000L))
        encoder.write(fileId)

        val rec1 = RecordMesg()
        rec1.timestamp = DateTime(Date(1696327200000L))
        rec1.heartRate = 75.toShort()
        encoder.write(rec1)

        val session = SessionMesg()
        session.timestamp = DateTime(Date(1696327200000L))
        session.sport = Sport.RUNNING
        encoder.write(session)

        encoder.close()

        val parseResult = FitWorkoutParser.parse(fitFile, context)
        assertTrue(parseResult.isSuccess)
        val parsed = parseResult.getOrThrow()
        assertTrue("Parsed RR intervals must be empty", parsed.rawRrIntervals.isEmpty())
    }

    @Test
    fun parseFitWithInterleavedHrvMesg_preservesMonotonicAnchors_andAvoidsDriftCollapse() {
        val fitFile = File(tempDir, "multi_minute_activity_with_hrv.fit")
        val encoder = FileEncoder(fitFile, Fit.ProtocolVersion.V2_0)

        val baseTs = 1696327200000L // start time
        val durationSeconds = 60 // 1-minute test with 60 records and interleaved HRVs

        val fileId = FileIdMesg()
        fileId.type = com.garmin.fit.File.ACTIVITY
        fileId.manufacturer = Manufacturer.GARMIN
        fileId.timeCreated = DateTime(Date(baseTs))
        encoder.write(fileId)

        for (sec in 0 until durationSeconds) {
            val rec = RecordMesg()
            rec.timestamp = DateTime(Date(baseTs + sec * 1000L))
            rec.heartRate = 75.toShort()
            encoder.write(rec)

            val hrv = HrvMesg()
            // ~1.2 beats per second (800ms intervals)
            hrv.setTime(0, 0.800f)
            encoder.write(hrv)
        }

        val session = SessionMesg()
        session.timestamp = DateTime(Date(baseTs + durationSeconds * 1000L))
        session.startTime = DateTime(Date(baseTs))
        session.totalElapsedTime = durationSeconds.toFloat()
        session.sport = Sport.RUNNING
        session.subSport = SubSport.GENERIC
        encoder.write(session)

        val activity = ActivityMesg()
        activity.timestamp = DateTime(Date(baseTs + durationSeconds * 1000L))
        activity.numSessions = 1
        encoder.write(activity)

        encoder.close()

        val parseResult = FitWorkoutParser.parse(fitFile, context)
        assertTrue("Parsing FIT file must succeed", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals("Should extract 60 parsed RR beats", 60, parsed.parsedRrBeats.size)

        // Verify anchors are not all identical to baseTs
        val firstAnchor = parsed.parsedRrBeats.first().anchorTimestampMs
        val lastAnchor = parsed.parsedRrBeats.last().anchorTimestampMs
        assertEquals("First anchor should match session start", baseTs, firstAnchor)
        assertEquals("Last anchor should match sec 59 record", baseTs + 59000L, lastAnchor)

        // Run through RrBeatTimestampReconstructor as LegacyImportEngine does
        val reconstructor = RrBeatTimestampReconstructor(initialAnchorMs = baseTs)
        val reconstructedTimestamps: List<Long> = parsed.parsedRrBeats.map { beat ->
            val anchor = if (beat.anchorTimestampMs > 0) beat.anchorTimestampMs else baseTs
            reconstructor.reconstruct(anchor, beat.rrMs).beatTimestampMs
        }

        // Verify timestamps are monotonic and span the full duration (NOT collapsed to first 2 seconds)
        for (i in 0 until reconstructedTimestamps.size - 1) {
            assertTrue(
                "Timestamps must be non-decreasing: t[${i}] <= t[${i + 1}]",
                reconstructedTimestamps[i] <= reconstructedTimestamps[i + 1]
            )
        }
        val totalSpanMs = reconstructedTimestamps.last() - reconstructedTimestamps.first()
        assertTrue(
            "Total beat span must span across the workout duration (~48s), got ${totalSpanMs}ms",
            totalSpanMs > 40000L
        )
    }
}
