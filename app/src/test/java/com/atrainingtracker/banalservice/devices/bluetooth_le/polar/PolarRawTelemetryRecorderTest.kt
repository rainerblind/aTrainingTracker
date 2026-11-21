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
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        // Write ACC frames with hardware monotonic timestamps
        recorder.recordAcc(100_000_000_000L, 12, -45, 980)
        recorder.recordAcc(100_005_000_000L, 14, -40, 985)

        // Write GYRO frame with hardware monotonic timestamp
        recorder.recordGyro(100_010_000_000L, 1.25f, -0.45f, 0.05f)

        // Write HR sample with host epoch timestamp (+1000ms from start)
        recorder.recordHr((startTime + 1000L) * 1_000_000L, 145)

        // Write RR beat event with host epoch timestamp (+1200ms from start)
        val rrEvent = RrBeatEvent(
            beatTimestampMs = startTime + 1200L,
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

        // ACC verification
        assertEquals(RawTelemetryChannel.ACC, session.rows[0].channel)
        assertEquals(12, session.rows[0].axMg)
        assertEquals(-45, session.rows[0].ayMg)
        assertEquals(980, session.rows[0].azMg)
        assertTrue("ACC elapsedMs must be non-negative", session.rows[0].elapsedMs >= 0L)
        assertEquals(
            "ACC delta elapsedMs must match hardware timestamp delta",
            session.rows[0].elapsedMs + 5L,
            session.rows[1].elapsedMs
        )

        // GYRO verification
        assertEquals(RawTelemetryChannel.GYRO, session.rows[2].channel)
        assertEquals(1.25f, session.rows[2].gxMdps)
        assertTrue("GYRO elapsedMs must be non-negative", session.rows[2].elapsedMs >= 0L)

        // HR verification: host epoch elapsedMs must match session offset exactly
        assertEquals(RawTelemetryChannel.HR, session.rows[3].channel)
        assertEquals(145, session.rows[3].hrBpm)
        assertEquals(1000L, session.rows[3].elapsedMs)

        // RR verification: host epoch elapsedMs must match session offset exactly
        assertEquals(RawTelemetryChannel.RR, session.rows[4].channel)
        assertEquals(750.5, session.rows[4].rrMs!!, 0.1)
        assertEquals(1200L, session.rows[4].elapsedMs)

        // Finding 2 verification: zero clock-domain mixing or multi-year drift
        assertTrue(
            "All channel elapsedMs values must be within realistic session bounds (< 60s)",
            session.rows.all { it.elapsedMs in 0L..60_000L }
        )
    }

    // TST-SET-119.1
    @Test
    fun preConnectedBridge_capturesFramesWhenWorkoutStartsLater() {
        val sensorManager = MySensorManager(context)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = 1L,
            macAddress = "00:22:D0:AA:BB:CC"
        )
        // 1. Simulate device connected BEFORE workout starts (e.g. on Dashboard)
        bridge.getSdkCallbackForTesting().deviceConnected(
            PolarDeviceInfo("H10_PRE_CONNECTED", "00:22:D0:AA:BB:CC", -60, "Polar H10", true)
        )
        // Verify recorder is null at connect time
        assertNull(PolarRawTelemetryRecorder.getActiveRecorder())

        // 2. Later, athlete starts workout session
        val startTime = System.currentTimeMillis()
        val recorder = PolarRawTelemetryRecorder.startActiveSession(
            context = context,
            fileBaseName = testBaseFileName,
            startTimeMs = startTime,
            sportId = 1
        )
        assertNotNull(recorder)

        // 3. Bridge processes frames during workout
        bridge.processAccData(
            PolarAccelerometerData(
                samples = listOf(
                    PolarAccelerometerData.PolarAccelerometerDataSample(50_000_000_000L, 10, 20, 980),
                    PolarAccelerometerData.PolarAccelerometerDataSample(50_005_000_000L, 12, 22, 985)
                )
            )
        )
        bridge.processHrData(
            PolarHrData(
                samples = listOf(
                    PolarHrData.PolarHrSample(140, 140, 0, listOf(800), listOf(800), true, true, true)
                )
            ),
            packetArrivalMs = startTime + 500L
        )

        // 4. Workout ends
        PolarRawTelemetryRecorder.stopActiveSession()
        bridge.shutDown()

        // 5. Verify sidecar was created and contains all frames
        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseFileName)
        assertTrue("Sidecar must exist for pre-connected bridge session", sidecar.exists())

        val session = PolarRawTelemetryReader.readFile(sidecar)
        assertTrue("Must record ACC and HR/RR frames", session.rows.isNotEmpty())
        assertTrue("Must contain ACC frames", session.rows.any { it.channel == RawTelemetryChannel.ACC })
        assertTrue("Must contain HR or RR frames", session.rows.any { it.channel == RawTelemetryChannel.HR || it.channel == RawTelemetryChannel.RR })
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

    // TST-CON-050.4
    @Test
    fun activeWorkoutRecorder_attachesWhenReplacementBridgeConnectsViaSessionOpen_andPersistsSidecar() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        val startTime = System.currentTimeMillis()
        val recorder = PolarRawTelemetryRecorder.startActiveSession(
            context = context,
            fileBaseName = testBaseFileName,
            startTimeMs = startTime,
            sportId = 1
        )
        assertNotNull(recorder)

        val mac = "24:AC:AC:24:82:56"
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.isFeatureReady(mac, PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns true
        every { mockApi.isFeatureReady(mac, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        every { mockApi.startHrStreaming(mac) } returns flowOf(
            PolarHrData(listOf(PolarHrData.PolarHrSample(138, 138, 0, listOf(435), listOf(435), true, true, true)))
        )
        coEvery { mockApi.requestStreamSettings(mac, PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        every { mockApi.startAccStreaming(mac, any()) } returns flowOf(
            PolarAccelerometerData(
                listOf(PolarAccelerometerData.PolarAccelerometerDataSample(50_000_000_000L, 15, -25, 990))
            )
        )

        val sensorManager = MySensorManager(context)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = 42L,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.startSearching()

        PolarRawTelemetryRecorder.stopActiveSession()
        bridge.shutDown()
        sensorManager.shutDown()
        PolarOfflineGattCoordinator.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()

        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseFileName)
        assertTrue("Sidecar file must exist in files/raw_sensor/ after mid-workout bridge handoff", sidecar.exists())
        val session = PolarRawTelemetryReader.readFile(sidecar)
        assertTrue("Sidecar must contain HR/RR frames", session.rows.any { it.channel == RawTelemetryChannel.HR || it.channel == RawTelemetryChannel.RR })
        assertTrue("Sidecar must contain ACC frames", session.rows.any { it.channel == RawTelemetryChannel.ACC })
    }

    // REQ-STB-023.3, REQ-STB-023.4 / TST-STB-023.3
    @Test
    fun testDropOldestBackpressure_onUndeliveredElementCountsDropsAndCancelsScopeOnStop_TST_STB_023_3() {
        val startTime = System.currentTimeMillis()
        val recorder = PolarRawTelemetryRecorder(
            context = context,
            fileBaseName = testBaseFileName,
            sessionStartTimeMs = startTime,
            metadata = PolarRawMetadata(sensorModel = "Polar H10", accRateHz = 200)
        )
        assertTrue("Private coroutine scope should start active", recorder.isScopeActiveForTesting())

        // Enqueue > CHANNEL_CAPACITY rows BEFORE starting IO drain so Channel(DROP_OLDEST) evicts oldest rows
        val excess = 250
        val total = PolarRawTelemetryRecorder.CHANNEL_CAPACITY + excess
        for (i in 0 until total) {
            recorder.enqueueRowForBackpressureTest(
                RawTelemetryRow(
                    sensorTimestampNs = (i + 1) * 5_000_000L,
                    elapsedMs = i * 5L,
                    channel = RawTelemetryChannel.ACC,
                    axMg = i,
                    ayMg = -i,
                    azMg = 1000
                )
            )
        }

        assertEquals(
            "onUndeliveredElement must accurately account every DROP_OLDEST evicted row",
            excess.toLong(),
            recorder.getDroppedFrameCount()
        )

        // Start drain and stop -> newest CHANNEL_CAPACITY rows written, scope cancelled cleanly
        recorder.start()
        recorder.stop()

        assertFalse("stop() must cancel private CoroutineScope", recorder.isScopeActiveForTesting())
        assertEquals(PolarRawTelemetryRecorder.CHANNEL_CAPACITY.toLong(), recorder.getRecordedRowCount())

        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseFileName)
        assertTrue(sidecar.exists())
        val parsed = PolarRawTelemetryReader.readFile(sidecar)
        assertEquals(PolarRawTelemetryRecorder.CHANNEL_CAPACITY, parsed.rows.size)
        assertEquals("Oldest retained row must start right after evicted prefix", excess, parsed.rows.first().axMg)
        assertEquals("Newest row must be preserved at tail", total - 1, parsed.rows.last().axMg)
    }

    // REQ-STB-023.4 / TST-STB-023.3
    @Test
    fun testAppendCsvLine_exactParityWithStringFormatAcrossEdgeCases_TST_STB_023_3() {
        val gyroCases = listOf(
            0.0f, -0.0f, 0.004f, -0.004f, 0.005f, -0.005f, 0.006f, -0.006f,
            1.25f, -1.25f, 12.345f, -12.345f, 999.995f, -999.995f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e15f
        )
        val rrCases = listOf(
            0.0, -0.0, 0.04, -0.04, 0.05, -0.05, 800.0, 800.05, 800.25, 812.35, -12.45,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e15
        )
        val sb = StringBuilder(128)
        for (i in gyroCases.indices) {
            val g = gyroCases[i]
            val rr = rrCases[i % rrCases.size]
            val row = RawTelemetryRow(
                sensorTimestampNs = 1_000_000_000L + i,
                elapsedMs = i.toLong(),
                channel = RawTelemetryChannel.GYRO,
                axMg = if (i % 2 == 0) -123 else null,
                ayMg = if (i % 2 == 0) 0 else null,
                azMg = if (i % 2 == 0) 998 else null,
                gxMdps = g,
                gyMdps = -g,
                gzMdps = g * 0.5f,
                hrBpm = if (i % 3 == 0) 142 else null,
                rrMs = rr
            )
            val expected = "${row.sensorTimestampNs},${row.elapsedMs},${row.channel.name}," +
                "${row.axMg ?: ""},${row.ayMg ?: ""},${row.azMg ?: ""}," +
                "${String.format(java.util.Locale.US, "%.2f", row.gxMdps!!)}," +
                "${String.format(java.util.Locale.US, "%.2f", row.gyMdps!!)}," +
                "${String.format(java.util.Locale.US, "%.2f", row.gzMdps!!)}," +
                "${row.hrBpm ?: ""}," +
                String.format(java.util.Locale.US, "%.1f", row.rrMs!!)

            sb.setLength(0)
            row.appendCsvLine(sb)
            assertEquals("appendCsvLine mismatch for gyro=$g rr=$rr", expected, sb.toString())
            assertEquals("toCsvLine mismatch for gyro=$g rr=$rr", expected, row.toCsvLine())
        }
    }

    // REQ-STB-025.2 / TST-STB-025.2: Salvage crash-truncated .polar_raw.csv.gz.tmp (dropping partial tail) and continue stream
    @Test
    fun testResumeOrStartActiveSession_salvagesTruncatedTmpDropsPartialLineAndAppendsPostCrashRows_TST_STB_025_2() {
        val fileBaseName = "crash_recovery_sidecar_2422"
        val rawDir = PolarRawTelemetryRecorder.getRawSensorDir(context)
        val tmpFile = File(rawDir, "$fileBaseName${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}")

        // 1. Write a valid GZIP buffer with metadata + 50 complete ACC rows + 1 truncated partial row,
        //    then chop trailing bytes to simulate mid-deflate OOM crash without GZIP footer.
        val originalMeta = PolarRawMetadata(sensorModel = "Polar H10", streamMode = "LIVE", accRateHz = 200, sportId = 2)
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(baos, true).use { gzip ->
            val writer = java.io.OutputStreamWriter(gzip, java.nio.charset.StandardCharsets.UTF_8)
            writer.write("# metadata: ${originalMeta.toJsonString()}\n")
            writer.write("${RawTelemetryRow.CSV_HEADER}\n")
            for (i in 1..50) {
                val row = RawTelemetryRow(
                    sensorTimestampNs = 1_760_000_000_000_000_000L + i * 5_000_000L,
                    elapsedMs = i * 5L,
                    channel = RawTelemetryChannel.ACC,
                    axMg = i,
                    ayMg = -i,
                    azMg = 1000
                )
                writer.write("${row.toCsvLine()}\n")
            }
            writer.flush()
            // Partial crash-truncated trailing line (missing columns)
            writer.write("1760000000300000000,300,ACC,12")
            writer.flush()
        }
        val fullBytes = baos.toByteArray()
        // Chop off GZIP 8-byte trailer + 4 bytes of compressed tail to simulate abrupt OOM kill
        tmpFile.writeBytes(fullBytes.copyOfRange(0, fullBytes.size - 12))
        assertTrue(tmpFile.exists())

        // 2. Resume active session -> must salvage pre-crash rows and append 20 post-crash rows
        val resumed = PolarRawTelemetryRecorder.resumeOrStartActiveSession(
            context = context,
            fileBaseName = fileBaseName,
            startTimeMs = 1_760_000_000_000L,
            sportId = 2
        )
        assertTrue("Resumed recorder must be created", resumed != null)

        // Idempotent re-call for same fileBaseName returns same active instance
        assertEquals(
            resumed,
            PolarRawTelemetryRecorder.resumeOrStartActiveSession(context, fileBaseName, 1_760_000_000_000L, 2)
        )

        for (j in 1..20) {
            resumed!!.recordAcc(
                sensorTimestampNs = 1_760_000_920_000_000_000L + j * 5_000_000L,
                axMg = 500 + j,
                ayMg = 0,
                azMg = 980
            )
        }
        PolarRawTelemetryRecorder.stopActiveSession()

        val finalFile = PolarRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
        assertTrue("Finalized .polar_raw.csv.gz must exist", finalFile.exists())
        assertFalse("Temporary .tmp file must not remain after stop()", tmpFile.exists())

        val parsed = PolarRawTelemetryReader.readFile(finalFile)
        assertEquals("Original pre-crash sensorModel metadata must be preserved", "Polar H10", parsed.metadata.sensorModel)
        assertEquals("Original pre-crash accRateHz metadata must be preserved", 200, parsed.metadata.accRateHz)
        // Must have salvaged all complete pre-crash rows (at least 45+ before chopped deflate tail) + all 20 post-crash rows
        assertTrue("Expected salvaged pre-crash + 20 post-crash rows (got ${parsed.rows.size})", parsed.rows.size >= 60)
        assertEquals("Final post-crash row must be preserved", 520, parsed.rows.last().axMg)

        // 3. Corrupt/garbage .tmp with zero recoverable rows leaves zero orphan files
        val corruptBase = "corrupt_crash_sidecar"
        val corruptTmp = File(rawDir, "$corruptBase${PolarRawTelemetryRecorder.SIDECAR_EXTENSION}${PolarRawTelemetryRecorder.TMP_EXTENSION}")
        corruptTmp.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        assertFalse(PolarRawSidecarRecovery.recoverAbandonedTmpToFinal(context, corruptBase))
        assertFalse("Corrupt .tmp must not produce orphan final file", PolarRawTelemetryRecorder.getSidecarFile(context, corruptBase).exists())
        assertFalse("Corrupt .tmp must be cleaned up", corruptTmp.exists())
    }
}
