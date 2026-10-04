package com.atrainingtracker.trainingtracker.routes

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin


/**
 * Compass-based exit gateway directions for corridor classification.
 */
enum class GatewayDirection(@StringRes val titleResId: Int) {
    ALL(R.string.gateway_all),
    NORTH(R.string.gateway_north),
    NORTHEAST(R.string.gateway_northeast),
    EAST(R.string.gateway_east),
    SOUTHEAST(R.string.gateway_southeast),
    SOUTH(R.string.gateway_south),
    SOUTHWEST(R.string.gateway_southwest),
    WEST(R.string.gateway_west),
    NORTHWEST(R.string.gateway_northwest),
    UNKNOWN(R.string.gateway_unknown)
}

/**
 * Pure algorithmic classifier evaluating departure corridors and clustering routes by exit gateway.
 *
 * Implements requirement REQ-MAP-030.
 */
object RouteCorridorClassifier {

    private const val MIN_NET_DISPLACEMENT_METERS = 50.0
    private const val DEFAULT_WINDOW_METERS = 1000.0

    /**
     * Classifies a normalized azimuth bearing in degrees [0.0, 360.0) into a [GatewayDirection].
     */
    fun classifyBearing(bearingDegrees: Double): GatewayDirection {
        val normalized = (bearingDegrees % 360.0 + 360.0) % 360.0
        return when {
            normalized >= 337.5 || normalized < 22.5 -> GatewayDirection.NORTH
            normalized < 67.5 -> GatewayDirection.NORTHEAST
            normalized < 112.5 -> GatewayDirection.EAST
            normalized < 157.5 -> GatewayDirection.SOUTHEAST
            normalized < 202.5 -> GatewayDirection.SOUTH
            normalized < 247.5 -> GatewayDirection.SOUTHWEST
            normalized < 292.5 -> GatewayDirection.WEST
            normalized < 337.5 -> GatewayDirection.NORTHWEST
            else -> GatewayDirection.UNKNOWN
        }
    }

    /**
     * Determines departure gateway heading by analyzing initial outbound trajectory along polyline.
     *
     * @param path List of route trackpoints
     * @param initialWindowMeters Target forward distance window (default 1000m)
     * @return Classified [GatewayDirection]
     */
    fun classifyGatewayHeading(
        path: List<PathPoint>,
        initialWindowMeters: Double = DEFAULT_WINDOW_METERS
    ): GatewayDirection {
        if (path.size < 2) return GatewayDirection.UNKNOWN

        val start = path[0]
        var targetPoint = path[1]
        var cumulativeDist = 0.0

        for (i in 0 until path.size - 1) {
            val p1 = path[i].latLng
            val p2 = path[i + 1].latLng
            val segDist = haversineDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            cumulativeDist += segDist
            targetPoint = path[i + 1]

            if (cumulativeDist >= initialWindowMeters) {
                break
            }
        }

        val startPos = start.latLng
        val targetPos = targetPoint.latLng
        val netDisplacement = haversineDistanceMeters(
            startPos.latitude, startPos.longitude, targetPos.latitude, targetPos.longitude
        )

        if (netDisplacement < MIN_NET_DISPLACEMENT_METERS) {
            return GatewayDirection.UNKNOWN
        }

        val bearing = calculateInitialBearing(startPos.latitude, startPos.longitude, targetPos.latitude, targetPos.longitude)
        return classifyBearing(bearing)
    }

    /**
     * Computes the initial forward azimuth bearing (degrees clockwise from True North) from (lat1, lng1) to (lat2, lng2).
     */
    fun calculateInitialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        return (Math.toDegrees(theta) + 360.0) % 360.0
    }

    /**
     * Calculates the great-circle geodesic distance in meters between two coordinates.
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * Clusters routes into groups according to their exit gateway direction.
     */
    fun groupRoutesByCorridor(routes: List<RouteWithPath>): Map<GatewayDirection, List<RouteWithPath>> {
        val groups = mutableMapOf<GatewayDirection, MutableList<RouteWithPath>>()

        for (route in routes) {
            val gateway = classifyGatewayHeading(route.path)
            groups.getOrPut(gateway) { mutableListOf() }.add(route)
        }

        return groups
    }
}
