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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ForkNavigationRepositoryTest {

    private lateinit var mockRoutesRepo: RoutesRepository
    private lateinit var allRoutesFlow: MutableStateFlow<List<RouteWithPath>>
    private lateinit var activeNavRouteIdFlow: MutableStateFlow<Long?>
    private lateinit var repository: ForkNavigationRepository

    private fun pt(lat: Double, lng: Double, dist: Double = 0.0) =
        PathPoint(dist, LatLng(lat, lng), 400.0)

    private fun dummySummary(id: Long, name: String, dist: Double = 10000.0, elev: Double = 150.0) = RouteSummary(
        id = id,
        externalId = "ext_$id",
        name = name,
        description = "",
        distance = dist,
        elevationGain = elev,
        bSportType = BSportType.BIKE,
        isSelected = false,
        source = RouteSource.LOCAL_GPX
    )

    private lateinit var route1: RouteWithPath
    private lateinit var route2: RouteWithPath

    @Before
    fun setUp() {
        mockRoutesRepo = mockk(relaxed = true)
        allRoutesFlow = MutableStateFlow(emptyList())
        activeNavRouteIdFlow = MutableStateFlow(null)

        every { mockRoutesRepo.allRoutes } returns allRoutesFlow
        every { mockRoutesRepo.activeNavigatedRouteId } returns activeNavRouteIdFlow

        // Route 1 (turns Left/West at ~1000m) and Route 2 (turns Right/East at ~1000m)
        val sharedPoints = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5045, 9.0000, 500.0),
            pt(48.5090, 9.0000, 1000.0)
        )
        val route1Path = sharedPoints + listOf(
            pt(48.5120, 8.9950, 1500.0),
            pt(48.5150, 8.9900, 2000.0)
        )
        val route2Path = sharedPoints + listOf(
            pt(48.5120, 9.0050, 1500.0),
            pt(48.5150, 9.0100, 2000.0)
        )
        route1 = RouteWithPath(dummySummary(1L, "Left Route", 38000.0, 420.0), route1Path)
        route2 = RouteWithPath(dummySummary(2L, "Right Route", 65000.0, 680.0), route2Path)

        allRoutesFlow.value = listOf(route1, route2)

        repository = ForkNavigationRepository(mockRoutesRepo)
    }

    @Test
    fun onLocationChanged_approachingFork_triggersAlertState() {
        // Approaching fork (at 800m, fork is at 1000m -> ~200m away)
        val pos = LatLng(48.5072, 9.0000)
        repository.onLocationChanged(pos)

        val state = repository.forkDecisionState.value
        assertNotNull(state)
        assertEquals(2, state?.branches?.size)
    }

    @Test
    fun onLocationChanged_pastForkOnBranchA_autoBindsRouteA() {
        // First approach fork to trigger alert
        val posApproach = LatLng(48.5072, 9.0000)
        repository.onLocationChanged(posApproach)
        assertNotNull(repository.forkDecisionState.value)

        // Now move > 50m down branch 1 (48.5120, 8.9950 is ~500m past fork on route 1, far from route 2)
        val posBranch1 = LatLng(48.5120, 8.9950)
        repository.onLocationChanged(posBranch1)

        // Verifies auto-binding of route 1
        verify { mockRoutesRepo.setActiveNavigatedRoute(1L) }
        assertNull(repository.forkDecisionState.value)
    }

    @Test
    fun onLocationChanged_deviatesFromBoth_dismissesPrompt() {
        // Approach fork
        val posApproach = LatLng(48.5072, 9.0000)
        repository.onLocationChanged(posApproach)
        assertNotNull(repository.forkDecisionState.value)

        // Athlete turns completely off route (e.g. 500m West at latitude 48.5072)
        val posDeviate = LatLng(48.5072, 8.9900)
        repository.onLocationChanged(posDeviate)

        assertNull(repository.forkDecisionState.value)
    }

    @Test
    fun selectRouteManually_bindsRouteImmediatelyAndClearsState() {
        // Approach fork
        val posApproach = LatLng(48.5072, 9.0000)
        repository.onLocationChanged(posApproach)
        assertNotNull(repository.forkDecisionState.value)

        repository.selectRouteManually(2L)

        verify { mockRoutesRepo.setActiveNavigatedRoute(2L) }
        assertNull(repository.forkDecisionState.value)
    }

    @Test
    fun dismissPrompt_clearsAlertWithoutBinding() {
        // Approach fork
        val posApproach = LatLng(48.5072, 9.0000)
        repository.onLocationChanged(posApproach)
        assertNotNull(repository.forkDecisionState.value)

        repository.dismissPrompt()

        assertNull(repository.forkDecisionState.value)
    }
}
