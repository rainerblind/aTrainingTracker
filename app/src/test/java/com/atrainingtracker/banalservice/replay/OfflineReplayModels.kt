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
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.SportSpeedPriors
import java.io.File
import java.util.Locale

/**
 * Input files & tuning options for [OfflineTelemetryReplayHarness] (REQ-EXP-046.2, ATT-9349).
 */
data class ReplayInputs(
    val phoneRawFile: File? = null,
    val polarRawFiles: List<File> = emptyList(),
    val config: ReplayConfig = ReplayConfig()
)

/**
 * GNSS provider stream selection for [OfflineTelemetryReplayHarness] (REQ-EXP-047.3, ATT-9604).
 * - [AUTO]: prefer raw hardware GPS (`GNSS`) when present, falling back to `GNSS_FUSED` only if
 *   the sidecar contains zero `GNSS` rows.
 * - [GPS]: consume `GNSS` rows only.
 * - [FUSED]: consume `GNSS_FUSED` rows only.
 */
enum class ReplayGnssSource {
    AUTO,
    GPS,
    FUSED;

    companion object {
        fun parseOrNull(raw: String): ReplayGnssSource? = when (raw.trim().lowercase(Locale.US)) {
            "auto" -> AUTO
            "gps" -> GPS
            "fused" -> FUSED
            else -> null
        }
    }
}

/**
 * Tunable simulation parameters enabling offline before-vs-after parameter sweeps (REQ-EXP-046.3).
 */
data class ReplayConfig(
    val motionSpeedThresholdMps: Double = 0.5,
    val altitude3StateEnabled: Boolean = true,
    val altitudeQg: Double = 1e-4,
    val altitudeQh: Double = 1e-3,
    val altitudeRBaro: Double = 0.25,
    val altitudeRGps: Double = 9.0,
    val gnssMaxAccuracyM: Double = 50.0,
    val enableErtsSmoothing: Boolean = true,
    val gnssSource: ReplayGnssSource = ReplayGnssSource.AUTO,
    val sportSpeedPriors: SportSpeedPriors = SportSpeedPriors.DEFAULT
)

/**
 * Summary statistics for a single cadence source (Phone IMU, Polar H10, or Polar Verity Sense).
 */
data class CadenceStats(
    val sourceName: String,
    val sampleCount: Int,
    val meanSpm: Double?,
    val minSpm: Double?,
    val maxSpm: Double?
)

/**
 * Pairwise cadence comparison (MAE and signed bias = sourceA - sourceB) on aligned 1-Hz buckets.
 */
data class CadencePairComparison(
    val sourceA: String,
    val sourceB: String,
    val pairedSeconds: Int,
    val maeSpm: Double,
    val biasSpm: Double
)

/**
 * 1-Hz time-series snapshot emitted by [OfflineTelemetryReplayHarness] for optional CSV plotting.
 */
