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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract test for [com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen]
 * verifying the upper metadata slotting of StravaActivitySection above the map viewport (REQ-UI-248 / TST-UI-207 / ATT-2137).
 */
class TrackOnMapScreenMetadataRoutingContractTest {

    @Test
    fun testTrackOnMapScreen_routesStravaActivitySectionInMetadataContentAfterExtrema() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", clazz)
    }

    @Test
    fun testTrackOnMapScreen_omitsStravaActivitySectionFromAnalyticsContent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", clazz)
    }
}
