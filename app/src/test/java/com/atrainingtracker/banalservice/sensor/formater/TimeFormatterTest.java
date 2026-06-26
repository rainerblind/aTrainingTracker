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

package com.atrainingtracker.banalservice.sensor.formater;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TimeFormatterTest {

    @Test
    public void testFormat() {
        TimeFormatter formatter = new TimeFormatter();
        
        // Testing null handling
        assertEquals("--:--:--", formatter.format(null));
        
        // Testing seconds
        assertEquals("0:00:05", formatter.format(5L));
        assertEquals("0:00:59", formatter.format(59L));
        
        // Testing minutes
        assertEquals("0:01:00", formatter.format(60L));
        assertEquals("0:01:01", formatter.format(61L));
        assertEquals("0:59:59", formatter.format(3599L));
        
        // Testing hours
        assertEquals("1:00:00", formatter.format(3600L));
        assertEquals("1:00:01", formatter.format(3601L));
        assertEquals("10:11:12", formatter.format(36672L));
    }

    @Test
    public void testFormatWithUnits() {
        TimeFormatter formatter = new TimeFormatter();

        // Testing null handling
        assertEquals("--:--:--", formatter.format_with_units(null));

        // Testing seconds (< 60s)
        assertEquals("5 sec", formatter.format_with_units(5L));
        assertEquals("59 sec", formatter.format_with_units(59L));

        // Testing minutes (< 1h)
        assertEquals("1:00 min", formatter.format_with_units(60L));
        assertEquals("1:01 min", formatter.format_with_units(61L));
        assertEquals("59:59 min", formatter.format_with_units(3599L));

        // Testing hours (< 100h)
        assertEquals("1:00:00 h", formatter.format_with_units(3600L));
        assertEquals("1:00:01 h", formatter.format_with_units(3601L));
        assertEquals("10:11:12 h", formatter.format_with_units(36672L));

        // Testing large hours (>= 100h)
        assertEquals("100 h", formatter.format_with_units(360000L));
    }
}
