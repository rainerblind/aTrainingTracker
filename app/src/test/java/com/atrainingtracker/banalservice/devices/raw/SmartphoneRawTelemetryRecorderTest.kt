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

package com.atrainingtracker.banalservice.devices.raw

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Pure-JVM unit tests for [SmartphoneRawTelemetryRecorder] (REQ-EXP-045.1, TST-EXP-045.1).
 */
// TST-EXP-045.1
class SmartphoneRawTelemetryRecorderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        SmartphoneRawTelemetryRecorder.stopActiveSession()
        SmartphoneRawTelemetryRecorder.setEnabledOverrideForTest(null)
    }

    @After
    fun tearDown() {
        SmartphoneRawTelemetryRecorder.stopActiveSession()
        SmartphoneRawTelemetryRecorder.setEnabledOverrideForTest(null)
    }

    @Test
    fun disabledByDefault_andMockContextWithoutFilesDir_neverThrowsAndCreatesZeroFiles_TST_EXP_045_1() {
        val mockCtx = mockk<Context>(relaxed = true)
        every { mockCtx.filesDir } returns null
        every { mockCtx.applicationContext } returns mockCtx

        assertFalse("Opt-in must default to false", SmartphoneRawTelemetryRecorder.isEnabled(mockCtx))
        assertNull(SmartphoneRawTelemetryRecorder.startActiveSession(mockCtx, "2026-11-21_080000", 1L))
        assertFalse(SmartphoneRawTelemetryRecorder.isRecordingActive())

        // Even when explicitly enabled, a plain-JVM MockK Context lacking filesDir safely returns null
        SmartphoneRawTelemetryRecorder.setEnabledOverrideForTest(true)
        assertNull(SmartphoneRawTelemetryRecorder.getSidecarFile(mockCtx, "2026-11-21_080000"))
        assertNull(SmartphoneRawTelemetryRecorder.startActiveSession(mockCtx, "2026-11-21_080000", 1L))
    }

    @Test
    fun plainJvmRelaxedMockContext_withoutFilesDirStub_returnsFalseWithoutInitializingDataStore_TST_EXP_045_1() {
        com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.setInstanceForTesting(null)
        val relaxedCtx = mockk<Context>(relaxed = true)
        every { relaxedCtx.applicationContext } returns relaxedCtx

        assertFalse(
            "Relaxed MockK Context without real filesDir must return false",
            SmartphoneRawTelemetryRecorder.isEnabled(relaxedCtx)
        )
        assertFalse(
            "Relaxed MockK Context must NOT initialize TuningPreferencesDataStore",
            com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore.hasInitializedInstance()
        )
    }

    @Test
    fun emptySession_discardsWithoutCreatingArchiveOrTmp_TST_EXP_045_1() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_080000.phone_raw.csv.gz")
        val recorder = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 2L, sessionStartTimeMs = 1_760_000_000_000L)
        )
        recorder.start()
        recorder.stop()

        assertFalse("Empty session must not leave .phone_raw.csv.gz", target.exists())
        assertFalse("Empty session must not leave .tmp", recorder.getTempFile().exists())
        assertEquals(0L, recorder.getWrittenRowCount())
    }

    @Test
    fun boundedDropOldestBackpressure_tracksExactDroppedFrameCountAndKeepsNewest_TST_EXP_045_1() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_080100.phone_raw.csv.gz")
        val consumerStarted = CountDownLatch(1)
        val releaseConsumer = CountDownLatch(1)
        val capacity = 16
        val totalFrames = 64

        val recorder = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 1L, sessionStartTimeMs = 1_760_000_000_000L),
            channelCapacity = capacity,
            onBeforeDrainRow = {
                consumerStarted.countDown()
                releaseConsumer.await(5, TimeUnit.SECONDS)
            }
        )
        recorder.start()
        // Send first row to wake and block the single IO consumer
        recorder.record(PhoneRawTelemetryRow(1L, 1000L, PhoneRawChannel.ACC, 0.0, 0.0, 9.81))
        assertTrue(consumerStarted.await(5, TimeUnit.SECONDS))

        // Flood remaining frames while consumer is blocked
        for (i in 2..totalFrames) {
            recorder.record(PhoneRawTelemetryRow(i.toLong(), 1000L + i, PhoneRawChannel.ACC, i.toDouble(), 0.0, 9.81))
        }
        releaseConsumer.countDown()
        recorder.stop()

        assertTrue("Finalized sidecar must exist", target.exists())
        val parsed = PhoneRawTelemetryReader.readAll(target)
        val dropped = recorder.getDroppedFrameCount()
        assertEquals("written + dropped must equal total sent", totalFrames.toLong(), parsed.rows.size.toLong() + dropped)
        assertTrue("Overflow burst must drop oldest frames (dropped=$dropped)", dropped > 0L)
        assertEquals("Newest frame must be retained under DROP_OLDEST", totalFrames.toLong(), parsed.rows.last().elapsedRealtimeNs)
    }

    @Test
    fun rotationVector_threeFloatArray_derivesUnitQuaternionQw_TST_EXP_045_1() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_080200.phone_raw.csv.gz")
        val recorder = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 1L, sessionStartTimeMs = 1_760_000_000_000L)
        )
        recorder.start()
        SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        // 3-element rotation vector -> derives qw = sqrt(1 - 0.1^2 - 0.2^2 - 0.3^2)
        SmartphoneRawTelemetryRecorder.recordRotationVector(100L, 2000L, floatArrayOf(0.1f, 0.2f, 0.3f))
        // 4-element rotation vector -> preserves provided qw
        SmartphoneRawTelemetryRecorder.recordRotationVector(200L, 2020L, floatArrayOf(0.0f, 0.0f, 0.7071f, 0.7071f))
        // 2-element invalid vector -> ignored cleanly
        SmartphoneRawTelemetryRecorder.recordRotationVector(300L, 2040L, floatArrayOf(0.1f, 0.2f))

        SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
        recorder.stop()

        val parsed = PhoneRawTelemetryReader.readAll(target)
        assertEquals(2, parsed.rows.size)
        val expectedQw = sqrt(1.0 - 0.1 * 0.1 - 0.2 * 0.2 - 0.3 * 0.3)
        assertEquals(PhoneRawChannel.ROTVEC, parsed.rows[0].channel)
        assertEquals(expectedQw, parsed.rows[0].v3, 1e-4)
        assertEquals(0.7071, parsed.rows[1].v3, 1e-4)
    }

    @Test
    fun sensorCapture_registersAndRecordsWithoutAttFusedPaired_TST_EXP_045_1() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_080300.phone_raw.csv.gz")
        val recorder = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 1L, sessionStartTimeMs = 1000L)
        )
        recorder.start()
        SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        val mockSm = mockk<SensorManager>(relaxed = true)
        val accSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_ACCELEROMETER }
        val gyroSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_GYROSCOPE }
        val rotSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_ROTATION_VECTOR }
        val baroSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_PRESSURE }
        val stepSensor = mockk<Sensor>(relaxed = true) { every { type } returns Sensor.TYPE_STEP_DETECTOR }

        every { mockSm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accSensor
        every { mockSm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) } returns gyroSensor
        every { mockSm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) } returns null
        every { mockSm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } returns rotSensor
        every { mockSm.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns baroSensor
        every { mockSm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) } returns stepSensor
        every { mockSm.registerListener(any<android.hardware.SensorEventListener>(), any<Sensor>(), any<Int>()) } returns true

        val mockCtx = mockk<Context>(relaxed = true)
        val capture = SmartphoneRawSensorCapture(
            context = mockCtx,
            sensorManagerProvider = { mockSm },
            useHandlerThread = false
        )
        try {
            assertTrue("Sensor capture must register directly without ATT Fused", capture.start())
            capture.onSensorChanged(syntheticEvent(accSensor, 10_000L, floatArrayOf(0.1f, 0.2f, 9.8f)))
            capture.onSensorChanged(syntheticEvent(gyroSensor, 20_000L, floatArrayOf(0.01f, -0.02f, 0.33f)))
            capture.onSensorChanged(syntheticEvent(rotSensor, 30_000L, floatArrayOf(0.1f, 0.2f, 0.3f)))
            capture.onSensorChanged(syntheticEvent(baroSensor, 40_000L, floatArrayOf(1013.25f)))
            capture.onSensorChanged(syntheticEvent(stepSensor, 50_000L, floatArrayOf(1.0f)))
        } finally {
            capture.stop()
            SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
            recorder.stop()
        }

        verify(exactly = 1) { mockSm.unregisterListener(capture) }
        val parsed = PhoneRawTelemetryReader.readAll(target)
        assertEquals(5, parsed.rows.size)
        assertEquals(
            listOf(PhoneRawChannel.ACC, PhoneRawChannel.GYRO, PhoneRawChannel.ROTVEC, PhoneRawChannel.BARO, PhoneRawChannel.STEP),
            parsed.rows.map { it.channel }
        )
        assertEquals(sqrt(1.0 - 0.1 * 0.1 - 0.2 * 0.2 - 0.3 * 0.3), parsed.rows[2].v3, 1e-4)
    }

    @Test
    fun stateLifecycleAndClockDomain_emitsInitialSportSwitchCarriageWithElapsedRealtimeNanos_TST_EXP_045_1() {
        val mockCtx = mockk<Context>(relaxed = true)
        every { mockCtx.filesDir } returns tempFolder.root
        every { mockCtx.applicationContext } returns mockCtx
        SmartphoneRawTelemetryRecorder.setEnabledOverrideForTest(true)

        val prevClock = SmartphoneRawTelemetryRecorder.elapsedRealtimeNanosProvider
        var fakeBoottimeNs = 500_000_000_000L
        SmartphoneRawTelemetryRecorder.elapsedRealtimeNanosProvider = { fakeBoottimeNs }

        try {
            val baseName = "2026-11-21_080400"
            val rec = SmartphoneRawTelemetryRecorder.startActiveSession(mockCtx, baseName, sportId = 42L, bSportCode = 2)
            assertTrue(rec != null)

            // Same fileBaseName in-memory resume must reuse active instance without stopping/re-salvaging
            val resumed = SmartphoneRawTelemetryRecorder.resumeOrStartActiveSession(mockCtx, baseName, sportId = 42L, bSportCode = 2)
            assertSame("In-memory resume for same fileBaseName must reuse active recorder", rec, resumed)

            // Sport-type switch WITHOUT carriage mode change must still emit STATE
            fakeBoottimeNs = 501_000_000_000L
            SmartphoneRawTelemetryRecorder.updateSportState(sportId = 99L, bSportCode = 1, epochMs = 2000L)

            // Carriage mode change must preserve actual sportId=99 & bSportCode=1
            fakeBoottimeNs = 502_000_000_000L
            SmartphoneRawTelemetryRecorder.updateCarriageMode(carriageCode = 5, epochMs = 3000L)

            // Wheel measurement uses unified CLOCK_BOOTTIME provider
            fakeBoottimeNs = 503_000_000_000L
            SmartphoneRawTelemetryRecorder.recordWheel(SmartphoneRawTelemetryRecorder.currentElapsedRealtimeNanos(), 4000L, 7.5, 0.05)

            SmartphoneRawTelemetryRecorder.stopActiveSession()

            val target = SmartphoneRawTelemetryRecorder.getSidecarFile(mockCtx, baseName)!!
            val parsed = PhoneRawTelemetryReader.readAll(target)
            assertEquals(4, parsed.rows.size)

            // Row 0: Initial STATE(42, 2, -1)
            assertEquals(PhoneRawChannel.STATE, parsed.rows[0].channel)
            assertEquals(500_000_000_000L, parsed.rows[0].elapsedRealtimeNs)
            assertEquals(42.0, parsed.rows[0].v0, 1e-9)
            assertEquals(2.0, parsed.rows[0].v1, 1e-9)
            assertEquals(-1.0, parsed.rows[0].v2, 1e-9)

            // Row 1: Sport switch STATE(99, 1, -1)
            assertEquals(PhoneRawChannel.STATE, parsed.rows[1].channel)
            assertEquals(501_000_000_000L, parsed.rows[1].elapsedRealtimeNs)
            assertEquals(99.0, parsed.rows[1].v0, 1e-9)
            assertEquals(1.0, parsed.rows[1].v1, 1e-9)
            assertEquals(-1.0, parsed.rows[1].v2, 1e-9)

            // Row 2: Carriage switch STATE(99, 1, 5)
            assertEquals(PhoneRawChannel.STATE, parsed.rows[2].channel)
            assertEquals(502_000_000_000L, parsed.rows[2].elapsedRealtimeNs)
            assertEquals(99.0, parsed.rows[2].v0, 1e-9)
            assertEquals(1.0, parsed.rows[2].v1, 1e-9)
            assertEquals(5.0, parsed.rows[2].v2, 1e-9)

            // Row 3: WHEEL on same CLOCK_BOOTTIME axis
            assertEquals(PhoneRawChannel.WHEEL, parsed.rows[3].channel)
            assertEquals(503_000_000_000L, parsed.rows[3].elapsedRealtimeNs)
        } finally {
            SmartphoneRawTelemetryRecorder.elapsedRealtimeNanosProvider = prevClock
            SmartphoneRawTelemetryRecorder.stopActiveSession()
        }
    }

    @Test
    fun stopBlocking_isTimeoutBoundedWhenWriterStalls_TST_EXP_045_2() {
        val target = File(tempFolder.root, "2026-11-21_080500.phone_raw.csv.gz")
        val started = CountDownLatch(1)
        val stall = CountDownLatch(1)
        val recorder = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 1L, sessionStartTimeMs = 1000L),
            onBeforeDrainRow = {
                started.countDown()
                stall.await(10, TimeUnit.SECONDS)
            }
        )
        recorder.start()
        recorder.record(PhoneRawTelemetryRow(1L, 1000L, PhoneRawChannel.STEP))
        assertTrue(started.await(2, TimeUnit.SECONDS))

        val t0 = System.currentTimeMillis()
        val finishedInTime = recorder.stopBlocking(timeoutMs = 150L)
        val elapsedMs = System.currentTimeMillis() - t0
        stall.countDown()

        assertFalse("Stalled writer must hit timeout guard rather than hanging", finishedInTime)
        assertTrue("stopBlocking must respect timeout bound (elapsed=$elapsedMs ms)", elapsedMs < 2000L)
        assertTrue(recorder.isStopped())
    }

    @Test
    fun recordLocation_rejectsNonFiniteLatLonAndSanitizesNonFiniteOptionalFloats_TST_EXP_045_1() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_080600.phone_raw.csv.gz")
        val recorder = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 1L, sessionStartTimeMs = 1000L)
        )
        recorder.start()
        SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        val badLatLoc = mockk<Location>(relaxed = true) {
            every { latitude } returns Double.POSITIVE_INFINITY
            every { longitude } returns 11.57
        }
        val badLonLoc = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.13
            every { longitude } returns Double.NaN
        }
        val infOptLoc = mockk<Location>(relaxed = true) {
            every { latitude } returns 48.137
            every { longitude } returns 11.576
            every { elapsedRealtimeNanos } returns 777_000_000L
            every { hasAltitude() } returns true
            every { altitude } returns Double.POSITIVE_INFINITY
            every { hasSpeed() } returns true
            every { speed } returns Float.POSITIVE_INFINITY
            every { hasBearing() } returns true
            every { bearing } returns Float.NaN
            every { hasAccuracy() } returns true
            every { accuracy } returns Float.NEGATIVE_INFINITY
        }

        try {
            SmartphoneRawTelemetryRecorder.recordLocation(badLatLoc, 1000L)
            SmartphoneRawTelemetryRecorder.recordLocation(badLonLoc, 2000L)
            SmartphoneRawTelemetryRecorder.recordLocation(infOptLoc, 3000L)
        } finally {
            SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
            recorder.stop()
        }

        val parsed = PhoneRawTelemetryReader.readAll(target)
        assertEquals("Only finite lat/lon fix must be recorded", 1, parsed.rows.size)
        val row = parsed.rows[0]
        assertEquals(PhoneRawChannel.GNSS, row.channel)
        assertEquals(777_000_000L, row.elapsedRealtimeNs)
        assertEquals(48.137, row.v0, 1e-6)
        assertEquals(11.576, row.v1, 1e-6)
        assertEquals(0.0, row.v2, 1e-9)
        assertEquals(0.0, row.v3, 1e-9)
        assertEquals(0.0, row.v4, 1e-9)
        assertEquals(0.0, row.v5, 1e-9)
    }

    @Test
    fun sensorCapture_whenZeroSensorsRegistered_cleansUpHandlerThreadAndReturnsFalse_TST_EXP_045_1() {
        val mockSm = mockk<SensorManager>(relaxed = true)
        every { mockSm.getDefaultSensor(any()) } returns null
        val mockCtx = mockk<Context>(relaxed = true)

        val capture = SmartphoneRawSensorCapture(
            context = mockCtx,
            sensorManagerProvider = { mockSm },
            useHandlerThread = true
        )
        assertFalse("start() must return false when 0 sensors register", capture.start())
        assertFalse("HandlerThread must be cleaned up when 0 sensors register", capture.hasActiveHandlerThreadForTest())
    }

    private fun syntheticEvent(sensor: Sensor, timestampNs: Long, data: FloatArray): SensorEvent {
        val event = mockk<SensorEvent>(relaxed = true)
        event.sensor = sensor
        event.timestamp = timestampNs
        SensorEvent::class.java.getField("values").apply { isAccessible = true }.set(event, data)
        return event
    }
}
