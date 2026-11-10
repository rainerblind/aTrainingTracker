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

import android.content.Context
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying data hydration and mapping of CardioPowerDynamicsRecord (REQ-REV-010.1 / TST-REV-011.1).
 */
class WorkoutDataMapperCardioPowerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)

    private lateinit var mapper: WorkoutDataMapper

    @Before
    fun setUp() {
        mapper = WorkoutDataMapper(
            context = mockContext,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb
        )
    }

    private fun createSnapshot(workoutId: Long, name: String): WorkoutDataMapper.RawCursorSnapshot {
        return WorkoutDataMapper.RawCursorSnapshot(
            workoutId = workoutId,
            sportId = 1L,
            equipmentId = 0L,
            formattedDate = "2026-11-10",
            formattedTime = "10:00:00",
            startTimeS = 1794300000L,
            localDateTime = LocalDateTime.of(2026, 11, 10, 10, 0, 0),
            fileBaseName = name,
            totalDistance = 25000.0,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            workoutName = name,
            clusterId = -1L,
            finished = true,
            commute = false,
            trainer = false,
            race = false,
            source = com.atrainingtracker.trainingtracker.database.WorkoutSource.TRACKED,
            uploadToStrava = 0,
            minLat = null,
            minLng = null,
            maxLat = null,
            maxLng = null,
            activeTimeSec = 3600L,
            totalTimeSec = 3800L,
            avgSpeedMps = 8.5,
            ascentMeters = 300L,
            descentMeters = 300L,
            description = null,
            goal = null,
            method = null,
            recordingErrors = null
        )
    }

    @Test
    fun testHydrationFromBatchMetadata() {
        val record = CardioPowerDynamicsRecord(
            workoutId = 42L,
            tauStart = 32.5,
            hr0Start = 95.0,
            kStart = 0.35,
            hr200Start = 165.0,
            r2Start = 0.88,
            rmseStart = 3.2,
            tauEnd = 45.0,
            hr0End = 105.0,
            kEnd = 0.34,
            hr200End = 173.0,
            r2End = 0.82,
            rmseEnd = 4.1,
            deltaHr200 = 8.0,
            deltaTau = 12.5,
            deltaHr0 = 10.0
        )

        val snapshot = createSnapshot(42L, "Test Ride")

        val batch = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            cardioPowerDynamics = mapOf(42L to record)
        )

        val workoutData = mapper.fromSnapshot(snapshot, batch)
        assertNotNull(workoutData.cardioPowerDynamics)
        assertEquals(42L, workoutData.cardioPowerDynamics?.workoutId)
        assertEquals(32.5, workoutData.cardioPowerDynamics?.tauStart ?: 0.0, 0.01)
        assertEquals(165.0, workoutData.cardioPowerDynamics?.hr200Start ?: 0.0, 0.01)
        assertEquals(8.0, workoutData.cardioPowerDynamics?.deltaHr200 ?: 0.0, 0.01)

        // Verify propagation to detailsData
        val details = workoutData.detailsData
        assertNotNull(details.cardioPowerDynamics)
        assertEquals(165.0, details.cardioPowerDynamics?.hr200Start ?: 0.0, 0.01)
    }

    @Test
    fun testHydrationFallbackWhenNotInBatch() {
        val record = CardioPowerDynamicsRecord(
            workoutId = 99L,
            tauStart = 28.0,
            hr0Start = 90.0,
            kStart = 0.38,
            hr200Start = 166.0,
            r2Start = 0.91,
            rmseStart = 2.8
        )
        every { mockSummariesDb.getCardioPowerDynamics(99L) } returns record

        val snapshot = createSnapshot(99L, "Test 99")

        val batch = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            cardioPowerDynamics = emptyMap()
        )

        val workoutData = mapper.fromSnapshot(snapshot, batch)
        assertNotNull(workoutData.cardioPowerDynamics)
        assertEquals(99L, workoutData.cardioPowerDynamics?.workoutId)
        assertEquals(28.0, workoutData.cardioPowerDynamics?.tauStart ?: 0.0, 0.01)
    }
}
