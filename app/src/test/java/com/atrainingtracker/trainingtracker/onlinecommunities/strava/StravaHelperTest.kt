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
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying Strava token refresh concurrency, double-checked locking,
 * 300s expiry safety margin, structural error discrimination, and timeout resilience
 * (REQ-EXT-011, TST-EXT-008).
 */
class StravaHelperTest {

    private lateinit var mockContext: Context

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

        mockContext = mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns mockContext
        every { TrainingApplication.getStravaRefreshToken() } returns "mock_refresh_token"
        every { TrainingApplication.setStravaAccessToken(any()) } just Runs
        every { TrainingApplication.setStravaRefreshToken(any()) } just Runs
        every { TrainingApplication.setStravaTokenExpiresAt(any()) } just Runs
        every { TrainingApplication.setStravaAthleteId(any()) } just Runs

        mockkObject(StravaDataPurgeManager)
        every { StravaDataPurgeManager.purgeAllStravaData(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        StravaHelper.resetHttpClient()
        unmockkAll()
    }

    private fun setMockHttpClient(interceptor: Interceptor) {
        StravaHelper.httpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    /**
     * TST-EXT-008.1: Application error (client_secret: invalid) returns null and NEVER purges local data.
     */
    @Test
    fun testRefreshFailureWithInvalidClientSecretNeverPurgesData() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec - 10) // Expired
        every { TrainingApplication.getStravaAccessToken() } returns "expired_token"

        val errorResponseJson = """
            {
                "message": "Bad Request",
                "errors": [
                    {
                        "resource": "Application",
                        "field": "client_secret",
                        "code": "invalid"
                    }
                ]
            }
        """.trimIndent()

        setMockHttpClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body(errorResponseJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertNull("Token refresh must return null on 400 error", token)
        verify(exactly = 0) { StravaDataPurgeManager.purgeAllStravaData(any(), any()) }
    }

    /**
     * TST-EXT-008.2: True refresh token revocation triggers local data purge.
     */
    @Test
    fun testRefreshFailureWithInvalidRefreshTokenTriggersPurge() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec - 10)
        every { TrainingApplication.getStravaAccessToken() } returns "expired_token"

        val errorResponseJson = """
            {
                "message": "Bad Request",
                "errors": [
                    {
                        "resource": "RefreshToken",
                        "field": "refresh_token",
                        "code": "invalid"
                    }
                ]
            }
        """.trimIndent()

        setMockHttpClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body(errorResponseJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertNull("Token refresh must return null on revocation", token)
        verify(exactly = 1) { StravaDataPurgeManager.purgeAllStravaData(mockContext, false) }
    }

