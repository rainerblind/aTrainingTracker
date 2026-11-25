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

package com.atrainingtracker.banalservice.odometry

import com.atrainingtracker.banalservice.BSportType
import java.util.Locale

/**
 * Pure-JVM projection of an active (non-placeholder) `SportTypes` DB row used to derive bounded
 * live speed priors without coupling odometry classifiers to Android SQLite (`REQ-FIL-047.1`, `ATT-9694`).
 */
data class SportSpeedBand(
    val bSportType: BSportType,
    val minSpeedMps: Double,
    val maxSpeedMps: Double,
    val gcName: String? = null,
    val stravaName: String? = null,
    val tcxName: String? = null
)

/**
 * Immutable, biomechanically clamped speed priors derived from user-configured `SportTypes` bands
 * (`REQ-FIL-047.1`, `ATT-9694`).
 *
 * Because `SportTypes.minSpeed` / `maxSpeed` are workout-average bands, these values act strictly
 * as bounded priors and initial-establishment floors rather than rigid instantaneous hard cuts.
 */
@Suppress("MagicNumber")
data class SportSpeedPriors(
    val maxWalkSpeedMps: Double = DEFAULT_MAX_WALK_SPEED_MPS,
    val minBikeSpeedMps: Double = DEFAULT_MIN_BIKE_SPEED_MPS,
    val maxRunSpeedMps: Double = DEFAULT_MAX_RUN_SPEED_MPS
) {
    /**
     * Returns a sanitized copy with non-finite values replaced by canonical defaults and all
     * dimensions clamped to safe biomechanical bounds.
     */
    fun clamped(): SportSpeedPriors = SportSpeedPriors(
        maxWalkSpeedMps = sanitizeAndClamp(
            maxWalkSpeedMps, DEFAULT_MAX_WALK_SPEED_MPS, MAX_WALK_MIN_MPS, MAX_WALK_MAX_MPS
        ),
        minBikeSpeedMps = sanitizeAndClamp(
            minBikeSpeedMps, DEFAULT_MIN_BIKE_SPEED_MPS, MIN_BIKE_MIN_MPS, MIN_BIKE_MAX_MPS
        ),
        maxRunSpeedMps = sanitizeAndClamp(
            maxRunSpeedMps, DEFAULT_MAX_RUN_SPEED_MPS, MAX_RUN_MIN_MPS, MAX_RUN_MAX_MPS
        )
    )

    companion object {
        const val DEFAULT_MAX_WALK_SPEED_MPS = 2.0
        const val MAX_WALK_MIN_MPS = 1.5
        const val MAX_WALK_MAX_MPS = 2.5

        const val DEFAULT_MIN_BIKE_SPEED_MPS = 2.5
        const val MIN_BIKE_MIN_MPS = 1.8
        const val MIN_BIKE_MAX_MPS = 3.5

        const val DEFAULT_MAX_RUN_SPEED_MPS = 5.5
        const val MAX_RUN_MIN_MPS = 3.5
        const val MAX_RUN_MAX_MPS = 6.5

        /** Absolute physical floor below which ungated non-wheel cycling carriage is always suppressed. */
        const val MIN_BIKE_SPEED_FLOOR_MPS = MIN_BIKE_MIN_MPS

        private const val WALK_BAND_MAX_MIN_SPEED_MPS = 1.0
        private const val WALK_BAND_MAX_UPPER_SPEED_MPS = 2.8

        @JvmField
        val DEFAULT = SportSpeedPriors()

        /**
         * Aggregates active [SportSpeedBand] rows into clamped [SportSpeedPriors], ignoring
         * non-finite, inverted, or zero-speed rows and falling back to [DEFAULT] per missing group.
         */
        @JvmStatic
        fun fromBands(bands: Collection<SportSpeedBand>): SportSpeedPriors {
            var maxWalk: Double? = null
            var minBike: Double? = null
            var maxRun: Double? = null

            for (band in bands) {
                val lo = band.minSpeedMps
                val hi = band.maxSpeedMps
                if (!lo.isFinite() || !hi.isFinite() || lo < 0.0 || hi <= 0.0 || hi < lo) continue

                when (band.bSportType) {
                    BSportType.RUN -> {
                        maxRun = if (maxRun == null || hi > maxRun) hi else maxRun
                        if (isWalkMetadata(band) ||
                            (!isRunMetadata(band) && lo <= WALK_BAND_MAX_MIN_SPEED_MPS && hi <= WALK_BAND_MAX_UPPER_SPEED_MPS)
                        ) {
                            maxWalk = if (maxWalk == null || hi > maxWalk) hi else maxWalk
                        }
                    }
                    BSportType.BIKE -> {
                        if (lo > 0.0 && hi > lo) {
                            minBike = if (minBike == null || lo < minBike) lo else minBike
                        }
                    }
                    else -> Unit
                }
            }

            return SportSpeedPriors(
                maxWalkSpeedMps = maxWalk ?: DEFAULT_MAX_WALK_SPEED_MPS,
                minBikeSpeedMps = minBike ?: DEFAULT_MIN_BIKE_SPEED_MPS,
                maxRunSpeedMps = maxRun ?: DEFAULT_MAX_RUN_SPEED_MPS
            ).clamped()
        }

        private fun isWalkMetadata(band: SportSpeedBand): Boolean {
            val gc = band.gcName?.trim()?.lowercase(Locale.ROOT)
            if (gc == "walk" || gc == "hike") return true
            val strava = band.stravaName?.trim()?.lowercase(Locale.ROOT)
            if (strava == "walk" || strava == "hike") return true
            val tcx = band.tcxName?.trim()?.lowercase(Locale.ROOT)
            return tcx == "walking"
        }

        private fun isRunMetadata(band: SportSpeedBand): Boolean {
            val gc = band.gcName?.trim()?.lowercase(Locale.ROOT)
            if (gc == "run") return true
            val strava = band.stravaName?.trim()?.lowercase(Locale.ROOT)
            if (strava == "run") return true
            val tcx = band.tcxName?.trim()?.lowercase(Locale.ROOT)
            return tcx == "running"
        }

        private fun sanitizeAndClamp(value: Double, fallback: Double, min: Double, max: Double): Double {
            val finite = if (value.isFinite()) value else fallback
            return finite.coerceIn(min, max)
        }
    }
}
