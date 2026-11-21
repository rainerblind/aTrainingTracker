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

package com.atrainingtracker.banalservice.sensor

/**
 * Dynamic health classification for a Heart Rate sensor participating in primary `SensorType.HR`
 * arbitration (`REQ-SET-126.3`, `ATT-9345`). Lower [rank] takes strictly higher precedence.
 */
enum class HrHealthTier(val rank: Int) {
    /** Sensor is active, receiving fresh valid HR (`> 0`), not flatlined, and not artifact-saturated. */
    HEALTHY(0),

    /** Sensor is active but degraded (constant-HR flatline or `>= 95%` optical HRV artifact saturation). */
    DEGRADED(1),

    /** Sensor has no valid HR yet, emitted persistent zero/lost-contact HR, or exceeded staleness timeout. */
    STALE_OR_NULL(2)
}

/**
 * Physiological measurement modality tier for Heart Rate sensors (`REQ-SET-126.3`, `ATT-9345`),
 * aligned with `DeviceManager` `ECG > ANT+ > PPG` RR arbitration. Lower [rank] wins ties within
 * the same [HrHealthTier].
 */
enum class HrModality(val rank: Int) {
    ECG(0),
    ANT_PLUS(1),
    OPTICAL_PPG(2),
    OTHER(3)
}

/**
 * Lock-free health and modality reporting contract implemented by Heart Rate devices so
 * [PrimaryHrSensorArbitrator] and [MySensorManager] can select the healthiest primary HR sensor
 * inside `synchronized (MySensorManager.this)` without acquiring any device locks (`REQ-SEN-021.3`,
 * `REQ-SET-126.3`).
 */
interface HrSensorHealthProvider {
    /**
     * Returns the current [HrHealthTier] at [nowMs]. Implementations MUST rely strictly on
     * `@Volatile` / atomic reads (zero lock acquisition).
     */
    fun getHrHealthTier(nowMs: Long): HrHealthTier

    /**
     * Returns the sensor's [HrModality] (`ECG`, `ANT_PLUS`, `OPTICAL_PPG`, or `OTHER`).
     * Implementations MUST be lock-free.
     */
    fun getHrModality(): HrModality

    /**
     * Convenience lock-free check returning `true` when the HR stream is degraded or stale.
     */
    fun isHrStreamDegraded(nowMs: Long = System.currentTimeMillis()): Boolean =
        getHrHealthTier(nowMs) != HrHealthTier.HEALTHY
}
