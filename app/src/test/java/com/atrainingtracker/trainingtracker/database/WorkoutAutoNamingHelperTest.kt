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
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Verification test suite for REQ-TRK-011 / TST-TRK-003 (ATT-1398)
 * and REQ-TRK-012 / TST-TRK-004 (ATT-2247):
 * Intelligent Workout Auto-Naming with Distance Enrichment based on Lieblingsorte.
 */
class WorkoutAutoNamingHelperTest {

    private lateinit var mockContext: Context
    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

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
                R.string.workout_name_run_from_with_distance -> "Run from ${varargs[0]} (${varargs[1]})"
                R.string.workout_name_ride_from_with_distance -> "Ride from ${varargs[0]} (${varargs[1]})"
                R.string.workout_name_activity_from_with_distance -> "Activity from ${varargs[0]} (${varargs[1]})"
                R.string.workout_name_round_trip_with_distance -> "Loop from ${varargs[0]} (${varargs[1]})"
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

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        unmockkStatic(TrainingApplication::class)
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

    // =========================================================================================
    // REQ-TRK-012 / TST-TRK-004 Distance Enrichment Tests (ATT-2247)
    // =========================================================================================

    @Test
    fun testFormatSessionDistance_metricPrecision() {
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        // < 10 km: exactly 1 decimal place
        assertEquals("8.2 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 8200.0))
        assertEquals("0.5 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 500.0))
        assertEquals("9.9 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 9900.0))

        // >= 10 km: whole number gets 0 decimals
        assertEquals("42 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 42000.0))
        assertEquals("10 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 10000.0))
        assertEquals("100 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 100000.0))

        // >= 10 km: fraction gets 1 decimal place
        assertEquals("42.5 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 42500.0))
        assertEquals("10.3 km", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 10300.0))
    }

    @Test
    fun testFormatSessionDistance_imperialPrecision() {
        every { TrainingApplication.getUnit() } returns MyUnits.IMPERIAL

        // < 10 mi (5.1 mi = 5.1 * 1609.344 = 8207.6544 m)
        assertEquals("5.1 mi", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 5.1 * 1609.344))
        assertEquals("0.5 mi", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 0.5 * 1609.344))

        // >= 10 mi: whole number (25 mi = 40233.6 m)
        assertEquals("25 mi", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 25.0 * 1609.344))

        // >= 10 mi: fraction (26.2 mi)
        assertEquals("26.2 mi", WorkoutAutoNamingHelper.formatSessionDistance(mockContext, 26.2 * 1609.344))
    }

    @Test
    fun testGenerateWorkoutName_roundTrip_withDistance_metric() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(1L, "Zuhause")

        // RUN: 42 km round trip
        val runName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 0f,
            distanceTotalMeters = 42000.0
        )
        assertEquals("Run from Zuhause (42 km)", runName)

        // BIKE: 42.5 km round trip
        val bikeName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 0f,
            distanceTotalMeters = 42500.0
        )
        assertEquals("Ride from Zuhause (42.5 km)", bikeName)

        // OTHER: 15 km round trip
        val otherName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.UNKNOWN,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 0f,
            distanceTotalMeters = 15000.0
        )
        assertEquals("Loop from Zuhause (15 km)", otherName)
    }

    @Test
    fun testGenerateWorkoutName_startOnly_withDistance_metric() {
        val startLoc = createLocation(2L, "Büro")

        // RUN: 8.2 km starting from Büro
        val runName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f,
            distanceTotalMeters = 8200.0
        )
        assertEquals("Run from Büro (8.2 km)", runName)

        // BIKE: 30 km starting from Büro
        val bikeName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f,
            distanceTotalMeters = 30000.0
        )
        assertEquals("Ride from Büro (30 km)", bikeName)

        // OTHER: 12 km starting from Büro
        val otherName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.UNKNOWN,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 5000f,
            distanceTotalMeters = 12000.0
        )
        assertEquals("Activity from Büro (12 km)", otherName)
    }

    @Test
    fun testGenerateWorkoutName_pointToPoint_doesNotAppendDistance() {
        val startLoc = createLocation(1L, "Zuhause")
        val endLoc = createLocation(2L, "Büro")

        val bikeName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = endLoc,
            distanceBetweenEndpoints = 15000f,
            distanceTotalMeters = 15000.0
        )
        // Point to point must remain concise: "Ride from Zuhause to Büro"
        assertEquals("Ride from Zuhause to Büro", bikeName)
    }

    @Test
    fun testGenerateWorkoutName_destinationOnly_doesNotAppendDistance() {
        val endLoc = createLocation(2L, "Büro")

        val runName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = null,
            endLocation = endLoc,
            distanceBetweenEndpoints = 10000f,
            distanceTotalMeters = 10000.0
        )
        // Destination only must remain: "Run to Büro"
        assertEquals("Run to Büro", runName)
    }

    @Test
    fun testGenerateWorkoutName_zeroOrNegativeDistance_omitsDistanceSuffix() {
        val startLoc = createLocation(1L, "Zuhause")

        // 0.0 distance
        val zeroDistName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 0f,
            distanceTotalMeters = 0.0
        )
        assertEquals("Ride from Zuhause", zeroDistName)

        // Negative distance
        val negDistName = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.RUN,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 0f,
            distanceTotalMeters = -50.0
        )
        assertEquals("Run from Zuhause", negDistName)
    }

    @Test
    fun testGenerateWorkoutName_backwardCompatibility_omittedDistanceParam() {
        val startLoc = createLocation(1L, "Zuhause")

        // Calling without the distanceTotalMeters parameter via default argument
        val name = WorkoutAutoNamingHelper.generateWorkoutName(
            context = mockContext,
            sportType = BSportType.BIKE,
            startLocation = startLoc,
            endLocation = null,
            distanceBetweenEndpoints = 0f
        )
        assertEquals("Ride from Zuhause", name)
    }
}
