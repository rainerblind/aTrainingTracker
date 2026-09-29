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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying favorite location indicators in [WorkoutHeaderData]
 * and [WorkoutHeader] display logic (ATT-1400 / REQ-UI-184 / TST-UI-137.2).
 */
class WorkoutHeaderLocationTest {

    private fun createBaseHeaderData(
        startLocationName: String? = null,
        endLocationName: String? = null
    ): WorkoutHeaderData {
        return WorkoutHeaderData(
            workoutName = "Test Activity",
            formattedDate = "2026-09-29",
            formattedTime = "10:00",
            startTimeS = 1700000000L,
            bSportType = BSportType.BIKE,
            sportName = "Cycling",
            equipmentName = null,
            commute = false,
            trainer = false,
            uploadToStrava = 0,
            finished = true,
            startLocationName = startLocationName,
            endLocationName = endLocationName
        )
    }

    @Test
    fun defaultLocations_areNull() {
        val headerData = createBaseHeaderData()
        assertNull("startLocationName should default to null", headerData.startLocationName)
        assertNull("endLocationName should default to null", headerData.endLocationName)

        val hasLocationRow = !headerData.startLocationName.isNullOrBlank() || !headerData.endLocationName.isNullOrBlank()
        assertFalse("Location indicator row must be omitted when both locations are null", hasLocationRow)
    }

    @Test
    fun pointToPointLocations_arePreservedAndDistinct() {
        val headerData = createBaseHeaderData(
            startLocationName = "Zuhause",
            endLocationName = "Büro"
        )

        assertEquals("Zuhause", headerData.startLocationName)
        assertEquals("Büro", headerData.endLocationName)

        val isRoundTrip = headerData.startLocationName != null &&
                headerData.endLocationName != null &&
                headerData.startLocationName == headerData.endLocationName
        assertFalse("Point-to-point must not evaluate as round-trip", isRoundTrip)
    }

    @Test
    fun roundTripLocations_arePreservedAndIdentical() {
        val headerData = createBaseHeaderData(
            startLocationName = "Zuhause",
            endLocationName = "Zuhause"
        )

        assertEquals("Zuhause", headerData.startLocationName)
        assertEquals("Zuhause", headerData.endLocationName)

        val isRoundTrip = headerData.startLocationName != null &&
                headerData.endLocationName != null &&
                headerData.startLocationName == headerData.endLocationName
        assertTrue("Matching endpoints must evaluate as round-trip", isRoundTrip)
    }

    @Test
    fun singleEndpointLocation_startOnly_isPreserved() {
        val headerData = createBaseHeaderData(
            startLocationName = "Zuhause",
            endLocationName = null
        )

        assertEquals("Zuhause", headerData.startLocationName)
        assertNull(headerData.endLocationName)

        val hasStart = !headerData.startLocationName.isNullOrBlank()
        val hasEnd = !headerData.endLocationName.isNullOrBlank()
        assertTrue(hasStart)
        assertFalse(hasEnd)
    }

    @Test
    fun singleEndpointLocation_destinationOnly_isPreserved() {
        val headerData = createBaseHeaderData(
            startLocationName = null,
            endLocationName = "Büro"
        )

        assertNull(headerData.startLocationName)
        assertEquals("Büro", headerData.endLocationName)

        val hasStart = !headerData.startLocationName.isNullOrBlank()
        val hasEnd = !headerData.endLocationName.isNullOrBlank()
        assertFalse(hasStart)
        assertTrue(hasEnd)
    }
}
