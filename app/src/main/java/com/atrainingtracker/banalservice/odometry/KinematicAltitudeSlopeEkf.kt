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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Coupled Kinematic Terrain-Slope Extended Kalman Filter Engine (REQ-FIL-016.1, REQ-FIL-016.2, ATT-6279).
 *
 * Couples vertical elevation progression $h_k$ directly to horizontal displacement $\Delta s_k$:
 * $$h_k = h_{k-1} + g_{k-1} \cdot \Delta s_k$$
 * where $g = \frac{dh}{ds}$ represents the terrain slope grade (dimensionless ratio).
 *
 * Observability Modes:
 * 1. 2-State Mode: $[h_k, g_k]^T$ (Unanchored: Barometer + Speed). Standstill clamping prevents false climb.
 * 2. 3-State Mode: $[h_k, g_k, b_{baro,k}]^T$ (Anchored: Barometer + DEM/GPS + Speed). Isolates barometric
 *    thermal/weather drift into $b_{baro}$ while preserving continuous smooth elevation without DEM grid steps.
 */
@Suppress("MagicNumber", "TooManyFunctions")
class KinematicAltitudeSlopeEkf(
    var is3StateMode: Boolean = false,
    val qg: Double = DEFAULT_Q_SLOPE,
    val qh: Double = DEFAULT_Q_ALTITUDE,
    val qBias: Double = DEFAULT_Q_BIAS,
    val rBaro: Double = DEFAULT_VAR_BARO,
    val rDem: Double = DEFAULT_VAR_DEM,
    val rGps: Double = DEFAULT_VAR_GPS,
    val huberK: Double = DEFAULT_HUBER_K,
    val standstillSpeedThresholdMps: Double = DEFAULT_STANDSTILL_THRESHOLD_MPS
) {

    companion object {
        const val DEFAULT_Q_SLOPE = 1e-4 // Process noise per meter of travel
        const val DEFAULT_Q_ALTITUDE = 1e-3 // Vertical process noise per second of travel
        const val DEFAULT_Q_BIAS = 1e-4 // Baro bias process noise per second
        const val DEFAULT_VAR_BARO = 0.25 // (0.5 m)^2
        const val DEFAULT_VAR_DEM = 4.0 // (2.0 m)^2
        const val DEFAULT_VAR_GPS = 16.0 // (4.0 m)^2
        const val DEFAULT_HUBER_K = 3.0 // 3-sigma innovation clamp
        const val DEFAULT_STANDSTILL_THRESHOLD_MPS = 0.3
    }

    var altitude: Double = 0.0
        private set

    var slope: Double = 0.0
        private set

    val slopePercent: Double
        get() = slope * 100.0

    var baroBias: Double = 0.0
        private set

    var isInitialized: Boolean = false
        private set

    // Covariance elements
    var p00: Double = 1.0 // var(h)
    var p01: Double = 0.0 // cov(h, g)
    var p10: Double = 0.0 // cov(g, h)
    var p11: Double = 0.01 // var(g)
    var p02: Double = 0.0 // cov(h, b_baro)
    var p20: Double = 0.0 // cov(b_baro, h)
    var p12: Double = 0.0 // cov(g, b_baro)
    var p21: Double = 0.0 // cov(b_baro, g)
    var p22: Double = 100.0 // var(b_baro)

    fun reset(initialAlt: Double = 0.0, initialSlope: Double = 0.0, initialBias: Double = 0.0) {
        altitude = initialAlt
        slope = initialSlope
        baroBias = initialBias
        p00 = rBaro
        p01 = 0.0
        p10 = 0.0
        p11 = 0.01
        p02 = 0.0
        p20 = 0.0
        p12 = 0.0
        p21 = 0.0
        p22 = 100.0
        isInitialized = false
    }

    fun setMode(threeState: Boolean) {
        if (is3StateMode == threeState) return
        is3StateMode = threeState
        if (threeState) {
            baroBias = 0.0
            p02 = 0.0
            p20 = 0.0
            p12 = 0.0
            p21 = 0.0
            p22 = 100.0
        }
    }

    /**
     * Propagates state and covariance forward across $\Delta t$ seconds at speed $v$.
     * Enforces standstill clamping when $v < v_{threshold}$ ($\Delta s = 0 \implies dh = 0, Q_g = 0$).
     */
    fun predict(dtSec: Double, speedMps: Double, deltaDistM: Double = speedMps * dtSec) {
        if (!isInitialized || dtSec <= 0.0) return

        val speed = max(0.0, speedMps)
        val isStandstill = speed < standstillSpeedThresholdMps || deltaDistM < 1e-5
        val deltaDist = if (isStandstill) 0.0 else max(0.0, deltaDistM)

        if (isStandstill) {
            // Standstill clamping: dh = 0, Qg = 0, Qh = 0
            if (is3StateMode) {
                p22 += qBias * dtSec
            }
            return
        }

        // Kinematic state propagation: h_k = h_{k-1} + g_{k-1} * deltaDist
        altitude += slope * deltaDist

        // Covariance propagation: P^- = F * P * F^T + Q
        val qgVal = qg * deltaDist
        val qhVal = qh * dtSec

        val p00New = p00 + 2.0 * deltaDist * p01 + deltaDist * deltaDist * p11 + qhVal
        val p01New = p01 + deltaDist * p11
        val p11New = p11 + qgVal

        p00 = p00New
        p01 = p01New
        p10 = p01New
        p11 = p11New

        if (is3StateMode) {
            val p02New = p02 + deltaDist * p12
            p02 = p02New
            p20 = p02New
            p22 += qBias * dtSec
        }
    }

    /**
     * Ingests a barometric altitude measurement (slew-clamped, without EMA).
     */
    @Suppress("ReturnCount")
    fun updateBarometer(baroAltM: Double, varBaro: Double = rBaro, isStandstill: Boolean = false) {
        if (baroAltM.isNaN() || baroAltM.isInfinite()) return

        if (!isInitialized) {
            altitude = baroAltM
            slope = 0.0
            baroBias = 0.0
            p00 = varBaro
            p11 = 0.01
            p22 = 100.0
            isInitialized = true
            return
        }

        if (isStandstill) {
            // In standstill mode: ground altitude is clamped (dh = 0).
            // In 3-State mode: barometric drift updates baroBias directly.
            if (is3StateMode) {
                val y = baroAltM - (altitude + baroBias)
                val s = p22 + varBaro
                val yH = applyHuber(y, s)
                val kBias = p22 / s
                baroBias += kBias * yH
                p22 = max(1e-6, (1.0 - kBias) * p22)
            }
            return
        }

        if (!is3StateMode) {
            // 2-State update: z = h
            val y = baroAltM - altitude
            val s = p00 + varBaro
            val yH = applyHuber(y, s)
            val k0 = p00 / s
            val k1 = p10 / s

            altitude += k0 * yH
            slope += k1 * yH

            p00 = max(1e-6, (1.0 - k0) * p00)
            p01 = (1.0 - k0) * p01
            p10 = p01
            p11 = max(1e-8, p11 - k1 * p01)
        } else {
            // 3-State update: z = h + b_baro
            val y = baroAltM - (altitude + baroBias)
            val s = p00 + 2.0 * p02 + p22 + varBaro
            val yH = applyHuber(y, s)

            val k0 = (p00 + p02) / s
            val k1 = (p10 + p12) / s
            val k2 = (p20 + p22) / s

            altitude += k0 * yH
            slope += k1 * yH
            baroBias += k2 * yH

            val v0 = p00 + p20
            val v1 = p01 + p21
            val v2 = p02 + p22

            p00 = max(1e-6, p00 - k0 * v0)
            p01 -= k0 * v1
            p02 -= k0 * v2
            p10 = p01
            p11 = max(1e-8, p11 - k1 * v1)
            p12 -= k1 * v2
            p20 = p02
            p21 = p12
            p22 = max(1e-6, p22 - k2 * v2)
        }
    }

    /**
     * Ingests an absolute reference altitude (DEM or GPS elevation).
     */
    fun updateReferenceAltitude(refAltM: Double, varRef: Double = rDem) {
        if (refAltM.isNaN() || refAltM.isInfinite()) return

        if (!isInitialized) {
            altitude = refAltM
            slope = 0.0
            baroBias = 0.0
            p00 = varRef
            p11 = 0.01
            p22 = 100.0
            isInitialized = true
            return
        }

        if (!is3StateMode) {
            val y = refAltM - altitude
            val s = p00 + varRef
            val yH = applyHuber(y, s)
            val k0 = p00 / s
            val k1 = p10 / s

            altitude += k0 * yH
            slope += k1 * yH

            p00 = max(1e-6, (1.0 - k0) * p00)
            p01 = (1.0 - k0) * p01
            p10 = p01
            p11 = max(1e-8, p11 - k1 * p01)
        } else {
            // In 3-State mode: z = h (H = [1, 0, 0])
            val y = refAltM - altitude
            val s = p00 + varRef
            val yH = applyHuber(y, s)

            val k0 = p00 / s
            val k1 = p10 / s
            val k2 = p20 / s

            altitude += k0 * yH
            slope += k1 * yH
            baroBias += k2 * yH

            val v0 = p00
            val v1 = p01
            val v2 = p02

            p00 = max(1e-6, p00 - k0 * v0)
            p01 -= k0 * v1
            p02 -= k0 * v2
            p10 = p01
            p11 = max(1e-8, p11 - k1 * v1)
            p12 -= k1 * v2
            p20 = p02
            p21 = p12
            p22 = max(1e-6, p22 - k2 * v2)
        }
    }

    fun getVerticalSpeedMps(speedMps: Double): Double {
        return if (speedMps < standstillSpeedThresholdMps) 0.0 else slope * speedMps
    }

    fun getVamMph(speedMps: Double): Double {
        return getVerticalSpeedMps(speedMps) * 3600.0
    }

    private fun applyHuber(residual: Double, variance: Double): Double {
        val stdDev = sqrt(max(1e-9, variance))
        val limit = huberK * stdDev
        val absRes = abs(residual)
        return if (absRes <= limit || absRes < 1e-9) {
            residual
        } else {
            (limit / absRes) * residual
        }
    }
}