data class OneHzReplayRow(
    val elapsedSec: Long,
    val carriageMode: PhoneCarriageMode,
    val inferredSport: BSportType,
    val ekfSpeedMps: Double,
    val ekfDistanceM: Double,
    val ekfAltitudeM: Double,
    val ekfSlopePct: Double,
    val ertsSpeedMps: Double?,
    val ertsAltitudeM: Double?,
    val ertsSlopePct: Double?,
    val phoneCadenceSpm: Double?,
    val polarH10CadenceSpm: Double?,
    val polarVerityCadenceSpm: Double?,
    val h10VoCm: Double?,
    val h10GctMs: Double?
) {
    fun toCsvLine(): String {
        fun fmt(v: Double?, decimals: Int = 3): String =
            if (v != null && v.isFinite()) String.format(Locale.US, "%.${decimals}f", v) else ""
        return buildString(128) {
            append(elapsedSec).append(',')
            append(carriageMode.name).append(',')
            append(inferredSport.name).append(',')
            append(fmt(ekfSpeedMps, 3)).append(',')
            append(fmt(ekfDistanceM, 2)).append(',')
            append(fmt(ekfAltitudeM, 2)).append(',')
            append(fmt(ekfSlopePct, 2)).append(',')
            append(fmt(ertsSpeedMps, 3)).append(',')
            append(fmt(ertsAltitudeM, 2)).append(',')
            append(fmt(ertsSlopePct, 2)).append(',')
            append(fmt(phoneCadenceSpm, 1)).append(',')
            append(fmt(polarH10CadenceSpm, 1)).append(',')
            append(fmt(polarVerityCadenceSpm, 1)).append(',')
            append(fmt(h10VoCm, 2)).append(',')
            append(fmt(h10GctMs, 1))
        }
    }

    companion object {
        const val CSV_HEADER =
            "elapsedSec,carriageMode,inferredSport,ekfSpeedMps,ekfDistanceM,ekfAltitudeM,ekfSlopePct," +
                "ertsSpeedMps,ertsAltitudeM,ertsSlopePct,phoneCadenceSpm,polarH10CadenceSpm," +
                "polarVerityCadenceSpm,h10VoCm,h10GctMs"
    }
}

/**
 * Deterministic quantitative scorecard produced by [OfflineTelemetryReplayHarness] (REQ-EXP-046.3).
 */
