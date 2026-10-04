/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.routes

import android.content.Context
import android.location.Location
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.*

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalCoroutinesApi::class)
class TurnAlertTriggersTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockTuningDataStore = mockk<TuningPreferencesDataStore>(relaxed = true)
    private val mockAudioAlertManager = mockk<NavigationAudioAlertManager>(relaxed = true)

    private val activeRouteIdFlow = MutableStateFlow<Long?>(null)
    private val allRoutesFlow = MutableStateFlow<List<RouteWithPath>>(emptyList())
    private val currentLocationFlow = MutableStateFlow<LatLng?>(null)
    private val tuningConfigFlow = MutableStateFlow(TuningConfig())

    @Before
    fun setUp() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            results[0] = (6371000 * c).toFloat()
        }

        every { mockRoutesRepo.activeNavigatedRouteId } returns activeRouteIdFlow
        every { mockRoutesRepo.allRoutes } returns allRoutesFlow
        every { mockBanalRepo.currentLocation } returns currentLocationFlow
        every { mockBanalRepo.currentSpeed } returns MutableStateFlow(5.0)
        every { mockBanalRepo.currentBearing } returns MutableStateFlow(0.0)
        every { mockTuningDataStore.tuningConfigFlow } returns tuningConfigFlow
    }

    @After
    fun tearDown() {
        unmockkStatic(Location::class)
    }

    @Test
    fun testAlertSequence_approachingTurnNowOffRouteAndRecovery() = runTest(testDispatcher) {
        val summary = RouteSummary(
            id = 42L,
            externalId = "route_42",
            name = "Test Route",
            description = "Route for testing alerts",
            isSelected = true,
            distance = 1000.0,
            elevationGain = 0.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0),
            PathPoint(distance = 500.0, latLng = LatLng(48.0045, 11.0), altitude = 0.0),
            PathPoint(distance = 1000.0, latLng = LatLng(48.009, 11.0), altitude = 0.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                id = 100L,
                routeId = 42L,
                latLng = LatLng(48.0045, 11.0),
                type = WaypointType.TURN_RIGHT,
                distanceFromStart = 500.0,
                name = "Turn Right onto Summit Track"
            )
        )
        val route = RouteWithPath(summary, path, waypoints)
        allRoutesFlow.value = listOf(route)
        activeRouteIdFlow.value = 42L

        val repository = TurnByTurnNavigationRepository(
            context = mockContext,
            routesRepository = mockRoutesRepo,
            banalRepository = mockBanalRepo,
            tuningDataStore = mockTuningDataStore,
            audioAlertManager = mockAudioAlertManager,
            navigationEngine = TurnByTurnNavigationEngine(),
            scope = testScope
        )

        // 1. Initial position far away (200m along route): 300m away from cue at 500m
        currentLocationFlow.value = LatLng(48.0018, 11.0)
        verify(exactly = 0) { mockAudioAlertManager.playApproachAlert() }

        // 2. Approach: Move to 420m (80m from cue <= 100m default countdown)
        currentLocationFlow.value = LatLng(48.00378, 11.0)
        verify(exactly = 1) { mockAudioAlertManager.playApproachAlert() }

        // 3. Turn Now: Move to 490m (10m from cue <= 25m)
        currentLocationFlow.value = LatLng(48.00441, 11.0)
        verify(exactly = 1) { mockAudioAlertManager.playTurnNowAlert() }

        // 4. Off-route deviation: 3 distinct fixes > 50m off corridor to trigger debounce
        currentLocationFlow.value = LatLng(48.0045, 11.0020)
        currentLocationFlow.value = LatLng(48.0045, 11.0021)
        currentLocationFlow.value = LatLng(48.0045, 11.0022)
        verify(exactly = 1) { mockAudioAlertManager.playOffRouteAlert() }

        // 5. Back on route recovery:
        currentLocationFlow.value = LatLng(48.0045, 11.0001) // within 10m
        verify(exactly = 1) { mockAudioAlertManager.playBackOnRouteAlert() }

        repository.release()
    }

    @Test
    fun testAlertSuppression_whenAudioAlertsDisabledInConfig() = runTest(testDispatcher) {
        tuningConfigFlow.value = TuningConfig(turnAudioAlertsEnabled = false)

        val summary = RouteSummary(
            id = 55L,
            externalId = "route_55",
            name = "Silent Route",
            description = "Route with muted alerts",
            isSelected = true,
            distance = 1000.0,
            elevationGain = 0.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0),
            PathPoint(distance = 1000.0, latLng = LatLng(48.009, 11.0), altitude = 0.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                id = 101L,
                routeId = 55L,
                latLng = LatLng(48.0045, 11.0),
                type = WaypointType.TURN_LEFT,
                distanceFromStart = 500.0
            )
        )
        val route = RouteWithPath(summary, path, waypoints)
        allRoutesFlow.value = listOf(route)
        activeRouteIdFlow.value = 55L

        val repository = TurnByTurnNavigationRepository(
            context = mockContext,
            routesRepository = mockRoutesRepo,
            banalRepository = mockBanalRepo,
            tuningDataStore = mockTuningDataStore,
            audioAlertManager = mockAudioAlertManager,
            navigationEngine = TurnByTurnNavigationEngine(),
            scope = testScope
        )

        // Move to approach zone (80m from cue)
        currentLocationFlow.value = LatLng(48.00378, 11.0)

        // Verify audio alert was suppressed
        verify(exactly = 0) { mockAudioAlertManager.playApproachAlert() }

        repository.release()
    }
}
