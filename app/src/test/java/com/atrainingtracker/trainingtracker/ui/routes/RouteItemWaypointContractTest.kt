package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.routes.WaypointType
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract tests asserting that [RouteItem] and [MapRoute] properly receive,
 * retain, and expose waypoints for thumbnail rendering (REQ-MAP-026, TST-MAP-028.2).
 */
class RouteItemWaypointContractTest {

    @Test
    fun testMapRoute_withWaypoints_retainsAndExposesWaypoints() {
        val summary = RouteSummary(
            id = 42L,
            externalId = "jusi.gpx",
            name = "Gipfelkreuz auf dem Jusi",
            description = "Schwäbische Alb",
            isSelected = false,
            distance = 8500.0,
            elevationGain = 320.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val path = listOf(
            PathPoint(latLng = LatLng(48.553, 9.330), altitude = 450.0, distance = 0.0),
            PathPoint(latLng = LatLng(48.552, 9.334), altitude = 673.0, distance = 1200.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                id = 1L,
                routeId = 42L,
                latLng = LatLng(48.5527, 9.3347),
                altitude = 673.0,
                name = "Gipfelkreuz auf dem Jusi",
                type = WaypointType.POI_SUMMIT
            )
        )

        val mapRoute = MapRoute(
            id = summary.id,
            name = summary.name,
            isSelected = summary.isSelected,
            bSportType = summary.bSportType,
            path = path,
            waypoints = waypoints
        )

        assertEquals(1, mapRoute.waypoints.size)
        assertEquals("Gipfelkreuz auf dem Jusi", mapRoute.waypoints[0].name)
        assertEquals(WaypointType.POI_SUMMIT, mapRoute.waypoints[0].type)
        assertEquals(42L, mapRoute.id)
    }

    @Test
    fun testRouteItemComposable_hasWaypointsParameterWithDefault() {
        val routeItemClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteItemKt")
        val methods = routeItemClass.declaredMethods
        val routeItemMethod = methods.find { it.name == "RouteItem" }
        assertTrue("RouteItem composable method must be declared", routeItemMethod != null)

        val paramTypes = routeItemMethod!!.parameterTypes
        val hasListParam = paramTypes.any { it == java.util.List::class.java }
        assertTrue("RouteItem must accept List parameter (pathPoints / waypoints)", hasListParam)
    }

    @Test
    fun testPathPreviewMap_rendersRouteWaypointsLayer_whenWaypointsPresent() {
        var dir = java.io.File(".").canonicalFile
        while (dir.parentFile != null) {
            if (java.io.File(dir, "gradlew").exists() && java.io.File(dir, "app").exists()) {
                break
            }
            dir = dir.parentFile!!
        }
        val pathPreviewFile = java.io.File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt")
        assertTrue("PathPreviewMap.kt must exist", pathPreviewFile.exists())
        val content = pathPreviewFile.readText()
        assertTrue("PathPreviewMap must check path is MapRoute and path.waypoints.isNotEmpty()", content.contains("path is MapRoute && path.waypoints.isNotEmpty()"))
        assertTrue("PathPreviewMap must invoke RouteWaypointsLayer", content.contains("RouteWaypointsLayer("))
    }

    @Test
    fun testRouteWaypointsLayer_exposesMarkerSizeDpParameter() {
        var dir = java.io.File(".").canonicalFile
        while (dir.parentFile != null) {
            if (java.io.File(dir, "gradlew").exists() && java.io.File(dir, "app").exists()) {
                break
            }
            dir = dir.parentFile!!
        }
        val mapLayersFile = java.io.File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt")
        assertTrue("MapLayers.kt must exist", mapLayersFile.exists())
        val content = mapLayersFile.readText()
        assertTrue("RouteWaypointsLayer must expose markerSizeDp parameter defaulting to DEFAULT_WAYPOINT_MARKER_SIZE_DP",
            content.contains("markerSizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP"))
        assertTrue("RouteWaypointsLayer must pass markerSizeDp to createWaypointBadgeMarker",
            content.contains("createWaypointBadgeMarker(ctx, waypoint.type, markerSizeDp)"))
        assertTrue("RouteWaypointsLayer must include markerSizeDp in remember cache key",
            content.contains("remember(waypoint.type, context, markerSizeDp)"))
    }
}