data class OfflineReplayScorecard(
    val durationSec: Double,
    val totalEventsProcessed: Int,
    val phoneEventsProcessed: Int,
    val polarEventsProcessed: Int,
    val carriageModeDistributionPct: Map<PhoneCarriageMode, Double>,
    val dominantCarriageMode: PhoneCarriageMode,
    val dominantCarriagePct: Double,
    val carriageTransitionCount: Int,
    val inferredSport: BSportType,
    val phoneCadence: CadenceStats,
    val polarH10Cadence: CadenceStats,
    val polarVerityCadence: CadenceStats,
    val cadenceComparisons: List<CadencePairComparison>,
    val h10MeanVerticalOscillationCm: Double?,
    val h10MeanGroundContactTimeMs: Double?,
    val ekfTotalDistanceM: Double,
    val ekfAscentM: Double,
    val ekfDescentM: Double,
    val ekfMinSlopePct: Double,
    val ekfMaxSlopePct: Double,
    val ertsTotalDistanceM: Double?,
    val ertsAscentM: Double?,
    val ertsDescentM: Double?,
    val ertsMinSlopePct: Double?,
    val ertsMaxSlopePct: Double?,
    val coldStartTimeToMotionSec: Double?,
    val phoneTotalStrides: Int = 0,
    val multisportEstablishedSport: BSportType = BSportType.UNKNOWN,
    val multisportTransitionCount: Int = 0
) {
    /**
     * Formats a concise human-readable scorecard table for CLI output.
     */
    fun toFormattedReport(): String {
        fun f(v: Double?, d: Int = 2): String =
            if (v != null && v.isFinite()) String.format(Locale.US, "%.${d}f", v) else "n/a"

        val sb = StringBuilder(512)
        sb.appendLine("=== Offline Telemetry Replay Scorecard ===")
        sb.appendLine("Duration: ${f(durationSec, 1)} s | Events: $totalEventsProcessed (phone=$phoneEventsProcessed, polar=$polarEventsProcessed)")
        sb.appendLine("Sport (inferred): $inferredSport | Multisport: established=$multisportEstablishedSport, transitions=$multisportTransitionCount | Cold-start latency: ${f(coldStartTimeToMotionSec, 2)} s")
        sb.appendLine("Carriage: dominant=$dominantCarriageMode (${f(dominantCarriagePct, 1)}%), transitions=$carriageTransitionCount")
        val distStr = carriageModeDistributionPct.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}=${f(it.value, 1)}%" }
        if (distStr.isNotEmpty()) sb.appendLine("  Distribution: $distStr")
        sb.appendLine("EKF  : dist=${f(ekfTotalDistanceM, 1)} m, ascent=${f(ekfAscentM, 1)} m, descent=${f(ekfDescentM, 1)} m, slope=[${f(ekfMinSlopePct, 1)}%, ${f(ekfMaxSlopePct, 1)}%]")
        if (ertsTotalDistanceM != null || ertsAscentM != null) {
            sb.appendLine("ERTS : dist=${f(ertsTotalDistanceM, 1)} m, ascent=${f(ertsAscentM, 1)} m, descent=${f(ertsDescentM, 1)} m, slope=[${f(ertsMinSlopePct, 1)}%, ${f(ertsMaxSlopePct, 1)}%]")
        }
        sb.appendLine("Cadence (mean spm): phone=${f(phoneCadence.meanSpm, 1)} (n=${phoneCadence.sampleCount}, strides=$phoneTotalStrides), H10=${f(polarH10Cadence.meanSpm, 1)} (n=${polarH10Cadence.sampleCount}), Verity=${f(polarVerityCadence.meanSpm, 1)} (n=${polarVerityCadence.sampleCount})")
        for (cmp in cadenceComparisons) {
            sb.appendLine("  Cadence diff ${cmp.sourceA} vs ${cmp.sourceB}: MAE=${f(cmp.maeSpm, 2)} spm, bias=${f(cmp.biasSpm, 2)} spm (n=${cmp.pairedSeconds}s)")
        }
        if (h10MeanVerticalOscillationCm != null || h10MeanGroundContactTimeMs != null) {
            sb.appendLine("H10 Running Dynamics: VO=${f(h10MeanVerticalOscillationCm, 2)} cm, GCT=${f(h10MeanGroundContactTimeMs, 1)} ms")
        }
        return sb.toString()
    }

    /**
     * Serializes the scorecard to pure-JVM JSON without `org.json`.
     */
    fun toJsonString(): String {
        fun numOrNull(v: Double?): String = if (v != null && v.isFinite()) String.format(Locale.US, "%.4f", v) else "null"
        val modesJson = carriageModeDistributionPct.entries
            .sortedBy { it.key.name }
            .joinToString(",") { "\"${it.key.name}\":${numOrNull(it.value)}" }
        val cmpJson = cadenceComparisons.joinToString(",") {
            "{\"sourceA\":\"${it.sourceA}\",\"sourceB\":\"${it.sourceB}\",\"pairedSeconds\":${it.pairedSeconds},\"maeSpm\":${numOrNull(it.maeSpm)},\"biasSpm\":${numOrNull(it.biasSpm)}}"
        }
        return buildString(512) {
            append('{')
            append("\"durationSec\":").append(numOrNull(durationSec))
            append(",\"totalEventsProcessed\":").append(totalEventsProcessed)
            append(",\"phoneEventsProcessed\":").append(phoneEventsProcessed)
            append(",\"polarEventsProcessed\":").append(polarEventsProcessed)
            append(",\"dominantCarriageMode\":\"").append(dominantCarriageMode.name).append('"')
            append(",\"dominantCarriagePct\":").append(numOrNull(dominantCarriagePct))
            append(",\"carriageTransitionCount\":").append(carriageTransitionCount)
            append(",\"carriageModeDistributionPct\":{").append(modesJson).append('}')
            append(",\"inferredSport\":\"").append(inferredSport.name).append('"')
            append(",\"multisportEstablishedSport\":\"").append(multisportEstablishedSport.name).append('"')
            append(",\"multisportTransitionCount\":").append(multisportTransitionCount)
            append(",\"phoneCadenceMeanSpm\":").append(numOrNull(phoneCadence.meanSpm))
            append(",\"phoneTotalStrides\":").append(phoneTotalStrides)
            append(",\"polarH10CadenceMeanSpm\":").append(numOrNull(polarH10Cadence.meanSpm))
            append(",\"polarVerityCadenceMeanSpm\":").append(numOrNull(polarVerityCadence.meanSpm))
            append(",\"cadenceComparisons\":[").append(cmpJson).append(']')
            append(",\"h10MeanVerticalOscillationCm\":").append(numOrNull(h10MeanVerticalOscillationCm))
            append(",\"h10MeanGroundContactTimeMs\":").append(numOrNull(h10MeanGroundContactTimeMs))
            append(",\"ekfTotalDistanceM\":").append(numOrNull(ekfTotalDistanceM))
            append(",\"ekfAscentM\":").append(numOrNull(ekfAscentM))
            append(",\"ekfDescentM\":").append(numOrNull(ekfDescentM))
            append(",\"ekfMinSlopePct\":").append(numOrNull(ekfMinSlopePct))
            append(",\"ekfMaxSlopePct\":").append(numOrNull(ekfMaxSlopePct))
            append(",\"ertsTotalDistanceM\":").append(numOrNull(ertsTotalDistanceM))
            append(",\"ertsAscentM\":").append(numOrNull(ertsAscentM))
            append(",\"ertsDescentM\":").append(numOrNull(ertsDescentM))
            append(",\"ertsMinSlopePct\":").append(numOrNull(ertsMinSlopePct))
            append(",\"ertsMaxSlopePct\":").append(numOrNull(ertsMaxSlopePct))
            append(",\"coldStartTimeToMotionSec\":").append(numOrNull(coldStartTimeToMotionSec))
            append('}')
        }
    }
}

