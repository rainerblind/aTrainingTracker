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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [ZoneCardDisplayMode] enum values, serialization, and backward compatibility (REQ-UI-372).
 */
class ZoneCardDisplayModeTest {

    @Test
    fun testZoneCardDisplayMode_containsAllModes() {
        val modes = ZoneCardDisplayMode.values()
        assertTrue("Must contain STACKED_BAR", modes.contains(ZoneCardDisplayMode.STACKED_BAR))
        assertTrue("Must contain COLUMNS", modes.contains(ZoneCardDisplayMode.COLUMNS))
        assertTrue("Must contain HISTOGRAM", modes.contains(ZoneCardDisplayMode.HISTOGRAM))
        assertTrue("Must contain legacy FIVE_ZONES", modes.contains(ZoneCardDisplayMode.FIVE_ZONES))
    }

    @Test
    fun testZoneCardDisplayMode_valueOf() {
        assertEquals(ZoneCardDisplayMode.STACKED_BAR, ZoneCardDisplayMode.valueOf("STACKED_BAR"))
        assertEquals(ZoneCardDisplayMode.COLUMNS, ZoneCardDisplayMode.valueOf("COLUMNS"))
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, ZoneCardDisplayMode.valueOf("HISTOGRAM"))
        assertEquals(ZoneCardDisplayMode.FIVE_ZONES, ZoneCardDisplayMode.valueOf("FIVE_ZONES"))
    }
}
