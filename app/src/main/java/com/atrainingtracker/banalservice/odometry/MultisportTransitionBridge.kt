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

package com.atrainingtracker.banalservice.odometry

import android.content.Intent
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType

/**
 * Intent codec and lock-free hardware telemetry snapshot helpers bridging
 * [MultisportTransitionDetector], [BANALService], [SpeedAndLocationDevice_ATTFused], and
 * `MultisportLapCoordinator` (`REQ-FIL-031.1`, `REQ-FIL-031.2`, `ATT-8917`).
 */
object MultisportTransitionBridge {
    const val EXTRA_FROM_SPORT = "com.atrainingtracker.banalservice.MS_FROM_SPORT"
    const val EXTRA_TO_SPORT = "com.atrainingtracker.banalservice.MS_TO_SPORT"
    const val EXTRA_EXIT_TIMESTAMP_MS = "com.atrainingtracker.banalservice.MS_EXIT_TS_MS"
    const val EXTRA_EXIT_ACTIVE_TIME_S = "com.atrainingtracker.banalservice.MS_EXIT_ACTIVE_S"
    const val EXTRA_EXIT_TOTAL_TIME_S = "com.atrainingtracker.banalservice.MS_EXIT_TOTAL_S"
    const val EXTRA_EXIT_DISTANCE_M = "com.atrainingtracker.banalservice.MS_EXIT_DIST_M"
    const val EXTRA_ENTER_TIMESTAMP_MS = "com.atrainingtracker.banalservice.MS_ENTER_TS_MS"
    const val EXTRA_ENTER_ACTIVE_TIME_S = "com.atrainingtracker.banalservice.MS_ENTER_ACTIVE_S"
    const val EXTRA_ENTER_TOTAL_TIME_S = "com.atrainingtracker.banalservice.MS_ENTER_TOTAL_S"
    const val EXTRA_ENTER_DISTANCE_M = "com.atrainingtracker.banalservice.MS_ENTER_DIST_M"
    const val EXTRA_CONFIRM_TIMESTAMP_MS = "com.atrainingtracker.banalservice.MS_CONFIRM_TS_MS"
    const val EXTRA_CONFIRM_ACTIVE_TIME_S = "com.atrainingtracker.banalservice.MS_CONFIRM_ACTIVE_S"
    const val EXTRA_CONFIRM_TOTAL_TIME_S = "com.atrainingtracker.banalservice.MS_CONFIRM_TOTAL_S"
    const val EXTRA_CONFIRM_DISTANCE_M = "com.atrainingtracker.banalservice.MS_CONFIRM_DIST_M"

    private const val ACTIVE_BIKE_SPEED_THRESHOLD_MPS = 0.3
    private const val ACTIVE_RUN_SPEED_THRESHOLD_MPS = 0.5

    /**
     * Serializes a confirmed [MultisportTransitionEvent] into a package-scoped
     * [BANALService.MULTISPORT_TRANSITION_INTENT].
     */
    @JvmStatic
    fun toIntent(event: MultisportTransitionEvent, packageName: String?): Intent {
        val intent = Intent(BANALService.MULTISPORT_TRANSITION_INTENT)
            .putExtra(EXTRA_FROM_SPORT, event.fromSport.name)
            .putExtra(EXTRA_TO_SPORT, event.toSport.name)
            .putExtra(EXTRA_EXIT_TIMESTAMP_MS, event.exitAnchor.timestampMs)
            .putExtra(EXTRA_EXIT_ACTIVE_TIME_S, event.exitAnchor.activeTimeS)
            .putExtra(EXTRA_EXIT_TOTAL_TIME_S, event.exitAnchor.totalTimeS)
            .putExtra(EXTRA_EXIT_DISTANCE_M, event.exitAnchor.totalDistanceM)
            .putExtra(EXTRA_ENTER_TIMESTAMP_MS, event.enterAnchor.timestampMs)
            .putExtra(EXTRA_ENTER_ACTIVE_TIME_S, event.enterAnchor.activeTimeS)
            .putExtra(EXTRA_ENTER_TOTAL_TIME_S, event.enterAnchor.totalTimeS)
            .putExtra(EXTRA_ENTER_DISTANCE_M, event.enterAnchor.totalDistanceM)
            .putExtra(EXTRA_CONFIRM_TIMESTAMP_MS, event.confirmAnchor.timestampMs)
            .putExtra(EXTRA_CONFIRM_ACTIVE_TIME_S, event.confirmAnchor.activeTimeS)
            .putExtra(EXTRA_CONFIRM_TOTAL_TIME_S, event.confirmAnchor.totalTimeS)
            .putExtra(EXTRA_CONFIRM_DISTANCE_M, event.confirmAnchor.totalDistanceM)
        if (!packageName.isNullOrEmpty()) {
            intent.setPackage(packageName)
        }
        return intent
    }

