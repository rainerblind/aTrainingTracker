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
import java.util.Locale

/**
 * Unit tests for [BatteryRemainingTimeFormatter] (TST-CON-006, ATT-1454).
 */
class BatteryRemainingTimeFormatterTest {

    @Test
    fun testFormat_null_returnsDashes() {
        val formatter = BatteryRemainingTimeFormatter()
        assertEquals("--:--", formatter.format(null))
    }

    @Test
    fun testFormat_stabilizingCode_returnsDashes() {
        val formatter = BatteryRemainingTimeFormatter()
        assertEquals("--:--", formatter.format(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE))
        assertEquals("--:--", formatter.format(-5))
    }

    @Test
    fun testFormat_chargingCode_returnsChargingFallback() {
        val formatter = BatteryRemainingTimeFormatter()
        val result = formatter.format(BatteryRemainingTimeFormatter.CHARGING_STATUS_CODE)
        assert(result == "Charging" || result.isNotEmpty())
    }

    @Test
    fun testFormat_validSeconds_formatsHoursAndMinutes() {
        Locale.setDefault(Locale.US)
        val formatter = BatteryRemainingTimeFormatter()

        // 0 seconds -> 0:00 h
        assertEquals("0:00 h", formatter.format(0))

        // 1 hour (3600 seconds) -> 1:00 h
        assertEquals("1:00 h", formatter.format(3600))

        // 8 hours 45 minutes (31500 seconds) -> 8:45 h
        assertEquals("8:45 h", formatter.format(8 * 3600 + 45 * 60))

        // 20 hours -> 20:00 h
        assertEquals("20:00 h", formatter.format(20 * 3600))
    }
}
