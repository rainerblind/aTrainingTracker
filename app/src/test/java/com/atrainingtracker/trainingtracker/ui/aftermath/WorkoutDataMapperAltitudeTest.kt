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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Automated unit tests verifying altitude extrema reconciliation and self-healing (REQ-DAT-009, TST-DAT-003, ATT-508).
 */
class WorkoutDataMapperAltitudeTest {

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

    /**
     * Verifies that when corrupted extrema (-63 m, +60 m) exist in SQLite for a workout with
     * smooth stream points (0-2 m), the true stream extrema are derived and persisted to SQLite.
     */
    @Test
    fun testReconcileAltitudeExtrema_CorruptedLegacyExtrema_HealsFromStreamAndUpdatesDb() {
        val streamAltitudes = listOf(0.0, 1.0, 2.0, 1.5, 0.5)
        val encodedStream = NumericalEncodingUtils.encodeDoubles(streamAltitudes)

        val workoutId = 508L
        val corruptedMin = -63.0
        val corruptedMax = 60.0

        val (reconciledMin, reconciledMax) = mapper.reconcileAltitudeExtrema(
            workoutId = workoutId,
            encodedAltitudes = encodedStream,
            recordedMin = corruptedMin,
            recordedMax = corruptedMax
        )

        // Assert reconciled values match stream bounds
        assertEquals("Authoritative min altitude must be derived from stream", 0.0, reconciledMin!!, 0.001)
        assertEquals("Authoritative max altitude must be derived from stream", 2.0, reconciledMax!!, 0.001)

        // Assert database persistence is triggered idempotently
        verify(exactly = 1) {
            mockSummariesDb.updateExtremaValue(
                workoutId,
                SensorType.ALTITUDE,
                ExtremaType.MIN,
                0.0,
                null
            )
        }
        verify(exactly = 1) {
            mockSummariesDb.updateExtremaValue(
                workoutId,
                SensorType.ALTITUDE,
                ExtremaType.MAX,
                2.0,
                null
            )
        }
    }

    /**
     * Verifies that legitimate workouts whose recorded extrema match their stream within tolerance
     * are not overwritten in the database.
     */
    @Test
    fun testReconcileAltitudeExtrema_LegitimateExtremaMatchingStream_DoesNotUpdateDb() {
        val streamAltitudes = listOf(100.0, 150.0, 250.0)
        val encodedStream = NumericalEncodingUtils.encodeDoubles(streamAltitudes)

        val workoutId = 509L
        val validMin = 100.0
        val validMax = 250.0

        val (reconciledMin, reconciledMax) = mapper.reconcileAltitudeExtrema(
            workoutId = workoutId,
            encodedAltitudes = encodedStream,
            recordedMin = validMin,
            recordedMax = validMax
        )

        assertEquals(100.0, reconciledMin!!, 0.001)
        assertEquals(250.0, reconciledMax!!, 0.001)

        // Zero DB updates should occur for clean records
        verify(exactly = 0) {
            mockSummariesDb.updateExtremaValue(any(), any(), any(), any(), any())
        }
    }

    /**
     * Verifies the "Kurz zum Bäcker" scenario (ATT-508 / REQ-DAT-009):
     * When SQLite extrema has recordedMin higher than the stream minimum (e.g. 368 m vs 360 m),
     * it is detected as contradictory and healed down to streamMin (360 m).
     */
    @Test
    fun testReconcileAltitudeExtrema_StoredMinHigherThanStream_HealedToStreamMin() {
        val streamAltitudes = listOf(364.0, 360.0, 370.0, 380.0)
        val encodedStream = NumericalEncodingUtils.encodeDoubles(streamAltitudes)

        val workoutId = 511L
        val recordedMin = 368.0 // Contradicts stream: higher than the lowest point
        val recordedMax = 380.0

        val (reconciledMin, reconciledMax) = mapper.reconcileAltitudeExtrema(
            workoutId = workoutId,
            encodedAltitudes = encodedStream,
            recordedMin = recordedMin,
            recordedMax = recordedMax
        )

        assertEquals("Authoritative min altitude must be healed down to stream minimum", 360.0, reconciledMin!!, 0.001)
        assertEquals(380.0, reconciledMax!!, 0.001)

        verify(exactly = 1) {
            mockSummariesDb.updateExtremaValue(
                workoutId,
                SensorType.ALTITUDE,
                ExtremaType.MIN,
                360.0,
                null
            )
        }
    }

    /**
     * Verifies that when SQLite extrema has recordedMax lower than the stream maximum,
     * it is detected as contradictory and healed up to streamMax.
     */
    @Test
    fun testReconcileAltitudeExtrema_StoredMaxLowerThanStream_HealedToStreamMax() {
        val streamAltitudes = listOf(360.0, 370.0, 385.0)
        val encodedStream = NumericalEncodingUtils.encodeDoubles(streamAltitudes)

        val workoutId = 512L
        val recordedMin = 360.0
        val recordedMax = 375.0 // Contradicts stream: lower than the peak

        val (reconciledMin, reconciledMax) = mapper.reconcileAltitudeExtrema(
            workoutId = workoutId,
            encodedAltitudes = encodedStream,
            recordedMin = recordedMin,
            recordedMax = recordedMax
        )

        assertEquals(360.0, reconciledMin!!, 0.001)
        assertEquals("Authoritative max altitude must be healed up to stream maximum", 385.0, reconciledMax!!, 0.001)

        verify(exactly = 1) {
            mockSummariesDb.updateExtremaValue(
                workoutId,
                SensorType.ALTITUDE,
                ExtremaType.MAX,
                385.0,
                null
            )
        }
    }

    /**
     * Verifies that workouts with empty altitude streams return original recorded values without DB writes.
     */
    @Test
    fun testReconcileAltitudeExtrema_EmptyStream_ReturnsRecordedValuesWithoutDbUpdate() {
        val workoutId = 510L
        val recordedMin = 50.0
        val recordedMax = 80.0

        val (reconciledMin, reconciledMax) = mapper.reconcileAltitudeExtrema(
            workoutId = workoutId,
            encodedAltitudes = "",
            recordedMin = recordedMin,
            recordedMax = recordedMax
        )

        assertEquals(50.0, reconciledMin!!, 0.001)
        assertEquals(80.0, reconciledMax!!, 0.001)

        verify(exactly = 0) {
            mockSummariesDb.updateExtremaValue(any(), any(), any(), any(), any())
        }
    }
}
