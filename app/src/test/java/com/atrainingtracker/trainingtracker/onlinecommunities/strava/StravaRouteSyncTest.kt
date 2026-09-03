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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Strava route import and synchronization flow (REQ-EXT-007, TST-EXT-004, ATT-497).
 */
class StravaRouteSyncTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testConstants_PreferenceAndStorageKeys() {
        assertEquals("updateStravaRoutes", TrainingApplication.UPDATE_STRAVA_ROUTES)
        assertEquals("lastUpdateTimeOfStravaRoutes", TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES)
    }

    @Test
    fun testStravaRouteJsonDecoding() {
        val jsonPayload = """
            [
              {
                "id": 28192348,
                "id_str": "28192348",
                "name": "Morning Valley Loop",
                "description": "Scenic ride through the valley",
                "distance": 42195.0,
                "elevation_gain": 350.0,
                "type": 1,
                "sub_type": 1,
                "private": false,
                "starred": true,
                "timestamp": 1600000000,
                "map": {
                  "id": "r28192348",
                  "polyline": null,
                  "summary_polyline": "u{~vFvyys@fG"
                }
              }
            ]
        """.trimIndent()

        val routes = json.decodeFromString<List<StravaRoute>>(jsonPayload)
        assertEquals(1, routes.size)

        val route = routes[0]
        assertEquals(28192348L, route.id)
        assertEquals("28192348", route.idStr)
        assertEquals("Morning Valley Loop", route.name)
        assertEquals("Scenic ride through the valley", route.description)
        assertEquals(42195.0, route.distance, 1e-4)
        assertEquals(350.0, route.elevationGain, 1e-4)
        assertEquals(1, route.type)

        val mappedSportType = when (route.type) {
            1 -> BSportType.BIKE
            2 -> BSportType.RUN
            else -> BSportType.UNKNOWN
        }
        assertEquals(BSportType.BIKE, mappedSportType)
        assertNotNull(route.map)
        assertEquals("u{~vFvyys@fG", route.map.summaryPolyline)
    }

    @Test
    fun testStravaStreamJsonDecoding() {
        val streamPayload = """
            [
              {
                "type": "latlng",
                "data": [[46.8833, 11.7542], [46.8845, 11.7556]],
                "series_type": "distance",
                "original_size": 2,
                "resolution": "high"
              },
              {
                "type": "distance",
                "data": [0.0, 150.0],
                "series_type": "distance",
                "original_size": 2,
                "resolution": "high"
              },
              {
                "type": "altitude",
                "data": [850.0, 862.0],
                "series_type": "distance",
                "original_size": 2,
                "resolution": "high"
              }
            ]
        """.trimIndent()

        val streams = json.decodeFromString<List<StravaStream>>(streamPayload)
        assertEquals(3, streams.size)

        val latLngStream = streams.find { it.type == "latlng" }
        assertNotNull(latLngStream)
        assertEquals(2, latLngStream!!.data.size)

        val firstCoord = latLngStream.data[0].jsonArray
        assertEquals(46.8833, firstCoord[0].jsonPrimitive.double, 1e-4)
        assertEquals(11.7542, firstCoord[1].jsonPrimitive.double, 1e-4)

        val distanceStream = streams.find { it.type == "distance" }
        assertNotNull(distanceStream)
        assertEquals(0.0, distanceStream!!.data[0].jsonPrimitive.double, 1e-4)
        assertEquals(150.0, distanceStream.data[1].jsonPrimitive.double, 1e-4)

        val altitudeStream = streams.find { it.type == "altitude" }
        assertNotNull(altitudeStream)
        assertEquals(850.0, altitudeStream!!.data[0].jsonPrimitive.double, 1e-4)
        assertEquals(862.0, altitudeStream.data[1].jsonPrimitive.double, 1e-4)
    }

    @Test
    fun testNullAccessToken_ReturnsFalse() = runBlocking {
        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns null

        val mockContext = mockk<Context>(relaxed = true)
        val mockRoutesDb = mockk<com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager>(relaxed = true)

        val repository = RoutesRepository(mockContext, mockRoutesDb)

        val result = repository.syncRoutesFromStrava()
        assertFalse("syncRoutesFromStrava must return false when access token is null", result)
    }
}
