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
import androidx.annotation.VisibleForTesting
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Service client for querying Digital Elevation Models (DEM) from the Open-Meteo Elevation API.
 *
 * Traceability:
 * - REQ-DAT-014: Internet Digital Elevation Model (DEM) Reference Altitude Retrieval & Spatial Caching.
 * - TST-DAT-008: Internet DEM Reference Altitude Retrieval, Schema V5, Healing & Resilience Test.
 */
class ElevationService @VisibleForTesting constructor(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    companion object {
        private const val TAG = "ElevationService"
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/elevation"
        private const val TIMEOUT_SECONDS = 5L
        private const val CIRCUIT_BREAKER_COOLDOWN_MS = 300_000L // 5 minutes

        @Volatile
        private var instance: ElevationService? = null

        @JvmStatic
        fun getInstance(): ElevationService {
            return instance ?: synchronized(this) {
                instance ?: ElevationService(
                    OkHttpClient.Builder()
                        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build()
                ).also { instance = it }
            }
        }

        /**
         * Quantizes a geographical coordinate to 5 decimal places (~1.11m precision).
         * Eliminates microscopic GPS jitter to prevent redundant API queries.
         */
        @JvmStatic
        fun quantizeCoordinate(coord: Double): Double {
            return (coord * 100000.0).roundToLong() / 100000.0
        }

        @JvmStatic
        fun formatQuantized(coord: Double): String {
            return String.format(Locale.US, "%.5f", quantizeCoordinate(coord))
        }
    }

    @Volatile
    private var circuitBreakerOpenUntilMs: Long = 0L

    @VisibleForTesting
    fun resetCircuitBreaker() {
        circuitBreakerOpenUntilMs = 0L
    }

    @VisibleForTesting
    fun isCircuitBreakerOpen(): Boolean {
        return System.currentTimeMillis() < circuitBreakerOpenUntilMs
    }

    /**
     * Synchronously fetches the topographic elevation for a single coordinate point.
     * Must be called from a background thread (e.g., [Dispatchers.IO]).
     */
    fun fetchElevation(latitude: Double, longitude: Double): ElevationResult {
        if (System.currentTimeMillis() < circuitBreakerOpenUntilMs) {
            Log.w(TAG, "Circuit breaker is OPEN. Skipping outbound elevation query.")
            return ElevationResult.CircuitBreakerOpen
        }

        val qLat = formatQuantized(latitude)
        val qLng = formatQuantized(longitude)
        val url = "$baseUrl?latitude=$qLat&longitude=$qLng"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "aTrainingTracker/Android")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            ElevationResult.NetworkError("Empty response body from elevation API")
                        } else {
                            val json = JSONObject(body)
                            val elevationArray = json.optJSONArray("elevation")
                            if (elevationArray != null && elevationArray.length() > 0 && !elevationArray.isNull(0)) {
                                val elevation = elevationArray.getDouble(0)
                                ElevationResult.Success(
                                    elevationMeters = elevation,
                                    latitude = quantizeCoordinate(latitude),
                                    longitude = quantizeCoordinate(longitude)
                                )
                            } else {
                                ElevationResult.NetworkError("Invalid or missing elevation array in JSON response: $body")
                            }
                        }
                    }
                    429 -> {
                        val retryHeader = response.header("Retry-After")
                        val backoffSeconds = retryHeader?.toLongOrNull() ?: 60L
                        circuitBreakerOpenUntilMs = System.currentTimeMillis() + (backoffSeconds * 1000L)
                        Log.w(TAG, "Open-Meteo rate limit (429) hit. Backoff for $backoffSeconds seconds.")
                        ElevationResult.RateLimited(backoffSeconds)
                    }
                    in 500..599 -> {
                        circuitBreakerOpenUntilMs = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS
                        Log.w(TAG, "Open-Meteo server error (${response.code}). Circuit breaker tripped for 5 minutes.")
                        ElevationResult.ServerError(response.code)
                    }
                    else -> {
                        ElevationResult.ServerError(response.code)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Socket timeout fetching elevation for ($qLat, $qLng): ${e.message}")
            ElevationResult.NetworkError("Connection timed out after ${TIMEOUT_SECONDS}s")
        } catch (e: IOException) {
            Log.w(TAG, "I/O error fetching elevation for ($qLat, $qLng): ${e.message}")
            ElevationResult.NetworkError(e.message ?: "I/O Network Exception")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching elevation: ${e.message}", e)
            ElevationResult.NetworkError(e.message ?: "Unexpected Exception")
        }
    }

    /**
     * Asynchronously fetches the topographic elevation using Kotlin Coroutines on [Dispatchers.IO].
     */
    suspend fun getElevationAsync(latitude: Double, longitude: Double): ElevationResult =
        withContext(Dispatchers.IO) {
            fetchElevation(latitude, longitude)
        }

    /**
     * Fetches elevations for multiple coordinate points in a single HTTP request.
     * Useful for batch healing legacy locations.
     */
    fun fetchBatchElevations(coordinates: List<LatLng>): ElevationResult {
        if (coordinates.isEmpty()) return ElevationResult.BatchSuccess(emptyList())

        if (System.currentTimeMillis() < circuitBreakerOpenUntilMs) {
            Log.w(TAG, "Circuit breaker is OPEN. Skipping batch elevation query.")
            return ElevationResult.CircuitBreakerOpen
        }

        val latString = coordinates.joinToString(",") { formatQuantized(it.latitude) }
        val lngString = coordinates.joinToString(",") { formatQuantized(it.longitude) }
        val url = "$baseUrl?latitude=$latString&longitude=$lngString"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "aTrainingTracker/Android")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            ElevationResult.NetworkError("Empty response body from batch elevation API")
                        } else {
                            val json = JSONObject(body)
                            val elevationArray = json.optJSONArray("elevation")
                            if (elevationArray != null) {
                                val elevations = mutableListOf<Double?>()
                                for (i in 0 until elevationArray.length()) {
                                    if (elevationArray.isNull(i)) {
                                        elevations.add(null)
                                    } else {
                                        elevations.add(elevationArray.getDouble(i))
                                    }
                                }
                                ElevationResult.BatchSuccess(elevations)
                            } else {
                                ElevationResult.NetworkError("Invalid or missing elevation array in JSON response: $body")
                            }
                        }
                    }
                    429 -> {
                        val retryHeader = response.header("Retry-After")
                        val backoffSeconds = retryHeader?.toLongOrNull() ?: 60L
                        circuitBreakerOpenUntilMs = System.currentTimeMillis() + (backoffSeconds * 1000L)
                        ElevationResult.RateLimited(backoffSeconds)
                    }
                    in 500..599 -> {
                        circuitBreakerOpenUntilMs = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS
                        ElevationResult.ServerError(response.code)
                    }
                    else -> {
                        ElevationResult.ServerError(response.code)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching batch elevations: ${e.message}")
            ElevationResult.NetworkError(e.message ?: "Batch Elevation Exception")
        }
    }

    /**
     * Asynchronously fetches batch elevations on [Dispatchers.IO].
     */
    suspend fun getBatchElevationsAsync(coordinates: List<LatLng>): ElevationResult =
        withContext(Dispatchers.IO) {
            fetchBatchElevations(coordinates)
        }
}
