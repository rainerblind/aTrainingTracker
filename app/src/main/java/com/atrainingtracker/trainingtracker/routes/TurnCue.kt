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

package com.atrainingtracker.trainingtracker.routes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.google.android.gms.maps.model.LatLng

/**
 * Standardized direction types for turn-by-turn navigation cues.
 *
 * Traceability:
 * - REQ-MAP-028: Visual & Auditory Turn-by-Turn Navigation Prompts, Battery Saver Wake-Up & Off-Route Alerts.
 * - TST-MAP-030: Turn-by-Turn Verification Suite.
 */
enum class TurnDirection(
    @DrawableRes val iconResId: Int,
    @StringRes val displayNameResId: Int
) {
    LEFT(R.drawable.ic_turn_left, R.string.turn_cue_left),
    RIGHT(R.drawable.ic_turn_right, R.string.turn_cue_right),
    SLIGHT_LEFT(R.drawable.ic_turn_slight_left, R.string.turn_cue_slight_left),
    SLIGHT_RIGHT(R.drawable.ic_turn_slight_right, R.string.turn_cue_slight_right),
    SHARP_LEFT(R.drawable.ic_turn_sharp_left, R.string.turn_cue_sharp_left),
    SHARP_RIGHT(R.drawable.ic_turn_sharp_right, R.string.turn_cue_sharp_right),
    STRAIGHT(R.drawable.ic_turn_straight, R.string.turn_cue_straight),
    U_TURN(R.drawable.ic_turn_u_turn, R.string.turn_cue_u_turn);

    companion object {
        /**
         * Maps a [WaypointType] into a corresponding [TurnDirection], or returns null if not a turn cue.
         */
        fun fromWaypointType(type: WaypointType): TurnDirection? = when (type) {
            WaypointType.TURN_LEFT -> LEFT
            WaypointType.TURN_RIGHT -> RIGHT
            WaypointType.TURN_STRAIGHT -> STRAIGHT
            else -> null
        }
    }
}

/**
 * Represents an individual navigational turn decision point along a route.
 *
 * @param id Unique identifier.
 * @param latLng Geographic coordinate of the turn intersection.
 * @param distanceFromStart Cumulative route distance in meters from start to this turn.
 * @param direction Classified [TurnDirection].
 * @param instruction Optional text instruction (e.g. "Links abbiegen").
 * @param wayName Optional name of the street or trail to turn onto.
 */
data class TurnCue(
    val id: Long = 0L,
    val latLng: LatLng,
    val distanceFromStart: Double = 0.0,
    val direction: TurnDirection,
    val instruction: String = "",
    val wayName: String = ""
)

/**
 * Real-time navigation status published by [TurnByTurnNavigationEngine].
 *
 * @param activeRouteId ID of the actively followed route, or null if inactive.
 * @param upcomingCue The next upcoming turn cue ahead, or null if route finished or no cues.
 * @param distanceToNextCueMeters Distance remaining from current position to the upcoming cue.
 * @param isApproaching True when within the countdown threshold (default 150m).
 * @param isTurnNow True when within immediate turn execution threshold (<= 25m).
 * @param isOffRoute True when athlete has deviated beyond the corridor threshold (> 50m).
 * @param crossTrackDistanceMeters Orthogonal distance to the closest point on the route polyline.
 * @param passedCuesCount Number of cues passed so far on the active route.
 * @param totalCuesCount Total number of turn cues on the active route.
 */
data class TurnNavigationState(
    val activeRouteId: Long? = null,
    val upcomingCue: TurnCue? = null,
    val distanceToNextCueMeters: Double = 0.0,
    val isApproaching: Boolean = false,
    val isTurnNow: Boolean = false,
    val isOffRoute: Boolean = false,
    val crossTrackDistanceMeters: Double = 0.0,
    val passedCuesCount: Int = 0,
    val totalCuesCount: Int = 0
)
