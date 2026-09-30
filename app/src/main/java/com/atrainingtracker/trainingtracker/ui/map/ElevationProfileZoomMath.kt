/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits

/**
 * Pure mathematical engine encapsulating visible window calculations, coordinate transformations,
 * centroid-anchored zoom scaling, clamping invariants, and adaptive tick sizing for [ElevationProfile].
 * (REQ-UI-192)
 */
object ElevationProfileZoomMath {
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 10.0f
    const val MIN_TRACK_DIST_FOR_ZOOM = 10.0 // meters

    /**
     * Calculates the horizontal distance span displayed within the viewport.
     */
    fun calculateVisibleDistance(totalDist: Double, zoomScale: Float): Double {
        if (totalDist <= MIN_TRACK_DIST_FOR_ZOOM) return totalDist.coerceAtLeast(0.0)
        val clampedScale = zoomScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return (totalDist / clampedScale).coerceAtLeast(1.0)
    }

    /**
     * Clamps the visible window start distance strictly within [0.0 .. (totalDist - visibleDist)].
     */
    fun clampStartDistance(totalDist: Double, visibleDist: Double, rawStartDist: Double): Double {
        val maxStart = (totalDist - visibleDist).coerceAtLeast(0.0)
        return rawStartDist.coerceIn(0.0, maxStart)
    }

    /**
     * Panning navigation: shifts [currentStartDist] horizontally by [panDeltaX] pixels across [canvasWidth].
     */
    fun applyPan(
        currentStartDist: Double,
        visibleDist: Double,
        panDeltaX: Float,
        canvasWidth: Float,
        totalDist: Double
    ): Double {
        if (canvasWidth <= 0f || totalDist <= MIN_TRACK_DIST_FOR_ZOOM) return currentStartDist
        val distDelta = (panDeltaX / canvasWidth).toDouble() * visibleDist
        return clampStartDistance(totalDist, visibleDist, currentStartDist - distDelta)
    }

    /**
     * Maps an absolute route distance to pixel X position on canvas of width [canvasWidth].
     */
    fun distanceToCanvasX(dist: Double, startDist: Double, visibleDist: Double, canvasWidth: Float): Float {
        if (visibleDist <= 0.0 || canvasWidth <= 0f) return 0f
        return (((dist - startDist) / visibleDist) * canvasWidth).toFloat()
    }

    /**
     * Maps a pixel X coordinate on canvas back to absolute route distance in meters.
     */
    fun canvasXToDistance(canvasX: Float, startDist: Double, visibleDist: Double, canvasWidth: Float, totalDist: Double): Double {
        if (canvasWidth <= 0f) return startDist.coerceIn(0.0, totalDist)
        val ratio = (canvasX / canvasWidth).toDouble().coerceIn(0.0, 1.0)
        return (startDist + ratio * visibleDist).coerceIn(0.0, totalDist)
    }

    /**
     * Applies a zoom scale adjustment anchored around gesture centroid [centroidX].
     * Guarantees that the distance at [centroidX] remains stationary under the touch point.
     */
    fun applyZoomAtCentroid(
        totalDist: Double,
        currentZoom: Float,
        targetZoom: Float,
        centroidX: Float,
        canvasWidth: Float,
        currentStartDist: Double
    ): Pair<Float, Double> {
        if (totalDist <= MIN_TRACK_DIST_FOR_ZOOM) return Pair(1.0f, 0.0)
        val newZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val currentVisible = calculateVisibleDistance(totalDist, currentZoom)
        val centroidRatio = if (canvasWidth > 0f) (centroidX / canvasWidth).toDouble().coerceIn(0.0, 1.0) else 0.5
        val centroidDist = currentStartDist + centroidRatio * currentVisible
        val newVisible = calculateVisibleDistance(totalDist, newZoom)
        val newStartDist = clampStartDistance(totalDist, newVisible, centroidDist - centroidRatio * newVisible)
        return Pair(newZoom, newStartDist)
    }

    /**
     * Determines an adaptive distance tick interval based on the visible distance span.
     */
    fun calculateAdaptiveDistanceStep(visibleDist: Double, unit: MyUnits): Float {
        return if (unit == MyUnits.METRIC) {
            when {
                visibleDist > 50_000 -> 10_000f
                visibleDist > 20_000 -> 5_000f
                visibleDist > 5_000 -> 1_000f
                visibleDist > 1_500 -> 500f
                visibleDist > 500 -> 100f
                else -> 50f
            }
        } else {
            val visibleMiles = visibleDist / BANALService.METER_PER_MILE
            val mileStep = when {
                visibleMiles > 30 -> 5f
                visibleMiles > 10 -> 2f
                visibleMiles > 3 -> 1f
                visibleMiles > 1 -> 0.5f
                visibleMiles > 0.3 -> 0.1f
                else -> 0.05f
            }
            (mileStep * BANALService.METER_PER_MILE).toFloat()
        }
    }

    /**
     * Determines an adaptive time tick interval in seconds based on visible time span in seconds.
     * (REQ-UI-201 / ATT-1391)
     */
    fun calculateAdaptiveTimeStep(visibleTimeSec: Double): Long {
        return when {
            visibleTimeSec > 14400 -> 3600L // > 4h: 1h ticks
            visibleTimeSec > 7200 -> 1800L  // > 2h: 30m ticks
            visibleTimeSec > 3600 -> 900L   // > 1h: 15m ticks
            visibleTimeSec > 1800 -> 300L   // > 30m: 5m ticks
            visibleTimeSec > 600 -> 120L    // > 10m: 2m ticks
            visibleTimeSec > 240 -> 60L     // > 4m: 1m ticks
            else -> 30L                     // <= 4m: 30s ticks
        }
    }

    /**
     * Formats elapsed seconds into an adaptive readable time label (m:ss or h:mm:ss).
     * (REQ-UI-201 / ATT-1391)
     */
    fun formatTimeTick(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(java.util.Locale.US, "%d:%02d", minutes, secs)
        }
    }

    /**
     * Checks whether an absolute distance point is within the visible window.
     */
    fun isDistanceVisible(dist: Double, startDist: Double, visibleDist: Double): Boolean {
        val endDist = startDist + visibleDist
        return dist in startDist..endDist
    }
}
