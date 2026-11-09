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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for transactional cluster split and centroid reconciliation (REQ-SET-096.5, TST-SET-096.2, ATT-5900).
 */
class WorkoutClusterSplitTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager
    private lateinit var clusterEngine: WorkoutClusterEngine

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkConstructor(android.content.ContentValues::class)
        every { anyConstructed<android.content.ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<android.content.ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<android.content.ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<android.content.ContentValues>().put(any<String>(), any<Double>()) } returns Unit

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(any(), *anyVararg()) } answers {
            val formatArgs = args.getOrNull(1)
            if (formatArgs is Array<*> && formatArgs.size >= 2) {
                "${formatArgs[0]} #${formatArgs[1]}"
            } else {
                "FormattedClusterName"
            }
        }

        mockSummariesDb = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        every { mockSummariesManager.database } returns mockSummariesDb

        mockClusterDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)

        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockSummariesManager)
        SportTypeDatabaseManager.resetForTesting(mockSportDb)
        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        WorkoutClusterEngine.resetForTesting(null)

        every { mockSportDb.getBSportType(any()) } returns BSportType.BIKE

        clusterEngine = WorkoutClusterEngine.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    private fun createDummyWorkout(
        id: Long,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        distance: Double
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Commute #$id",
            sportId = 1L,
            sportName = "Biking",
            bSportType = BSportType.BIKE,
            startTimeS = 1000L * id,
            formattedDate = "2026-11-08",
            formattedTime = "10:00",
            localDateTime = java.time.LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "dummy_poly_$id",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = distance,
            maxDisplacement = distance / 2.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = distance / 1800.0,
            ascentMeters = 0L,
            descentMeters = 0L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = LatLng(startLat, startLng),
            endLatLng = LatLng(endLat, endLng),
            maxDisplacementLatLng = LatLng((startLat + endLat) / 2.0, (startLng + endLng) / 2.0),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testSplitCluster_createsNewClusterAndReconcilesPrimary() {
        val primaryId = 10L
        val newClusterId = 20L

        val primaryCluster = WorkoutCluster(
            id = primaryId,
            name = "Commute",
            probableSportId = 1L,
            startLat = 48.7000,
            startLng = 9.0000,
            endLat = 48.7000,
            endLng = 9.1000,
            maxDispLat = 48.7050,
            maxDispLng = 9.0500,
            refDistance = 10000.0,
            hitCount = 4,
            bSportType = BSportType.BIKE,
            hasCounter = true
        )

        val w1 = createDummyWorkout(1L, 48.7000, 9.0000, 48.7000, 9.1000, 10000.0)
        val w2 = createDummyWorkout(2L, 48.7000, 9.0000, 48.7000, 9.1000, 10000.0)
        val w3 = createDummyWorkout(3L, 48.7200, 9.0000, 48.7200, 9.1000, 12000.0)
        val w4 = createDummyWorkout(4L, 48.7200, 9.0000, 48.7200, 9.1000, 12000.0)

        every { mockClusterDb.getClusterById(primaryId) } returns primaryCluster
        every { mockClusterDb.isNameTaken(any(), any()) } returns false
        every { mockClusterDb.insertCluster(any()) } returns newClusterId

        // Remaining primary count will be 2 (w1, w2)
        every { mockSummariesManager.getWorkoutCountForCluster(primaryId) } returns 2
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any()) } returns mockCursor
        every { mockCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { mockCursor.getColumnIndexOrThrow(any()) } returns 0
        every { mockCursor.getLong(any()) } returns 1L
        every { mockCursor.getDouble(any()) } returns 10000.0

        every { mockSummariesManager.getExtremaPosition(any(), any(), any()) } answers {
            LatLng(48.7000, 9.0000)
        }

        // Split off w3 and w4 into a variant
        val variantToSplit = listOf("Commute var 2" to listOf(w3, w4))
        val createdIds = clusterEngine.splitCluster(mockContext, primaryId, variantToSplit, renameWorkouts = true)

        assertEquals(listOf(newClusterId), createdIds)

        // 1. Verify new cluster insertion
        val newClusterSlot = slot<WorkoutCluster>()
        verify { mockClusterDb.insertCluster(capture(newClusterSlot)) }
        val inserted = newClusterSlot.captured
        assertEquals("Commute var 2", inserted.name)
        assertEquals(2, inserted.hitCount)
        assertEquals(48.7200, inserted.startLat, 1e-4)
        assertEquals(12000.0, inserted.refDistance, 1e-4)

        // 2. Verify workout reassignment in summaries DB
        verify(exactly = 2) {
            mockSummariesDb.update(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
                any()
            )
        }

        // 3. Verify primary cluster update
        val updatedPrimarySlot = slot<WorkoutCluster>()
        verify { mockClusterDb.updateCluster(capture(updatedPrimarySlot)) }
        assertEquals(2, updatedPrimarySlot.captured.hitCount)
    }

    @Test
    fun testSplitCluster_returnsEmptyWhenClusterNotFound() {
        every { mockClusterDb.getClusterById(99L) } returns null
        val result = clusterEngine.splitCluster(mockContext, 99L, listOf("Var" to emptyList()))
        assertTrue(result.isEmpty())
        verify(exactly = 0) { mockClusterDb.insertCluster(any()) }
    }

    @Test
    fun testSplitCluster_returnsEmptyWhenVariantsEmpty() {
        val primaryCluster = WorkoutCluster(
            id = 1L, name = "Test", probableSportId = 1L,
            startLat = 10.0, startLng = 10.0, endLat = 11.0, endLng = 11.0,
            maxDispLat = 10.5, maxDispLng = 10.5, refDistance = 5000.0, hitCount = 2,
            bSportType = BSportType.BIKE
        )
        every { mockClusterDb.getClusterById(1L) } returns primaryCluster

        val result = clusterEngine.splitCluster(mockContext, 1L, emptyList())
        assertTrue(result.isEmpty())
        verify(exactly = 0) { mockClusterDb.insertCluster(any()) }
    }
}
