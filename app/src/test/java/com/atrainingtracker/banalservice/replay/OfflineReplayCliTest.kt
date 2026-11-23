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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * Pure-JVM unit verification for [OfflineReplayCli] (TST-EXP-046.4 / REQ-EXP-046.4, ATT-9349).
 */
class OfflineReplayCliTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // TST-EXP-046.4
    @Test
    fun cli_validInputsWithPassingTruth_printsScorecardWritesCsvAndJsonAndReturnsZero() {
        val phoneFile = OfflineTelemetryReplayHarnessTest.writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("cli_phone.phone_raw.csv.gz"),
            durationSec = 40
        )
        val polarFile = OfflineTelemetryReplayHarnessTest.writeSyntheticPolarH10Sidecar(
            tempFolder.newFile("cli_h10.polar_raw.csv.gz"),
            durationSec = 40
        )
        val truthFile = tempFolder.newFile("pass.truth.json").apply {
            writeText("""{"minDominantModePct": 10.0, "maxColdStartSec": 15.0}""", StandardCharsets.UTF_8)
        }
        val csvOut = File(tempFolder.root, "out/cli_1hz.csv")
        val jsonOut = File(tempFolder.root, "out/cli_scorecard.json")

        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val exitCode = OfflineReplayCli.runCli(
            args = arrayOf(
                "--phone", phoneFile.absolutePath,
                "--polar", polarFile.absolutePath,
                "--csv", csvOut.absolutePath,
                "--json", jsonOut.absolutePath,
                "--truth", truthFile.absolutePath
            ),
            out = PrintStream(outBuf, true, StandardCharsets.UTF_8.name()),
            err = PrintStream(errBuf, true, StandardCharsets.UTF_8.name())
        )

        val stdout = outBuf.toString(StandardCharsets.UTF_8.name())
        val stderr = errBuf.toString(StandardCharsets.UTF_8.name())

        assertEquals("Expected exit code 0, stderr=$stderr", 0, exitCode)
        assertTrue("Expected scorecard header in stdout:\n$stdout", stdout.contains("=== Offline Telemetry Replay Scorecard ==="))
        assertTrue("Expected GROUND TRUTH: PASS in stdout:\n$stdout", stdout.contains("GROUND TRUTH: PASS"))
        assertTrue("Expected CSV output file", csvOut.exists() && csvOut.length() > 100)
        assertTrue("Expected JSON output file", jsonOut.exists() && jsonOut.readText().contains("\"ekfTotalDistanceM\""))
    }

    // TST-EXP-046.4
    @Test
    fun cli_failingGroundTruth_printsViolationsAndReturnsNonZero() {
        val phoneFile = OfflineTelemetryReplayHarnessTest.writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("cli_fail_phone.phone_raw.csv.gz"),
            durationSec = 30
        )
        val truthFile = tempFolder.newFile("fail.truth.json").apply {
            writeText("""{"expectedDistanceM": 99999.0, "distanceToleranceM": 5.0}""", StandardCharsets.UTF_8)
        }

        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val exitCode = OfflineReplayCli.runCli(
            args = arrayOf("--phone", phoneFile.absolutePath, "--truth", truthFile.absolutePath),
            out = PrintStream(outBuf, true, StandardCharsets.UTF_8.name()),
            err = PrintStream(errBuf, true, StandardCharsets.UTF_8.name())
        )

        val stderr = errBuf.toString(StandardCharsets.UTF_8.name())
        assertEquals("Expected exit code 1 on ground-truth failure", 1, exitCode)
        assertTrue("Expected GROUND TRUTH: FAIL in stderr:\n$stderr", stderr.contains("GROUND TRUTH: FAIL"))
        assertTrue("Expected distance violation message in stderr:\n$stderr", stderr.contains("Distance"))
    }

    // TST-EXP-046.4
    @Test
    fun cli_missingOrInvalidArguments_returnsUsageErrorCode() {
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val exitNoArgs = OfflineReplayCli.runCli(
            args = emptyArray(),
            out = PrintStream(outBuf, true, StandardCharsets.UTF_8.name()),
            err = PrintStream(errBuf, true, StandardCharsets.UTF_8.name())
        )
        assertEquals(2, exitNoArgs)
        assertTrue(errBuf.toString(StandardCharsets.UTF_8.name()).contains("At least one --phone or --polar"))

        val errBuf2 = ByteArrayOutputStream()
        val exitMissingFile = OfflineReplayCli.runCli(
            args = arrayOf("--phone", File(tempFolder.root, "missing.phone_raw.csv.gz").absolutePath),
            out = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
            err = PrintStream(errBuf2, true, StandardCharsets.UTF_8.name())
        )
        assertEquals(2, exitMissingFile)
        assertTrue(errBuf2.toString(StandardCharsets.UTF_8.name()).contains("does not exist"))
    }

    // REQ-EXP-047.3 / TST-EXP-047.3
    @Test
    fun cli_gnssProviderFlag_selectsAutoGpsFusedAndRejectsInvalidValue_TST_EXP_047_3() {
        val phoneFile = OfflineTelemetryReplayHarnessTest.writeSyntheticPhoneRunningSidecar(
            tempFolder.newFile("cli_provider_phone.phone_raw.csv.gz"),
            durationSec = 25
        )
        val jsonAuto = File(tempFolder.root, "out/auto.json")
        val jsonGps = File(tempFolder.root, "out/gps.json")
        val jsonFused = File(tempFolder.root, "out/fused.json")

        val exitAuto = OfflineReplayCli.runCli(
            args = arrayOf("--phone", phoneFile.absolutePath, "--gnss-provider", "auto", "--json", jsonAuto.absolutePath),
            out = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
            err = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name())
        )
        val exitGps = OfflineReplayCli.runCli(
            args = arrayOf("--phone", phoneFile.absolutePath, "--gnss-provider", "gps", "--json", jsonGps.absolutePath),
            out = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
            err = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name())
        )
        val exitFused = OfflineReplayCli.runCli(
            args = arrayOf("--phone", phoneFile.absolutePath, "--gnss-provider", "fused", "--json", jsonFused.absolutePath),
            out = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
            err = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name())
        )

        assertEquals(0, exitAuto)
        assertEquals(0, exitGps)
        assertEquals(0, exitFused)
        assertEquals(jsonAuto.readText(), jsonGps.readText())
        assertTrue(
            "Fused-only CLI run on GPS-only sidecar must report 0.0000 distance",
            jsonFused.readText().contains("\"ekfTotalDistanceM\":0.0000")
        )

        val errInvalid = ByteArrayOutputStream()
        val exitInvalid = OfflineReplayCli.runCli(
            args = arrayOf("--phone", phoneFile.absolutePath, "--gnss-provider", "bogus"),
            out = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()),
            err = PrintStream(errInvalid, true, StandardCharsets.UTF_8.name())
        )
        val errText = errInvalid.toString(StandardCharsets.UTF_8.name())
        assertEquals(2, exitInvalid)
        assertTrue("Expected invalid provider diagnostic:\n$errText", errText.contains("Invalid --gnss-provider"))
        assertTrue("Expected usage line to document --gnss-provider:\n$errText", errText.contains("--gnss-provider auto|gps|fused"))
    }
}
