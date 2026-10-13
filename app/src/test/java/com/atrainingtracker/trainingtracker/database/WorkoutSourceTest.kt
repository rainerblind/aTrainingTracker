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

package com.atrainingtracker.trainingtracker.database

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSourceTest {

    @Test
    fun fromString_validInputs_returnsCorrectEnum() {
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString("TRACKED"))
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString("tracked"))
        assertEquals(WorkoutSource.TCX, WorkoutSource.fromString("TCX"))
        assertEquals(WorkoutSource.TCX, WorkoutSource.fromString("tcx"))
        assertEquals(WorkoutSource.GPX, WorkoutSource.fromString("GPX"))
        assertEquals(WorkoutSource.GPX, WorkoutSource.fromString("gpx"))
        assertEquals(WorkoutSource.FIT, WorkoutSource.fromString("FIT"))
        assertEquals(WorkoutSource.FIT, WorkoutSource.fromString("fit"))
    }

    @Test
    fun fromString_invalidOrNullInputs_defaultsToTracked() {
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString(null))
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString(""))
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString("   "))
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString("UNKNOWN"))
        assertEquals(WorkoutSource.TRACKED, WorkoutSource.fromString("random_string"))
    }
}
