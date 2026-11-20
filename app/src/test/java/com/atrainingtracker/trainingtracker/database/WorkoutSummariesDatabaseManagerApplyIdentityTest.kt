/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.database

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager.InferredIdentity
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.migration.DatabaseConnectionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit verification for tri-state [WorkoutSummaries.UPLOAD_TO_STRAVA] preservation inside
 * [WorkoutSummariesDatabaseManager.applyInferredIdentity] (`REQ-MIG-054`, `TST-MIG-052`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutSummariesDatabaseManagerApplyIdentityTest {

    private lateinit var context: Context
    private lateinit var summariesDb: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        context.deleteDatabase(WorkoutSummariesDbHelper.DB_NAME)
        summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
    }

    @After
    fun tearDown() {
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        context.deleteDatabase(WorkoutSummariesDbHelper.DB_NAME)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    private fun insertSummaryRow(fileBaseName: String, uploadToStrava: Int?, omitUploadColumn: Boolean = false): Long {
        val values = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.WORKOUT_NAME, fileBaseName)
            put(WorkoutSummaries.TIME_START, "2026-11-20 08:00:00")
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.B_SPORT, BSportType.UNKNOWN.name)
            if (!omitUploadColumn) {
                if (uploadToStrava == null) {
                    putNull(WorkoutSummaries.UPLOAD_TO_STRAVA)
                } else {
                    put(WorkoutSummaries.UPLOAD_TO_STRAVA, uploadToStrava)
                }
            }
        }
        return summariesDb.database.insert(WorkoutSummaries.TABLE, null, values)
    }

    private fun queryRow(workoutId: Long): Triple<Long, String, Int> {
        summariesDb.database.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.SPORT_ID, WorkoutSummaries.B_SPORT, WorkoutSummaries.UPLOAD_TO_STRAVA),
            "${WorkoutSummaries.C_ID}=?",
            arrayOf(workoutId.toString()),
            null, null, null
        ).use { c ->
            require(c.moveToFirst()) { "Expected row for workoutId=$workoutId" }
            return Triple(c.getLong(0), c.getString(1), c.getInt(2))
        }
    }

    @Test
    fun applyInferredIdentity_populatesUploadToStrava_whenSchemaDefaultMinusOne_REQ_MIG_054_TST_MIG_052() {
        // REQ-MIG-054, TST-MIG-052
        val idOptIn = insertSummaryRow("live_default_opt_in", uploadToStrava = null, omitUploadColumn = true)
        val idNoUpload = insertSummaryRow("live_default_no_upload", uploadToStrava = -1)

        summariesDb.applyInferredIdentity(
            idOptIn,
            InferredIdentity(sportId = 7L, bSportType = BSportType.BIKE, equipmentId = 3L, stravaSportName = "Ride", uploadToStrava = 1, isHighConfidence = true)
        )
        summariesDb.applyInferredIdentity(
            idNoUpload,
            InferredIdentity(sportId = 8L, bSportType = BSportType.RUN, equipmentId = -1L, stravaSportName = null, uploadToStrava = 0, isHighConfidence = false)
        )

        assertEquals(Triple(7L, BSportType.BIKE.name, 1), queryRow(idOptIn))
        assertEquals(Triple(8L, BSportType.RUN.name, 0), queryRow(idNoUpload))
    }

    @Test
    fun applyInferredIdentity_populatesUploadToStrava_whenSqlNull_REQ_MIG_054_TST_MIG_052() {
        // REQ-MIG-054, TST-MIG-052
        val idSqlNull = insertSummaryRow("legacy_sql_null", uploadToStrava = null, omitUploadColumn = false)

        summariesDb.applyInferredIdentity(
            idSqlNull,
            InferredIdentity(sportId = 5L, bSportType = BSportType.BIKE, equipmentId = -1L, stravaSportName = "Ride", uploadToStrava = 1, isHighConfidence = false)
        )

        assertEquals(Triple(5L, BSportType.BIKE.name, 1), queryRow(idSqlNull))
    }

    @Test
    fun applyInferredIdentity_preservesExplicitOptOutZero_REQ_MIG_054_TST_MIG_052() {
        // REQ-MIG-054, TST-MIG-052
        val idOptOut = insertSummaryRow("tcx_explicit_opt_out", uploadToStrava = 0)

        summariesDb.applyInferredIdentity(
            idOptOut,
            InferredIdentity(sportId = 11L, bSportType = BSportType.BIKE, equipmentId = 4L, stravaSportName = "Ride", uploadToStrava = 1, isHighConfidence = false)
        )

        assertEquals(Triple(11L, BSportType.BIKE.name, 0), queryRow(idOptOut))
    }

    @Test
    fun applyInferredIdentity_preservesExplicitOptInOne_REQ_MIG_054_TST_MIG_052() {
        // REQ-MIG-054, TST-MIG-052
        val idOptIn = insertSummaryRow("tcx_explicit_opt_in", uploadToStrava = 1)

        summariesDb.applyInferredIdentity(
            idOptIn,
            InferredIdentity(sportId = 12L, bSportType = BSportType.RUN, equipmentId = -1L, stravaSportName = null, uploadToStrava = 0, isHighConfidence = false)
        )

        assertEquals(Triple(12L, BSportType.RUN.name, 1), queryRow(idOptIn))
    }

    @Test
    fun applyInferredIdentity_nonExistentWorkoutId_isSafeNoOp_REQ_MIG_054_TST_MIG_052() {
        // REQ-MIG-054, TST-MIG-052
        val existingId = insertSummaryRow("unchanged_row", uploadToStrava = 0)

        summariesDb.applyInferredIdentity(
            999_999L,
            InferredIdentity(sportId = 19L, bSportType = BSportType.BIKE, equipmentId = 2L, stravaSportName = "Ride", uploadToStrava = 1, isHighConfidence = true)
        )

        assertEquals(Triple(1L, BSportType.UNKNOWN.name, 0), queryRow(existingId))
    }
}
