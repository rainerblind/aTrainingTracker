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

package com.atrainingtracker.trainingtracker.migration.parsers

import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Immutable, thread- and DST-safe ISO-8601 timestamp normalization helpers shared by
 * [GpxWorkoutParser] and [TcxWorkoutParser] (REQ-MIG-055).
 */
internal object WorkoutTimestampParser {
    private val DB_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Converts an ISO-8601 timestamp (`Z`, numeric offset `+HH:MM`/`-HH:MM`, optional `.SSS`,
     * or bare offset-less local ISO) into deterministic UTC-wall-clock `"yyyy-MM-dd HH:mm:ss"`
     * without consulting the device timezone or lenient DST rules. Returns [rawTime] unchanged on error.
     */
    fun formatIsoToUtcDbString(rawTime: String): String {
        val trimmed = rawTime.trim()
        if (trimmed.isEmpty()) return rawTime
        return try {
            OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .format(DB_TIME_FORMATTER)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .format(DB_TIME_FORMATTER)
            } catch (_: Exception) {
                rawTime
            }
        }
    }

    /**
     * Computes non-negative elapsed seconds between two `"yyyy-MM-dd HH:mm:ss"` UTC timestamps,
     * returning `null` if either string cannot be parsed.
     */
    fun computeDurationSeconds(startDbTime: String, endDbTime: String): Double? {
        return try {
            val t1 = LocalDateTime.parse(startDbTime, DB_TIME_FORMATTER).toInstant(ZoneOffset.UTC)
            val t2 = LocalDateTime.parse(endDbTime, DB_TIME_FORMATTER).toInstant(ZoneOffset.UTC)
            (Duration.between(t1, t2).toMillis() / 1000.0).coerceAtLeast(0.0)
        } catch (_: Exception) {
            null
        }
    }
}