    /**
     * Deserializes a [BANALService.MULTISPORT_TRANSITION_INTENT] into a [MultisportTransitionEvent],
     * returning `null` if required sport extras are missing or invalid.
     */
    @JvmStatic
    fun fromIntent(intent: Intent?): MultisportTransitionEvent? {
        if (intent == null) return null
        val fromName = intent.getStringExtra(EXTRA_FROM_SPORT) ?: return null
        val toName = intent.getStringExtra(EXTRA_TO_SPORT) ?: return null
        val fromSport = runCatching { BSportType.valueOf(fromName) }.getOrNull() ?: return null
        val toSport = runCatching { BSportType.valueOf(toName) }.getOrNull() ?: return null
        if (fromSport != BSportType.BIKE && fromSport != BSportType.RUN) return null
        if (toSport != BSportType.BIKE && toSport != BSportType.RUN) return null
        if (fromSport == toSport) return null

        val exitAnchor = MultisportTelemetryAnchor(
            timestampMs = intent.getLongExtra(EXTRA_EXIT_TIMESTAMP_MS, 0L),
            activeTimeS = intent.getIntExtra(EXTRA_EXIT_ACTIVE_TIME_S, 0),
            totalTimeS = intent.getIntExtra(EXTRA_EXIT_TOTAL_TIME_S, 0),
            totalDistanceM = intent.getDoubleExtra(EXTRA_EXIT_DISTANCE_M, 0.0)
        )
        val enterAnchor = MultisportTelemetryAnchor(
            timestampMs = intent.getLongExtra(EXTRA_ENTER_TIMESTAMP_MS, exitAnchor.timestampMs),
            activeTimeS = intent.getIntExtra(EXTRA_ENTER_ACTIVE_TIME_S, exitAnchor.activeTimeS),
            totalTimeS = intent.getIntExtra(EXTRA_ENTER_TOTAL_TIME_S, exitAnchor.totalTimeS),
            totalDistanceM = intent.getDoubleExtra(EXTRA_ENTER_DISTANCE_M, exitAnchor.totalDistanceM)
        )
        val confirmAnchor = MultisportTelemetryAnchor(
            timestampMs = intent.getLongExtra(EXTRA_CONFIRM_TIMESTAMP_MS, enterAnchor.timestampMs),
            activeTimeS = intent.getIntExtra(EXTRA_CONFIRM_ACTIVE_TIME_S, enterAnchor.activeTimeS),
            totalTimeS = intent.getIntExtra(EXTRA_CONFIRM_TOTAL_TIME_S, enterAnchor.totalTimeS),
            totalDistanceM = intent.getDoubleExtra(EXTRA_CONFIRM_DISTANCE_M, enterAnchor.totalDistanceM)
        )
        return MultisportTransitionEvent(
            fromSport = fromSport,
            toSport = toSport,
            exitAnchor = exitAnchor,
            enterAnchor = enterAnchor,
            confirmAnchor = confirmAnchor
        )
    }

