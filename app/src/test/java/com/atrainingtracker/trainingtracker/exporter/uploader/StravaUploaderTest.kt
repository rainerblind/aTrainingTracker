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

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import io.mockk.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying StravaUploader authorization header safety, null-safe token guards,
 * and elimination of "Authorization: Bearer null" (REQ-EXT-011.5, TST-EXT-008.6).
 */
class StravaUploaderTest {

    private lateinit var mockContext: Context
    private val capturedRequests = mutableListOf<Request>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.isLoggable(any<String>(), any<Int>()) } returns false

        // Warm up OkHttp classloading outside MockK recording blocks
        OkHttpClient()

        mockContext = mockk(relaxed = true)
        mockkStatic(StravaHelper::class)
        capturedRequests.clear()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class TestableStravaUploaderWithClient(
        context: Context,
        testClient: OkHttpClient
    ) : StravaUploader(context) {
        init {
            // Replace internal client
            client = testClient
        }
    }

    private fun createUploaderWithCapturingClient(): Pair<StravaUploader, AtomicInteger> {
        val requestCount = AtomicInteger(0)
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedRequests.add(request)
                requestCount.incrementAndGet()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"id": 12345}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()
        return Pair(TestableStravaUploaderWithClient(mockContext, testClient), requestCount)
    }

    /**
     * TST-EXT-008.6: When StravaHelper returns null, updateStravaActivity, getStravaJson,
     * and getStravaJsonArray return null immediately and NEVER dispatch HTTP requests with "Bearer null".
     */
    @Test
    fun testUploaderMethodsAbortImmediatelyWhenTokenIsNull() {
        every { StravaHelper.getRefreshedAccessToken() } returns null

        val (uploader, requestCount) = createUploaderWithCapturingClient()

        val updateResult = uploader.updateStravaActivity("99999", FormBody.Builder().build())
        assertNull("updateStravaActivity must return null on null token", updateResult)

        val activityResult = uploader.getStravaActivity("99999")
        assertNull("getStravaActivity must return null on null token", activityResult)

        val gearResult = uploader.getStravaGear("g99999")
        assertNull("getStravaGear must return null on null token", gearResult)

        val jsonArrayResult = uploader.getStravaJsonArray("https://www.strava.com/api/v3/athlete/activities")
        assertNull("getStravaJsonArray must return null on null token", jsonArrayResult)

        assertEquals("Zero HTTP requests should be dispatched when token is null", 0, requestCount.get())
        assertTrue("No requests captured", capturedRequests.isEmpty())
    }

    /**
     * TST-EXT-008.6 (blank token): When StravaHelper returns blank string, methods abort immediately.
     */
    @Test
    fun testUploaderMethodsAbortImmediatelyWhenTokenIsBlank() {
        every { StravaHelper.getRefreshedAccessToken() } returns "   "

        val (uploader, requestCount) = createUploaderWithCapturingClient()

        val updateResult = uploader.updateStravaActivity("99999", FormBody.Builder().build())
        assertNull(updateResult)

        val activityResult = uploader.getStravaActivity("99999")
        assertNull(activityResult)

        val jsonArrayResult = uploader.getStravaJsonArray("https://www.strava.com/api/v3/athlete/activities")
        assertNull(jsonArrayResult)

        assertEquals("Zero HTTP requests dispatched on blank token", 0, requestCount.get())
    }

    /**
     * TST-EXT-008.6 (positive check): When StravaHelper returns valid token, Authorization header
     * contains "Bearer <valid_token>" and never "Bearer null".
     */
    @Test
    fun testUploaderSetsValidAuthorizationHeaderWhenTokenPresent() {
        every { StravaHelper.getRefreshedAccessToken() } returns "valid_oauth_token_xyz"

        val (uploader, requestCount) = createUploaderWithCapturingClient()

        val updateResult = uploader.updateStravaActivity("99999", FormBody.Builder().build())
        assertNotNull(updateResult)
        assertEquals(1, requestCount.get())

        val lastRequest = capturedRequests.last()
        val authHeader = lastRequest.header("Authorization")
        assertNotNull(authHeader)
        assertEquals("Bearer valid_oauth_token_xyz", authHeader)
        assertFalse("Authorization header must NEVER contain 'Bearer null'", authHeader!!.contains("null"))
    }
}
