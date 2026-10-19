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
import com.atrainingtracker.trainingtracker.exporter.BaseExporter.ExportResult
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import java.io.File
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

    /**
     * TST-EXP-018.1: HTTP 429 rate limit is classified as retryable, logs X-RateLimit-* headers.
     */
    @Test
    fun testUploadToStravaRateLimit429ReturnsRetryTrueAndLogsHeaders() {
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .header("X-RateLimit-Limit", "100,1000")
                    .header("X-RateLimit-Usage", "105,950")
                    .body("""{"message": "Rate Limit Exceeded"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val uploader = StravaUploader(mockContext, testClient).apply {
            sleepStrategy = {}
        }
        val tempFile = File.createTempFile("test_workout", ".tcx").apply {
            writeText("<tcx>data</tcx>")
            deleteOnExit()
        }
        val exportInfo = ExportInfo("test_workout", FileFormat.STRAVA, ExportType.COMMUNITY)

        val result = uploader.uploadToStrava(exportInfo, tempFile, "mock_token")
        assertFalse("Rate limit upload must not be marked success", result.success())
        assertTrue("HTTP 429 must be classified as retryable", result.shallRetry())
        assertTrue("Status message must mention 429", result.answer().contains("429"))
        assertTrue("Status message must include rate limit info", result.answer().contains("105,950"))
    }

    /**
     * TST-EXP-018.1: HTTP 503 service unavailable is classified as retryable server error.
     */
    @Test
    fun testUploadToStravaServerError503ReturnsRetryTrue() {
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("""{"message": "Strava is temporarily down"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val uploader = StravaUploader(mockContext, testClient).apply {
            sleepStrategy = {}
        }
        val tempFile = File.createTempFile("test_workout", ".tcx").apply {
            writeText("<tcx>data</tcx>")
            deleteOnExit()
        }
        val exportInfo = ExportInfo("test_workout", FileFormat.STRAVA, ExportType.COMMUNITY)

        val result = uploader.uploadToStrava(exportInfo, tempFile, "mock_token")
        assertFalse(result.success())
        assertTrue("HTTP 503 must be classified as retryable", result.shallRetry())
        assertTrue("Status message must mention 503", result.answer().contains("503"))
    }

    /**
     * TST-EXP-018.1: HTTP 400 client error is classified as non-retryable fatal error.
     */
    @Test
    fun testUploadToStravaClientError400ReturnsRetryFalse() {
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("""{"message": "Malformed payload"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val uploader = StravaUploader(mockContext, testClient).apply {
            sleepStrategy = {}
        }
        val tempFile = File.createTempFile("test_workout", ".tcx").apply {
            writeText("<tcx>data</tcx>")
            deleteOnExit()
        }
        val exportInfo = ExportInfo("test_workout", FileFormat.STRAVA, ExportType.COMMUNITY)

        val result = uploader.uploadToStrava(exportInfo, tempFile, "mock_token")
        assertFalse(result.success())
        assertFalse("HTTP 400 client error must NOT be retryable", result.shallRetry())
        assertTrue("Status message must mention 400", result.answer().contains("400"))
    }

    /**
     * TST-EXP-018.2: Polling loop recovers from transient null response on attempt 1
     * and successfully completes when attempt 2 returns status = ready.
     */
    @Test
    fun testPollingRecoversFromTransientNullGlitch() {
        val mockDbHelper = mockk<StravaUploadDbHelper>(relaxed = true)
        StravaUploadDbHelper.resetForTesting(mockDbHelper)

        var requestStep = 0
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestStep++
                when (requestStep) {
                    1 -> {
                        // POST upload response
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(201)
                            .message("Created")
                            .body("""{"id": "upl_12345", "status": "Your activity is still being processed."}""".toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    }
                    2 -> {
                        // GET upload status attempt 1: simulated server glitch (500), causing getStravaUploadStatus to return null
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Internal Server Error")
                            .body("""{"message": "Transient error"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    }
                    3 -> {
                        // GET upload status attempt 2: successful ready response
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"id": "upl_12345", "status": "Your activity is ready.", "activity_id": 998877}""".toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{}""".toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    }
                }
            }
            .build()

        val uploader = object : StravaUploader(mockContext, testClient) {
            override fun doUpdate(exportInfo: ExportInfo, isDuplicate: Boolean): ExportResult {
                return ExportResult(true, false, "successfully updated")
            }
        }.apply {
            sleepStrategy = {}
        }

        every { StravaHelper.getRefreshedAccessToken() } returns "mock_token"

        val tempFile = File.createTempFile("test_workout", ".tcx").apply {
            writeText("<tcx>data</tcx>")
            deleteOnExit()
        }
        val exportInfo = ExportInfo("test_workout", FileFormat.STRAVA, ExportType.COMMUNITY)

        val result = uploader.uploadToStrava(exportInfo, tempFile, "mock_token")
        assertTrue("Upload must succeed despite attempt 1 glitch", result.success())
        assertFalse("Successful upload must not retry", result.shallRetry())
        assertEquals("successfully updated", result.answer())
        assertTrue("Polling must have reached attempt 2 (request 3)", requestStep >= 3)
    }

    /**
     * TST-EXP-018.2: Polling loop exhausting MAX_REQUESTS returns timeout with retry = true.
     */
    @Test
    fun testPollingExhaustsMaxRequestsReturnsTimeoutRetryTrue() {
        val mockDbHelper = mockk<StravaUploadDbHelper>(relaxed = true)
        StravaUploadDbHelper.resetForTesting(mockDbHelper)

        var requestStep = 0
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestStep++
                if (requestStep == 1) {
                    // Initial POST
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(201)
                        .message("Created")
                        .body("""{"id": "upl_timeout"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                } else {
                    // Continuous processing status
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"id": "upl_timeout", "status": "Your activity is still being processed."}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val uploader = StravaUploader(mockContext, testClient).apply {
            sleepStrategy = {}
        }
        every { StravaHelper.getRefreshedAccessToken() } returns "mock_token"

        val tempFile = File.createTempFile("test_workout", ".tcx").apply {
            writeText("<tcx>data</tcx>")
            deleteOnExit()
        }
        val exportInfo = ExportInfo("test_workout", FileFormat.STRAVA, ExportType.COMMUNITY)

        val result = uploader.uploadToStrava(exportInfo, tempFile, "mock_token")
        assertFalse("Timeout must not be marked success", result.success())
        assertTrue("Timeout must be classified as retryable", result.shallRetry())
        assertTrue("Status must mention timeout", result.answer().contains("Timeout waiting for Strava processing"))
    }
}
