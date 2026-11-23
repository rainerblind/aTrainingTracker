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

package com.atrainingtracker.trainingtracker.geo

import com.atrainingtracker.trainingtracker.routes.GeoUtils
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos

/**
 * Result of orthogonal coordinate projection onto a single line segment (REQ-GEO-003).
 *
 * @property projectedLatLng Closest coordinate clamped onto the segment `[start, end]`.
 * @property t Normalized linear interpolation parameter along the segment in `[0.0, 1.0]`.
 * @property crossTrackDistanceMeters Geodesic distance in meters from the query point to [projectedLatLng].
 */
data class SegmentProjection(
    val projectedLatLng: LatLng,
    val t: Double,
    val crossTrackDistanceMeters: Double
)

/**
 * Canonical result of orthogonal coordinate projection onto a polyline trajectory
 * (REQ-GEO-001, REQ-GEO-002, REQ-GEO-003).
 *
 * @property projectedLatLng Closest coordinate on the polyline segment.
 * @property segmentIndex Index of the polyline segment `[i]` where projection fell.
 * @property t Linear interpolation parameter along the segment, clamped to `[0.0, 1.0]`.
 * @property alongTrackDistance Cumulative distance along the polyline from track start to [projectedLatLng] in meters.
 * @property crossTrackDistanceMeters Geodesic perpendicular distance from user location to [projectedLatLng] in meters.
 * @property progressFraction Normalized progress along the polyline in `[0.0f, 1.0f]`.
 * @property interpolatedAltitude Linear interpolated altitude at the projected point in meters, if available.
 * @property instantaneousGradePercent Instantaneous grade of the active segment as a percentage, if available.
 */
data class PolylineProjection(
    val projectedLatLng: LatLng,
    val segmentIndex: Int,
    val t: Double,
    val alongTrackDistance: Double,
    val crossTrackDistanceMeters: Double,
    val progressFraction: Float,
    val interpolatedAltitude: Double?,
    val instantaneousGradePercent: Double?
)

/**
 * Pure-JVM geodesic projection and elevation-gain math primitives shared across navigation,
 * route detection, segment matching, return-corridor snapping, climb deduplication, and
 * GPX/TCX route parsing (REQ-GEO-003, ATT-8133).
 *
 * Uses local equirectangular `cos(meanLatitude)` longitude scaling to eliminate mid- and
 * high-latitude East-West projection distortion while maintaining zero Android SDK dependencies.
 */
object GeoMath {

    /** Canonical dead-band hysteresis threshold in meters for route elevation gain filtering. */
    const val DEFAULT_ELEVATION_GAIN_HYSTERESIS_METERS = 2.0

    private const val MIN_COS_LAT = 1e-6
    private const val MIN_SEGMENT_LEN_SQ = 1e-14

    /**
     * Orthogonally projects `(pointLat, pointLng)` onto the geographic line segment
     * `[(startLat, startLng), (endLat, endLng)]` using local equirectangular `cos(meanLat)`
     * longitude scaling and Haversine cross-track distance (REQ-GEO-002, REQ-GEO-003).
     */
    @JvmStatic
    fun projectPointOntoSegment(
        pointLat: Double,
        pointLng: Double,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): SegmentProjection {
        if (!pointLat.isFinite() || !pointLng.isFinite() ||
            !startLat.isFinite() || !startLng.isFinite() ||
            !endLat.isFinite() || !endLng.isFinite()
        ) {
            val safeLat = if (startLat.isFinite()) startLat else 0.0
            val safeLng = if (startLng.isFinite()) startLng else 0.0
            return SegmentProjection(
                projectedLatLng = LatLng(safeLat, safeLng),
                t = 0.0,
                crossTrackDistanceMeters = Double.POSITIVE_INFINITY
            )
        }

        val meanLatRad = Math.toRadians((startLat + endLat) / 2.0)
        val cosPhi = cos(meanLatRad).coerceAtLeast(MIN_COS_LAT)

        val dX = (endLng - startLng) * cosPhi
        val dY = endLat - startLat
        val segLenSq = dX * dX + dY * dY

        val t = if (segLenSq.isFinite() && segLenSq > MIN_SEGMENT_LEN_SQ) {
            val pX = (pointLng - startLng) * cosPhi
            val pY = pointLat - startLat
            val rawT = (pX * dX + pY * dY) / segLenSq
            if (rawT.isFinite()) rawT.coerceIn(0.0, 1.0) else 0.0
        } else {
            0.0
        }

        val projLat = startLat + t * (endLat - startLat)
        val projLng = startLng + t * (endLng - startLng)
        val rawCrossTrack = GeoUtils.haversineDistanceMeters(pointLat, pointLng, projLat, projLng)
        val crossTrack = if (rawCrossTrack.isFinite()) rawCrossTrack else Double.POSITIVE_INFINITY

        return SegmentProjection(
            projectedLatLng = LatLng(projLat, projLng),
            t = t,
            crossTrackDistanceMeters = crossTrack
        )
    }

