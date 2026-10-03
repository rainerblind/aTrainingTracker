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

/**
 * Result hierarchy for Digital Elevation Model (DEM) queries via [ElevationService].
 *
 * Traceability: REQ-DAT-014, TST-DAT-008.
 */
sealed class ElevationResult {
    /**
     * Successful single-point DEM elevation lookup.
     *
     * @param elevationMeters Topographic height above sea level in meters.
     * @param latitude Quantized latitude submitted in the query.
     * @param longitude Quantized longitude submitted in the query.
     */
    data class Success(
        val elevationMeters: Double,
        val latitude: Double,
        val longitude: Double
    ) : ElevationResult()

    /**
     * Successful multi-point batch DEM elevation lookup.
     *
     * @param elevations List of retrieved elevations in meters matching the order of requested coordinates.
     */
    data class BatchSuccess(
        val elevations: List<Double?>
    ) : ElevationResult()

    /**
     * Request rate-limited by remote API (HTTP 429).
     *
     * @param retryAfterSeconds Recommended backoff interval before retrying.
     */
    data class RateLimited(
        val retryAfterSeconds: Long = 60
    ) : ElevationResult()

    /**
     * Remote service error (HTTP 500, 502, 503, 504).
     *
     * @param statusCode HTTP response code.
     */
    data class ServerError(
        val statusCode: Int
    ) : ElevationResult()

    /**
     * Request blocked locally because the circuit breaker is currently OPEN following server errors.
     */
    data object CircuitBreakerOpen : ElevationResult()

    /**
     * Network connectivity failure, socket timeout, or I/O error.
     *
     * @param message Descriptive error message.
     */
    data class NetworkError(
        val message: String
    ) : ElevationResult()
}
