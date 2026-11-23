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

import android.content.Context
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge
import com.atrainingtracker.banalservice.devices.hrv.HrvTelemetryProvider

/**
 * Pure-JVM deterministic arbitrator selecting the canonical primary `SensorType.HR` source across
 * multiple registered Heart Rate devices (`REQ-SET-126.3`, `ATT-9345`).
 *
 * Ordering tuple (strictly ascending):
 * `(healthTier [HEALTHY < DEGRADED < STALE_OR_NULL], modalityTier [ECG < ANT+ < Optical PPG < Other], registrationOrder)`.
 *
 * When ALL registered HR candidates are [HrHealthTier.STALE_OR_NULL], still returns the
 * highest-modality candidate (lowest modality rank, then registration order) so `ProxySensor`
 * retains its source binding and `mSensorsSensor` does not spuriously drop `H` while stale
 * sensors emit `null`.
 */
object PrimaryHrSensorArbitrator {

    /** Default staleness window for generic HR sensors lacking an explicit [HrSensorHealthProvider]. */
    const val DEFAULT_STALE_TIMEOUT_MS: Long = 15_000L

    /** Rolling optical HRV artifact percentage at or above which an optical HRM is treated as degraded. */
    const val OPTICAL_ARTIFACT_SATURATION_PERCENT: Double = 95.0

    /**
     * Selects the best primary HR sensor from [candidates] at wall-clock [nowMs].
     * All device/sensor reads are guarded defensively so non-relaxed test mocks never throw.
     */
    @JvmStatic
    @JvmOverloads
    fun selectBestHrSensor(
        candidates: List<MySensor<*>>,
        nowMs: Long = System.currentTimeMillis(),
        staleTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS
    ): MySensor<*>? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]

        var bestSensor: MySensor<*>? = null
        var bestHealthRank = Int.MAX_VALUE
        var bestModalityRank = Int.MAX_VALUE

        for (sensor in candidates) {
            val modality = resolveModality(sensor.device)
            val health = resolveHealthTier(sensor, modality, nowMs, staleTimeoutMs)
            val healthRank = health.rank
            val modalityRank = modality.rank

            val isBetter = bestSensor == null ||
                healthRank < bestHealthRank ||
                (healthRank == bestHealthRank && modalityRank < bestModalityRank)
            if (isBetter) {
                bestSensor = sensor
                bestHealthRank = healthRank
                bestModalityRank = modalityRank
            }
        }
        return bestSensor
    }

    @JvmStatic
    fun resolveModality(device: MyDevice?): HrModality {
        if (device == null) return HrModality.OTHER
        if (device is HrSensorHealthProvider) {
            runCatching { device.getHrModality() }.getOrNull()?.let { return it }
        }
        if (device is PolarOnlineStreamingBridge) {
            val isOptical = runCatching { device.isVeritySense }.getOrDefault(false)
            return if (isOptical) HrModality.OPTICAL_PPG else HrModality.ECG
        }
        if (device is BTLEHeartRateDevice) {
            return resolveModality(
                protocol = Protocol.BLUETOOTH_LE,
                deviceType = DeviceType.HRM,
                deviceName = runCatching { device.name }.getOrNull(),
                manufacturer = runCatching { device.manufacturerName }.getOrNull(),
                macAddress = runCatching { device.address }.getOrNull(),
                context = null
            )
        }
        if (device is ANTHeartRateDevice) {
            return HrModality.ANT_PLUS
        }
        return HrModality.OTHER
    }

    /**
     * Resolves [HrModality] from persisted device protocol and capability metadata (`REQ-SET-126.3`,
     * `REQ-UI-2804.1`), sharing the same classification rules as live [resolveModality].
     */
    @JvmStatic
    @JvmOverloads
    fun resolveModality(
        protocol: Protocol?,
        deviceType: DeviceType?,
        deviceName: String?,
        manufacturer: String? = null,
        macAddress: String? = null,
        context: Context? = null
    ): HrModality {
        if (deviceType != DeviceType.HRM) return HrModality.OTHER
        return when (protocol) {
            Protocol.BLUETOOTH_LE -> {
                val isOptical = runCatching {
                    PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
                        context,
                        deviceName ?: "",
                        manufacturer ?: "",
                        macAddress
                    )
                }.getOrElse {
                    runCatching {
                        PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
                            null,
                            deviceName ?: "",
                            manufacturer ?: "",
                            macAddress
                        )
                    }.getOrDefault(false)
                }
                if (isOptical) HrModality.OPTICAL_PPG else HrModality.ECG
            }
            Protocol.ANT_PLUS -> HrModality.ANT_PLUS
            else -> HrModality.OTHER
        }
    }

    /**
     * Resolves the current [HrHealthTier] for a live [sensor] at wall-clock [nowMs],
     * matching [selectBestHrSensor] (`REQ-SET-126.3`, `REQ-UI-2804.1`).
     */
    @JvmStatic
    @JvmOverloads
    fun resolveHealthTier(
        sensor: MySensor<*>,
        modality: HrModality = resolveModality(runCatching { sensor.device }.getOrNull()),
        nowMs: Long = System.currentTimeMillis(),
        staleTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS
    ): HrHealthTier {
        val activated = runCatching { sensor.isActivated }.getOrDefault(true)
        if (!activated) {
            return HrHealthTier.STALE_OR_NULL
        }

        val device = runCatching { sensor.device }.getOrNull()
        if (device is HrSensorHealthProvider) {
            runCatching { device.getHrHealthTier(nowMs) }.getOrNull()?.let { return it }
        }

        val opticalArtifactSaturated = modality == HrModality.OPTICAL_PPG &&
            device is HrvTelemetryProvider &&
            (runCatching { device.latestArtifactRatePercent }.getOrNull() ?: 0.0) >= OPTICAL_ARTIFACT_SATURATION_PERCENT

        val value = runCatching { sensor.value }.getOrNull()
        val numericHr = (value as? Number)?.toInt() ?: 0
        if (numericHr <= 0) {
            return HrHealthTier.STALE_OR_NULL
        }

        val lastUpdateMs = runCatching { sensor.lastUpdateTimestamp }.getOrDefault(0L)
        if (lastUpdateMs > 0L && (nowMs - lastUpdateMs) > staleTimeoutMs) {
            return HrHealthTier.STALE_OR_NULL
        }

        return if (opticalArtifactSaturated) HrHealthTier.DEGRADED else HrHealthTier.HEALTHY
    }

    /**
     * Resolves `(hrHealthTier, hrModality)` for a live [device] carrying `SensorType.HR`,
     * or `(null, null)` for non-HR devices (`REQ-UI-2804.2`).
     */
    @JvmStatic
    @JvmOverloads
    fun resolveDeviceHrTelemetryMetadata(
        device: MyDevice,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<HrHealthTier?, HrModality?> {
        val hrSensor = runCatching { device.getSensor(SensorType.HR) }.getOrNull() ?: return null to null
        val modality = resolveModality(device)
        return resolveHealthTier(hrSensor, modality, nowMs) to modality
    }
}
