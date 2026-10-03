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

import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain

/**
 * Pure mathematical utility encapsulating canonical domain evaluation (Distance vs. Time)
 * and horizontal span calculations across all Aftermath telemetry visualizers.
 * (REQ-UI-261 / ATT-2032)
 */
object ProfileDomainMath {

    /**
     * Determines whether a workout is stationary/trackless (no GPS distance but valid elapsed duration).
     *
     * @param points Ordered list of track points, or null.
     * @return true if points contains data, total distance is <= 0.0, and elapsed duration > 0.
     */
    fun isTracklessWorkout(points: List<PathPoint>?): Boolean {
        val last = points?.lastOrNull() ?: return false
        return isTracklessWorkout(totalDistance = last.distance, totalTimeSec = last.timeSec)
    }

    /**
     * Determines whether a workout is stationary/trackless given scalar summary metrics.
     *
     * @param totalDistance Total distance in meters.
     * @param totalTimeSec Total elapsed active time in seconds.
     * @return true if total distance <= 0.0 and elapsed time > 0.
     */
    fun isTracklessWorkout(totalDistance: Double, totalTimeSec: Long): Boolean {
        return totalDistance <= 0.0 && totalTimeSec > 0L
    }

    /**
     * Determines whether the effective horizontal domain should be TIME.
     * Enforces that stationary workouts always resolve to TIME, and configured TIME domain
     * safely falls back to DISTANCE if the track has zero elapsed duration (corrupt/missing timestamps).
     *
     * @param domain Athlete's configured [ProfileXAxisDomain] preference.
     * @param points Ordered list of track points, or null.
     * @return true if the chart should render along the temporal domain (seconds).
     */
    fun isEffectiveTimeDomain(domain: ProfileXAxisDomain, points: List<PathPoint>?): Boolean {
        val last = points?.lastOrNull() ?: return false
        return isEffectiveTimeDomain(domain, totalDistance = last.distance, totalTimeSec = last.timeSec)
    }

    /**
     * Determines whether the effective horizontal domain should be TIME given scalar metrics.
     *
     * @param domain Athlete's configured [ProfileXAxisDomain] preference.
     * @param totalDistance Total distance in meters.
     * @param totalTimeSec Total elapsed active time in seconds.
     * @return true if the chart should render along the temporal domain (seconds).
     */
    fun isEffectiveTimeDomain(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Boolean {
        if (isTracklessWorkout(totalDistance, totalTimeSec)) return true
        return domain == ProfileXAxisDomain.TIME && totalTimeSec > 0L
    }

    /**
     * Calculates the total horizontal span in effective domain units (meters or seconds).
     *
     * @param domain Athlete's configured [ProfileXAxisDomain] preference.
     * @param points Ordered list of track points, or null.
     * @return Total horizontal span (seconds if effective time domain, meters otherwise).
     */
    fun calculateTotalSpan(domain: ProfileXAxisDomain, points: List<PathPoint>?): Double {
        val last = points?.lastOrNull() ?: return 0.0
        return calculateTotalSpan(domain, totalDistance = last.distance, totalTimeSec = last.timeSec)
    }

    /**
     * Calculates the total horizontal span in effective domain units (meters or seconds).
     *
     * @param domain Athlete's configured [ProfileXAxisDomain] preference.
     * @param totalDistance Total distance in meters.
     * @param totalTimeSec Total elapsed active time in seconds.
     * @return Total horizontal span (seconds if effective time domain, meters otherwise).
     */
    fun calculateTotalSpan(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Double {
        val isTime = isEffectiveTimeDomain(domain, totalDistance, totalTimeSec)
        return if (isTime) {
            totalTimeSec.toDouble().coerceAtLeast(0.0)
        } else {
            totalDistance.coerceAtLeast(0.0)
        }
    }
}
