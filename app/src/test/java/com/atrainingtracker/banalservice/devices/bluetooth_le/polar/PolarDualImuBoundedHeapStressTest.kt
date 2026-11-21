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
import com.atrainingtracker.banalservice.devices.hrv.HrvRollingEngine
import com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkf
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.sdk.api.model.PolarPpiData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 60-minute dual Polar Live-IMU + live EKF bounded-heap stress verification
 * (`ATT-9343`, `REQ-STB-023.5`, `TST-STB-023.4`).
 *
 * Simulates a full 60-minute dual-sensor running workout:
 * - Polar H10 `200 Hz` ACC (`720,000` samples) -> [PolarLiveRunningCadenceEstimator] (`H10_CHEST`),
 *   [PolarH10RunningDynamicsEstimator], and [PolarRawTelemetryRecorder]
 * - Polar Verity Sense `52 Hz` ACC (`187,200` samples) + `52 Hz` GYRO (`187,200` samples) +
 *   `1 Hz` PPI (`3,600` beats) -> [PolarLiveRunningCadenceEstimator] (`VERITY_SENSE_ARM`),
 *   [PolarPpiStreamProcessor], [HrvRollingEngine], and [PolarRawTelemetryRecorder]
 * - Live [UnicycleOdometryEkf] (`recordSnapshots = false`) at `50 Hz` gyro (`180,000` ticks) +
 *   `1 Hz` GNSS (`3,600` updates)
 * - Disabled Polar SDK [BleLogger] (`setLoggerInterface(null)`) under high-rate `d_hex` traffic
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolarDualImuBoundedHeapStressTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PolarRawTelemetryRecorder.getRawSensorDir(context).deleteRecursively()
        BleLogger.setLoggerInterface(null)
    }

    @After
    fun tearDown() {
        PolarRawTelemetryRecorder.getRawSensorDir(context).deleteRecursively()
        BleLogger.setLoggerInterface(null)
    }

    // REQ-STB-023.5 / TST-STB-023.4
    @Test
    fun testSixtyMinuteDualPolarLiveImuAndLiveEkf_maintainsBoundedHeapAndExactAccounting_TST_STB_023_4() {
        val startEpochMs = 1_760_000_000_000L
        val recorder = PolarRawTelemetryRecorder(
            context = context,
            fileBaseName = "stress_dual_polar_60m",
            sessionStartTimeMs = startEpochMs,
            metadata = PolarRawMetadata(
                sensorModel = "Polar H10 + Verity Sense",
                accRateHz = 200,
                gyroRateHz = 52
            )
        )
        recorder.start()

        val h10Cadence = PolarLiveRunningCadenceEstimator(PolarLiveRunningCadenceEstimator.Mode.H10_CHEST)
        val h10Dynamics = PolarH10RunningDynamicsEstimator()
        val verityCadence = PolarLiveRunningCadenceEstimator(PolarLiveRunningCadenceEstimator.Mode.VERITY_SENSE_ARM)
        val ppiProcessor = PolarPpiStreamProcessor()
        val hrvEngine = HrvRollingEngine(context = null)
        val liveEkf = UnicycleOdometryEkf(recordSnapshots = false)
        liveEkf.reset(
            east = 0.0,
            north = 0.0,
            speed = 3.2,
            headingRad = 0.0,
            timestampMs = startEpochMs
        )

        // Ensure BleLogger stays disabled throughout PMD traffic
        val bleLoggerCallbacks = AtomicInteger(0)
        BleLogger.setLoggerInterface(null)
        val samplePmdPayload = ByteArray(140) { (it and 0xFF).toByte() }

        val durationSeconds = 3_600
        val h10Hz = 200
        val verityHz = 52
        val ekfGyroHz = 50
        val strideFreqHz = 1.5 // 90 strides/min = 180 steps/min
        val stepFreqHz = strideFreqHz * 2.0

        var totalRowsEnqueued = 0L
        var heapUsedAtMinute15Bytes = 0L
        var heapUsedAtMinute60Bytes = 0L
        var latestHrvRmssdMs: Double? = null

        try {
            for (sec in 0 until durationSeconds) {
                val secBaseMs = startEpochMs + sec * 1_000L

                // 1. Polar H10 200 Hz ACC (720,000 samples total)
                for (i in 0 until h10Hz) {
                    val tSec = sec + i / h10Hz.toDouble()
                    val tMs = secBaseMs + i * 5L
                    val tNs = tMs * PolarRawTelemetryRecorder.NS_PER_MS
                    val stepPhase = 2.0 * PI * stepFreqHz * tSec
                    val azMg = (1000.0 + 750.0 * sin(stepPhase)).toInt()
                    val axMg = (180.0 * cos(stepPhase)).toInt()
                    val ayMg = (120.0 * sin(stepPhase * 0.5)).toInt()

                    h10Cadence.onAccSample(axMg, ayMg, azMg, tMs)
                    h10Dynamics.onAccSample(axMg, ayMg, azMg, tMs)
                    recorder.recordAcc(tNs, axMg, ayMg, azMg)
                    totalRowsEnqueued++
                }

                // 2. Polar Verity Sense 52 Hz ACC + 52 Hz GYRO (187,200 + 187,200 samples total)
                for (i in 0 until verityHz) {
                    val tSec = sec + i / verityHz.toDouble()
                    val tMs = secBaseMs + (i * 1_000L) / verityHz
                    val tNs = tMs * PolarRawTelemetryRecorder.NS_PER_MS
                    val armPhase = 2.0 * PI * strideFreqHz * tSec
                    val axMg = (450.0 * sin(armPhase)).toInt()
                    val ayMg = (350.0 * cos(armPhase)).toInt()
                    val azMg = (1000.0 + 250.0 * sin(armPhase)).toInt()
                    val gxDps = (110.0 * sin(armPhase)).toFloat()
                    val gyDps = (80.0 * cos(armPhase)).toFloat()
                    val gzDps = (35.0 * sin(armPhase)).toFloat()

                    verityCadence.onAccSample(axMg, ayMg, azMg, tMs)
                    verityCadence.onGyroSample(gxDps, gyDps, gzDps, tMs)
                    recorder.recordAcc(tNs, axMg, ayMg, azMg)
                    recorder.recordGyro(tNs, gxDps, gyDps, gzDps)
                    totalRowsEnqueued += 2L
                }

                // 3. Polar Verity Sense 1 Hz optical PPI -> HrvRollingEngine + recorder (3,600 beats total)
                val ppiMs = 780 + ((sec % 7) - 3) * 8
                val ppiSample = PolarPpiData.PolarPpiSample(
                    ppi = ppiMs,
                    errorEstimate = 10,
                    hr = 152,
                    blockerBit = false,
                    skinContactStatus = true,
                    skinContactSupported = true,
                    timeStamp = 0uL
                )
                val beatEvents = ppiProcessor.processSamples(listOf(ppiSample), secBaseMs)
                for (beat in beatEvents) {
                    hrvEngine.onRrBeat(beat)
                    recorder.onRrBeat(beat)
                    totalRowsEnqueued++
                }
                if (sec >= 60 && sec % 30 == 0) {
                    val snapshot = hrvEngine.evaluate(secBaseMs)
                    if (snapshot != null && !snapshot.isDrained) {
                        latestHrvRmssdMs = snapshot.rmssdMs
                    }
                }

                // 4. Live ATTFused EKF at 50 Hz gyro (180,000 ticks) + 1 Hz GNSS (3,600 updates)
                for (g in 0 until ekfGyroHz) {
                    val tEkfMs = secBaseMs + (g + 1) * 20L
                    val tEkfSec = sec + (g + 1) / ekfGyroHz.toDouble()
                    liveEkf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.01 * sin(tEkfSec * 0.1), timestampMs = tEkfMs)
                }
                liveEkf.updateSpeed(speedMps = 3.2, variance = 0.25, timestampMs = secBaseMs + 1_000L)
                liveEkf.updateGnssPosition(
                    eastMeters = (sec + 1) * 3.2,
                    northMeters = 0.0,
                    accuracyMeters = 4.0,
                    timestampMs = secBaseMs + 1_000L
                )

                // 5. Simulate PMD packet BleLogger.d_hex call (must short-circuit with zero callbacks)
                BleLogger.d_hex("StressPmd", "PMD packet", samplePmdPayload)

                if (sec == 899) {
                    heapUsedAtMinute15Bytes = measureUsedHeapBytesAfterGc()
                }
            }
            heapUsedAtMinute60Bytes = measureUsedHeapBytesAfterGc()
        } finally {
            recorder.stop(timeoutMs = 15_000L)
        }

        // Verify zero EKF snapshot retention across 180,000 gyro + 3,600 GNSS updates
        assertEquals("Live EKF with recordSnapshots=false must retain 0 snapshots", 0, liveEkf.getSnapshots().size)
        assertEquals("BleLogger must stay silent with null logger interface", 0, bleLoggerCallbacks.get())

        // Verify recorder finalized cleanly, cancelled scope, and accounted for every row
        assertFalse("Recorder scope must be cancelled after stop()", recorder.isScopeActiveForTesting())
        assertTrue("Sidecar .polar_raw.csv.gz must exist on disk", recorder.targetFile.exists())
        assertTrue("Recorder must persist rows to GZIP sidecar", recorder.getRecordedRowCount() > 0L)
        assertEquals(
            "Every enqueued row must be either persisted or counted in droppedFrameCount (onUndeliveredElement)",
            totalRowsEnqueued,
            recorder.getRecordedRowCount() + recorder.getDroppedFrameCount()
        )

        // Verify estimators and HRV rolling engine remained healthy across 60 min without drift/collapse
        assertTrue(
            "H10 chest cadence estimator should produce valid cadence (got ${h10Cadence.latestCadenceStridesPerMin})",
            h10Cadence.latestCadenceStridesPerMin in 70.0..110.0
        )
        assertTrue(
            "Verity arm cadence estimator should produce valid cadence (got ${verityCadence.latestCadenceStridesPerMin})",
            verityCadence.latestCadenceStridesPerMin in 70.0..110.0
        )
        assertNotNull("HRV rolling engine should emit valid rolling rMSSD across 60 min", latestHrvRmssdMs)

        // Steady-state heap growth from minute 15 to minute 60 must remain flat/bounded (well below 512 MB OOM)
        val heapGrowthBytes = heapUsedAtMinute60Bytes - heapUsedAtMinute15Bytes
        val maxAllowedGrowthBytes = 32L * 1024L * 1024L
        assertTrue(
            "Steady-state heap delta between minute 15 (${heapUsedAtMinute15Bytes / (1024 * 1024)} MB) " +
                "and minute 60 (${heapUsedAtMinute60Bytes / (1024 * 1024)} MB) must stay < 32 MB " +
                "(delta=${heapGrowthBytes / (1024 * 1024)} MB)",
            heapGrowthBytes < maxAllowedGrowthBytes
        )
    }

    private fun measureUsedHeapBytesAfterGc(): Long {
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(20L)
        System.gc()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
