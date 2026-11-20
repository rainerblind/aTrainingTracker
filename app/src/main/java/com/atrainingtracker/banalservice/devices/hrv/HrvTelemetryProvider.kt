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

package com.atrainingtracker.banalservice.devices.hrv

import com.atrainingtracker.banalservice.devices.DeviceManager

/**
 * Exposes live HRV signal-quality and rolling artifact-rate metadata for both the global
 * arbitrated [HRVMetricsDevice] and individual RR-capable HRM devices (`REQ-HRV-050.2`).
 */
interface HrvTelemetryProvider {
    val signalQualityResult: HrvSignalQualityResult
    val latestArtifactRatePercent: Double?
}

/**
 * Resolves the authoritative [HrvTelemetryProvider] for a cockpit tile or dialog without adding
 * methods to [DeviceManager] (`REQ-HRV-050.2`, `REQ-HRV-050.4`).
 */
object HrvDeviceLookup {

    /**
     * Returns the per-device [HrvTelemetryProvider] when [deviceName] is non-null/non-blank, or
     * falls back to the global arbitrated [HRVMetricsDevice] when [deviceName] is null/blank
     * ("Best Sensor").
     */
    @JvmStatic
    fun resolveProvider(
        deviceManager: DeviceManager?,
        deviceName: String?
    ): HrvTelemetryProvider? {
        if (deviceManager == null) {
            return null
        }
        return if (!deviceName.isNullOrEmpty()) {
            deviceManager.getMyDeviceByName(deviceName) as? HrvTelemetryProvider
        } else {
            deviceManager.hrvMetricsDevice
        }
    }
}
