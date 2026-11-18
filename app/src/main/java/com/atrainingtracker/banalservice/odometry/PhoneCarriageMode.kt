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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Physical filter, EKF, and ERTS tuning parameters corresponding to a [PhoneCarriageMode]
 * (REQ-FIL-028.3, REQ-FIL-028.4, ATT-8890).
 *
 * Centralizing mode parameters here guarantees uniform live and offline behavior without
 * duplicated switch tables across devices and smoothers.
 */
data class CarriageFilterProfile(
    val gyroNoiseScale: Double,
    val yawWindowSec: Double,
    val enableGlanceGating: Boolean,
    val glanceOrientationShiftDeg: Double,
    val baroMedianWindowSize: Int,
    val baroMaxSlewRateMps: Double,
    val rBaroM2: Double,
    val minChordHeadingSpeedMps: Double,
    val chordHeadingVarianceRad2: Double,
    val forceStationaryPinning: Boolean,
    val ertsGyroNoiseScale: Double,
    val ertsYawHalfWidthSamples: Int,
    val ertsBaroVarianceM2: Double
)

/**
 * Persisted 1-Hz activity and phone carriage regime classification (REQ-FIL-028, ATT-8890).
 *
 * Integer [code] values are stored directly in `WorkoutSamples.db` and `CSV` exports under
 * `SensorType.ACTIVITY_AND_CARRIAGE_MODE` (`UNKNOWN(-1)` and `0..7`).
 */
