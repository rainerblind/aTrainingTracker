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

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying ExistingWorkoutsSnapshot creation, case-insensitive base name lookup,
 * Strava Activity ID lookup, and thread-safe registration (REQ-MIG-050.1 / TST-MIG-048.1).
 */
class ExistingWorkoutsSnapshotTest {

    @Test
    fun testSnapshot_caseInsensitiveBaseNameMatching() {
        val snapshot = ExistingWorkoutsSnapshot(
            initialBaseNames = setOf("2026-11-01_100000", "Morning_Ride", "STRAVA_12345"),
            initialStravaIds = setOf("123456789")
        )

        assertEquals(3, snapshot.totalBaseNames)
        assertEquals(1, snapshot.totalStravaIds)

        // Exact match
        assertTrue(snapshot.containsFileBaseName("Morning_Ride"))
        // Case-insensitive match
        assertTrue(snapshot.containsFileBaseName("morning_ride"))
        assertTrue(snapshot.containsFileBaseName("MORNING_RIDE"))
        assertTrue(snapshot.containsFileBaseName("2026-11-01_100000"))
        assertTrue(snapshot.containsFileBaseName("strava_12345"))

        // Non-existent
        assertFalse(snapshot.containsFileBaseName("Evening_Ride"))
        assertFalse(snapshot.containsFileBaseName(null))
        assertFalse(snapshot.containsFileBaseName(""))
        assertFalse(snapshot.containsFileBaseName("   "))
    }

    @Test
    fun testSnapshot_stravaActivityIdMatching() {
        val snapshot = ExistingWorkoutsSnapshot(
            initialBaseNames = emptySet(),
            initialStravaIds = setOf("9876543210", "11223344")
        )

        assertTrue(snapshot.containsStravaActivityId("9876543210"))
        assertTrue(snapshot.containsStravaActivityId("11223344"))
        assertTrue(snapshot.containsStravaActivityId(" 11223344 "))

        assertFalse(snapshot.containsStravaActivityId("9999999999"))
        assertFalse(snapshot.containsStravaActivityId(null))
        assertFalse(snapshot.containsStravaActivityId(""))
    }

    @Test
    fun testSnapshot_isDuplicate() {
        val snapshot = ExistingWorkoutsSnapshot(
            initialBaseNames = setOf("run_2026_01"),
            initialStravaIds = setOf("strava_id_42")
        )

        // Duplicate by base name
        assertTrue(snapshot.isDuplicate("run_2026_01", null))
        assertTrue(snapshot.isDuplicate("RUN_2026_01", "unknown_id"))

        // Duplicate by Strava activity ID
        assertTrue(snapshot.isDuplicate("new_workout", "strava_id_42"))

        // Non-duplicate
        assertFalse(snapshot.isDuplicate("brand_new_workout", "new_strava_id"))
        assertFalse(snapshot.isDuplicate(null, null))
    }

    @Test
    fun testSnapshot_registerUpdatesStateDynamically() {
        val snapshot = ExistingWorkoutsSnapshot()

        assertFalse(snapshot.containsFileBaseName("ride_01"))
        assertFalse(snapshot.containsStravaActivityId("123"))

        snapshot.register("ride_01", "123")

        assertTrue(snapshot.containsFileBaseName("ride_01"))
        assertTrue(snapshot.containsFileBaseName("RIDE_01"))
        assertTrue(snapshot.containsStravaActivityId("123"))
        assertEquals(1, snapshot.totalBaseNames)
        assertEquals(1, snapshot.totalStravaIds)
    }

    @Test
    fun testSnapshot_factoryMethodWithMocks() {
        val mockSummaryDb = mockk<WorkoutSummariesDatabaseManager>()
        val mockStravaDb = mockk<StravaUploadDbHelper>()

        every { mockSummaryDb.allFileBaseNames } returns setOf("file_a", "file_b")
        every { mockStravaDb.allActivityIds } returns setOf("strava_1", "strava_2")

        val snapshot = ExistingWorkoutsSnapshot.create(mockSummaryDb, mockStravaDb)

        assertEquals(2, snapshot.totalBaseNames)
        assertEquals(2, snapshot.totalStravaIds)
        assertTrue(snapshot.containsFileBaseName("FILE_A"))
        assertTrue(snapshot.containsStravaActivityId("strava_1"))
    }
}
