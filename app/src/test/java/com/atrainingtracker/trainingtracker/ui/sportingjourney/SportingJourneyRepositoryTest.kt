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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit verification suite for [SportingJourneyRepository] across All-Time and Annual Wrapped horizons (REQ-SPT-002, TST-SPT-002).
 */
@RunWith(RobolectricTestRunner::class)
class SportingJourneyRepositoryTest {

    private lateinit var context: Context
    private lateinit var dbManager: WorkoutSummariesDatabaseManager
    private lateinit var repository: SportingJourneyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(WorkoutSummariesDbHelper.DB_NAME)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        SportingJourneyRepository.resetForTesting(null)

        dbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        repository = SportingJourneyRepository(context, dbManager)
    }

    @After
    fun tearDown() {
        try {
            if (dbManager.database.isOpen) {
                dbManager.database.close()
            }
        } catch (_: Throwable) {}
        context.deleteDatabase(WorkoutSummariesDbHelper.DB_NAME)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        SportingJourneyRepository.resetForTesting(null)
    }

    private fun insertTestWorkout(
        name: String,
        sport: String,
        timeStart: String,
        activeTimeS: Long,
        distanceM: Double,
        elevationM: Int,
        clusterId: Int,
        avgCadence: Double? = null,
        avgHr: Double? = null,
        avgPower: Double? = null
    ): Long {
        val db = dbManager.database
        val values = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, name)
            put(WorkoutSummaries.B_SPORT, sport)
            put(WorkoutSummaries.TIME_START, timeStart)
            put(WorkoutSummaries.TIME_ACTIVE_s, activeTimeS)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, distanceM)
            put(WorkoutSummaries.ASCENDING, elevationM)
            put(WorkoutSummaries.CLUSTER_ID, clusterId)
            put(WorkoutSummaries.FINISHED, 1)
        }
        val workoutId = db.insert(WorkoutSummaries.TABLE, null, values)
        assertTrue("Workout insert failed", workoutId > 0)

        if (avgCadence != null) {
            val extValues = ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_ID, workoutId)
                put(WorkoutSummaries.SENSOR_TYPE, SensorType.CADENCE.name)
                put(WorkoutSummaries.EXTREMA_TYPE, ExtremaType.AVG.name)
                put(WorkoutSummaries.VALUE, avgCadence)
            }
            db.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, extValues)
        }

        if (avgHr != null) {
            val extValues = ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_ID, workoutId)
                put(WorkoutSummaries.SENSOR_TYPE, SensorType.HR.name)
                put(WorkoutSummaries.EXTREMA_TYPE, ExtremaType.AVG.name)
                put(WorkoutSummaries.VALUE, avgHr)
            }
            db.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, extValues)
        }

        if (avgPower != null) {
            val extValues = ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_ID, workoutId)
                put(WorkoutSummaries.SENSOR_TYPE, SensorType.POWER.name)
                put(WorkoutSummaries.EXTREMA_TYPE, ExtremaType.AVG.name)
                put(WorkoutSummaries.VALUE, avgPower)
            }
            db.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, extValues)
        }

        return workoutId
    }

    @Test
    fun testAllTimeHorizon_aggregatesBothWorkouts() = runTest {
        insertTestWorkout(
            name = "Morning Ride 2026",
            sport = "BIKE",
            timeStart = "2026-06-15 08:30:00",
            activeTimeS = 3600,
            distanceM = 30000.0,
            elevationM = 500,
            clusterId = 10,
            avgCadence = 85.0,
            avgHr = 145.0,
            avgPower = 200.0
        )

        insertTestWorkout(
            name = "Evening Run 2025",
            sport = "RUN",
            timeStart = "2025-05-10 18:15:00",
            activeTimeS = 1800,
            distanceM = 5000.0,
            elevationM = 50,
            clusterId = 0,
            avgHr = 160.0
        )

        val data = repository.loadJourneyData(SportingHorizon.AllTime, SportingSportFilter.ALL)

        assertEquals(2, data.heroTotals.totalWorkouts)
        assertEquals(35.0, data.heroTotals.totalDistanceKm, 0.01)
        assertEquals(1.5, data.heroTotals.totalActiveHours, 0.01)
        assertEquals(550, data.heroTotals.totalElevationM)
        assertEquals(1, data.heroTotals.favoriteRoutesCount)

        // Hidden Gems: Cadence revs from 2026 bike = 85 * 60 = 5100
        assertEquals(5100L, data.hiddenGems.pedalRevolutions)
        assertEquals(1, data.hiddenGems.cadenceTrackedRidesCount)

        // Heartbeats: (145 * 60) + (160 * 30) = 8700 + 4800 = 13500
        assertEquals(13500L, data.hiddenGems.estimatedHeartbeats)
        assertEquals(2, data.hiddenGems.hrTrackedWorkoutsCount)

        // Power kWh: 200W * 3600s = 720 kJ = 0.2 kWh
        assertEquals(0.2, data.hiddenGems.mechanicalKwh, 0.01)
        assertEquals(1, data.hiddenGems.powerTrackedWorkoutsCount)
    }

    @Test
    fun testYearHorizon_2026_filtersAnnualWrappedWithoutSqlError() = runTest {
        insertTestWorkout(
            name = "Morning Ride 2026",
            sport = "BIKE",
            timeStart = "2026-06-15 08:30:00",
            activeTimeS = 3600,
            distanceM = 30000.0,
            elevationM = 500,
            clusterId = 10,
            avgCadence = 85.0,
            avgHr = 145.0,
            avgPower = 200.0
        )

        insertTestWorkout(
            name = "Evening Run 2025",
            sport = "RUN",
            timeStart = "2025-05-10 18:15:00",
            activeTimeS = 1800,
            distanceM = 5000.0,
            elevationM = 50,
            clusterId = 0,
            avgHr = 160.0
        )

        // Annual Wrapped 2026: exercises the joined queries with year filter (no w.strftime syntax error)
        val data2026 = repository.loadJourneyData(SportingHorizon.Year(2026), SportingSportFilter.ALL)

        assertEquals(1, data2026.heroTotals.totalWorkouts)
        assertEquals(30.0, data2026.heroTotals.totalDistanceKm, 0.01)
        assertEquals(500, data2026.heroTotals.totalElevationM)

        // Cadence and Power from 2026 MUST be non-zero
        assertEquals(5100L, data2026.hiddenGems.pedalRevolutions)
        assertEquals(1, data2026.hiddenGems.cadenceTrackedRidesCount)
        assertEquals(0.2, data2026.hiddenGems.mechanicalKwh, 0.01)
        assertEquals(1, data2026.hiddenGems.powerTrackedWorkoutsCount)

        // 2025 HR must be excluded: only 145 * 60 = 8700
        assertEquals(8700L, data2026.hiddenGems.estimatedHeartbeats)
        assertEquals(1, data2026.hiddenGems.hrTrackedWorkoutsCount)
    }

    @Test
    fun testSportFilter_running_filtersCyclingOut() = runTest {
        insertTestWorkout(
            name = "Morning Ride 2026",
            sport = "BIKE",
            timeStart = "2026-06-15 08:30:00",
            activeTimeS = 3600,
            distanceM = 30000.0,
            elevationM = 500,
            clusterId = 10,
            avgCadence = 85.0
        )

        insertTestWorkout(
            name = "Evening Run 2026",
            sport = "RUN",
            timeStart = "2026-07-20 18:15:00",
            activeTimeS = 1800,
            distanceM = 5000.0,
            elevationM = 50,
            clusterId = 0,
            avgHr = 160.0
        )

        val runData = repository.loadJourneyData(SportingHorizon.Year(2026), SportingSportFilter.RUNNING)

        assertEquals(1, runData.heroTotals.totalWorkouts)
        assertEquals(5.0, runData.heroTotals.totalDistanceKm, 0.01)
        assertEquals(0L, runData.hiddenGems.pedalRevolutions)
        assertEquals(4800L, runData.hiddenGems.estimatedHeartbeats)
    }

    @Test
    fun testGetAvailableYears_returnsDistinctDescendingYears() = runTest {
        insertTestWorkout("W1", "BIKE", "2026-01-01 10:00:00", 100, 1000.0, 10, 0)
        insertTestWorkout("W2", "BIKE", "2026-05-01 10:00:00", 100, 1000.0, 10, 0)
        insertTestWorkout("W3", "RUN", "2024-03-01 10:00:00", 100, 1000.0, 10, 0)
        insertTestWorkout("W4", "RUN", "2025-08-01 10:00:00", 100, 1000.0, 10, 0)

        val years = repository.getAvailableYears()
        assertEquals(listOf(2026, 2025, 2024), years)
    }
}
