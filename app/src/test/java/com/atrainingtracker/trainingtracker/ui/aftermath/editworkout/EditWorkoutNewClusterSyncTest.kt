/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ClusterWorkoutDataPortRegistry
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.toClusterWorkoutSnapshot
import com.atrainingtracker.trainingtracker.ui.clusters.EditWorkoutClusterDialog
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * Behavioral Compose & Robolectric SQLite verification for `REQ-SET-072` / `TST-SET-061`:
 * 1. [EditWorkoutClusterDialog] pre-fills `stripHitCount(initialWorkoutName)`.
 * 2. [WorkoutClusterEngine.createNewClusterFromWorkout] sets `forceIdentity = true`, updates
 *    `WORKOUT_NAME` & `CLUSTER_ID` in SQLite, and reconciles old/new cluster hit counts.
 */
// Linked test specs: TST-SET-061
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class EditWorkoutNewClusterSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetAll()
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
    }

    @After
    fun tearDown() {
        resetAll()
        unmockkAll()
    }

    private fun resetAll() {
        ClusterWorkoutDataPortRegistry.resetForTesting()
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
        context.deleteDatabase(WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
    }

    @Test
    fun editWorkoutClusterDialog_prefillsStrippedHitCountOnCreateNew_TST_SET_061() {
        // TST-SET-061
        var createdName: String? = null
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditWorkoutClusterDialog(
                    currentClusterId = 10L,
                    candidates = emptyList(),
                    initialWorkoutName = "Hausrunde #3",
                    onSelectCluster = {},
                    onUnassignCluster = {},
                    onCreateNewCluster = { name, _ -> createdName = name },
                    onDismiss = {},
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN }
                )
            }
        }

        composeTestRule.waitForIdle()
        val createNewLabel = context.getString(R.string.cluster_naming__create_new)
        composeTestRule.onNodeWithText(createNewLabel).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Hausrunde").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.OK)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Hausrunde", createdName)
    }

    @Test
    fun createNewClusterFromWorkout_updatesSqliteNameAndDecrementsOldCluster_TST_SET_061() {
        // TST-SET-061
        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.RUN
        SportTypesRepository.resetForTesting(mockSportRepo)

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val lowConfIdentity = mockk<EquipmentAndSportTypeDiscoveryManager.InferredIdentity>(relaxed = true).apply {
            every { sportId } returns 2L
            every { equipmentId } returns 0L
            every { isHighConfidence } returns false
        }
        every { mockDiscovery.resolveIdentity(any<Long>(), any<BSportType>(), any<Double>()) } returns lowConfIdentity
        every { mockDiscovery.inferIdentityFromSport(any<Long>()) } returns lowConfIdentity
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscovery)

        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
        val oldClusterId = clusterDb.insertCluster(
            WorkoutCluster(
                name = "Hausrunde",
                probableSportId = 2L,
                startLat = 48.1,
                startLng = 11.5,
                endLat = 48.1,
                endLng = 11.5,
                maxDispLat = 48.12,
                maxDispLng = 11.52,
                refDistance = 5000.0,
                hitCount = 3,
                bSportType = BSportType.RUN
            )
        )

        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val summariesDb = summariesManager.database
        val workoutId = summariesDb.insertOrThrow(
            WorkoutSummaries.TABLE,
            null,
            ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_NAME, "Hausrunde #3")
                put(WorkoutSummaries.FILE_BASE_NAME, "run_3")
                put(WorkoutSummaries.SPORT_ID, 2L)
                put(WorkoutSummaries.TIME_START, "2026-09-10 08:00:00")
                put(WorkoutSummaries.FINISHED, 1)
                put(WorkoutSummaries.CLUSTER_ID, oldClusterId)
                put(WorkoutSummaries.DISTANCE_TOTAL_m, 5000.0)
            }
        )

        val workoutData = WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "run_3",
            workoutName = "Hausrunde #3",
            sportId = 2L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-10",
            formattedTime = "08:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = 1500.0,
            activeTimeSec = 1500L,
            totalTimeSec = 1500L,
            avgSpeedMps = 3.33,
            ascentMeters = 20L,
            descentMeters = 20L,
            minAltitude = 500.0,
            maxAltitude = 520.0,
            startLatLng = LatLng(48.1, 11.5),
            endLatLng = LatLng(48.1, 11.5),
            maxDisplacementLatLng = LatLng(48.12, 11.52),
            description = null,
            goal = null,
            method = null,
            stravaSportName = "Run",
            clusterId = oldClusterId,
            clusterName = "Hausrunde"
        )

        val engine = WorkoutClusterEngine.getInstance(context)
        val newClusterId = engine.createNewClusterFromWorkout(
            context = context,
            workout = workoutData.toClusterWorkoutSnapshot(),
            customName = "Neue Runde",
            hasCounter = true
        )

        assertTrue(newClusterId > 0L && newClusterId != oldClusterId)
        assertEquals(newClusterId, summariesManager.getLong(workoutId, WorkoutSummaries.CLUSTER_ID))
        assertEquals("Neue Runde", summariesManager.getString(workoutId, WorkoutSummaries.WORKOUT_NAME))
        assertEquals(1, clusterDb.getClusterById(newClusterId)?.hitCount)
        assertEquals(0, clusterDb.getClusterById(oldClusterId)?.hitCount)
    }
}
