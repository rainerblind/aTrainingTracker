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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Pure-JVM unit tests for [PolarOfflineHrTimestampAligner] verifying UTC anchor conversion across
 * non-UTC timezones (`Europe/Berlin` CET/CEST & DST fall-back, `America/New_York` EST), range-guarded
 * `PbLocalDateTime` offset parsing, local-first + UTC-fallback entry matching, and structured
 * diagnostics (`ATT-9611`, `REQ-SET-130.1`, `REQ-SET-130.3`, `TST-SET-132.1`).
 */
class PolarOfflineHrTimestampAlignerTest {

    // REQ-SET-130.1 / TST-SET-132.1
    @Test
    fun testResolveHrOfflineAnchorMs_interpretsStartTimeAsUtcRegardlessOfSystemTimezone_TST_SET_132_1() {
        val sdkUtcStart = LocalDateTime.of(2026, 11, 22, 9, 43, 8)
        val expectedUtcMs = Instant.parse("2026-11-22T09:43:08Z").toEpochMilli()
        val summerSdkUtcStart = LocalDateTime.of(2026, 7, 15, 8, 15, 0)
        val expectedSummerUtcMs = Instant.parse("2026-07-15T08:15:00Z").toEpochMilli()
        val fallbackMs = 1_700_000_000_000L

        val originalTz = TimeZone.getDefault()
        try {
            for (tzId in listOf("UTC", "Europe/Berlin", "America/New_York", "Asia/Tokyo")) {
                TimeZone.setDefault(TimeZone.getTimeZone(tzId))
                assertEquals(
                    "CET winter startTime must resolve as exact UTC under $tzId",
                    expectedUtcMs,
                    PolarOfflineHrTimestampAligner.resolveHrOfflineAnchorMs(sdkUtcStart, fallbackMs)
                )
                assertEquals(
                    "CEST summer startTime must resolve as exact UTC under $tzId",
                    expectedSummerUtcMs,
                    PolarOfflineHrTimestampAligner.resolveHrOfflineAnchorMs(summerSdkUtcStart, fallbackMs)
                )
            }
        } finally {
            TimeZone.setDefault(originalTz)
        }

        // Null and pre-2020 uninitialized clocks fall back safely
        assertEquals(fallbackMs, PolarOfflineHrTimestampAligner.resolveHrOfflineAnchorMs(null, fallbackMs))
        assertEquals(
            fallbackMs,
            PolarOfflineHrTimestampAligner.resolveHrOfflineAnchorMs(LocalDateTime.of(2000, 1, 1, 0, 0, 0), fallbackMs)
        )
    }

    // REQ-SET-130.1 / TST-SET-132.1
    @Test
    fun testParsePbLocalDateTimeMs_honorsValidOffsetAndGuardsOutOfRangeWithoutException_TST_SET_132_1() {
        val berlinZone = ZoneId.of("Europe/Berlin")
        val pbDate = Types.PbDate.newBuilder().setYear(2026).setMonth(11).setDay(22).buildPartial()
        val pbTime = Types.PbTime.newBuilder().setHour(10).setMinute(43).setSeconds(8).setMillis(0).buildPartial()

        // Valid +60 min (CET) offset -> 09:43:08Z
        val withCetOffset = Types.PbLocalDateTime.newBuilder()
            .setDate(pbDate)
            .setTime(pbTime)
            .setTimeZoneOffset(60)
            .buildPartial()
        assertEquals(
            Instant.parse("2026-11-22T09:43:08Z").toEpochMilli(),
            PolarOfflineHrTimestampAligner.parsePbLocalDateTimeMs(withCetOffset, ZoneOffset.UTC)
        )

        // Valid -300 min (EST) offset -> 15:43:08Z
        val withEstOffset = Types.PbLocalDateTime.newBuilder()
            .setDate(pbDate)
            .setTime(pbTime)
            .setTimeZoneOffset(-300)
            .buildPartial()
        assertEquals(
            Instant.parse("2026-11-22T15:43:08Z").toEpochMilli(),
            PolarOfflineHrTimestampAligner.parsePbLocalDateTimeMs(withEstOffset, berlinZone)
        )

        // Out-of-range malformed offsets (> +1080 or < -1080) must NOT throw DateTimeException
        val expectedFallbackMs = LocalDateTime.of(2026, 11, 22, 10, 43, 8)
            .atZone(berlinZone).toInstant().toEpochMilli()
        val malformedHigh = Types.PbLocalDateTime.newBuilder()
            .setDate(pbDate)
            .setTime(pbTime)
            .setTimeZoneOffset(1081)
            .buildPartial()
        val malformedLow = Types.PbLocalDateTime.newBuilder()
            .setDate(pbDate)
            .setTime(pbTime)
            .setTimeZoneOffset(-2000)
            .buildPartial()
        assertEquals(expectedFallbackMs, PolarOfflineHrTimestampAligner.parsePbLocalDateTimeMs(malformedHigh, berlinZone))
        assertEquals(expectedFallbackMs, PolarOfflineHrTimestampAligner.parsePbLocalDateTimeMs(malformedLow, berlinZone))

        // Absent offset falls back to fallbackZone; null returns null
        val noOffset = Types.PbLocalDateTime.newBuilder().setDate(pbDate).setTime(pbTime).buildPartial()
        assertEquals(expectedFallbackMs, PolarOfflineHrTimestampAligner.parsePbLocalDateTimeMs(noOffset, berlinZone))
        assertNull(PolarOfflineHrTimestampAligner.parsePbLocalDateTimeMs(null, berlinZone))
    }

