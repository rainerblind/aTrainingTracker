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
 */

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for REQ-TRK-011 and TST-TRK-003 (ATT-1398):
 * Intelligent Workout Auto-Naming based on Recognized Start and Destination Lieblingsorte.
 */
class WorkoutAutoNamingHelperTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)

        every { mockContext.getString(any<Int>(), *anyVararg()) } answers {
            val resId = invocation.args[0] as Int
            val rawArgs = invocation.args[1]
            val varargs: Array<*> = when (rawArgs) {
                is Array<*> -> rawArgs
                else -> arrayOf(rawArgs)
            }
            when (resId) {
                R.string.workout_name_run_from -> "Run from ${varargs[0]}"
                R.string.workout_name_ride_from -> "Ride from ${varargs[0]}"
                R.string.workout_name_activity_from -> "Activity from ${varargs[0]}"
                R.string.workout_name_round_trip -> "Loop from ${varargs[0]}"
                R.string.workout_name_run_from_to -> "Run from ${varargs[0]} to ${varargs[1]}"
                R.string.workout_name_ride_from_to -> "Ride from ${varargs[0]} to ${varargs[1]}"
                R.string.workout_name_activity_from_to -> "Activity from ${varargs[0]} to ${varargs[1]}"
                R.string.workout_name_run_to -> "Run to ${varargs[0]}"
                R.string.workout_name_ride_to -> "Ride to ${varargs[0]}"
                R.string.workout_name_activity_to -> "Activity to ${varargs[0]}"
                R.string.cluster_seed_name_loop_format -> "Loop from ${varargs[0]}"
                R.string.known_location_unnamed_format -> "Start Location (${varargs[0]}, ${varargs[1]})"
                else -> "MockedString"
            }
        }
    }

    private fun createLocation(id: Long, name: String, lat: Double = 48.137, lng: Double = 11.576, radius: Int = 200): MyLocation {
        return MyLocation(id, lat, lng, name, 520.0, radius, 10, true, ElevationSource.MANUAL_USER)
    }

    @Test
    fun testGenerateWorkoutName_roundTrip_run() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(1L, "Zuhause")

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 50f
        )

        assertEquals("Run from Zuhause", name)
    }

    @Test
    fun testGenerateWorkoutName_roundTrip_bike() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(1L, "Zuhause")

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 100f
        )

        assertEquals("Ride from Zuhause", name)
    }

    @Test
    fun testGenerateWorkoutName_roundTrip_other() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(1L, "Zuhause")

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.UNKNOWN,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 100f
        )

        assertEquals("Loop from Zuhause", name)
    }

    @Test
    fun testGenerateWorkoutName_roundTrip_distanceWithinRadius() {
        // End location not recognized as a separate DB entity, but within start location radius
        val startLoc = createLocation(1L, "Zuhause", radius = 250)

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 180f
        )

        assertEquals("Run from Zuhause", name)
    }

    @Test
    fun testGenerateWorkoutName_pointToPoint_run() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(2L, "Büro")

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 8500f
        )

        assertEquals("Run from Zuhause to Büro", name)
    }

    @Test
    fun testGenerateWorkoutName_pointToPoint_bike() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(2L, "Büro")

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 8500f
        )

        assertEquals("Ride from Zuhause to Büro", name)
    }

    @Test
    fun testGenerateWorkoutName_pointToPoint_other() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(2L, "Büro")

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.CONFLICT,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 8500f
        )

        assertEquals("Activity from Zuhause to Büro", name)
    }

    @Test
    fun testGenerateWorkoutName_startOnly() {
        val startLoc = createLocation(1L, "Zuhause", radius = 200)

        val nameRun = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f
        )
        assertEquals("Run from Zuhause", nameRun)

        val nameBike = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f
        )
        assertEquals("Ride from Zuhause", nameBike)

        val nameOther = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.UNKNOWN,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f
        )
        assertEquals("Activity from Zuhause", nameOther)
    }

    @Test
    fun testGenerateWorkoutName_destinationOnly() {
        val endLoc = createLocation(2L, "Büro")

        val nameRun = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = null,
            endLocation = endLoc,
            distanceBetweenEndpoints = 5000f
        )
        assertEquals("Run to Büro", nameRun)

        val nameBike = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = null,
            endLocation = endLoc,
            distanceBetweenEndpoints = 5000f
        )
        assertEquals("Ride to Büro", nameBike)

        val nameOther = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.UNKNOWN,
            startLocation = null,
            endLocation = endLoc,
            distanceBetweenEndpoints = 5000f
        )
        assertEquals("Activity to Büro", nameOther)
    }

    @Test
    fun testGenerateWorkoutName_neitherKnown_returnsNull() {
        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = null,
            endLocation = null,
            distanceBetweenEndpoints = 5000f
        )

        assertNull(name)
    }

    @Test
    fun testClusterSeedName_seedFromLocation() {
        val startLoc = createLocation(3L, "Olympiapark")

        val seedName = WorkoutAutoNamingHelper.generateClusterSeedName(mockContext, startLoc)

        assertEquals("Loop from Olympiapark", seedName)
    }

    @Test
    fun testClusterSeedName_nullLocation_returnsNull() {
        val seedName = WorkoutAutoNamingHelper.generateClusterSeedName(mockContext, null)

        assertNull(seedName)
    }

    @Test
    fun testLocationWithoutName_usesCoordinateFallback() {
        val unnamedLoc = createLocation(4L, "", lat = 48.137, lng = 11.576)

        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = unnamedLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f
        )

        assertNotNull(name)
        assertTrue(name!!.contains("Start Location"))
    }
}
