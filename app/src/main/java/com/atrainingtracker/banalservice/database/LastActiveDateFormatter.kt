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

package com.atrainingtracker.banalservice.database

import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Thread-local cached UTC ISO-8601 formatter and backward-compatible parser for device
 * `lastActive` strings (REQ-DAT-048.3, refining REQ-STB-021.4).
 *
 * Persists canonical locale-independent UTC timestamps (`yyyy-MM-dd'T'HH:mm:ss'Z'`) with zero
 * per-call `SimpleDateFormat`/`ZoneInfo` churn, while parsing canonical UTC ISO-8601, legacy
 * DB ISO formats, and legacy locale-formatted `DateFormat.getDateTimeInstance()` strings.
 */
object LastActiveDateFormatter {
    private const val UTC_ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private val LEGACY_ISO_PATTERNS = arrayOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )
    private val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

    private data class CachedFormatter(
        val locale: Locale,
        val timeZone: TimeZone,
        val format: DateFormat
    )

    private data class CachedLegacyIsoParsers(
        val timeZone: TimeZone,
        val formats: List<SimpleDateFormat>
    )

    private val threadUtcFormatter = ThreadLocal<SimpleDateFormat>()
    private val threadLegacyIsoParsers = ThreadLocal<CachedLegacyIsoParsers>()
    private val threadFormatter = ThreadLocal<CachedFormatter>()

    @JvmStatic
    fun formatNow(): String = formatEpochMillis(System.currentTimeMillis())

    @JvmStatic
    fun formatEpochMillis(epochMillis: Long): String = currentUtcFormat().format(Date(epochMillis))

    /**
     * Parses a persisted `lastActive` string into epoch milliseconds, trying in order:
     * 1. Canonical UTC ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss'Z'`)
     * 2. Legacy DB ISO formats (`yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd`)
     * 3. Legacy locale-formatted `DateFormat.getDateTimeInstance()` strings
     *
     * Returns `null` if [lastSeen] is null, blank, or unparseable.
     */
    @JvmStatic
    fun parseToEpochMillis(lastSeen: String?): Long? {
        val trimmed = lastSeen?.trim()
        if (trimmed.isNullOrEmpty()) return null

        // 1. Canonical UTC ISO-8601 (fast path, locale-independent)
        parseFull(currentUtcFormat(), trimmed)?.let { return it }

        // 2. Legacy DB ISO formats in local timezone
        for (format in currentLegacyIsoFormats()) {
            parseFull(format, trimmed)?.let { return it }
        }

        // 3. Legacy locale-dependent DateFormat.getDateTimeInstance() rows
        return parseFull(currentFormat(), trimmed)
    }

    private fun parseFull(format: DateFormat, input: String): Long? {
        return try {
            val pos = ParsePosition(0)
            val date = format.parse(input, pos)
            if (date != null && pos.index == input.length && pos.errorIndex == -1) {
                date.time
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun currentUtcFormat(): SimpleDateFormat {
        val existing = threadUtcFormatter.get()
        if (existing != null) {
            return existing
        }
        val created = SimpleDateFormat(UTC_ISO_PATTERN, Locale.US).apply {
            timeZone = UTC_TIME_ZONE
            isLenient = false
        }
        threadUtcFormatter.set(created)
        return created
    }

    private fun currentLegacyIsoFormats(): List<SimpleDateFormat> {
        val timeZone = TimeZone.getDefault()
        val existing = threadLegacyIsoParsers.get()
        if (existing != null && existing.timeZone == timeZone) {
            return existing.formats
        }
        val created = LEGACY_ISO_PATTERNS.map { pattern ->
            SimpleDateFormat(pattern, Locale.US).apply {
                this.timeZone = timeZone
                isLenient = false
            }
        }
        threadLegacyIsoParsers.set(CachedLegacyIsoParsers(timeZone, created))
        return created
    }

    internal fun currentFormat(): DateFormat {
        val locale = Locale.getDefault()
        val timeZone = TimeZone.getDefault()
        val existing = threadFormatter.get()
        if (existing != null && existing.locale == locale && existing.timeZone == timeZone) {
            return existing.format
        }
        val created = DateFormat.getDateTimeInstance().apply {
            this.timeZone = timeZone
        }
        threadFormatter.set(CachedFormatter(locale, timeZone, created))
        return created
    }
}
