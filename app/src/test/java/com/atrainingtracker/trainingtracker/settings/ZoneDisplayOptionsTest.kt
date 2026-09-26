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

package com.atrainingtracker.trainingtracker.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for [ZoneDisplayOptions] domain model (TST-UI-124).
 */
class ZoneDisplayOptionsTest {

    @Test
    fun defaultValues_matchEstablishedBehavior() {
        val options = ZoneDisplayOptions()
        assertTrue("showBackground must default to true", options.showBackground)
        assertTrue("showLeftBar must default to true", options.showLeftBar)
        assertFalse("showRightBar must default to false", options.showRightBar)
        assertFalse("showTextColor must default to false", options.showTextColor)
    }

    @Test
    fun customValues_areProperlyAssigned() {
        val options = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = false,
            showRightBar = true,
            showTextColor = true
        )
        assertFalse(options.showBackground)
        assertFalse(options.showLeftBar)
        assertTrue(options.showRightBar)
        assertTrue(options.showTextColor)
    }

    @Test
    fun copy_updatesTargetPropertiesOnly() {
        val original = ZoneDisplayOptions()
        val modified = original.copy(showRightBar = true, showTextColor = true)

        assertTrue(modified.showBackground)
        assertTrue(modified.showLeftBar)
        assertTrue(modified.showRightBar)
        assertTrue(modified.showTextColor)

        // Original remains immutable and unchanged
        assertTrue(original.showBackground)
        assertTrue(original.showLeftBar)
        assertFalse(original.showRightBar)
        assertFalse(original.showTextColor)
    }

    @Test
    fun equalityAndHashCode_respectAllFields() {
        val opt1 = ZoneDisplayOptions(showBackground = true, showLeftBar = true, showRightBar = false, showTextColor = false)
        val opt2 = ZoneDisplayOptions(showBackground = true, showLeftBar = true, showRightBar = false, showTextColor = false)
        val opt3 = ZoneDisplayOptions(showBackground = false, showLeftBar = true, showRightBar = false, showTextColor = false)

        assertEquals(opt1, opt2)
        assertEquals(opt1.hashCode(), opt2.hashCode())
        assertNotEquals(opt1, opt3)
        assertNotEquals(opt1.hashCode(), opt3.hashCode())
    }
}
