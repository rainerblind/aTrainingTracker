package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteCorridorClassifierTest {

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
    fun classifyBearing_cardinalDirections_mapsDegreesAccurately() {
        assertEquals(GatewayDirection.NORTH, RouteCorridorClassifier.classifyBearing(0.0))
        assertEquals(GatewayDirection.NORTH, RouteCorridorClassifier.classifyBearing(355.0))
        assertEquals(GatewayDirection.NORTH, RouteCorridorClassifier.classifyBearing(10.0))

        assertEquals(GatewayDirection.NORTHEAST, RouteCorridorClassifier.classifyBearing(45.0))
        assertEquals(GatewayDirection.EAST, RouteCorridorClassifier.classifyBearing(90.0))
        assertEquals(GatewayDirection.SOUTHEAST, RouteCorridorClassifier.classifyBearing(135.0))
        assertEquals(GatewayDirection.SOUTH, RouteCorridorClassifier.classifyBearing(180.0))
        assertEquals(GatewayDirection.SOUTHWEST, RouteCorridorClassifier.classifyBearing(225.0))
        assertEquals(GatewayDirection.WEST, RouteCorridorClassifier.classifyBearing(270.0))
        assertEquals(GatewayDirection.NORTHWEST, RouteCorridorClassifier.classifyBearing(315.0))
    }

    @Test
    fun classifyGatewayHeading_emptyOrSinglePoint_returnsUnknown() {
        assertEquals(GatewayDirection.UNKNOWN, RouteCorridorClassifier.classifyGatewayHeading(emptyList()))
        assertEquals(
            GatewayDirection.UNKNOWN,
            RouteCorridorClassifier.classifyGatewayHeading(listOf(pt(48.0, 9.0)))
        )
    }

    @Test
    fun classifyGatewayHeading_shortOrStationaryPath_returnsUnknown() {
        // Less than 50 meters displacement
        val path = listOf(
            pt(48.0, 9.0),
            pt(48.0001, 9.0001) // ~13 meters away
        )
        assertEquals(GatewayDirection.UNKNOWN, RouteCorridorClassifier.classifyGatewayHeading(path))
    }

    @Test
    fun classifyGatewayHeading_outboundVectorNorth_classifiesNorth() {
        // Heading straight North by 0.01 deg latitude (~1.1 km)
        val path = listOf(
            pt(48.0, 9.0),
            pt(48.005, 9.0),
            pt(48.01, 9.0)
        )
        val gateway = RouteCorridorClassifier.classifyGatewayHeading(path)
        assertEquals(GatewayDirection.NORTH, gateway)
    }

    @Test
    fun classifyGatewayHeading_outboundVectorEast_classifiesEast() {
        // Heading East by 0.02 deg longitude (~1.5 km)
        val path = listOf(
            pt(48.0, 9.0),
            pt(48.0, 9.01),
            pt(48.0, 9.02)
        )
        val gateway = RouteCorridorClassifier.classifyGatewayHeading(path)
        assertEquals(GatewayDirection.EAST, gateway)
    }

    @Test
    fun groupRoutesByCorridor_multipleRoutes_clustersCorrectly() {
        val routeNorth = RouteWithPath(
            summary = dummySummary(1L, "North Loop"),
            path = listOf(
                pt(48.0, 9.0),
                pt(48.01, 9.0)
            )
        )
        val routeEast = RouteWithPath(
            summary = dummySummary(2L, "East Loop"),
            path = listOf(
                pt(48.0, 9.0),
                pt(48.0, 9.02)
            )
        )
        val routeNorth2 = RouteWithPath(
            summary = dummySummary(3L, "North Pass"),
            path = listOf(
                pt(48.0, 9.0),
                pt(48.015, 9.001)
            )
        )

        val groups = RouteCorridorClassifier.groupRoutesByCorridor(listOf(routeNorth, routeEast, routeNorth2))

        assertEquals(2, groups[GatewayDirection.NORTH]?.size)
        assertEquals(1, groups[GatewayDirection.EAST]?.size)
        assertEquals(null, groups[GatewayDirection.SOUTH])
    }
}
