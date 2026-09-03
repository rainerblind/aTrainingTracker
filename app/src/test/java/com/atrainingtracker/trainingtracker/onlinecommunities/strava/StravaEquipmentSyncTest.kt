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
import android.net.Uri
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies Strava OAuth scope compliance and equipment synchronization parsing (REQ-EXT-006, TST-EXT-003, ATT-588).
 */
class StravaEquipmentSyncTest {

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
        unmockkStatic(Log::class)
    }

    @Test
    fun testAuthorizationUrl_ContainsProfileReadAllScope() {
        val authUrl = StravaHelper.getAuthorizationUrl()
        
        // Extract query parameters cleanly in JVM test
        val queryString = authUrl.substringAfter("?", "")
        val queryParams = queryString.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to (if (parts.size > 1) parts[1] else "")
        }
        val scopeParam = queryParams[StravaHelper.SCOPE]

        // Must not be null
        assertTrue("Scope parameter must be present in authorization URL", !scopeParam.isNullOrEmpty())

        val scopes = scopeParam!!.split(",")

        // REQ-EXT-006: profile:read_all is mandatory for athlete gear access
        assertTrue("Scope must contain profile:read_all for gear synchronization", scopes.contains("profile:read_all"))
        assertTrue("Scope must contain read", scopes.contains("read"))
        assertTrue("Scope must contain read_all", scopes.contains("read_all"))
        assertTrue("Scope must contain activity:read_all", scopes.contains("activity:read_all"))
        assertTrue("Scope must contain activity:write", scopes.contains("activity:write"))
    }

    @Test
    fun testConstants_ProfileReadAllMatchesSpecification() {
        assertEquals("profile:read_all", StravaHelper.PROFILE_READ_ALL)
    }

    @Test
    fun testMissingGearDetection_WhenShoesAndBikesOmitted() {
        val mockContext = mockk<Context>(relaxed = true)
        val syncThread = StravaEquipmentSynchronizeThread(mockContext)

        val mockJson = mockk<JSONObject>()
        io.mockk.every { mockJson.has("shoes") } returns false
        io.mockk.every { mockJson.has("bikes") } returns false

        val result = syncThread.fillDbFromJsonObject(mockJson)
        assertEquals("No gear returned (missing profile:read_all permission)", result)
    }
}