enum class PhoneCarriageMode(
    val code: Int,
    @StringRes val labelResId: Int,
    val defaultEnglishLabel: String,
    val profile: CarriageFilterProfile
) {
    UNKNOWN(
        code = -1,
        labelResId = R.string.carriage_mode_unknown,
        defaultEnglishLabel = "Unknown",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 1.0,
            yawWindowSec = 1.0,
            enableGlanceGating = false,
            glanceOrientationShiftDeg = Double.POSITIVE_INFINITY,
            baroMedianWindowSize = 7,
            baroMaxSlewRateMps = 1.2,
            rBaroM2 = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO,
            minChordHeadingSpeedMps = UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS,
            chordHeadingVarianceRad2 = 0.16,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 25.0,
            ertsYawHalfWidthSamples = 2,
            ertsBaroVarianceM2 = 1.0
        )
    ),
    STATIONARY(
        code = 0,
        labelResId = R.string.carriage_mode_stationary,
        defaultEnglishLabel = "Stationary",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 1.0,
            yawWindowSec = 1.0,
            enableGlanceGating = false,
            glanceOrientationShiftDeg = Double.POSITIVE_INFINITY,
            baroMedianWindowSize = 7,
            baroMaxSlewRateMps = 0.6,
            rBaroM2 = 2.0,
            minChordHeadingSpeedMps = UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS,
            chordHeadingVarianceRad2 = 0.25,
            forceStationaryPinning = true,
            ertsGyroNoiseScale = 1.0,
            ertsYawHalfWidthSamples = 1,
            ertsBaroVarianceM2 = 2.0
        )
    ),
    WALKING_TRANSITION(
        code = 1,
        labelResId = R.string.carriage_mode_walking_transition,
        defaultEnglishLabel = "Walking / Transition",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 8.0,
            yawWindowSec = 1.5,
            enableGlanceGating = true,
            glanceOrientationShiftDeg = 20.0,
            baroMedianWindowSize = 7,
            baroMaxSlewRateMps = 0.7,
            rBaroM2 = 1.5,
            minChordHeadingSpeedMps = 0.8,
            chordHeadingVarianceRad2 = 0.14,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 15.0,
            ertsYawHalfWidthSamples = 2,
            ertsBaroVarianceM2 = 1.5
        )
    ),
    BIKE_HANDLEBAR(
        code = 2,
        labelResId = R.string.carriage_mode_bike_handlebar,
        defaultEnglishLabel = "Bike Handlebar",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 1.0,
            yawWindowSec = 0.25,
            enableGlanceGating = false,
            glanceOrientationShiftDeg = Double.POSITIVE_INFINITY,
            baroMedianWindowSize = 3,
            baroMaxSlewRateMps = 1.2,
            rBaroM2 = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO,
            minChordHeadingSpeedMps = UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS,
            chordHeadingVarianceRad2 = 0.16,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 4.0,
            ertsYawHalfWidthSamples = 0,
            ertsBaroVarianceM2 = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO
        )
    ),
    BIKE_JERSEY_BACK(
        code = 3,
        labelResId = R.string.carriage_mode_bike_jersey_back,
        defaultEnglishLabel = "Bike Jersey Pocket",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 4.0,
            yawWindowSec = 1.0,
            enableGlanceGating = false,
            glanceOrientationShiftDeg = Double.POSITIVE_INFINITY,
            baroMedianWindowSize = 5,
            baroMaxSlewRateMps = 1.0,
            rBaroM2 = 0.6,
            minChordHeadingSpeedMps = UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS,
            chordHeadingVarianceRad2 = 0.16,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 8.0,
            ertsYawHalfWidthSamples = 1,
            ertsBaroVarianceM2 = 0.6
        )
    ),
    BIKE_FRONT_POCKET(
        code = 4,
        labelResId = R.string.carriage_mode_bike_front_pocket,
        defaultEnglishLabel = "Bike Trouser Pocket",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 30.0,
            yawWindowSec = 1.75,
            enableGlanceGating = true,
            glanceOrientationShiftDeg = 22.0,
            baroMedianWindowSize = 9,
            baroMaxSlewRateMps = 0.7,
            rBaroM2 = 2.0,
            minChordHeadingSpeedMps = UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS,
            chordHeadingVarianceRad2 = 0.16,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 35.0,
            ertsYawHalfWidthSamples = 2,
            ertsBaroVarianceM2 = 2.0
        )
    ),
    RUN_WAIST_HIP(
        code = 5,
        labelResId = R.string.carriage_mode_run_waist_hip,
        defaultEnglishLabel = "Run Waist / Hip",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 6.0,
            yawWindowSec = 1.25,
            enableGlanceGating = false,
            glanceOrientationShiftDeg = Double.POSITIVE_INFINITY,
            baroMedianWindowSize = 5,
            baroMaxSlewRateMps = 1.0,
            rBaroM2 = 0.5,
            minChordHeadingSpeedMps = 0.8,
            chordHeadingVarianceRad2 = 0.12,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 10.0,
            ertsYawHalfWidthSamples = 1,
            ertsBaroVarianceM2 = 0.5
        )
    ),
    RUN_BICEP_ARM(
        code = 6,
        labelResId = R.string.carriage_mode_run_bicep_arm,
        defaultEnglishLabel = "Run Armband",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 25.0,
            yawWindowSec = 1.35,
            enableGlanceGating = false,
            glanceOrientationShiftDeg = Double.POSITIVE_INFINITY,
            baroMedianWindowSize = 5,
            baroMaxSlewRateMps = 1.0,
            rBaroM2 = 0.5,
            minChordHeadingSpeedMps = 0.8,
            chordHeadingVarianceRad2 = 0.10,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 25.0,
            ertsYawHalfWidthSamples = 2,
            ertsBaroVarianceM2 = 0.5
        )
    ),
    RUN_HANDHELD(
        code = 7,
        labelResId = R.string.carriage_mode_run_handheld,
        defaultEnglishLabel = "Run Handheld",
        profile = CarriageFilterProfile(
            gyroNoiseScale = 40.0,
            yawWindowSec = 1.75,
            enableGlanceGating = true,
            glanceOrientationShiftDeg = 15.0,
            baroMedianWindowSize = 7,
            baroMaxSlewRateMps = 0.8,
            rBaroM2 = 1.5,
            minChordHeadingSpeedMps = 0.8,
            chordHeadingVarianceRad2 = 0.10,
            forceStationaryPinning = false,
            ertsGyroNoiseScale = 40.0,
            ertsYawHalfWidthSamples = 2,
            ertsBaroVarianceM2 = 1.5
        )
    );

    fun isCyclingMode(): Boolean =
        this == BIKE_HANDLEBAR || this == BIKE_JERSEY_BACK || this == BIKE_FRONT_POCKET

    fun isRunningMode(): Boolean =
        this == RUN_WAIST_HIP || this == RUN_BICEP_ARM || this == RUN_HANDHELD

    fun isWalkingOrStationary(): Boolean =
        this == STATIONARY || this == WALKING_TRANSITION

    companion object {
        @JvmStatic
        fun fromCode(code: Int?): PhoneCarriageMode {
            if (code == null) return UNKNOWN
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
