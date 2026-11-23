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

import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import fi.polar.remote.representation.protobuf.Types
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Pure-JVM timestamp alignment, entry selection, and structured diagnostics helper for Polar
 * offline HR backfill (`ATT-9611`, `REQ-SET-130.1`, `REQ-SET-130.3`).
 *
 * Polar BLE SDK `8.6.0` (`OfflineRecordingData.parseStartTime`) parses PMD offline file headers as
 * UTC (`+"Z"`, `ZoneId.of("UTC")`) and exposes them as a timezone-less [LocalDateTime]. Converting
 * that value with [ZoneId.systemDefault] shifts the entire HR series by `-tzOffset` in non-UTC
 * timezones (e.g., `-1 h` in CET / `-2 h` in CEST). This helper strictly anchors PMD offline
 * recordings in [ZoneOffset.UTC] while handling local-time PFTP directory entries (including DST
 * fall-back overlap hours) and range-guarded [Types.PbLocalDateTime] offsets safely.
 */
object PolarOfflineHrTimestampAligner {

    const val MAX_HR_MATCH_TOLERANCE_MS: Long = 5_000L
    const val MIN_VALID_EPOCH_YEAR: Int = 2020
    const val MIN_VALID_HR_BPM: Int = 25
    const val MAX_VALID_HR_BPM: Int = 250
    const val MILLIS_PER_SECOND: Long = 1_000L
    const val MAX_TZ_OFFSET_MINUTES: Int = 1080

    /**
     * Converts Polar SDK `HrOfflineRecording.startTime` (UTC wall-clock [LocalDateTime]) into UTC
     * epoch milliseconds matching SQLite `WorkoutSamples.time` (`REQ-SET-130.1`).
     *
     * Falls back to [fallbackAnchorMs] strictly when [startTime] is null, earlier than
     * [MIN_VALID_EPOCH_YEAR], or yields a non-positive epoch timestamp.
     */
    fun resolveHrOfflineAnchorMs(
        startTime: LocalDateTime?,
        fallbackAnchorMs: Long
    ): Long {
        if (startTime == null || startTime.year < MIN_VALID_EPOCH_YEAR) {
            return fallbackAnchorMs
        }
        val utcMs = startTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        return if (utcMs > 0L) utcMs else fallbackAnchorMs
    }

    /**
     * Parses a Polar protobuf [Types.PbLocalDateTime] into UTC epoch milliseconds (`REQ-SET-130.1`).
     *
     * Honors `timeZoneOffset` (minutes from UTC) strictly when `hasTimeZoneOffset()` is true and
     * the value lies within `-1080..1080` (`±18 h`, `ZoneOffset` bounds); otherwise falls back
     * safely to [fallbackZone] without throwing `DateTimeException`.
     */
    fun parsePbLocalDateTimeMs(
        pbDateTime: Types.PbLocalDateTime?,
        fallbackZone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (pbDateTime == null || !pbDateTime.hasDate() || !pbDateTime.hasTime()) {
            return null
        }
        val date = pbDateTime.date
        val time = pbDateTime.time
        val ldt = LocalDateTime.of(
            date.year,
            date.month,
            date.day,
            time.hour,
            time.minute,
            time.seconds,
            time.millis * 1_000_000
        )
        return if (pbDateTime.hasTimeZoneOffset() &&
            pbDateTime.timeZoneOffset in -MAX_TZ_OFFSET_MINUTES..MAX_TZ_OFFSET_MINUTES
        ) {
            val offset = ZoneOffset.ofTotalSeconds(pbDateTime.timeZoneOffset * 60)
            ldt.toInstant(offset).toEpochMilli()
        } else {
            ldt.atZone(fallbackZone).toInstant().toEpochMilli()
        }
    }

