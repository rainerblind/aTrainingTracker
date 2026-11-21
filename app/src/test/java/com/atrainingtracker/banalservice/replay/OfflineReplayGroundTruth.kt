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

import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Optional ground-truth expectations for automated offline replay regression assertions
 * (REQ-EXP-046.3, ATT-9349). Pure JVM with zero `org.json` dependency.
 */
data class GroundTruthSpec(
    val expectedDominantMode: PhoneCarriageMode? = null,
    val minDominantModePct: Double? = null,
    val maxTransitions: Int? = null,
    val expectedDistanceM: Double? = null,
    val distanceToleranceM: Double = 50.0,
    val expectedAscentM: Double? = null,
    val ascentToleranceM: Double = 15.0,
    val maxCadenceMaeSpm: Double? = null,
    val maxColdStartSec: Double? = null
)

/**
 * Structured pass/fail evaluation of an [OfflineReplayScorecard] against a [GroundTruthSpec].
 */
data class GroundTruthCheckResult(
    val passed: Boolean,
    val violations: List<String>
)

/**
 * Pure-JVM JSON parser and bound evaluator for optional `.truth.json` ground-truth sidecars.
 */
object OfflineReplayGroundTruth {

    private val STR_DOM_MODE = Regex("\"expectedDominantMode\"\\s*:\\s*\"([A-Z0-9_]+)\"")
    private val NUM_MIN_DOM_PCT = Regex("\"minDominantModePct\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val NUM_MAX_TRANS = Regex("\"maxTransitions\"\\s*:\\s*(-?\\d+)")
    private val NUM_EXP_DIST = Regex("\"expectedDistanceM\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val NUM_DIST_TOL = Regex("\"distanceToleranceM\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val NUM_EXP_ASCENT = Regex("\"expectedAscentM\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val NUM_ASCENT_TOL = Regex("\"ascentToleranceM\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val NUM_MAX_CAD_MAE = Regex("\"maxCadenceMaeSpm\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val NUM_MAX_COLD = Regex("\"maxColdStartSec\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")

    fun parseJson(json: String): GroundTruthSpec {
        val trimmed = json.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "Ground-truth file must be a valid JSON object"
        }
        val modeName = STR_DOM_MODE.find(trimmed)?.groupValues?.get(1)
        val parsedMode = modeName?.let { name ->
            PhoneCarriageMode.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Unknown expectedDominantMode: $name")
        }
        return GroundTruthSpec(
            expectedDominantMode = parsedMode,
            minDominantModePct = NUM_MIN_DOM_PCT.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull(),
            maxTransitions = NUM_MAX_TRANS.find(trimmed)?.groupValues?.get(1)?.toIntOrNull(),
            expectedDistanceM = NUM_EXP_DIST.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull(),
            distanceToleranceM = NUM_DIST_TOL.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull() ?: 50.0,
            expectedAscentM = NUM_EXP_ASCENT.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull(),
            ascentToleranceM = NUM_ASCENT_TOL.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull() ?: 15.0,
            maxCadenceMaeSpm = NUM_MAX_CAD_MAE.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull(),
            maxColdStartSec = NUM_MAX_COLD.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()
        )
    }

    fun parseFile(file: File): GroundTruthSpec = parseJson(file.readText(Charsets.UTF_8))

    fun verify(scorecard: OfflineReplayScorecard, spec: GroundTruthSpec): GroundTruthCheckResult {
        val violations = mutableListOf<String>()

        spec.expectedDominantMode?.let { expected ->
            if (scorecard.dominantCarriageMode != expected) {
                violations.add(
                    "Dominant carriage mode mismatch: expected $expected, actual ${scorecard.dominantCarriageMode} " +
                        "(${String.format(Locale.US, "%.1f", scorecard.dominantCarriagePct)}%)"
                )
            }
            spec.minDominantModePct?.let { minPct ->
                val actualPct = scorecard.carriageModeDistributionPct[expected] ?: 0.0
                if (actualPct < minPct) {
                    violations.add(
                        "Expected mode $expected share ${String.format(Locale.US, "%.1f", actualPct)}% < min ${String.format(Locale.US, "%.1f", minPct)}%"
                    )
                }
            }
        } ?: spec.minDominantModePct?.let { minPct ->
            if (scorecard.dominantCarriagePct < minPct) {
                violations.add(
                    "Dominant carriage share ${String.format(Locale.US, "%.1f", scorecard.dominantCarriagePct)}% < min ${String.format(Locale.US, "%.1f", minPct)}%"
                )
            }
        }

        spec.maxTransitions?.let { maxT ->
            if (scorecard.carriageTransitionCount > maxT) {
                violations.add("Carriage transitions ${scorecard.carriageTransitionCount} > max $maxT")
            }
        }

        spec.expectedDistanceM?.let { expDist ->
            val actualDist = scorecard.ertsTotalDistanceM ?: scorecard.ekfTotalDistanceM
            val err = abs(actualDist - expDist)
            if (err > spec.distanceToleranceM) {
                violations.add(
                    "Distance ${String.format(Locale.US, "%.1f", actualDist)} m outside ${String.format(Locale.US, "%.1f", expDist)} ± ${String.format(Locale.US, "%.1f", spec.distanceToleranceM)} m"
                )
            }
        }

        spec.expectedAscentM?.let { expAsc ->
            val actualAsc = scorecard.ertsAscentM ?: scorecard.ekfAscentM
            val err = abs(actualAsc - expAsc)
            if (err > spec.ascentToleranceM) {
                violations.add(
                    "Ascent ${String.format(Locale.US, "%.1f", actualAsc)} m outside ${String.format(Locale.US, "%.1f", expAsc)} ± ${String.format(Locale.US, "%.1f", spec.ascentToleranceM)} m"
                )
            }
        }

        spec.maxCadenceMaeSpm?.let { maxMae ->
            for (cmp in scorecard.cadenceComparisons) {
                if (cmp.maeSpm > maxMae) {
                    violations.add(
                        "Cadence MAE (${cmp.sourceA} vs ${cmp.sourceB}) ${String.format(Locale.US, "%.2f", cmp.maeSpm)} spm > max ${String.format(Locale.US, "%.2f", maxMae)} spm"
                    )
                }
            }
        }

        spec.maxColdStartSec?.let { maxCold ->
            val actualCold = scorecard.coldStartTimeToMotionSec
            if (actualCold == null || actualCold > maxCold) {
                violations.add("Cold-start time-to-motion ${actualCold ?: "n/a"} s > max $maxCold s")
            }
        }

        return GroundTruthCheckResult(passed = violations.isEmpty(), violations = violations)
    }
}
