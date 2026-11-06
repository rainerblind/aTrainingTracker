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
     * Determines 2 sensible default sensors to keep (Speed/Location GPS and Barometer, or 2 most recent).
     */
    fun determineDefaultKeepDeviceIds(pairedDevices: List<DeviceUiData>): Set<Long> {
        val keep = mutableSetOf<Long>()
        val gps = pairedDevices.firstOrNull { it.deviceType == DeviceType.SPEED_AND_LOCATION_GPS }
        if (gps != null) keep.add(gps.id)
        val baro = pairedDevices.firstOrNull { it.deviceType == DeviceType.ALTITUDE_FROM_PRESSURE }
        if (baro != null && keep.size < MAX_FREE_PAIRED_DEVICES) keep.add(baro.id)
        if (keep.size < MAX_FREE_PAIRED_DEVICES) {
            for (dev in pairedDevices) {
                if (keep.size >= MAX_FREE_PAIRED_DEVICES) break
                keep.add(dev.id)
            }
        }
        return keep
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
