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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric SQLite verification for route-to-cluster creation and name synchronization
 * (`REQ-SET-031`, `TST-SET-022`, `REQ-PRO-133`: zero `src/main/` reads).
 */
// Linked test specs: TST-SET-022
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutClusterEngineRouteSyncTest {

    private lateinit var context: Context
    private lateinit var dbManager: WorkoutClusterDatabaseManager
    private lateinit var engine: WorkoutClusterEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetAll()

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.useSportTypeForClustering() } returns true
        every { TrainingApplication.useAltitudePosForClustering() } returns false
        every { TrainingApplication.getClusterScoreThreshold() } returns 1.2f

        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        SportTypesRepository.resetForTesting(mockSportRepo)

        val mockLocRepo = mockk<KnownLocationsRepository>(relaxed = true)
        every { mockLocRepo.getMyLocation(any()) } returns null
        KnownLocationsRepository.resetForTesting(mockLocRepo)

        dbManager = WorkoutClusterDatabaseManager.getInstance(context)
        engine = WorkoutClusterEngine.getInstance(context)
    }

    @After
    fun tearDown() {
        resetAll()
        unmockkAll()
    }

    private fun resetAll() {
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        KnownLocationsRepository.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
    }

    @Test
    fun learnFromRoute_andSyncRouteNameChange_createAndRenameLinkedCluster_TST_SET_022() {
        // TST-SET-022
        val route = RouteWithPath(
            summary = RouteSummary(
                id = 12L,
                externalId = "gpx_12",
                name = "GPX Alpine Tour",
                description = "Imported GPX",
                isSelected = false,
                distance = 25000.0,
                elevationGain = 450.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX
            ),
            path = listOf(
                PathPoint(distance = 0.0, latLng = LatLng(47.80, 11.40)),
                PathPoint(distance = 12500.0, latLng = LatLng(47.88, 11.48)),
                PathPoint(distance = 25000.0, latLng = LatLng(47.80, 11.40))
            )
        )

        val clusterId = engine.learnFromRoute(route)
        assertTrue("learnFromRoute must create a cluster", clusterId > 0L)

        val created = dbManager.getClusterById(clusterId)
        assertNotNull(created)
        assertEquals("GPX Alpine Tour", created!!.name)
        assertEquals(25000.0, created.refDistance, 0.1)

        // Rename route -> cluster name synchronizes cleanly
        engine.syncRouteNameChange(clusterId, "Renamed Alpine Loop")
        val renamed = dbManager.getClusterById(clusterId)
        assertEquals("Renamed Alpine Loop", renamed?.name)
    }
}
