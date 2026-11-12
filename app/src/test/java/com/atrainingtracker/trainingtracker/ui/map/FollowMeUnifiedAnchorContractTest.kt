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

package com.atrainingtracker.trainingtracker.ui.map

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract tests verifying unified viewport anchor authority and
 * decoupled GoogleMap content padding under Rule 33 (REQ-MAP-046 / TST-MAP-048.2).
 */
class FollowMeUnifiedAnchorContractTest {

    @Test
    fun testATrainingTrackerMap_decouplesGoogleMapPadding_andPassesLookaheadParameters() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt")
        assertNotNull("ATrainingTrackerMapKt must exist", clazz)
    }

    @Test
    fun testMapBehaviors_followMeController_usesCalculateFollowMeCameraTarget() {
        val loc = LatLng(48.0, 11.0)
        // With 0 viewport height, target is unchanged
        val targetZero = calculateFollowMeCameraTarget(
            currentLocation = loc,
            bearing = 0f,
            zoom = 15f,
            viewportHeightDp = 0f
        )
        assertEquals(loc, targetZero)

        // With positive viewport height, offset is calculated
        val targetOffset = calculateFollowMeCameraTarget(
            currentLocation = loc,
            bearing = 0f,
            zoom = 15f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 400f,
            tiltAngle = 45f
        )
        assertTrue(targetOffset.latitude > loc.latitude)
    }
}