    /**
     * Selects the matching HR [PolarOfflineRecordingEntry] for `[windowStartMs, windowEndMs]`
     * (`REQ-SET-130.1`).
     *
     * Evaluates `preferredPath` first, then all valid local offsets from
     * `zoneId.rules.getValidOffsets(entry.date)` (handling DST fall-back 2-offset overlap hours),
     * falling back to UTC path interpretation ONLY when zero local matches exist in a non-UTC zone.
     */
    fun selectMatchingHrEntry(
        entries: List<PolarOfflineRecordingEntry>,
        windowStartMs: Long,
        windowEndMs: Long,
        preferredPath: String? = null,
        toleranceMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PolarOfflineRecordingEntry? {
        val hrEntries = entries.filter { it.type == PolarBleApi.PolarDeviceDataType.HR }
        if (hrEntries.isEmpty()) return null

        if (!preferredPath.isNullOrBlank()) {
            val pathMatch = hrEntries.firstOrNull {
                it.path == preferredPath || it.path.contains(preferredPath)
            }
            if (pathMatch != null) return pathMatch
        }

        if (windowStartMs <= 0L) {
            return hrEntries.minByOrNull { entry ->
                entry.date.atZone(zoneId).toInstant().toEpochMilli()
            }
        }

        val effectiveEndMs = if (windowEndMs >= windowStartMs) windowEndMs else windowStartMs
        val minWindowMs = windowStartMs - toleranceMs
        val maxWindowMs = effectiveEndMs + toleranceMs

        val localMatch = hrEntries.mapNotNull { entry ->
            val bestLocalDelta = localEpochCandidates(entry.date, zoneId)
                .filter { it in minWindowMs..maxWindowMs }
                .minOfOrNull { abs(it - windowStartMs) }
            bestLocalDelta?.let { entry to it }
        }.minByOrNull { it.second }?.first

        if (localMatch != null || zoneId == ZoneOffset.UTC) {
            return localMatch
        }

        return hrEntries.mapNotNull { entry ->
            val utcMs = entry.date.toInstant(ZoneOffset.UTC).toEpochMilli()
            if (utcMs in minWindowMs..maxWindowMs) entry to abs(utcMs - windowStartMs) else null
        }.minByOrNull { it.second }?.first
    }

    /**
     * Aligns 1-Hz optical [PolarHrData.PolarHrSample] values to UTC epoch timestamps starting at
     * [anchorMs], retaining physiologically valid BPM values (`25..250`) (`REQ-SET-130.1`).
     */
    fun alignHrSamples(
        samples: List<PolarHrData.PolarHrSample>,
        anchorMs: Long
    ): List<Pair<Long, Int>> {
        if (samples.isEmpty()) return emptyList()
        val pairs = ArrayList<Pair<Long, Int>>(samples.size)
        for ((index, sample) in samples.withIndex()) {
            val rawBpm = if (sample.hr > 0) sample.hr else sample.correctedHr
            if (rawBpm in MIN_VALID_HR_BPM..MAX_VALID_HR_BPM) {
                pairs.add((anchorMs + index * MILLIS_PER_SECOND) to rawBpm)
            }
        }
        return pairs
    }

    /**
     * Aligns 1-Hz integer BPM samples to UTC epoch timestamps starting at [anchorMs], retaining
     * physiologically valid BPM values (`25..250`).
     */
    fun alignIntHrSamples(hrSamples: List<Int>, anchorMs: Long): List<Pair<Long, Int>> {
        if (hrSamples.isEmpty()) return emptyList()
        val pairs = ArrayList<Pair<Long, Int>>(hrSamples.size)
        for ((index, rawBpm) in hrSamples.withIndex()) {
            if (rawBpm in MIN_VALID_HR_BPM..MAX_VALID_HR_BPM) {
                pairs.add((anchorMs + index * MILLIS_PER_SECOND) to rawBpm)
            }
        }
        return pairs
    }

    /**
     * Extracts pool turn timestamps from monotonic cumulative distance samples (`REQ-SET-110.2`).
     */
    fun extractTurnTimestamps(
        distanceSamples: List<Float>,
        startMs: Long,
        sampleIntervalMs: Long = MILLIS_PER_SECOND
    ): List<Long> {
        if (distanceSamples.size < 2) return emptyList()
        val turns = mutableListOf<Long>()
        var prevDist = distanceSamples[0]
        for (i in 1 until distanceSamples.size) {
            val dist = distanceSamples[i]
            if (dist > prevDist) {
                turns.add(startMs + i * sampleIntervalMs)
                prevDist = dist
            }
        }
        return turns
    }

    /**
     * Formats structured single-line backfill diagnostics shared across Verity Sense and H10
     * controllers (`REQ-SET-130.3`).
     */
    @Suppress("LongParameterList")
    fun formatBackfillDiagnostics(
        controller: String,
        workoutId: Long,
        entryPath: String?,
        rawStartTime: LocalDateTime?,
        anchorMs: Long,
        windowStartMs: Long,
        windowEndMs: Long,
        hrValues: List<Int>,
        firstSampleMs: Long?,
        lastSampleMs: Long?,
        patchedCount: Int,
        committed: Boolean,
        flashRemoved: Boolean,
        beatsCount: Int? = null
    ): String {
        val hrRange = if (hrValues.isEmpty()) {
            "empty"
        } else {
            var minBpm = Int.MAX_VALUE
            var maxBpm = Int.MIN_VALUE
            for (bpm in hrValues) {
                if (bpm < minBpm) minBpm = bpm
                if (bpm > maxBpm) maxBpm = bpm
            }
            "$minBpm..$maxBpm"
        }
        val sampleSpan = if (firstSampleMs != null && lastSampleMs != null) {
            "$firstSampleMs..$lastSampleMs"
        } else {
            "none"
        }
        val beatsPart = beatsCount?.let { " beats=$it" }.orEmpty()
        val startPart = rawStartTime?.let { " startTime=$it" }.orEmpty()
        return "$controller backfill: workoutId=$workoutId entry=${entryPath ?: "none"}" +
            "$startPart anchorMs=$anchorMs window=$windowStartMs..$windowEndMs" +
            " samples=${hrValues.size}$beatsPart hrRange=[$hrRange] sampleSpan=[$sampleSpan]" +
            " patchedCount=$patchedCount committed=$committed flashRemoved=$flashRemoved"
    }

    private fun localEpochCandidates(dateTime: LocalDateTime, zoneId: ZoneId): List<Long> {
        val validOffsets = zoneId.rules.getValidOffsets(dateTime)
        return if (validOffsets.isNotEmpty()) {
            validOffsets.map { offset -> dateTime.toInstant(offset).toEpochMilli() }
        } else {
            listOf(dateTime.atZone(zoneId).toInstant().toEpochMilli())
        }
    }
}