/**
 * Full replay output combining [scorecard] and 1-Hz [series] rows.
 */
data class OfflineReplayResult(
    val scorecard: OfflineReplayScorecard,
    val series: List<OneHzReplayRow>
) {
    fun writeCsv(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(OneHzReplayRow.CSV_HEADER)
            writer.newLine()
            for (row in series) {
                writer.write(row.toCsvLine())
                writer.newLine()
            }
        }
    }
}

/**
 * Quantitative before-vs-after diff between two [OfflineReplayScorecard]s (REQ-EXP-046.3).
 */
data class OfflineReplayDiff(
    val deltaDominantCarriagePct: Double,
    val deltaTransitions: Int,
    val deltaEkfDistanceM: Double,
    val deltaEkfAscentM: Double,
    val deltaEkfDescentM: Double,
    val deltaErtsDistanceM: Double?,
    val deltaErtsAscentM: Double?,
    val deltaPhoneCadenceMeanSpm: Double?,
    val deltaColdStartSec: Double?
) {
    companion object {
        fun compare(baseline: OfflineReplayScorecard, candidate: OfflineReplayScorecard): OfflineReplayDiff {
            fun diffOpt(a: Double?, b: Double?): Double? = if (a != null && b != null) b - a else null
            return OfflineReplayDiff(
                deltaDominantCarriagePct = candidate.dominantCarriagePct - baseline.dominantCarriagePct,
                deltaTransitions = candidate.carriageTransitionCount - baseline.carriageTransitionCount,
                deltaEkfDistanceM = candidate.ekfTotalDistanceM - baseline.ekfTotalDistanceM,
                deltaEkfAscentM = candidate.ekfAscentM - baseline.ekfAscentM,
                deltaEkfDescentM = candidate.ekfDescentM - baseline.ekfDescentM,
                deltaErtsDistanceM = diffOpt(baseline.ertsTotalDistanceM, candidate.ertsTotalDistanceM),
                deltaErtsAscentM = diffOpt(baseline.ertsAscentM, candidate.ertsAscentM),
                deltaPhoneCadenceMeanSpm = diffOpt(baseline.phoneCadence.meanSpm, candidate.phoneCadence.meanSpm),
                deltaColdStartSec = diffOpt(baseline.coldStartTimeToMotionSec, candidate.coldStartTimeToMotionSec)
            )
        }
    }
}
