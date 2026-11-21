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

package com.atrainingtracker.banalservice.replay

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawMetadata
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryReader
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.RawTelemetryRow
import com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel
import com.atrainingtracker.banalservice.devices.raw.PhoneRawMetadata
import com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryRow
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-JVM verification suite for [OfflineTelemetryReplayHarness], [OfflineReplayDiff], and
 * [OfflineReplayGroundTruth] (TST-EXP-046.1, TST-EXP-046.2, TST-EXP-046.3 / REQ-EXP-046, ATT-9349).
 *
 * Intentionally runs on plain JVM without Robolectric to enforce zero Android runtime dependency.
 */
class OfflineTelemetryReplayHarnessTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // TST-EXP-046.1
    @Test
    fun polarMetadataAndReader_executeOnPureJvmWithoutRobolectric() {
        val polarFile = writeSyntheticPolarH10Sidecar(tempFolder.newFile("pure_jvm_h10.polar_raw.csv.gz"), durationSec = 5)
        val session = PolarRawTelemetryReader.readFile(polarFile)
        assertEquals("Polar H10 1234ABCD", session.metadata.sensorModel)
        assertEquals(200, session.metadata.accRateHz)
        assertTrue("Expected H10 ACC rows", session.rows.size >= 900)
    }

    // TST-EXP-046.2
    @Test
    fun replay_syntheticRunningWorkout_phoneOnly_computesCarriageCadenceEkfAndErtsMetrics() {
        val phoneFile = writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("phone_only.phone_raw.csv.gz"),
            durationSec = 70
        )

        val result = OfflineTelemetryReplayHarness.replay(ReplayInputs(phoneRawFile = phoneFile))
        val sc = result.scorecard

        assertTrue("Expected duration ~70s, got ${sc.durationSec}", sc.durationSec >= 65.0)
        assertTrue("Expected phone events processed", sc.phoneEventsProcessed > 1000)
        assertEquals(0, sc.polarEventsProcessed)
        assertTrue("Expected carriage mode distribution populated", sc.carriageModeDistributionPct.isNotEmpty())
        assertTrue("Expected dominant carriage share > 0%", sc.dominantCarriagePct > 0.0)
        assertEquals(BSportType.RUN, sc.inferredSport)
        assertTrue("Expected positive EKF distance, got ${sc.ekfTotalDistanceM}", sc.ekfTotalDistanceM > 150.0)
        assertTrue("Expected positive EKF ascent on 4% climb, got ${sc.ekfAscentM}", sc.ekfAscentM > 4.0)
        assertNotNull("Expected ERTS total distance", sc.ertsTotalDistanceM)
        assertTrue("Expected positive ERTS distance", sc.ertsTotalDistanceM!! > 150.0)
        assertNotNull("Expected ERTS ascent", sc.ertsAscentM)
        assertTrue("Expected positive ERTS ascent", sc.ertsAscentM!! > 4.0)
        assertNotNull("Expected cold-start latency", sc.coldStartTimeToMotionSec)
        assertTrue("Expected cold-start latency <= 5s, got ${sc.coldStartTimeToMotionSec}", sc.coldStartTimeToMotionSec!! <= 5.0)
        assertNotNull("Expected phone IMU cadence mean", sc.phoneCadence.meanSpm)
        assertTrue(
            "Expected phone cadence near 85 strides/min (170 steps/min), got ${sc.phoneCadence.meanSpm}",
            sc.phoneCadence.meanSpm!! in 70.0..100.0
        )
        assertTrue("Expected 1-Hz series populated", result.series.size >= 65)
    }

    // TST-EXP-046.2
    @Test
    fun replay_syntheticRunningWorkout_polarOnly_computesH10AndVerityCadenceAndDynamics() {
        val h10File = writeSyntheticPolarH10Sidecar(
            tempFolder.newFile("polar_h10.polar_raw.csv.gz"),
            durationSec = 60
        )
        val verityFile = writeSyntheticPolarVeritySidecar(
            tempFolder.newFile("polar_verity.polar_raw.csv.gz"),
            durationSec = 60
        )

        val result = OfflineTelemetryReplayHarness.replay(
            ReplayInputs(phoneRawFile = null, polarRawFiles = listOf(h10File, verityFile))
        )
        val sc = result.scorecard

        assertEquals(0, sc.phoneEventsProcessed)
        assertTrue("Expected Polar events processed", sc.polarEventsProcessed > 10_000)
        assertNotNull("Expected H10 cadence", sc.polarH10Cadence.meanSpm)
        assertTrue(
            "Expected H10 cadence near 85 strides/min (170 steps/min), got ${sc.polarH10Cadence.meanSpm}",
            sc.polarH10Cadence.meanSpm!! in 75.0..95.0
        )
        assertNotNull("Expected Verity cadence", sc.polarVerityCadence.meanSpm)
        assertTrue(
            "Expected Verity cadence near 85 strides/min (170 steps/min), got ${sc.polarVerityCadence.meanSpm}",
            sc.polarVerityCadence.meanSpm!! in 70.0..100.0
        )
        assertTrue("Expected pairwise H10 vs Verity cadence comparison", sc.cadenceComparisons.isNotEmpty())
        assertNotNull("Expected H10 vertical oscillation", sc.h10MeanVerticalOscillationCm)
        assertNotNull("Expected H10 ground contact time", sc.h10MeanGroundContactTimeMs)
    }

    // TST-EXP-046.2
    @Test
    fun replay_veritySenseGyroOnlySidecar_preservesUnscaledDegPerSecAndRoutesSenseModel() {
        val verityGyroOnlyFile = tempFolder.newFile("verity_gyro_only.polar_raw.csv.gz")
        val metadata = PolarRawMetadata(
            schemaVersion = 1,
            appVersion = "5.0.0",
            sensorModel = "Polar Sense 12345678",
            firmwareVersion = "2.1.0",
            streamMode = "LIVE",
            accRateHz = 0,
            gyroRateHz = 0, // Intentionally 0 to verify "sense" model routing without relying on gyroRateHz > 0
            sportId = BSportType.RUN.ordinal
        )
        val strideFreqHz = (170.0 / 60.0) / 2.0 // 85 strides/min = 1.4167 Hz arm swing
        val dtMs = 19L // ~52.6 Hz
        val totalMs = 45_000L

        FileOutputStream(verityGyroOnlyFile).use { fos ->
            GZIPOutputStream(fos).use { gzipOut ->
                BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { w ->
                    w.write("# metadata: ${metadata.toJsonString()}")
                    w.newLine()
                    w.write(RawTelemetryRow.CSV_HEADER)
                    w.newLine()

                    var tMs = 0L
                    while (tMs <= totalMs) {
                        val tSec = tMs / 1000.0
                        val tNs = tMs * 1_000_000L
                        val omega = 2.0 * PI * strideFreqHz * tSec
                        // Realistic arm-swing angular velocity in deg/s stored unscaled in gx_mdps..gz_mdps
                        val gxDps = 180.0 * sin(omega)
                        val gyDps = 45.0 * cos(omega)
                        val gzDps = 25.0 * sin(2.0 * omega)
                        w.write(
                            String.format(
                                Locale.US,
                                "%d,%d,GYRO,,,,%.2f,%.2f,%.2f,,",
                                tNs, tMs, gxDps, gyDps, gzDps
                            )
                        )
                        w.newLine()
                        tMs += dtMs
                    }
                }
            }
        }

        val result = OfflineTelemetryReplayHarness.replay(
            ReplayInputs(phoneRawFile = null, polarRawFiles = listOf(verityGyroOnlyFile))
        )
        val sc = result.scorecard

        assertEquals("Sense model must route to Verity, not H10", 0, sc.polarH10Cadence.sampleCount)
        assertNotNull("Expected gyro-only Verity Sense cadence", sc.polarVerityCadence.meanSpm)
        assertTrue(
            "Expected gyro-only Verity cadence near 85 strides/min, got ${sc.polarVerityCadence.meanSpm}",
            sc.polarVerityCadence.meanSpm!! in 75.0..95.0
        )
    }

    // TST-EXP-046.2
    @Test
    fun replay_syntheticRunningWorkout_pairedPhoneAndPolar_isBitExactDeterministicAcrossRuns() {
        val phoneFile = writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("paired_phone.phone_raw.csv.gz"),
            durationSec = 65
        )
        val h10File = writeSyntheticPolarH10Sidecar(
            tempFolder.newFile("paired_h10.polar_raw.csv.gz"),
            durationSec = 65
        )
        val verityFile = writeSyntheticPolarVeritySidecar(
            tempFolder.newFile("paired_verity.polar_raw.csv.gz"),
            durationSec = 65
        )
        val inputs = ReplayInputs(
            phoneRawFile = phoneFile,
            polarRawFiles = listOf(h10File, verityFile)
        )

        val run1 = OfflineTelemetryReplayHarness.replay(inputs)
        val run2 = OfflineTelemetryReplayHarness.replay(inputs)

        assertEquals("Repeated replay scorecards must be bit-exact identical", run1.scorecard, run2.scorecard)
        assertEquals("Repeated replay 1-Hz series must be bit-exact identical", run1.series, run2.series)
        assertEquals(
            "Repeated replay JSON output must be bit-exact identical",
            run1.scorecard.toJsonString(),
            run2.scorecard.toJsonString()
        )
        assertTrue("Expected both phone and polar events", run1.scorecard.phoneEventsProcessed > 0 && run1.scorecard.polarEventsProcessed > 0)
        assertTrue("Expected pairwise cadence comparisons across sources", run1.scorecard.cadenceComparisons.isNotEmpty())
    }

    // TST-EXP-046.3
    @Test
    fun diff_compareBaselineAndCandidate_computesExactQuantitativeDeltas() {
        val phoneFile = writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("diff_phone.phone_raw.csv.gz"),
            durationSec = 60
        )

        val baseline = OfflineTelemetryReplayHarness.replay(
            ReplayInputs(phoneRawFile = phoneFile, config = ReplayConfig(motionSpeedThresholdMps = 0.5))
        ).scorecard
        val candidate = OfflineTelemetryReplayHarness.replay(
            ReplayInputs(phoneRawFile = phoneFile, config = ReplayConfig(motionSpeedThresholdMps = 3.2))
        ).scorecard

        val diff = OfflineReplayDiff.compare(baseline, candidate)
        assertEquals(candidate.ekfTotalDistanceM - baseline.ekfTotalDistanceM, diff.deltaEkfDistanceM, 1e-9)
        assertEquals(candidate.ekfAscentM - baseline.ekfAscentM, diff.deltaEkfAscentM, 1e-9)
        assertEquals(candidate.carriageTransitionCount - baseline.carriageTransitionCount, diff.deltaTransitions)
        assertEquals(candidate.dominantCarriagePct - baseline.dominantCarriagePct, diff.deltaDominantCarriagePct, 1e-9)
    }

    // TST-EXP-046.3
    @Test
    fun writeCsv_emitsDotDecimalOneHzRowsWithCanonicalHeader() {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val phoneFile = writeSyntheticPhoneRunningSidecar(
                tempFolder.newFile("csv_locale.phone_raw.csv.gz"),
                durationSec = 20
            )
            val result = OfflineTelemetryReplayHarness.replay(ReplayInputs(phoneRawFile = phoneFile))
            val csvOut = File(tempFolder.root, "nested/replay_1hz.csv")
            result.writeCsv(csvOut)

            assertTrue(csvOut.exists())
            val lines = csvOut.readLines(StandardCharsets.UTF_8)
            assertEquals(OneHzReplayRow.CSV_HEADER, lines.first())
            assertEquals(result.series.size + 1, lines.size)
            val dataLine = lines[1]
            // Canonical header has 15 columns (14 commas); German decimal comma would corrupt column count.
            assertEquals(14, dataLine.count { it == ',' })
            assertTrue("Expected dot-decimal formatting in CSV row: $dataLine", dataLine.contains('.'))
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    // TST-EXP-046.3
    @Test
    fun groundTruth_verify_passesWithinBoundsAndReportsExplicitViolationsOnRegression() {
        val phoneFile = writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("truth_phone.phone_raw.csv.gz"),
            durationSec = 60
        )
        val scorecard = OfflineTelemetryReplayHarness.replay(ReplayInputs(phoneRawFile = phoneFile)).scorecard
        val actualDist = scorecard.ertsTotalDistanceM ?: scorecard.ekfTotalDistanceM
        val actualAscent = scorecard.ertsAscentM ?: scorecard.ekfAscentM

        val passingJson = """
            {
              "expectedDominantMode": "${scorecard.dominantCarriageMode.name}",
              "minDominantModePct": 50.0,
              "maxTransitions": 20,
              "expectedDistanceM": ${String.format(Locale.US, "%.1f", actualDist)},
              "distanceToleranceM": 25.0,
              "expectedAscentM": ${String.format(Locale.US, "%.1f", actualAscent)},
              "ascentToleranceM": 10.0,
              "maxColdStartSec": 10.0
            }
        """.trimIndent()
        val passSpec = OfflineReplayGroundTruth.parseJson(passingJson)
        val passCheck = OfflineReplayGroundTruth.verify(scorecard, passSpec)
        assertTrue("Expected passing ground truth, violations=${passCheck.violations}", passCheck.passed)
        assertTrue(passCheck.violations.isEmpty())

        val failingSpec = GroundTruthSpec(
            expectedDominantMode = if (scorecard.dominantCarriageMode == PhoneCarriageMode.BIKE_HANDLEBAR) {
                PhoneCarriageMode.RUN_WAIST_HIP
            } else {
                PhoneCarriageMode.BIKE_HANDLEBAR
            },
            expectedDistanceM = actualDist + 500.0,
            distanceToleranceM = 10.0,
            expectedAscentM = actualAscent + 200.0,
            ascentToleranceM = 5.0
        )
        val failCheck = OfflineReplayGroundTruth.verify(scorecard, failingSpec)
        assertFalse("Expected ground-truth regression failure", failCheck.passed)
        assertTrue("Expected >= 3 explicit violation messages, got ${failCheck.violations}", failCheck.violations.size >= 3)
        assertTrue(failCheck.violations.any { it.contains("Dominant carriage mode mismatch") })
        assertTrue(failCheck.violations.any { it.contains("Distance") })
        assertTrue(failCheck.violations.any { it.contains("Ascent") })
    }

    companion object {
        /**
         * Generates a realistic synthetic running workout `.phone_raw.csv.gz` containing all 8 channels:
         * `STATE(RUN)`, 50-Hz `ACC` + `GYRO` + `ROTVEC` (running stride at 170 spm = 2.833 Hz step frequency,
         * 1.417 Hz stride cycle), 1-Hz `BARO` (4% grade climb), 1-Hz `GNSS` (~3.0 m/s northbound), and `STEP`.
         */
        fun writeSyntheticPhoneRunningSidecar(file: File, durationSec: Int): File {
            val metadata = PhoneRawMetadata(
                schemaVersion = 1,
                appVersion = "5.0.0",
                deviceModel = "SyntheticTestPhone",
                sportId = BSportType.RUN.ordinal.toLong(),
                sessionStartTimeMs = 1_760_000_000_000L
            )

            val stepFreqHz = 170.0 / 60.0 // 2.8333 steps/sec
            val speedMps = 3.0 // 3.0 m/s running pace
            val grade = 0.04 // +4% climb -> +0.12 m/s vertical speed
            val baseLat = 48.1351
            val baseLon = 11.5820
            val baseAltM = 500.0
            val metersPerDegLat = 111_111.0

            FileOutputStream(file).use { fos ->
                GZIPOutputStream(fos).use { gzipOut ->
                    BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { w ->
                        w.write(metadata.toHeaderLine())
                        w.newLine()
                        w.write(PhoneRawTelemetryRow.CSV_HEADER)
                        w.newLine()

                        // Initial STATE row: v0=sportId, v1=bSportCode, v2=carriageCode
                        val baseEpochMs = 1_760_000_000_000L
                        w.write(
                            PhoneRawTelemetryRow(
                                elapsedRealtimeNs = 0L,
                                epochMs = baseEpochMs,
                                channel = PhoneRawChannel.STATE,
                                v0 = BSportType.RUN.ordinal.toDouble(),
                                v1 = BSportType.RUN.ordinal.toDouble(),
                                v2 = PhoneCarriageMode.UNKNOWN.code.toDouble()
                            ).toCsvLine()
                        )
                        w.newLine()

                        var nextStepTMs = 353L
                        val dtMs = 20L // 50 Hz
                        val totalMs = durationSec * 1000L
                        var tMs = 0L
                        while (tMs <= totalMs) {
                            val tSec = tMs / 1000.0
                            val tNs = tMs * 1_000_000L
                            val epochMs = baseEpochMs + tMs
                            val omegaStep = 2.0 * PI * stepFreqHz * tSec
                            val omegaStride = PI * stepFreqHz * tSec

                            // Running pocket acceleration: vertical bounce at stepFreq + lateral sway at strideFreq
                            val ax = 1.8 * sin(omegaStride)
                            val ay = 9.81 + 6.5 * sin(omegaStep) + 1.2 * cos(2.0 * omegaStep)
                            val az = 2.4 * cos(omegaStride)
                            w.write(
                                PhoneRawTelemetryRow(
                                    elapsedRealtimeNs = tNs,
                                    epochMs = epochMs,
                                    channel = PhoneRawChannel.ACC,
                                    v0 = ax,
                                    v1 = ay,
                                    v2 = az
                                ).toCsvLine()
                            )
                            w.newLine()

                            // Pocket thigh-swing gyro (clear oscillatory pitch/roll at stride frequency)
                            val gx = 1.8 * cos(omegaStride)
                            val gy = 0.4 * sin(omegaStep)
                            val gz = 0.9 * sin(omegaStride)
                            w.write(
                                PhoneRawTelemetryRow(
                                    elapsedRealtimeNs = tNs,
                                    epochMs = epochMs,
                                    channel = PhoneRawChannel.GYRO,
                                    v0 = gx,
                                    v1 = gy,
                                    v2 = gz
                                ).toCsvLine()
                            )
                            w.newLine()

                            // Upright identity-like rotation vector quaternion (qx, qy, qz, qw)
                            w.write(
                                PhoneRawTelemetryRow(
                                    elapsedRealtimeNs = tNs,
                                    epochMs = epochMs,
                                    channel = PhoneRawChannel.ROTVEC,
                                    v0 = 0.0,
                                    v1 = 0.0,
                                    v2 = 0.0,
                                    v3 = 1.0
                                ).toCsvLine()
                            )
                            w.newLine()

                            // 1-Hz GNSS + BARO
                            if (tMs % 1000L == 0L) {
                                val distM = speedMps * tSec
                                val altM = baseAltM + distM * grade
                                val lat = baseLat + (distM / metersPerDegLat)
                                val lon = baseLon
                                // Standard barometric formula inverse: p = 1013.25 * (1 - alt / 44330)^(1/0.1903)
                                val pressureHpa = 1013.25 * Math.pow(1.0 - altM / 44330.0, 5.255)

                                w.write(
                                    PhoneRawTelemetryRow(
                                        elapsedRealtimeNs = tNs,
                                        epochMs = epochMs,
                                        channel = PhoneRawChannel.BARO,
                                        v0 = pressureHpa,
                                        v1 = altM
                                    ).toCsvLine()
                                )
                                w.newLine()

                                w.write(
                                    PhoneRawTelemetryRow(
                                        elapsedRealtimeNs = tNs,
                                        epochMs = epochMs,
                                        channel = PhoneRawChannel.GNSS,
                                        v0 = lat,
                                        v1 = lon,
                                        v2 = altM,
                                        v3 = speedMps,
                                        v4 = 0.0,
                                        v5 = 3.5
                                    ).toCsvLine()
                                )
                                w.newLine()
                            }

                            if (tMs >= nextStepTMs) {
                                w.write(
                                    PhoneRawTelemetryRow(
                                        elapsedRealtimeNs = tNs,
                                        epochMs = epochMs,
                                        channel = PhoneRawChannel.STEP
                                    ).toCsvLine()
                                )
                                w.newLine()
                                nextStepTMs += 353L
                            }

                            tMs += dtMs
                        }
                    }
                }
            }
            return file
        }

        /**
         * Generates a synthetic Polar H10 200-Hz ACC sidecar matching a 170 spm running cadence
         * (2.833 Hz vertical chest oscillation + footstrike impact peaks).
         */
        fun writeSyntheticPolarH10Sidecar(file: File, durationSec: Int): File {
            val metadata = PolarRawMetadata(
                schemaVersion = 1,
                appVersion = "5.0.0",
                sensorModel = "Polar H10 1234ABCD",
                firmwareVersion = "3.2.0",
                streamMode = "LIVE",
                accRateHz = 200,
                gyroRateHz = 0,
                sportId = BSportType.RUN.ordinal
            )
            val stepFreqHz = 170.0 / 60.0
            val dtMs = 5L // 200 Hz
            val totalMs = durationSec * 1000L

            FileOutputStream(file).use { fos ->
                GZIPOutputStream(fos).use { gzipOut ->
                    BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { w ->
                        w.write("# metadata: ${metadata.toJsonString()}")
                        w.newLine()
                        w.write(RawTelemetryRow.CSV_HEADER)
                        w.newLine()

                        var tMs = 0L
                        while (tMs <= totalMs) {
                            val tSec = tMs / 1000.0
                            val tNs = tMs * 1_000_000L
                            val omega = 2.0 * PI * stepFreqHz * tSec
                            // H10 chest-mounted vertical axis is X (aligned with gravity ~1000 mG)
                            val axMg = (1000.0 + 750.0 * sin(omega) + 220.0 * cos(2.0 * omega)).toInt()
                            val ayMg = (150.0 * sin(0.5 * omega)).toInt()
                            val azMg = (180.0 * cos(omega)).toInt()
                            w.write("$tNs,$tMs,ACC,$axMg,$ayMg,$azMg,,,,,,")
                            w.newLine()
                            tMs += dtMs
                        }
                    }
                }
            }
            return file
        }

        /**
         * Generates a synthetic Polar Verity Sense 52-Hz GYRO + ACC sidecar matching a 170 spm
         * running arm swing (arm oscillates at stride frequency = stepFreq / 2 = 1.417 Hz).
         */
        fun writeSyntheticPolarVeritySidecar(file: File, durationSec: Int): File {
            val metadata = PolarRawMetadata(
                schemaVersion = 1,
                appVersion = "5.0.0",
                sensorModel = "Polar Sense 9876EF01",
                firmwareVersion = "2.1.0",
                streamMode = "LIVE",
                accRateHz = 52,
                gyroRateHz = 52,
                sportId = BSportType.RUN.ordinal
            )
            val strideFreqHz = (170.0 / 60.0) / 2.0 // 1.4167 Hz arm swing
            val dtMs = 19L // ~52.6 Hz
            val totalMs = durationSec * 1000L

            FileOutputStream(file).use { fos ->
                GZIPOutputStream(fos).use { gzipOut ->
                    BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { w ->
                        w.write("# metadata: ${metadata.toJsonString()}")
                        w.newLine()
                        w.write(RawTelemetryRow.CSV_HEADER)
                        w.newLine()

                        var tMs = 0L
                        while (tMs <= totalMs) {
                            val tSec = tMs / 1000.0
                            val tNs = tMs * 1_000_000L
                            val omegaStride = 2.0 * PI * strideFreqHz * tSec
                            // Strong arm-swing angular rate in deg/s (Verity Sense GYRO columns)
                            val gxDps = 180.0 * sin(omegaStride)
                            val gyDps = 45.0 * cos(omegaStride)
                            val gzDps = 30.0 * sin(2.0 * omegaStride)
                            w.write(
                                String.format(
                                    Locale.US,
                                    "%d,%d,GYRO,,,,%.2f,%.2f,%.2f,,",
                                    tNs, tMs, gxDps, gyDps, gzDps
                                )
                            )
                            w.newLine()

                            val axMg = (300.0 * sin(omegaStride)).toInt()
                            val ayMg = (980.0 + 420.0 * sin(2.0 * omegaStride)).toInt()
                            val azMg = (200.0 * cos(omegaStride)).toInt()
                            w.write("$tNs,$tMs,ACC,$axMg,$ayMg,$azMg,,,,,,")
                            w.newLine()

                            tMs += dtMs
                        }
                    }
                }
            }
            return file
        }
    }
}
