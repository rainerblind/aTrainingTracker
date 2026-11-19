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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Thread-local cached [DateFormat.getDateTimeInstance] formatter for device `last_active` strings
 * (REQ-STB-021.4).
 *
 * Eliminates repeated `DateFormat` and `ZoneInfo` allocations during frequent sensor status updates
 * while preserving exact string compatibility with downstream UI/sort consumers and invalidating
 * automatically if [Locale.getDefault] or [TimeZone.getDefault] changes.
 */
object LastActiveDateFormatter {
    private data class CachedFormatter(
        val locale: Locale,
        val timeZone: TimeZone,
        val format: DateFormat
    )

    private val threadFormatter = ThreadLocal<CachedFormatter>()

    @JvmStatic
    fun formatNow(): String = formatEpochMillis(System.currentTimeMillis())

    @JvmStatic
    fun formatEpochMillis(epochMillis: Long): String = currentFormat().format(Date(epochMillis))

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
