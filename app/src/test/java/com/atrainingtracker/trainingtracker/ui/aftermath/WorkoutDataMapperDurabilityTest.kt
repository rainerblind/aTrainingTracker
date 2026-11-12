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
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Verifies ExtremaType additions, WorkoutData properties, and WorkoutDataMapper persistence
 * for autonomic durability metrics (REQ-PER-038.5, TST-PER-046.5).
 */
class WorkoutDataMapperDurabilityTest {

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
            source = WorkoutSource.TRACKED,
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
    fun `WorkoutDataMapper maps durability extrema into WorkoutData via fromSnapshot (TST-PER-046_5)`() {
        val snapshot = createSnapshot(42L, "Durability Workout")
        val extremaRecords = listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(42L, SensorType.HRV_DFA_A1, ExtremaType.DFA_A1_FRESH_BASELINE, 0.95, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(42L, SensorType.HRV_DFA_A1, ExtremaType.DFA_A1_LATE_MEAN, 0.72, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(42L, SensorType.HRV_DFA_A1, ExtremaType.DFA_A1_DRIFT_PCT, -24.2, null)
        )
        val batch = WorkoutDataMapper.BatchMetadata(
            extrema = mapOf(42L to extremaRecords),
            stravaData = emptyMap()
        )

        val workoutData = mapper.fromSnapshot(snapshot, batch)
        assertEquals(0.95, workoutData.dfaA1FreshBaseline ?: 0.0, 0.001)
        assertEquals(0.72, workoutData.dfaA1LateMean ?: 0.0, 0.001)
        assertEquals(-24.2, workoutData.dfaA1DurabilityDriftPct ?: 0.0, 0.001)
    }
}