    /**
     * Convenience overload projecting [point] onto segment `[start, end]`.
     */
    @JvmStatic
    fun projectPointOntoSegment(
        point: LatLng,
        start: LatLng,
        end: LatLng
    ): SegmentProjection = projectPointOntoSegment(
        pointLat = point.latitude,
        pointLng = point.longitude,
        startLat = start.latitude,
        startLng = start.longitude,
        endLat = end.latitude,
        endLng = end.longitude
    )

    /**
     * Computes the perpendicular geodesic distance in meters from `(pointLat, pointLng)`
     * to the geographic segment `[start, end]` using equirectangular `cos(meanLat)` scaling.
     */
    @JvmStatic
    fun distanceToSegmentMeters(
        pointLat: Double,
        pointLng: Double,
        start: LatLng,
        end: LatLng
    ): Double = projectPointOntoSegment(
        pointLat = pointLat,
        pointLng = pointLng,
        startLat = start.latitude,
        startLng = start.longitude,
        endLat = end.latitude,
        endLng = end.longitude
    ).crossTrackDistanceMeters

    /**
     * Convenience overload computing perpendicular distance in meters from [point] to `[start, end]`.
     */
    @JvmStatic
    fun distanceToSegmentMeters(
        point: LatLng,
        start: LatLng,
        end: LatLng
    ): Double = projectPointOntoSegment(point, start, end).crossTrackDistanceMeters

    /**
     * Projects [location] orthogonally onto a polyline [path] (optionally constrained to
     * [segmentRange]) and returns the canonical [PolylineProjection], or `null` if [path] is empty.
     *
     * @param path Ordered list of [PathPoint] defining the trajectory.
     * @param location Query coordinate to project.
     * @param segmentRange Optional inclusive segment index range within `0..(path.size - 2)`.
     */
    @JvmStatic
    @JvmOverloads
    fun projectOntoPolyline(
        path: List<PathPoint>,
        location: LatLng,
        segmentRange: IntProgression? = null
    ): PolylineProjection? = projectOntoPolyline(
        path = path,
        pointLat = location.latitude,
        pointLng = location.longitude,
        segmentRange = segmentRange
    )

