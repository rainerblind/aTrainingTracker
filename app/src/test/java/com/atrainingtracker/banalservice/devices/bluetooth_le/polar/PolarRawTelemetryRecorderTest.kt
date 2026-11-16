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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit verification for Polar raw telemetry streaming GZIP recorder (TST-SET-119.1 / REQ-SET-117.1).
 */
@RunWith(RobolectricTestRunner::class)
class PolarRawTelemetryRecorderTest {

    private lateinit var context: Context
    private val testBaseFileName = "2026-11-16_143000"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupFiles()
    }

    @After
    fun tearDown() {
        PolarRawTelemetryRecorder.stopActiveSession()
        cleanupFiles()
    }

    private fun cleanupFiles() {
        val target = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseFileName)
        val tmp = File(PolarRawTelemetryRecorder.getRawSensorDir(context), "$testBaseFileName.polar_raw.csv.gz.tmp")
        if (target.exists()) target.delete()
        if (tmp.exists()) tmp.delete()
    }

    // TST-SET-119.1
    @Test
    fun emptyRecordingSession_discardsFile_leavingZeroOrphans() {
        val recorder = PolarRawTelemetryRecorder(
            context = context,
            fileBaseName = testBaseFileName,
            sessionStartTimeMs = System.currentTimeMillis()
        )
        recorder.start()
        recorder.stop()

        assertFalse(
            "Sidecar file must NOT exist for empty session",
            recorder.targetFile.exists()
        )
        assertFalse(
            "Temporary file must be deleted for empty session",
            recorder.tempFile.exists()
        )
    }

    // TST-SET-119.1
    @Test
    fun streamingSession_recordsMultiChannelFrames_andProducesValidGzipFile() {
        val startTime = System.currentTimeMillis()
        val metadata = PolarRawMetadata(
            sensorModel = "Polar H10 12345678",
            streamMode = "LIVE",
            accRateHz = 200,
            sportId = 1
        )
        val recorder = PolarRawTelemetryRecorder(
            context = context,
            fileBaseName = testBaseFileName,
            sessionStartTimeMs = startTime,
            metadata = metadata
        )
        recorder.start()

        // Write ACC frames
        recorder.recordAcc(1_000_000_000L, 12, -45, 980)
        recorder.recordAcc(1_005_000_000L, 14, -40, 985)

        // Write GYRO frame
        recorder.recordGyro(1_010_000_000L, 1.25f, -0.45f, 0.05f)

        // Write HR sample
        recorder.recordHr(1_015_000_000L, 145)

        // Write RR beat event
        val rrEvent = RrBeatEvent(
            beatTimestampMs = startTime + 1000L,
            rrMs = 750.5,
            rawCounter = 1,
            protocol = ProtocolType.BLE
        )
        recorder.onRrBeat(rrEvent)

        recorder.stop()

        assertTrue("Sidecar file must exist after stopping session with frames", recorder.targetFile.exists())
        assertFalse("Temporary file must have been renamed", recorder.tempFile.exists())
        assertTrue("File size must be greater than 0", recorder.targetFile.length() > 0)

        // Verify roundtrip read
        val session = PolarRawTelemetryReader.readFile(recorder.targetFile)
        assertEquals(metadata.sensorModel, session.metadata.sensorModel)
        assertEquals(metadata.streamMode, session.metadata.streamMode)
        assertEquals(metadata.accRateHz, session.metadata.accRateHz)
        assertEquals(5, session.rows.size)

        assertEquals(RawTelemetryChannel.ACC, session.rows[0].channel)
        assertEquals(12, session.rows[0].axMg)
        assertEquals(-45, session.rows[0].ayMg)
        assertEquals(980, session.rows[0].azMg)

        assertEquals(RawTelemetryChannel.GYRO, session.rows[2].channel)
        assertEquals(1.25f, session.rows[2].gxMdps)

        assertEquals(RawTelemetryChannel.HR, session.rows[3].channel)
        assertEquals(145, session.rows[3].hrBpm)

        assertEquals(RawTelemetryChannel.RR, session.rows[4].channel)
        assertEquals(750.5, session.rows[4].rrMs!!, 0.1)
    }

    // TST-SET-119.1
    @Test
    fun highVolumeBurst_bufferedWithoutDropping() {
        val recorder = PolarRawTelemetryRecorder(
            context = context,
            fileBaseName = testBaseFileName,
            sessionStartTimeMs = System.currentTimeMillis()
        )
        recorder.start()

        val sampleCount = 2000
        for (i in 0 until sampleCount) {
            recorder.recordAcc(1_000_000_000L + (i * 5_000_000L), i, i * 2, 1000)
        }

        recorder.stop()

        val session = PolarRawTelemetryReader.readFile(recorder.targetFile)
        assertEquals(sampleCount, session.rows.size)
    }

    // TST-SET-119.1
    @Test
    fun offlineExerciseSync_persistsSidecarFile() {
        val hrSamples = listOf(750, 745, 760, 755, 740)
        val success = PolarRawTelemetryRecorder.recordOfflineExercise(
            context = context,
            fileBaseName = testBaseFileName,
            sensorModel = "Polar H10 OFFLINE",
            startTimeMs = 1700000000000L,
            hrSamples = hrSamples
        )

        assertTrue(success)
        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseFileName)
        assertTrue(sidecar.exists())

        val session = PolarRawTelemetryReader.readFile(sidecar)
        assertEquals("OFFLINE", session.metadata.streamMode)
        assertEquals("Polar H10 OFFLINE", session.metadata.sensorModel)
        assertEquals(5, session.rows.size)
        assertEquals(750.0, session.rows[0].rrMs!!, 0.1)
        assertEquals(80, session.rows[0].hrBpm) // 60,000 / 750 = 80 bpm
    }
}
