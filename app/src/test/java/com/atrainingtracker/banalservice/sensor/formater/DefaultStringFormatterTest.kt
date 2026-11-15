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

package com.atrainingtracker.banalservice.sensor.formater

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [DefaultStringFormatter] (REQ-ARC-007, TST-ARC-007).
 */
class DefaultStringFormatterTest {

    private val formatter = DefaultStringFormatter()

    @Test
    fun format_null_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun format_nonNull_returnsExactValue() {
        assertEquals("", formatter.format(""))
        assertEquals("Running", formatter.format("Running"))
        assertEquals("Sensor 123", formatter.format("Sensor 123"))
    }
}
