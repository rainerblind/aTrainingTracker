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

package com.atrainingtracker.trainingtracker.elevation

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Unit tests for ElevationService covering nominal DEM API queries, coordinate quantization,
 * HTTP 429 rate limit backoff, HTTP 503 circuit breaker, timeouts, and batch fetching.
 *
 * Traceability:
 * - REQ-DAT-014: Internet Digital Elevation Model (DEM) Reference Altitude Retrieval & Spatial Caching.
 * - TST-DAT-008: Internet DEM Reference Altitude Retrieval, Schema V5, Healing & Resilience Test.
 */
class ElevationServiceTest {

    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: Call
    private lateinit var service: ElevationService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockClient = mockk(relaxed = true)
        mockCall = mockk(relaxed = true)
        every { mockClient.newCall(any()) } returns mockCall

        service = ElevationService(mockClient)
        service.resetCircuitBreaker()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-DAT-008.1: Nominal DEM API Retrieval Unit Test.
     * Verifies 200 OK parsing, 5-decimal quantization, and Success result encapsulation.
     */
    @Test
    fun testFetchElevation_nominalSuccess() {
        val request = Request.Builder().url("https://api.open-meteo.com/v1/elevation?latitude=48.13715&longitude=11.57612").build()
        val responseBody = """{"elevation": [312.5]}""".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        every { mockCall.execute() } returns response

        val result = service.fetchElevation(48.13715123, 11.57612456)

        assertTrue("Result must be Success", result is ElevationResult.Success)
        val success = result as ElevationResult.Success
        assertEquals(312.5, success.elevationMeters, 0.001)
        assertEquals(48.13715, success.latitude, 0.000001)
        assertEquals(11.57612, success.longitude, 0.000001)
        assertFalse("Circuit breaker must remain closed", service.isCircuitBreakerOpen())
    }

    /**
     * Verifies coordinate quantization to 5 decimal places (~1.1m precision).
     */
    @Test
    fun testCoordinateQuantization() {
        assertEquals(48.13715, ElevationService.quantizeCoordinate(48.13715123), 0.000001)
        assertEquals(48.13715, ElevationService.quantizeCoordinate(48.13715499), 0.000001)
        assertEquals("48.13715", ElevationService.formatQuantized(48.13715123))
        assertEquals("11.57612", ElevationService.formatQuantized(11.57612499))
    }

    /**
     * TST-DAT-008.3: HTTP Rate-Limit (429) & Backoff Test.
     * Verifies Retry-After parsing and RateLimited result.
     */
    @Test
    fun testFetchElevation_rateLimited429() {
        val request = Request.Builder().url("https://api.open-meteo.com/v1/elevation?latitude=48.13715&longitude=11.57612").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "120")
            .body("".toResponseBody(null))
            .build()

        every { mockCall.execute() } returns response

        val result = service.fetchElevation(48.13715, 11.57612)

        assertTrue("Result must be RateLimited", result is ElevationResult.RateLimited)
        val rateLimited = result as ElevationResult.RateLimited
        assertEquals(120L, rateLimited.retryAfterSeconds)
        assertTrue("Circuit breaker must be tripped by rate limit", service.isCircuitBreakerOpen())

        // Subsequent call must immediately return CircuitBreakerOpen without invoking client
        val nextResult = service.fetchElevation(48.13715, 11.57612)
        assertEquals(ElevationResult.CircuitBreakerOpen, nextResult)
    }

    /**
     * TST-DAT-008.4: Server Error (503/5xx) & Circuit Breaker Test.
     * Verifies 503 trips the 5-minute circuit breaker.
     */
    @Test
    fun testFetchElevation_serverError503_circuitBreaker() {
        val request = Request.Builder().url("https://api.open-meteo.com/v1/elevation?latitude=48.13715&longitude=11.57612").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(503)
            .message("Service Unavailable")
            .body("".toResponseBody(null))
            .build()

        every { mockCall.execute() } returns response

        val result = service.fetchElevation(48.13715, 11.57612)

        assertTrue("Result must be ServerError", result is ElevationResult.ServerError)
        val serverError = result as ElevationResult.ServerError
        assertEquals(503, serverError.statusCode)
        assertTrue("Circuit breaker must be tripped for 5 minutes", service.isCircuitBreakerOpen())

        // Subsequent call returns CircuitBreakerOpen
        val nextResult = service.fetchElevation(48.13715, 11.57612)
        assertEquals(ElevationResult.CircuitBreakerOpen, nextResult)
    }

    /**
     * TST-DAT-008.5: Timeout & Offline Fallback Test.
     * Verifies socket timeout returns NetworkError.
     */
    @Test
    fun testFetchElevation_timeoutOfflineFallback() {
        every { mockCall.execute() } throws SocketTimeoutException("Read timed out after 5000ms")

        val result = service.fetchElevation(48.13715, 11.57612)

        assertTrue("Result must be NetworkError", result is ElevationResult.NetworkError)
        val error = result as ElevationResult.NetworkError
        assertTrue("Error message must mention timeout", error.message.contains("timed out"))
    }

    /**
     * Verifies batch coordinate queries for legacy location healing.
     */
    @Test
    fun testFetchBatchElevations_nominalSuccess() {
        val request = Request.Builder().url("https://api.open-meteo.com/v1/elevation?latitude=48.13715,48.20000&longitude=11.57612,11.60000").build()
        val responseBody = """{"elevation": [312.5, 450.0]}""".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        every { mockCall.execute() } returns response

        val coords = listOf(
            LatLng(48.13715, 11.57612),
            LatLng(48.20000, 11.60000)
        )
        val result = service.fetchBatchElevations(coords)

        assertTrue("Result must be BatchSuccess", result is ElevationResult.BatchSuccess)
        val batch = result as ElevationResult.BatchSuccess
        assertEquals(2, batch.elevations.size)
        assertEquals(312.5, batch.elevations[0]!!, 0.001)
        assertEquals(450.0, batch.elevations[1]!!, 0.001)
    }

    @Test
    fun testFetchBatchElevations_emptyList_returnsEmptyBatch() {
        val result = service.fetchBatchElevations(emptyList())
        assertTrue(result is ElevationResult.BatchSuccess)
        assertTrue((result as ElevationResult.BatchSuccess).elevations.isEmpty())
    }
}
