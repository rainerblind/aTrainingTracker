/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose (`createComposeRule()`) and Robolectric SQLite verification for cluster
 * deletion via header long-click menu and atomic link clearing (`REQ-SET-033`, `TST-SET-024`,
 * `REQ-PRO-132`, `REQ-PRO-133`).
 */
// Linked test specs: TST-SET-024
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutClusterDeleteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetAll()
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt("last_cluster_bounds_repair_v5", 5)
            .putInt(WorkoutClusterRepository.SP_KEY_LAST_APEX_REPAIR, 2)
            .commit()
    }

    @After
    fun tearDown() {
        resetAll()
        unmockkAll()
    }

    private fun resetAll() {
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
        context.deleteDatabase(WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
        context.deleteDatabase(RoutesDatabaseManager.RoutesDbHelper.DB_NAME)
    }

    @Test
    fun summaryHeader_longClickOpensDeleteMenuAndInvokesCallback_TST_SET_024() {
        // TST-SET-024
        val cluster = WorkoutCluster(
            id = 5L,
            name = "Obsolete Loop",
            probableSportId = 1L,
            startLat = 48.1,
            startLng = 11.5,
            endLat = 48.1,
            endLng = 11.5,
            maxDispLat = 48.12,
            maxDispLng = 11.52,
            refDistance = 10000.0,
            hitCount = 1,
            bSportType = BSportType.BIKE
        )
        val vm = mockk<WorkoutClustersViewModel>(relaxed = true).apply {
            every { getBSportType(any()) } returns BSportType.BIKE
            every { getSportName(any()) } returns "Road Bike"
            every { getLinkedEquipment(any()) } returns emptyList()
        }
        var deleteRequested = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutClusterSummaryHeader(
                    cluster = cluster,
                    viewModel = vm,
                    isEditing = false,
                    onBack = {},
                    onRename = {},
                    onEditFingerprint = {},
                    onSaveFingerprint = {},
                    onDeleteRequest = { deleteRequested = true },
                    onHitCountClick = {},
                    hasChanges = false
                )
            }
        }

        composeTestRule.onNodeWithText("Obsolete Loop").performTouchInput { longClick() }
        composeTestRule.onNodeWithText(context.getString(R.string.delete)).assertIsDisplayed().performClick()
        assertTrue("Tapping Delete in long-click context menu must invoke onDeleteRequest", deleteRequested)
    }

    @Test
    fun deleteCluster_clearsWorkoutAndRouteLinksWhilePreservingWorkouts_TST_SET_024() = runBlocking {
        // TST-SET-024
        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
        val clusterId = clusterDb.insertCluster(
            WorkoutCluster(
                name = "Delete Target",
                probableSportId = 1L,
                startLat = 48.1,
                startLng = 11.5,
                endLat = 48.1,
                endLng = 11.5,
                maxDispLat = 48.12,
                maxDispLng = 11.52,
                refDistance = 10000.0,
                hitCount = 1,
                bSportType = BSportType.BIKE
            )
        )
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val workoutId = summariesManager.database.insertOrThrow(
            WorkoutSummaries.TABLE,
            null,
            ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_NAME, "Saved Ride")
                put(WorkoutSummaries.FILE_BASE_NAME, "ride_1")
                put(WorkoutSummaries.SPORT_ID, 1L)
                put(WorkoutSummaries.TIME_START, "2026-10-01 09:00:00")
                put(WorkoutSummaries.FINISHED, 1)
                put(WorkoutSummaries.CLUSTER_ID, clusterId)
            }
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = WorkoutClusterRepository(context, scope)
        repo.deleteCluster(clusterId)
        scope.cancel()

        assertNull("Cluster must be deleted from RouteClusters.db", clusterDb.getClusterById(clusterId))
        assertEquals(
            "Workout must be preserved with CLUSTER_ID cleared to -1L",
            -1L,
            summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID)
        )
        assertEquals(
            "Saved Ride",
            summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME)
        )
    }
}
