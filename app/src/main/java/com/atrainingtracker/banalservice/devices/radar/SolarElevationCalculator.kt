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

package com.atrainingtracker.banalservice.devices.radar

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Astronomical solar elevation angle calculator and solar profile classifier (REQ-CON-034.2, ATT-5104).
 *
 * Computes solar elevation angle from latitude, longitude, and epoch timestamp using the standard
 * solar position algorithm. Classifies environment into [SolarProfile.DAY] (elevation >= -3.0 deg)
 * vs [SolarProfile.DUSK_NIGHT] (elevation < -3.0 deg) as primary fallback for tunnel/dusk detection
 * when ambient light sensor is gated or occluded in a jersey pocket.
 */
object SolarElevationCalculator {

    /** Civil twilight boundary threshold for cycling lighting profile transitions (-3.0 degrees) */
    const val CIVIL_TWILIGHT_THRESHOLD_DEG = -3.0

    enum class SolarProfile {
        DAY,
        DUSK_NIGHT
    }

    /**
     * Calculates the solar elevation angle in degrees for the given geographic coordinates and epoch time.
     *
     * @param latitudeDeg Latitude in degrees (-90.0 .. +90.0).
     * @param longitudeDeg Longitude in degrees (-180.0 .. +180.0).
     * @param epochTimeMs Milliseconds since Unix epoch (UTC).
     * @return Solar elevation angle in degrees (-90.0 .. +90.0).
     */
    @JvmStatic
    fun calculateSolarElevation(latitudeDeg: Double, longitudeDeg: Double, epochTimeMs: Long): Double {
        val jd = epochTimeMs / 86400000.0 + 2440587.5
        val n = jd - 2451545.0

        val l = (280.460 + 0.9856474 * n) % 360.0
        val gRad = Math.toRadians((357.528 + 0.9856003 * n) % 360.0)

        val lambdaSunDeg = (l + 1.915 * sin(gRad) + 0.020 * sin(2.0 * gRad)) % 360.0
        val lambdaSunRad = Math.toRadians(lambdaSunDeg)

        val epsRad = Math.toRadians(23.439 - 0.0000004 * n)

        val sinDelta = sin(epsRad) * sin(lambdaSunRad)
        val deltaRad = asin(max(-1.0, min(1.0, sinDelta)))

        val alphaSunDeg = (Math.toDegrees(atan2(cos(epsRad) * sin(lambdaSunRad), cos(lambdaSunRad))) + 360.0) % 360.0

        val gmstDeg = (280.46061837 + 360.98564736629 * n) % 360.0
        val lstDeg = (gmstDeg + longitudeDeg + 360.0) % 360.0

        var hDeg = (lstDeg - alphaSunDeg) % 360.0
        if (hDeg > 180.0) hDeg -= 360.0
        if (hDeg < -180.0) hDeg += 360.0

        val hRad = Math.toRadians(hDeg)
        val latRad = Math.toRadians(latitudeDeg)

        val sinH = sin(latRad) * sin(deltaRad) + cos(latRad) * cos(deltaRad) * cos(hRad)
        return Math.toDegrees(asin(max(-1.0, min(1.0, sinH))))
    }

    /**
     * Determines whether the given location and time correspond to [SolarProfile.DAY] or [SolarProfile.DUSK_NIGHT].
     */
    @JvmStatic
    fun getSolarProfile(latitudeDeg: Double, longitudeDeg: Double, epochTimeMs: Long): SolarProfile {
        val elevation = calculateSolarElevation(latitudeDeg, longitudeDeg, epochTimeMs)
        return if (elevation >= CIVIL_TWILIGHT_THRESHOLD_DEG) {
            SolarProfile.DAY
        } else {
            SolarProfile.DUSK_NIGHT
        }
    }
}
