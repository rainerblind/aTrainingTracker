package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.routes.GatewayDirection
import com.atrainingtracker.trainingtracker.routes.RouteCorridorClassifier
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCorridorUiIntegrationTest {

    private fun pt(lat: Double, lng: Double, alt: Double = 400.0, dist: Double = 0.0) =
        PathPoint(dist, LatLng(lat, lng), alt)

    private fun dummySummary(id: Long, name: String) = RouteSummary(
        id = id,
        externalId = "ext_$id",
        name = name,
        description = "",
        distance = 10000.0,
        elevationGain = 100.0,
        bSportType = BSportType.BIKE,
        isSelected = false,
        source = RouteSource.LOCAL_GPX
    )

    @Test
    fun corridorGrouping_whenEnabled_filtersRoutesBySelectedGateway() {
        val tuningConfig = TuningConfig(corridorGroupingEnabled = true)

        val northRoute = RouteWithPath(
            summary = dummySummary(1L, "North Mountain"),
            path = listOf(
                pt(48.0, 9.0),
                pt(48.01, 9.0)
            )
        )
        val southRoute = RouteWithPath(
            summary = dummySummary(2L, "South River"),
            path = listOf(
                pt(48.0, 9.0),
                pt(47.99, 9.0)
            )
        )

        val allRoutes = listOf(northRoute, southRoute)

        // When ALL is selected, both routes remain visible
        val selectedGatewayAll = GatewayDirection.ALL
        val routesAll = if (tuningConfig.corridorGroupingEnabled && selectedGatewayAll != GatewayDirection.ALL) {
            allRoutes.filter { RouteCorridorClassifier.classifyGatewayHeading(it.path) == selectedGatewayAll }
        } else {
            allRoutes
        }
        assertEquals(2, routesAll.size)

        // When SOUTH is selected, only southRoute remains visible
        val selectedGatewaySouth = GatewayDirection.SOUTH
        val routesSouth = if (tuningConfig.corridorGroupingEnabled && selectedGatewaySouth != GatewayDirection.ALL) {
            allRoutes.filter { RouteCorridorClassifier.classifyGatewayHeading(it.path) == selectedGatewaySouth }
        } else {
            allRoutes
        }
        assertEquals(1, routesSouth.size)
        assertEquals("South River", routesSouth[0].summary.name)
    }

    @Test
    fun corridorGrouping_whenDisabled_preservesFlatListRegardlessOfGateway() {
        val tuningConfig = TuningConfig(corridorGroupingEnabled = false)

        val northRoute = RouteWithPath(
            summary = dummySummary(1L, "North Mountain"),
            path = listOf(
                pt(48.0, 9.0),
                pt(48.01, 9.0)
            )
        )
        val southRoute = RouteWithPath(
            summary = dummySummary(2L, "South River"),
            path = listOf(
                pt(48.0, 9.0),
                pt(47.99, 9.0)
            )
        )

        val allRoutes = listOf(northRoute, southRoute)
        val selectedGateway = GatewayDirection.SOUTH

        val finalRoutes = if (tuningConfig.corridorGroupingEnabled && selectedGateway != GatewayDirection.ALL) {
            allRoutes.filter { RouteCorridorClassifier.classifyGatewayHeading(it.path) == selectedGateway }
        } else {
            allRoutes
        }

        // Feature is disabled, so both routes are displayed in standard flat list order
        assertEquals(2, finalRoutes.size)
    }

    @Test
    fun tuningConfig_defaults_areBothEnabled() {
        val config = TuningConfig()
        assertTrue("Corridor grouping should be enabled by default", config.corridorGroupingEnabled)
        assertTrue("Focused thumbnail zoom should be enabled by default", config.focusedThumbnailZoomEnabled)
    }
}
