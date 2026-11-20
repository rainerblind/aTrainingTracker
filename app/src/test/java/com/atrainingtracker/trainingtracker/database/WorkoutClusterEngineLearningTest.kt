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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deterministic unit & Robolectric SQLite verification for [WorkoutClusterEngine] learning,
 * name-aware matching, unique naming, stable cluster naming, fingerprint parity, sport/UNKNOWN
 * penalties, and idempotent route cluster creation (`REQ-PRO-133`: zero `src/main/` reads).
 */
// Linked test specs: TST-SET-001, TST-SET-005, TST-SET-007, TST-SET-025, TST-SET-026, TST-SET-033, TST-SET-034, TST-SET-035, TST-SET-047
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutClusterEngineLearningTest {

    private lateinit var context: Context
    private lateinit var dbManager: WorkoutClusterDatabaseManager
    private lateinit var engine: WorkoutClusterEngine
    private lateinit var mockSportTypesRepo: SportTypesRepository
    private lateinit var mockKnownLocRepo: KnownLocationsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetSingletonsAndDbs()

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        every { TrainingApplication.getClusterTolEndpoints() } returns 200f
        every { TrainingApplication.getClusterTolApex() } returns 400f
        every { TrainingApplication.getClusterTolDistance() } returns 0.20f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 400f
        every { TrainingApplication.useSportTypeForClustering() } returns true
        every { TrainingApplication.useAltitudePosForClustering() } returns false
        every { TrainingApplication.getClusterScoreThreshold() } returns 1.2f

        mockSportTypesRepo = mockk(relaxed = true)
        every { mockSportTypesRepo.getBSportType(1L) } returns BSportType.BIKE
        every { mockSportTypesRepo.getBSportType(2L) } returns BSportType.RUN
        every { mockSportTypesRepo.getBSportType(0L) } returns BSportType.UNKNOWN
        SportTypesRepository.resetForTesting(mockSportTypesRepo)

        mockKnownLocRepo = mockk(relaxed = true)
        every { mockKnownLocRepo.getMyLocation(any()) } returns null
        KnownLocationsRepository.resetForTesting(mockKnownLocRepo)

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        val lowConfIdentity = mockk<EquipmentAndSportTypeDiscoveryManager.InferredIdentity>(relaxed = true).apply {
            every { sportId } returns 2L
            every { equipmentId } returns 0L
            every { isHighConfidence } returns false
        }
        every { mockDiscovery.resolveIdentity(any<Long>(), any<BSportType>(), any<Double>()) } returns lowConfIdentity
        every { mockDiscovery.inferIdentityFromSport(any<Long>()) } returns lowConfIdentity
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscovery)

        dbManager = WorkoutClusterDatabaseManager.getInstance(context)
        engine = WorkoutClusterEngine.getInstance(context)
    }

    @After
    fun tearDown() {
        resetSingletonsAndDbs()
        unmockkAll()
    }

    private fun resetSingletonsAndDbs() {
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        SportTypesRepository.resetForTesting(null)
        KnownLocationsRepository.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
        context.deleteDatabase(WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
    }

    @Test
    fun learnFromWorkout_learnsFingerprintRefinesCentroidAndPreservesCustomName_TST_SET_001() {
        // TST-SET-001
        val start = LatLng(48.1000, 11.5000)
        val end = LatLng(48.1001, 11.5001)
        val apex = LatLng(48.1200, 11.5200)

        val clusterId = engine.learnFromWorkout(
            start = start,
            end = end,
            apex = apex,
            distance = 5000.0,
            userSpecifiedName = "Park Loop",
            userSportId = 2L
        )
        assertTrue(clusterId > 0L)

        // Increment hitCount to 1 as if first workout was assigned
        val created = dbManager.getClusterById(clusterId)!!
        assertEquals("Park Loop", created.name)
        assertEquals(BSportType.RUN, created.bSportType)
        dbManager.updateCluster(created.copy(hitCount = 1))

        // Second workout on slightly shifted path matches same cluster and refines centroid
        val shiftedApex = LatLng(48.1204, 11.5204)
        val matchedId = engine.learnFromWorkout(
            start = start,
            end = end,
            apex = shiftedApex,
            distance = 5100.0,
            userSpecifiedName = "Park Loop",
            userSportId = 2L
        )
        assertEquals(clusterId, matchedId)

        val refined = dbManager.getClusterById(clusterId)!!
        assertEquals("Park Loop", refined.name)
        assertEquals((apex.latitude + shiftedApex.latitude) / 2.0, refined.maxDispLat, 1e-6)
        assertEquals(5050.0, refined.refDistance, 0.1)
    }

    @Test
    fun calculateSimilarity_appliesHalfScoreBonusForMatchingNormalizedName_TST_SET_005() {
        // TST-SET-005
        val cluster = WorkoutCluster(
            id = 1L,
            name = "Home Loop",
            probableSportId = 2L,
            startLat = 48.1000,
            startLng = 11.5000,
            endLat = 48.1000,
            endLng = 11.5000,
            maxDispLat = 48.1200,
            maxDispLng = 11.5200,
            refDistance = 5000.0,
            hitCount = 1,
            bSportType = BSportType.RUN
        )
        val driftedStart = LatLng(48.1012, 11.5000) // ~133m drift
        val end = LatLng(48.1000, 11.5000)
        val apex = LatLng(48.1200, 11.5200)

        val unnamedScore = engine.calculateSimilarity(
            start = driftedStart,
            end = end,
            apex = apex,
            distance = 5000.0,
            cluster = cluster,
            workoutName = "Different Name",
            workoutSportType = BSportType.RUN
        )
        val namedScore = engine.calculateSimilarity(
            start = driftedStart,
            end = end,
            apex = apex,
            distance = 5000.0,
            cluster = cluster,
            workoutName = "Home Loop #4",
            workoutSportType = BSportType.RUN
        )
        assertEquals(unnamedScore * 0.5, namedScore, 1e-6)
    }

    @Test
    fun learnFromWorkout_appendsVariantCounterForDistinctRoutesWithSameName_TST_SET_007() {
        // TST-SET-007
        val id1 = engine.learnFromWorkout(
            start = LatLng(48.10, 11.50),
            end = LatLng(48.10, 11.50),
            apex = LatLng(48.12, 11.52),
            distance = 5000.0,
            userSpecifiedName = "Home",
            userSportId = 2L
        )
        // Spatially distinct route (far away, different distance) using explicit unique name check
        val uniqueForSecond = engine.findUniqueClusterName("Home", excludeId = -1L)
        assertEquals("Home var 2", uniqueForSecond)

        val id2 = dbManager.insertCluster(
            WorkoutCluster(
                name = uniqueForSecond,
                probableSportId = 2L,
                startLat = 49.00,
                startLng = 12.00,
                endLat = 49.00,
                endLng = 12.00,
                maxDispLat = 49.05,
                maxDispLng = 12.05,
                refDistance = 12000.0,
                hitCount = 0,
                bSportType = BSportType.RUN
            )
        )
        assertTrue(id2 != id1)
        assertEquals("Home", dbManager.getClusterById(id1)?.name)
        assertEquals("Home var 2", dbManager.getClusterById(id2)?.name)
    }

    @Test
    fun assignClusterToWorkout_preservesBaseClusterNameWithoutCounterSuffix_TST_SET_025() {
        // TST-SET-025
        val clusterId = dbManager.insertCluster(
            WorkoutCluster(
                name = "Park Loop",
                probableSportId = 2L,
                startLat = 48.10,
                startLng = 11.50,
                endLat = 48.10,
                endLng = 11.50,
                maxDispLat = 48.12,
                maxDispLng = 11.52,
                refDistance = 5000.0,
                hitCount = 1,
                bSportType = BSportType.RUN
            )
        )
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
        val cv = android.content.ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, "")
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME, "run_2")
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID, 2L)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START, "2026-09-01 08:00:00")
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED, 1)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, -1L)
        }
        val workoutId = summariesDb.insertOrThrow(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null,
            cv
        )

        engine.assignClusterToWorkout(context, workoutId, clusterId, forceIdentity = true)

        val clusterAfter = dbManager.getClusterById(clusterId)!!
        assertEquals("Park Loop", clusterAfter.name)
        val workoutName = WorkoutSummariesDatabaseManager.getInstance(context)
            .getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
        assertEquals("Park Loop #2", workoutName)
    }

    @Test
    fun learnFromRoute_andWorkoutOnSamePolyline_produceIdenticalFingerprint_TST_SET_033() {
        // TST-SET-033
        val polylinePts = listOf(
            LatLng(48.100, 11.500),
            LatLng(48.115, 11.515),
            LatLng(48.130, 11.530),
            LatLng(48.100, 11.500)
        )
        val route = RouteWithPath(
            summary = RouteSummary(
                id = 5L,
                externalId = "r_5",
                name = "Parity Loop",
                description = "",
                isSelected = false,
                distance = 8000.0,
                elevationGain = 50.0,
                bSportType = BSportType.RUN,
                source = RouteSource.LOCAL_GPX
            ),
            path = polylinePts.mapIndexed { idx, pt -> PathPoint(distance = idx * 2500.0, latLng = pt) }
        )

        val clusterId = engine.learnFromRoute(route)
        val cluster = dbManager.getClusterById(clusterId)!!
        val expectedApex = engine.findApexFromPoints(polylinePts.first(), polylinePts, polylinePts.last())

        assertEquals(expectedApex.latitude, cluster.maxDispLat, 1e-6)
        assertEquals(expectedApex.longitude, cluster.maxDispLng, 1e-6)

        val score = engine.calculateSimilarity(
            start = polylinePts.first(),
            end = polylinePts.last(),
            apex = expectedApex,
            distance = 8000.0,
            cluster = cluster,
            workoutName = "Parity Loop",
            workoutSportType = BSportType.RUN
        )
        assertEquals(0.0, score, 1e-6)
    }

    @Test
    fun suggestCluster_respectsSportAwareClusteringToggle_TST_SET_034() {
        // TST-SET-034
        val bikeCluster = WorkoutCluster(
            id = 10L,
            name = "River Bike",
            probableSportId = 1L,
            startLat = 48.10,
            startLng = 11.50,
            endLat = 48.10,
            endLng = 11.50,
            maxDispLat = 48.12,
            maxDispLng = 11.52,
            refDistance = 5000.0,
            hitCount = 2,
            bSportType = BSportType.BIKE
        )
        dbManager.insertCluster(bikeCluster)

        // Toggle ON -> RUN workout on BIKE cluster receives +5.0 penalty and is rejected
        every { TrainingApplication.useSportTypeForClustering() } returns true
        val rejected = engine.suggestCluster(
            start = LatLng(48.10, 11.50),
            end = LatLng(48.10, 11.50),
            apex = LatLng(48.12, 11.52),
            distance = 5000.0,
            workoutSportType = BSportType.RUN
        )
        assertNull("Sport-aware ON must keep RUN and BIKE separate", rejected)

        // Toggle OFF -> 0.0 sport penalty allows merging
        every { TrainingApplication.useSportTypeForClustering() } returns false
        val merged = engine.suggestCluster(
            start = LatLng(48.10, 11.50),
            end = LatLng(48.10, 11.50),
            apex = LatLng(48.12, 11.52),
            distance = 5000.0,
            workoutSportType = BSportType.RUN
        )
        assertNotNull("Sport-aware OFF must allow merging regardless of sport", merged)
    }

    @Test
    fun calculateSimilarity_appliesTwoPointPenaltyForRunVsUnknownCluster_TST_SET_035() {
        // TST-SET-035
        val unknownCluster = WorkoutCluster(
            id = 11L,
            name = "Mystery Loop",
            probableSportId = 0L,
            startLat = 48.10,
            startLng = 11.50,
            endLat = 48.10,
            endLng = 11.50,
            maxDispLat = 48.12,
            maxDispLng = 11.52,
            refDistance = 5000.0,
            hitCount = 1,
            bSportType = BSportType.UNKNOWN
        )
        dbManager.insertCluster(unknownCluster)

        every { TrainingApplication.useSportTypeForClustering() } returns true
        val score = engine.calculateSimilarity(
            start = LatLng(48.10, 11.50),
            end = LatLng(48.10, 11.50),
            apex = LatLng(48.12, 11.52),
            distance = 5000.0,
            cluster = unknownCluster,
            workoutSportType = BSportType.RUN
        )
        assertTrue("RUN vs UNKNOWN must incur +2.0 penalty, was $score", score >= 2.0)

        val match = engine.suggestCluster(
            start = LatLng(48.10, 11.50),
            end = LatLng(48.10, 11.50),
            apex = LatLng(48.12, 11.52),
            distance = 5000.0,
            workoutSportType = BSportType.RUN
        )
        assertNull("RUN workout must NOT merge into UNKNOWN cluster when toggle is ON", match)
    }

    @Test
    fun learnFromWorkout_reusesExistingNormalizedNameWithoutCreatingDuplicate_TST_SET_047() {
        // TST-SET-047
        val firstId = engine.learnFromWorkout(
            start = LatLng(48.100, 11.500),
            end = LatLng(48.100, 11.500),
            apex = LatLng(48.120, 11.520),
            distance = 5000.0,
            userSpecifiedName = "Route A",
            userSportId = 2L
        )
        val first = dbManager.getClusterById(firstId)!!
        dbManager.updateCluster(first.copy(hitCount = 1))

        // Second import with same route name "Route A #2" and slightly shifted bounds/anchors
        val secondId = engine.learnFromWorkout(
            start = LatLng(48.105, 11.505),
            end = LatLng(48.105, 11.505),
            apex = LatLng(48.125, 11.525),
            distance = 6500.0,
            userSpecifiedName = "Route A #2",
            userSportId = 2L,
            minLat = 48.095,
            minLng = 11.495,
            maxLat = 48.130,
            maxLng = 11.530
        )

        assertEquals(firstId, secondId)
        val allClusters = dbManager.getAllClusters()
        assertEquals("Idempotent route cluster creation must not create var 2", 1, allClusters.size)
        assertEquals("Route A", allClusters.first().name)
        assertEquals(48.095, allClusters.first().minLat!!, 1e-6)
        assertEquals(48.130, allClusters.first().maxLat!!, 1e-6)
    }

    @Test
    fun assignClusterToWorkout_capsPreviewPathsAtFiveTracks_TST_SET_026() {
        // TST-SET-026
        val existingPreviews = listOf("p1", "p2", "p3", "p4", "p5")
        val clusterId = dbManager.insertCluster(
            WorkoutCluster(
                name = "Preview Cap Loop",
                probableSportId = 2L,
                startLat = 48.1,
                startLng = 11.5,
                endLat = 48.1,
                endLng = 11.5,
                maxDispLat = 48.12,
                maxDispLng = 11.52,
                refDistance = 5000.0,
                hitCount = 5,
                bSportType = BSportType.RUN,
                previewPaths = existingPreviews
            )
        )
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
        val workoutId = summariesDb.insertOrThrow(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null,
            android.content.ContentValues().apply {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, "Latest Run")
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME, "run_6")
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID, 2L)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START, "2026-09-02 08:00:00")
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED, 1)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, -1L)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE, "p6_newest")
            }
        )

        engine.assignClusterToWorkout(context, workoutId, clusterId)

        val updated = dbManager.getClusterById(clusterId)!!
        assertEquals(5, updated.previewPaths.size)
        assertEquals("p6_newest", updated.previewPaths.first())
    }
}