    /**
     * TST-EXT-008.2 (variant): invalid_grant error triggers local data purge.
     */
    @Test
    fun testRefreshFailureWithInvalidGrantTriggersPurge() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec - 10)
        every { TrainingApplication.getStravaAccessToken() } returns "expired_token"

        val errorResponseJson = """
            {
                "error": "invalid_grant"
            }
        """.trimIndent()

        setMockHttpClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body(errorResponseJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertNull("Token refresh must return null on invalid_grant", token)
        verify(exactly = 1) { StravaDataPurgeManager.purgeAllStravaData(mockContext, false) }
    }

    /**
     * TST-EXT-008.3: Concurrent token refresh requests are serialized and deduplicated.
     * 10 threads call getRefreshedAccessToken() simultaneously on expired token; exactly 1 HTTP call is made.
     */
    @Test
    fun testConcurrentRefreshDeduplication() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        var tokenExpiry = nowSec - 10 // Start expired
        var currentToken = "old_token"

        every { TrainingApplication.getStravaTokenExpiresAt() } answers { tokenExpiry }
        every { TrainingApplication.getStravaAccessToken() } answers { currentToken }

        val requestCount = AtomicInteger(0)
        val successResponseJson = """
            {
                "token_type": "Bearer",
                "access_token": "brand_new_token_12345",
                "expires_at": ${nowSec + 21600},
                "expires_in": 21600,
                "refresh_token": "new_refresh_token"
            }
        """.trimIndent()

        setMockHttpClient { chain ->
            requestCount.incrementAndGet()
            Thread.sleep(50) // Simulate network latency
            // Simulate TrainingApplication updating on successful storeJSONData
            tokenExpiry = nowSec + 21600
            currentToken = "brand_new_token_12345"

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(successResponseJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val threadCount = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = java.util.Collections.synchronizedList(mutableListOf<String?>())

        for (i in 0 until threadCount) {
            Thread {
                try {
                    startLatch.await()
                    val result = StravaHelper.getRefreshedAccessToken()
                    results.add(result)
                } finally {
                    doneLatch.countDown()
                }
            }.start()
        }

        startLatch.countDown()
        assertTrue("All threads must finish within 5 seconds", doneLatch.await(5, TimeUnit.SECONDS))

        assertEquals("Exactly 1 HTTP request must be dispatched for 10 concurrent threads", 1, requestCount.get())
        assertEquals(10, results.size)
        results.forEach { token ->
            assertEquals("brand_new_token_12345", token)
        }
    }

    /**
     * TST-EXT-008.4: 5-minute (300s) expiry margin refreshes eagerly when token expires in 200s.
     */
    @Test
    fun testExpiryMarginRefreshesEagerly() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        // Valid for another 200 seconds (raw epoch is in future, but within 300s margin)
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec + 200)
        every { TrainingApplication.getStravaAccessToken() } returns "old_expiring_token"

        val requestCount = AtomicInteger(0)
        val successResponseJson = """
            {
                "token_type": "Bearer",
                "access_token": "freshly_refreshed_token",
                "expires_at": ${nowSec + 21600},
                "expires_in": 21600,
                "refresh_token": "new_refresh_token"
            }
        """.trimIndent()

        setMockHttpClient { chain ->
            requestCount.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(successResponseJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertEquals("freshly_refreshed_token", token)
        assertEquals("Network refresh must be triggered when within 300s margin", 1, requestCount.get())
    }

    /**
     * TST-EXT-008.5: Valid unexpired token (now + 3600s) returns immediately without network.
     */
    @Test
    fun testValidUnexpiredTokenReturnsCachedImmediately() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec + 3600)
        every { TrainingApplication.getStravaAccessToken() } returns "valid_unexpired_token"

        val requestCount = AtomicInteger(0)
        setMockHttpClient { chain ->
            requestCount.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertEquals("valid_unexpired_token", token)
        assertEquals("Zero network calls must be executed for valid unexpired token", 0, requestCount.get())
    }

    /**
     * TST-EXT-008.7: Non-JSON malformed error body (e.g. Cloudflare HTML 502/400) defaults to DO NOT PURGE.
     */
    @Test
    fun testNonJsonErrorBodyDefaultsToNoPurge() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec - 10)
        every { TrainingApplication.getStravaAccessToken() } returns "expired_token"

        val htmlErrorBody = "<html><head><title>502 Bad Gateway</title></head><body>Cloudflare error invalid</body></html>"

        setMockHttpClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body(htmlErrorBody.toResponseBody("text/html".toMediaTypeOrNull()))
                .build()
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertNull(token)
        verify(exactly = 0) { StravaDataPurgeManager.purgeAllStravaData(any(), any()) }
    }

    /**
     * TST-EXT-008.8: Network timeout (SocketTimeoutException) cleanly returns null without crashing or purging.
     */
    @Test
    fun testNetworkTimeoutReturnsNullCleanly() {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        every { TrainingApplication.getStravaTokenExpiresAt() } returns (nowSec - 10)
        every { TrainingApplication.getStravaAccessToken() } returns "expired_token"

        setMockHttpClient {
            throw SocketTimeoutException("Simulated connection timeout")
        }

        val token = StravaHelper.getRefreshedAccessToken()

        assertNull("Network timeout must return null safely", token)
        verify(exactly = 0) { StravaDataPurgeManager.purgeAllStravaData(any(), any()) }
    }

    /**
     * Tests sanitizeForLog masks secrets and truncates long strings.
     */
    @Test
    fun testSanitizeForLog() {
        val input = "client_secret=supersecret123&other=val"
        val sanitized = StravaHelper.sanitizeForLog(input)
        assertTrue(sanitized.contains("client_secret=***"))
        assertFalse(sanitized.contains("supersecret123"))

        val empty = StravaHelper.sanitizeForLog("")
        assertEquals("", empty)

        val longInput = "a".repeat(300)
        val sanitizedLong = StravaHelper.sanitizeForLog(longInput)
        assertTrue(sanitizedLong.contains("[truncated]"))
    }
}
