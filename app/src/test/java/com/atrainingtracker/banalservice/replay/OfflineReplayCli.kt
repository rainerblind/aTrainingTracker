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

import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Pure-JVM CLI entrypoint for `:app:replayRawTelemetry` (REQ-EXP-046.4, ATT-9349).
 *
 * Supported arguments:
 * - `--phone <path>`   Path to `.phone_raw.csv.gz`
 * - `--polar <path>`   Path to `.polar_raw.csv.gz` (may be specified multiple times)
 * - `--polar2 <path>`  Optional second `.polar_raw.csv.gz` (e.g., Verity Sense alongside H10)
 * - `--csv <path>`     Optional 1-Hz CSV output path
 * - `--json <path>`    Optional JSON scorecard output path
 * - `--truth <path>`   Optional `.truth.json` ground-truth regression file (non-zero exit on violation)
 */
object OfflineReplayCli {

    fun runCli(
        args: Array<String>,
        out: PrintStream = System.out,
        err: PrintStream = System.err
    ): Int {
        var phoneFile: File? = null
        val polarFiles = mutableListOf<File>()
        var csvOut: File? = null
        var jsonOut: File? = null
        var truthFile: File? = null

        var i = 0
        while (i < args.size) {
            when (val flag = args[i]) {
                "--phone" -> {
                    val v = args.getOrNull(i + 1) ?: return usageError(err, "Missing value for --phone")
                    phoneFile = File(v); i += 2
                }
                "--polar", "--polar2" -> {
                    val v = args.getOrNull(i + 1) ?: return usageError(err, "Missing value for $flag")
                    polarFiles.add(File(v)); i += 2
                }
                "--csv" -> {
                    val v = args.getOrNull(i + 1) ?: return usageError(err, "Missing value for --csv")
                    csvOut = File(v); i += 2
                }
                "--json" -> {
                    val v = args.getOrNull(i + 1) ?: return usageError(err, "Missing value for --json")
                    jsonOut = File(v); i += 2
                }
                "--truth" -> {
                    val v = args.getOrNull(i + 1) ?: return usageError(err, "Missing value for --truth")
                    truthFile = File(v); i += 2
                }
                else -> return usageError(err, "Unknown argument: $flag")
            }
        }

        if (phoneFile == null && polarFiles.isEmpty()) {
            return usageError(err, "At least one --phone or --polar sidecar file is required.")
        }
        if (phoneFile != null && !phoneFile.exists()) {
            err.println("ERROR: Phone sidecar does not exist: ${phoneFile.absolutePath}")
            return 2
        }
        for (pf in polarFiles) {
            if (!pf.exists()) {
                err.println("ERROR: Polar sidecar does not exist: ${pf.absolutePath}")
                return 2
            }
        }
        if (truthFile != null && !truthFile.exists()) {
            err.println("ERROR: Ground-truth file does not exist: ${truthFile.absolutePath}")
            return 2
        }

        val result = OfflineTelemetryReplayHarness.replay(
            ReplayInputs(phoneRawFile = phoneFile, polarRawFiles = polarFiles)
        )
        out.print(result.scorecard.toFormattedReport())

        csvOut?.let { file ->
            result.writeCsv(file)
            out.println("Wrote 1-Hz CSV (${result.series.size} rows): ${file.absolutePath}")
        }
        jsonOut?.let { file ->
            file.parentFile?.mkdirs()
            file.writeText(result.scorecard.toJsonString(), Charsets.UTF_8)
            out.println("Wrote JSON scorecard: ${file.absolutePath}")
        }

        if (truthFile != null) {
            val spec = OfflineReplayGroundTruth.parseFile(truthFile)
            val check = OfflineReplayGroundTruth.verify(result.scorecard, spec)
            if (check.passed) {
                out.println("GROUND TRUTH: PASS")
            } else {
                err.println("GROUND TRUTH: FAIL (${check.violations.size} violation(s))")
                for (v in check.violations) {
                    err.println("  - $v")
                }
                return 1
            }
        }
        return 0
    }

    private fun usageError(err: PrintStream, message: String): Int {
        err.println("ERROR: $message")
        err.println("Usage: replayRawTelemetry --phone <path.phone_raw.csv.gz> [--polar <path.polar_raw.csv.gz>] [--csv <out.csv>] [--json <out.json>] [--truth <spec.truth.json>]")
        return 2
    }
}

fun main(args: Array<String>) {
    val exitCode = OfflineReplayCli.runCli(args)
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
