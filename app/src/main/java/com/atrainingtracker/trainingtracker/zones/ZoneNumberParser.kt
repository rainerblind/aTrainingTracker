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

package com.atrainingtracker.trainingtracker.zones

/**
 * Robust locale-aware integer number parser for training zone inputs.
 *
 * Conforms to REQ-UI-373.9:
 * - Handles standard ASCII digits.
 * - Handles localized European decimal separators (comma vs period).
 * - Strips whitespace, non-breaking spaces (\u00A0), and grouping separators.
 * - Returns null on non-numeric or empty input without throwing exceptions.
 */
object ZoneNumberParser {

    /**
     * Parses an integer from raw text input safely.
     *
     * @param raw Input string from user text field.
     * @return Parsed integer, or null if input is empty, blank, or invalid.
     */
    fun parseIntOrNull(raw: String): Int? {
        if (raw.isBlank()) return null

        // Remove standard spaces, non-breaking spaces (\u00A0), and narrow NBSP (\u202F)
        val cleaned = raw.replace(" ", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .trim()

        if (cleaned.isEmpty()) return null

        // If user typed a decimal number (e.g. "250,5" or "250.5"), isolate the integer part
        val integerPart = when {
            cleaned.contains(",") -> cleaned.substringBefore(",")
            cleaned.contains(".") -> cleaned.substringBefore(".")
            else -> cleaned
        }

        return integerPart.toIntOrNull()
    }
}