    // REQ-SET-130.1 / TST-SET-132.1
    @Test
    fun testSelectMatchingHrEntry_handlesDstFallBackTwoOffsetsAndUtcFallbackWithoutCollision_TST_SET_132_1() {
        val berlinZone = ZoneId.of("Europe/Berlin")
        // 2026-10-25 02:30:00 in Europe/Berlin occurs TWICE: 00:30Z (CEST +2) and 01:30Z (CET +1)
        val overlapLocal = LocalDateTime.of(2026, 10, 25, 2, 30, 0)
        val overlapEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261025/R/023000/HR.REC",
            size = 512L,
            date = overlapLocal,
            type = PolarBleApi.PolarDeviceDataType.HR
        )

        val cestWindowStart = Instant.parse("2026-10-25T00:29:55Z").toEpochMilli()
        val cetWindowStart = Instant.parse("2026-10-25T01:29:55Z").toEpochMilli()
        assertEquals(
            overlapEntry,
            PolarOfflineHrTimestampAligner.selectMatchingHrEntry(
                entries = listOf(overlapEntry),
                windowStartMs = cestWindowStart,
                windowEndMs = cestWindowStart + 120_000L,
                toleranceMs = 180_000L,
                zoneId = berlinZone
            )
        )
        assertEquals(
            overlapEntry,
            PolarOfflineHrTimestampAligner.selectMatchingHrEntry(
                entries = listOf(overlapEntry),
                windowStartMs = cetWindowStart,
                windowEndMs = cetWindowStart + 120_000L,
                toleranceMs = 180_000L,
                zoneId = berlinZone
            )
        )

        // Multi-session separation: local match wins over a +1h entry that would coincide in UTC
        val workoutWindowStart = Instant.parse("2026-11-22T09:43:06Z").toEpochMilli() // 10:43:06 CET
        val localSessionEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261122/R/104308/HR.REC",
            size = 1024L,
            date = LocalDateTime.of(2026, 11, 22, 10, 43, 8),
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val earlierSessionEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261122/R/094308/HR.REC",
            size = 1024L,
            date = LocalDateTime.of(2026, 11, 22, 9, 43, 8),
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        assertEquals(
            localSessionEntry,
            PolarOfflineHrTimestampAligner.selectMatchingHrEntry(
                entries = listOf(earlierSessionEntry, localSessionEntry),
                windowStartMs = workoutWindowStart,
                windowEndMs = workoutWindowStart + 140_000L,
                toleranceMs = 180_000L,
                zoneId = berlinZone
            )
        )

        // UTC fallback applies ONLY when zero local matches exist in a non-UTC timezone
        val utcOnlyMatch = PolarOfflineHrTimestampAligner.selectMatchingHrEntry(
            entries = listOf(earlierSessionEntry),
            windowStartMs = workoutWindowStart,
            windowEndMs = workoutWindowStart + 140_000L,
            toleranceMs = 180_000L,
            zoneId = berlinZone
        )
        assertNotNull(utcOnlyMatch)
        assertEquals(earlierSessionEntry, utcOnlyMatch)
    }

    // REQ-SET-130.1 / REQ-SET-130.3 / TST-SET-132.1
    @Test
    fun testAlignHrSamplesAndFormatBackfillDiagnostics_filtersBoundsAndReportsMinMaxSpan_TST_SET_132_1() {
        val anchorMs = Instant.parse("2026-11-22T09:43:08Z").toEpochMilli()
        val samples = listOf(
            PolarHrData.PolarHrSample(87, 87, 100, emptyList(), emptyList(), false, true, true),
            PolarHrData.PolarHrSample(0, 94, 100, emptyList(), emptyList(), false, true, true),
            PolarHrData.PolarHrSample(10, 10, 0, emptyList(), emptyList(), false, false, true),
            PolarHrData.PolarHrSample(106, 106, 100, emptyList(), emptyList(), false, true, true)
        )
        val aligned = PolarOfflineHrTimestampAligner.alignHrSamples(samples, anchorMs)
        assertEquals(
            listOf(
                anchorMs to 87,
                (anchorMs + 1_000L) to 94,
                (anchorMs + 3_000L) to 106
            ),
            aligned
        )

        val diag = PolarOfflineHrTimestampAligner.formatBackfillDiagnostics(
            controller = "Verity",
            workoutId = 2430L,
            entryPath = "/U/0/20261122/R/104308/HR.REC",
            rawStartTime = LocalDateTime.of(2026, 11, 22, 9, 43, 8),
            anchorMs = anchorMs,
            windowStartMs = anchorMs - 2_000L,
            windowEndMs = anchorMs + 141_000L,
            hrValues = aligned.map { it.second },
            firstSampleMs = aligned.first().first,
            lastSampleMs = aligned.last().first,
            patchedCount = 73,
            committed = true,
            flashRemoved = true
        )
        assertTrue(diag.contains("workoutId=2430"))
        assertTrue(diag.contains("startTime=2026-11-22T09:43:08"))
        assertTrue(diag.contains("anchorMs=$anchorMs"))
        assertTrue(diag.contains("samples=3"))
        assertTrue(diag.contains("hrRange=[87..106]"))
        assertTrue(diag.contains("patchedCount=73"))
        assertTrue(diag.contains("committed=true"))
        assertTrue(diag.contains("flashRemoved=true"))
    }
}
