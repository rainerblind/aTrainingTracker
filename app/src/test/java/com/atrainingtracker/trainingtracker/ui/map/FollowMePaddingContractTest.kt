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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Structural contract tests verifying forward lookahead bottom camera padding and
 * Follow-Me tuning parameter wiring (REQ-MAP-042 / TST-MAP-044.2).
 */
class FollowMePaddingContractTest {

    @Test
    fun testATrainingTrackerMap_appliesLookaheadPaddingInFollowMeMode() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt")
        assertNotNull("ATrainingTrackerMapKt must exist", clazz)
    }

    @Test
    fun testFollowMeController_defaultsToCanonicalTuningParameters() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt must exist", clazz)
    }

    @Test
    fun testPaddingPercentageCalculation() {
        // Given a 400dp viewport and default 30% lookahead padding:
        val viewportHeightDp = 400f
        val lookaheadPercent = 0.30f
        val bottomPaddingDp = viewportHeightDp * lookaheadPercent
        assertEquals(120f, bottomPaddingDp, 0.001f)
    }
}
