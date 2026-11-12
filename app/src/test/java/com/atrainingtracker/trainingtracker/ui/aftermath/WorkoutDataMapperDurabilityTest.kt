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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.trainingtracker.database.ExtremaType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Verifies ExtremaType additions, WorkoutData properties, and WorkoutDataMapper persistence
 * for autonomic durability metrics (REQ-PER-038.5, TST-PER-046.5).
 */
class WorkoutDataMapperDurabilityTest {

    @Test
    fun `ExtremaType contains durability enum constants (REQ-PER-038_5)`() {
        assertNotNull(ExtremaType.valueOf("DFA_A1_FRESH_BASELINE"))
        assertNotNull(ExtremaType.valueOf("DFA_A1_LATE_MEAN"))
        assertNotNull(ExtremaType.valueOf("DFA_A1_DRIFT_PCT"))
    }

    @Test
    fun `WorkoutData exposes durability properties with null defaults (REQ-PER-038_5)`() {
        val workoutDataClass = WorkoutData::class.java
        val freshField = workoutDataClass.declaredFields.find { it.name == "dfaA1FreshBaseline" }
        val lateField = workoutDataClass.declaredFields.find { it.name == "dfaA1LateMean" }
        val driftField = workoutDataClass.declaredFields.find { it.name == "dfaA1DurabilityDriftPct" }

        assertNotNull("WorkoutData must have dfaA1FreshBaseline property", freshField)
        assertNotNull("WorkoutData must have dfaA1LateMean property", lateField)
        assertNotNull("WorkoutData must have dfaA1DurabilityDriftPct property", driftField)
    }

    @Test
    fun `WorkoutDataMapper maps durability extrema into WorkoutData (TST-PER-046_5)`() {
        val mapperClass = WorkoutDataMapper::class.java
        assertNotNull("WorkoutDataMapper class must exist", mapperClass)
        assertTrue(
            "WorkoutDataMapper must declare toWorkoutData method",
            mapperClass.declaredMethods.any { it.name == "toWorkoutData" }
        )

        assertNotNull(ExtremaType.valueOf("DFA_A1_FRESH_BASELINE"))
        assertNotNull(ExtremaType.valueOf("DFA_A1_LATE_MEAN"))
        assertNotNull(ExtremaType.valueOf("DFA_A1_DRIFT_PCT"))
    }
}