    /**
     * Inspects active remote devices lock-free to distinguish actively pedaling/rolling bike
     * sensors and active running footpods from parked/silent `0 W / 0 RPM / 0 m/s` sensors in `T2`.
     */
    @JvmStatic
    fun inspectHardwareSignals(deviceManager: DeviceManager?): MultisportHardwareSignals {
        if (deviceManager == null) return MultisportHardwareSignals()
        val activeDevices: List<MyRemoteDevice> = try {
            deviceManager.activeRemoteDevices ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        val hwSport = try {
            deviceManager.sportType
        } catch (_: Throwable) {
            BSportType.UNKNOWN
        }
        return inspectRemoteDevices(activeDevices, hwSport)
    }

    @JvmStatic
    fun inspectRemoteDevices(
        activeDevices: List<MyRemoteDevice>,
        hardwareSportType: BSportType
    ): MultisportHardwareSignals {
        var hasActiveBike = false
        var hasActiveRun = false
        for (device in activeDevices) {
            if (!device.isReceivingData) continue
            when (device.deviceType.sportType) {
                BSportType.BIKE -> {
                    val power = readDoubleSensor(device, SensorType.POWER)
                    val cadence = readDoubleSensor(device, SensorType.CADENCE)
                    val speed = readDoubleSensor(device, SensorType.SPEED_mps)
                    if (power > 0.0 || cadence > 0.0 || speed > ACTIVE_BIKE_SPEED_THRESHOLD_MPS) {
                        hasActiveBike = true
                    }
                }
                BSportType.RUN -> {
                    val speed = readDoubleSensor(device, SensorType.SPEED_mps)
                    val cadence = readDoubleSensor(device, SensorType.CADENCE)
                    if (speed > ACTIVE_RUN_SPEED_THRESHOLD_MPS || cadence > 0.0) {
                        hasActiveRun = true
                    }
                }
                else -> Unit
            }
        }
        return MultisportHardwareSignals(
            hasActiveBikeHardware = hasActiveBike,
            hasActiveRunHardware = hasActiveRun,
            hardwareSportType = hardwareSportType
        )
    }

    @JvmStatic
    fun captureAnchor(
        timestampMs: Long,
        activeTimeData: SensorData<*>?,
        totalTimeData: SensorData<*>?,
        distanceData: SensorData<*>?
    ): MultisportTelemetryAnchor {
        val activeS = (activeTimeData?.value as? Number)?.toInt() ?: 0
        val totalS = (totalTimeData?.value as? Number)?.toInt() ?: activeS
        val rawDist = (distanceData?.value as? Number)?.toDouble() ?: 0.0
        val safeDist = if (rawDist.isFinite() && rawDist >= 0.0) rawDist else 0.0
        return MultisportTelemetryAnchor(
            timestampMs = timestampMs,
            activeTimeS = activeS.coerceAtLeast(0),
            totalTimeS = totalS.coerceAtLeast(0),
            totalDistanceM = safeDist
        )
    }

    @JvmStatic
    fun evaluateAndDispatch(
        context: android.content.Context?,
        detector: MultisportTransitionDetector,
        mode: PhoneCarriageMode,
        kinematicSport: BSportType,
        timestampMs: Long
    ): MultisportTransitionEvent? {
        val service = context as? BANALService
        val isLocked = try { service?.isUserSportLocked ?: BANALService.isActiveUserSportLocked() } catch (_: Throwable) { false }
        val isTracking = try { com.atrainingtracker.trainingtracker.TrainingApplication.isTracking() } catch (_: Throwable) { false }
        val isPaused = try {
            com.atrainingtracker.trainingtracker.TrainingApplication.isPaused() ||
                com.atrainingtracker.trainingtracker.TrainingApplication.isAutoPaused()
        } catch (_: Throwable) { false }
        val hw = inspectHardwareSignals(BANALService.getDeviceManager())
        val anchor = captureAnchor(
            timestampMs = timestampMs,
            activeTimeData = BANALService.getActiveSensorData(SensorType.TIME_ACTIVE),
            totalTimeData = BANALService.getActiveSensorData(SensorType.TIME_TOTAL),
            distanceData = BANALService.getActiveSensorData(SensorType.DISTANCE_m)
        )
        val event = detector.onTick(mode, kinematicSport, hw, anchor, isTracking, isPaused, isLocked) ?: return null
        try {
            if (service != null) service.onConfirmedMultisportTransition(event)
            else BANALService.notifyActiveMultisportTransition(event)
        } catch (_: Throwable) {
        }
        return event
    }

    private fun readDoubleSensor(device: MyRemoteDevice, sensorType: SensorType): Double {
        return try {
            val v = (device.getSensor(sensorType)?.value as? Number)?.toDouble() ?: 0.0
            if (v.isFinite()) v else 0.0
        } catch (_: Throwable) {
            0.0
        }
    }
}
