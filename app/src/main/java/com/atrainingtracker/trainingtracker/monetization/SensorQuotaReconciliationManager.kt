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

package com.atrainingtracker.trainingtracker.monetization

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicelist.KeepTwoSensorsDialogEvent
import com.atrainingtracker.trainingtracker.TrainingApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Centralized manager responsible for sensor pairing quota reconciliation when an athlete
 * downgrades to Free tier with > 2 paired sensors (REQ-SET-084.5).
 *
 * Evaluates paired sensor count against [MAX_FREE_PAIRED_DEVICES], emits [KeepTwoSensorsDialogEvent]
 * for global presentation across the app, and non-destructively unpairs unselected sensors
 * while strictly suppressing reconciliation during active tracking ([isTrackingProvider]).
 */
class SensorQuotaReconciliationManager @JvmOverloads constructor(
    private val context: Context,
    private val devicesDBRepository: DeviceDataRepository = DeviceDataRepository.getInstance(
        (context as? Application) ?: (context.applicationContext as? Application) ?: (context as Application)
    ),
    private val proFeatureManager: ProFeatureManager = ProFeatureManager.getInstance(context),
    private val isTrackingProvider: () -> Boolean = {
        try {
            TrainingApplication.isTracking()
        } catch (e: Throwable) {
            false
        }
    },
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    private val _keepTwoSensorsDialogEvent = MutableStateFlow<KeepTwoSensorsDialogEvent?>(null)
    val keepTwoSensorsDialogEvent: StateFlow<KeepTwoSensorsDialogEvent?> = _keepTwoSensorsDialogEvent.asStateFlow()

    /**
     * Checks if sensor quota reconciliation is required on Free tier (> 2 paired devices outside active tracking).
     *
     * @return true if reconciliation event was emitted, false otherwise.
     */
    fun checkDowngradeReconciliation(): Boolean {
        if (isTrackingProvider()) return false

        if (!proFeatureManager.isProActive.value) {
            val pairedDevices = devicesDBRepository.getPairedDevices()
            if (pairedDevices.size > MAX_FREE_PAIRED_DEVICES) {
                val defaultKeepIds = determineDefaultKeepDeviceIds(pairedDevices)
                _keepTwoSensorsDialogEvent.value = KeepTwoSensorsDialogEvent(
                    currentlyPairedDevices = pairedDevices,
                    defaultKeepDeviceIds = defaultKeepIds
                )
                return true
            }
        }
        return false
    }

    /**
     * Confirms the athlete's selection of up to 2 sensors to keep, non-destructively
     * unpairing all unselected devices in persistence (REQ-SET-084.5).
     */
    fun confirmKeepTwoSensors(selectedIds: Set<Long>) {
        coroutineScope.launch {
            devicesDBRepository.reconcilePairedDevices(selectedIds)
            _keepTwoSensorsDialogEvent.value = null
        }
    }

    /**
     * Dismisses the reconciliation sheet. To prevent bypassing the Free tier quota,
     * automatically applies the sensible default sensors (GPS + Barometer).
     */
    fun dismissKeepTwoSensors() {
        val event = _keepTwoSensorsDialogEvent.value
        coroutineScope.launch {
            if (event != null) {
                devicesDBRepository.reconcilePairedDevices(event.defaultKeepDeviceIds)
            }
            _keepTwoSensorsDialogEvent.value = null
        }
    }

    /**
     * Parses a device's [lastSeen] display string into epoch milliseconds for raw numeric sorting
     * (strictly conforming to coding standards Sec. 1.3 numeric primitive invariant).
     */
    fun parseLastSeenTimestamp(lastSeen: String?): Long {
        if (lastSeen.isNullOrBlank()) return 0L

        // 1. Localized standard DateFormat.getDateTimeInstance()
        try {
            val date = DateFormat.getDateTimeInstance().parse(lastSeen)
            if (date != null) return date.time
        } catch (_: Exception) {}

        // 2. Common fallback formats
        val fallbackPatterns = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
            "dd.MM.yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss"
        )
        for (pattern in fallbackPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(lastSeen)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        // 3. Raw epoch milliseconds representation
        try {
            return lastSeen.toLong()
        } catch (_: Exception) {}

        return 0L
    }

    /**
     * Determines 2 sensible default sensors to keep on Free tier downgrade (REQ-SET-084.5, ATT-5490):
     * - Slot 1: Primary location sensor (prefers [DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED] if both
     *   Google Fused and GPS are paired; otherwise preserves whichever single location sensor is paired).
     * - Slot 2: Most recently active remote sensor ([Protocol.SMARTPHONE] excluded), prioritized by
     *   connected status first ([DeviceUiData.isConnected]), latest [parseLastSeenTimestamp] second,
     *   and original list order third.
     * - Fallback: If < 2 sensors are selected, falls back to [DeviceType.ALTITUDE_FROM_PRESSURE]
     *   and remaining paired devices in list order.
     */
    fun determineDefaultKeepDeviceIds(pairedDevices: List<DeviceUiData>): Set<Long> {
        val keep = mutableSetOf<Long>()

        // Slot 1: Primary location sensor
        val fused = pairedDevices.firstOrNull { it.deviceType == DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED }
        val gps = pairedDevices.firstOrNull { it.deviceType == DeviceType.SPEED_AND_LOCATION_GPS }
        val locationDevice = if (fused != null && gps != null) {
            fused
        } else {
            fused ?: gps
        }
        if (locationDevice != null) {
            keep.add(locationDevice.id)
        }

        // Slot 2: Most recently active remote sensor (non-smartphone)
        val remoteSensors = pairedDevices
            .filter { it.protocol != Protocol.SMARTPHONE && it.id !in keep }
            .sortedWith(
                compareByDescending<DeviceUiData> { it.isConnected }
                    .thenByDescending { parseLastSeenTimestamp(it.lastSeen) }
            )
        val topRemote = remoteSensors.firstOrNull()
        if (topRemote != null && keep.size < MAX_FREE_PAIRED_DEVICES) {
            keep.add(topRemote.id)
        }

        // Fallback: Barometer and remaining devices if < MAX_FREE_PAIRED_DEVICES
        if (keep.size < MAX_FREE_PAIRED_DEVICES) {
            val baro = pairedDevices.firstOrNull { it.deviceType == DeviceType.ALTITUDE_FROM_PRESSURE && it.id !in keep }
            if (baro != null) {
                keep.add(baro.id)
            }
        }
        if (keep.size < MAX_FREE_PAIRED_DEVICES) {
            for (dev in pairedDevices) {
                if (keep.size >= MAX_FREE_PAIRED_DEVICES) break
                keep.add(dev.id)
            }
        }
        return keep
    }

    /**
     * Determines the recommended device to unpair when pairing a 3rd device on Free tier (REQ-SET-084.2, ATT-5490, ATT-5657):
     * 1. Recommends [DeviceType.ALTITUDE_FROM_PRESSURE] first if paired.
     * 2. If both Google Fused and GPS are paired, recommends [DeviceType.SPEED_AND_LOCATION_GPS] (retaining Google Fused).
     * 3. Prioritizes non-location sensors (disconnected first, oldest [parseLastSeenTimestamp] second, list order third)
     *    and NEVER recommends [DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED] or a sole location sensor if any non-location sensor exists.
     * 4. Otherwise falls back to the first paired device in list order.
     */
    fun determineRecommendedUnpairDeviceId(pairedDevices: List<DeviceUiData>): Long {
        val barometer = pairedDevices.firstOrNull { it.deviceType == DeviceType.ALTITUDE_FROM_PRESSURE }
        if (barometer != null) return barometer.id

        val hasFused = pairedDevices.any { it.deviceType == DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED }
        val gps = pairedDevices.firstOrNull { it.deviceType == DeviceType.SPEED_AND_LOCATION_GPS }
        if (hasFused && gps != null) return gps.id

        val nonLocationDevices = pairedDevices.filter {
            it.deviceType != DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED &&
                it.deviceType != DeviceType.SPEED_AND_LOCATION_GPS
        }
        if (nonLocationDevices.isNotEmpty()) {
            val candidate = nonLocationDevices.sortedWith(
                compareBy<DeviceUiData> { it.isConnected }
                    .thenBy { parseLastSeenTimestamp(it.lastSeen) }
            ).firstOrNull()
            if (candidate != null) return candidate.id
        }

        return pairedDevices.firstOrNull()?.id ?: -1L
    }

    companion object {
        const val MAX_FREE_PAIRED_DEVICES = 2

        @Volatile
        private var INSTANCE: SensorQuotaReconciliationManager? = null

        @JvmStatic
        fun getInstance(context: Context): SensorQuotaReconciliationManager {
            return INSTANCE ?: synchronized(this) {
                val effectiveContext = (context as? Application) ?: context.applicationContext ?: context
                INSTANCE ?: SensorQuotaReconciliationManager(effectiveContext).also {
                    INSTANCE = it
                }
            }
        }

        @VisibleForTesting
        fun setInstanceForTesting(instance: SensorQuotaReconciliationManager?) {
            INSTANCE = instance
        }
    }
}