    /**
     * Projects `(pointLat, pointLng)` orthogonally onto [path] (optionally constrained to
     * [segmentRange]) without requiring a caller-allocated [LatLng].
     */
    @JvmStatic
    @JvmOverloads
    fun projectOntoPolyline(
        path: List<PathPoint>,
        pointLat: Double,
        pointLng: Double,
        segmentRange: IntProgression? = null
    ): PolylineProjection? {
        if (path.isEmpty() || !pointLat.isFinite() || !pointLng.isFinite()) return null
        if (path.size == 1) {
            val p0 = path.first()
            if (!p0.latLng.latitude.isFinite() || !p0.latLng.longitude.isFinite()) return null
            val d = GeoUtils.haversineDistanceMeters(
                pointLat, pointLng,
                p0.latLng.latitude, p0.latLng.longitude
            )
            if (!d.isFinite()) return null
            return PolylineProjection(
                projectedLatLng = p0.latLng,
                segmentIndex = 0,
                t = 0.0,
                alongTrackDistance = 0.0,
                crossTrackDistanceMeters = d,
                progressFraction = 0f,
                interpolatedAltitude = p0.altitude,
                instantaneousGradePercent = p0.slope
            )
        }

        val range = segmentRange ?: (0 until path.size - 1)
        var bestSegIdx = range.first
        var bestProjection: SegmentProjection? = null

        for (i in range) {
            val p1 = path[i].latLng
            val p2 = path[i + 1].latLng
            if (!p1.latitude.isFinite() || !p1.longitude.isFinite() ||
                !p2.latitude.isFinite() || !p2.longitude.isFinite()
            ) {
                continue
            }
            val proj = projectPointOntoSegment(
                pointLat = pointLat,
                pointLng = pointLng,
                startLat = p1.latitude,
                startLng = p1.longitude,
                endLat = p2.latitude,
                endLng = p2.longitude
            )
            if (!proj.crossTrackDistanceMeters.isFinite()) continue
            val currentBest = bestProjection
            if (currentBest == null || proj.crossTrackDistanceMeters < currentBest.crossTrackDistanceMeters) {
                bestProjection = proj
                bestSegIdx = i
            }
        }

        val winner = bestProjection ?: return null
        val p1 = path[bestSegIdx]
        val p2 = path[bestSegIdx + 1]
        val segDist = (p2.distance - p1.distance).coerceAtLeast(0.0)
        val originDist = path.first().distance
        val totalPathDist = (path.last().distance - originDist).coerceAtLeast(0.0)
        val alongTrackDist = ((p1.distance - originDist) + winner.t * segDist).coerceAtLeast(0.0)
        val progressFraction = if (totalPathDist > 0.0) {
            (alongTrackDist / totalPathDist).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        val alt1 = p1.altitude
        val alt2 = p2.altitude
        val segAlt = if (alt1 != null && alt2 != null) alt2 - alt1 else null
        val interpolatedAlt = if (alt1 != null && alt2 != null && segAlt != null) {
            alt1 + winner.t * segAlt
        } else {
            alt1 ?: alt2
        }
        val grade = if (segDist > 0.0 && segAlt != null) {
            (segAlt / segDist) * 100.0
        } else {
            p1.slope
        }

        return PolylineProjection(
            projectedLatLng = winner.projectedLatLng,
            segmentIndex = bestSegIdx,
            t = winner.t,
            alongTrackDistance = alongTrackDist,
            crossTrackDistanceMeters = winner.crossTrackDistanceMeters,
            progressFraction = progressFraction,
            interpolatedAltitude = interpolatedAlt,
            instantaneousGradePercent = grade
        )
    }

    /**
     * Computes cumulative positive elevation gain in meters over [altitudes] using a dead-band
     * hysteresis threshold of [hysteresisMeters] (default [DEFAULT_ELEVATION_GAIN_HYSTERESIS_METERS] = 2.0 m)
     * to suppress GPS/barometric micro-jitter consistently across GPX and TCX route imports (REQ-GEO-003).
     *
     * Non-finite (`NaN`, `Infinity`) and `null` samples are skipped safely.
     */
    @JvmStatic
    @JvmOverloads
    fun calculateElevationGain(
        altitudes: Iterable<Double?>,
        hysteresisMeters: Double = DEFAULT_ELEVATION_GAIN_HYSTERESIS_METERS
    ): Double {
        var gain = 0.0
        var lastAlt: Double? = null
        val threshold = if (!hysteresisMeters.isFinite()) {
            DEFAULT_ELEVATION_GAIN_HYSTERESIS_METERS
        } else {
            hysteresisMeters.coerceAtLeast(0.0)
        }

        for (alt in altitudes) {
            if (alt == null || !alt.isFinite()) continue
            val prev = lastAlt
            if (prev == null) {
                lastAlt = alt
                continue
            }
            val delta = alt - prev
            if (threshold == 0.0) {
                if (delta > 0.0) gain += delta
                lastAlt = alt
            } else if (delta >= threshold) {
                gain += delta
                lastAlt = alt
            } else if (delta <= -threshold) {
                lastAlt = alt
            }
        }
        return gain
    }

    /**
     * Computes cumulative positive elevation gain in meters along [points] using canonical
     * hysteresis filtering ([hysteresisMeters], default 2.0 m).
     */
    @JvmStatic
    @JvmOverloads
    fun calculatePathElevationGain(
        points: List<PathPoint>,
        hysteresisMeters: Double = DEFAULT_ELEVATION_GAIN_HYSTERESIS_METERS
    ): Double = calculateElevationGain(
        altitudes = Iterable {
            object : Iterator<Double?> {
                private var idx = 0
                override fun hasNext(): Boolean = idx < points.size
                override fun next(): Double? = points[idx++].altitude
            }
        },
        hysteresisMeters = hysteresisMeters
    )
}
