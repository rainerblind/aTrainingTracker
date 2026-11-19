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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings
import java.util.Deque
import java.util.function.Supplier

/**
 * Coordinates periodic long-term re-search scheduling outside [DeviceManager] after initial
 * sequential search tries (`mMyRemoteDeviceTries`) are exhausted (ATT-8965, REQ-CON-052.2).
 *
 * Keeps [DeviceManager] within its ratcheted monolith LoC ceiling while applying
 * [SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs] (`32s -> 64s -> 128s -> <finalPeriodMs>`)
 * uniformly across BLE and Polar streaming devices.
 */
class DeviceSearchPeriodicRetryCoordinator @JvmOverloads constructor(
    private val context: Context,
    initialScheduleDelayed: ((Long, Runnable) -> Unit)? = null,
    initialCancelScheduled: ((Runnable) -> Unit)? = null
) {
    private val lock = Any()
    private val lastScheduledDelayByDeviceId = HashMap<Long, Long>()
    private val pendingRunnablesByDeviceId = HashMap<Long, Runnable>()

    private val defaultHandler: Handler? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            Looper.getMainLooper()?.let { Handler(it) }
        } catch (_: Throwable) {
            null
        }
    }

    @Volatile
    private var scheduleDelayedFn: (Long, Runnable) -> Unit = initialScheduleDelayed ?: { delayMs, runnable ->
        defaultHandler?.postDelayed(runnable, delayMs)
    }

    @Volatile
    private var cancelScheduledFn: (Runnable) -> Unit = initialCancelScheduled ?: { runnable ->
        defaultHandler?.removeCallbacks(runnable)
    }

    @Volatile
    private var isShutDown = false

    @VisibleForTesting
    fun setSchedulerForTesting(
        scheduleDelayed: (Long, Runnable) -> Unit,
        cancelScheduled: (Runnable) -> Unit
    ) {
        scheduleDelayedFn = scheduleDelayed
        cancelScheduledFn = cancelScheduled
    }

    /**
     * Handles `SEARCHING_STOPPED_FOR_ONE_INTENT` queue progression for [current] while caller holds
     * `synchronized(searchQueue)`.
     */
    fun handleSearchStoppedForCurrentDevice(
        current: MyRemoteDevice,
        deviceId: Long,
        success: Boolean,
        remoteDevices: MutableMap<Long, MyRemoteDevice>,
        deviceTries: MutableMap<MyRemoteDevice, Int>,
        searchQueue: Deque<MyRemoteDevice>,
        currentlySearchingSupplier: Supplier<MyRemoteDevice?>,
        detachAndGiveUpDevice: Runnable,
        searchNextRunnable: Runnable
    ) {
        if (success) {
            onDeviceSearchSucceeded(deviceId)
            return
        }
        val tries = deviceTries[current]
        if (tries == null || tries <= 1) {
            if (!current.shouldKeepAliveOnSearchTimeout()) {
                val scheduled = scheduleRetryIfEnabled(
                    deviceId = deviceId,
                    onDisabledGiveUp = {
                        synchronized(searchQueue) {
                            if (isShutDown) return@synchronized
                            val dev = remoteDevices[deviceId]
                            if (dev === current && !dev.isReceivingData && currentlySearchingSupplier.get() !== dev && !searchQueue.contains(dev)) {
                                detachAndGiveUpDevice.run()
                            }
                        }
                    },
                    onRetryDue = {
                        enqueueDeviceForPeriodicResearch(
                            deviceId = deviceId,
                            remoteDevices = remoteDevices,
                            deviceTries = deviceTries,
                            searchQueue = searchQueue,
                            currentlySearchingSupplier = currentlySearchingSupplier,
                            searchNextRunnable = searchNextRunnable
                        )
                    }
                )
                if (!scheduled) {
                    detachAndGiveUpDevice.run()
                }
            }
        } else {
            deviceTries[current] = tries - 1
            searchQueue.addLast(current)
        }
    }

    private fun enqueueDeviceForPeriodicResearch(
        deviceId: Long,
        remoteDevices: MutableMap<Long, MyRemoteDevice>,
        deviceTries: MutableMap<MyRemoteDevice, Int>,
        searchQueue: Deque<MyRemoteDevice>,
        currentlySearchingSupplier: Supplier<MyRemoteDevice?>,
        searchNextRunnable: Runnable
    ) {
        synchronized(searchQueue) {
            if (isShutDown) return
            val device = remoteDevices[deviceId] ?: return
            if (device.isReceivingData || searchQueue.contains(device) || currentlySearchingSupplier.get() === device) {
                return
            }
            deviceTries[device] = 1
            searchQueue.addLast(device)
            if (currentlySearchingSupplier.get() == null) {
                searchNextRunnable.run()
            }
        }
    }

    @JvmOverloads
    fun scheduleRetryIfEnabled(
        deviceId: Long,
        onDisabledGiveUp: (() -> Unit)? = null,
        onRetryDue: () -> Unit
    ): Boolean {
        if (isShutDown || !SensorSearchSettings.isPeriodicResearchEnabled(context)) {
            cancelDevice(deviceId)
            return false
        }
        val finalPeriodMs = SensorSearchSettings.getPeriodicResearchFinalPeriodMs(context)
        lateinit var runnable: Runnable
        runnable = Runnable {
            val shouldExecute = synchronized(lock) {
                if (pendingRunnablesByDeviceId[deviceId] !== runnable) {
                    return@synchronized false
                }
                pendingRunnablesByDeviceId.remove(deviceId)
                !isShutDown
            }
            if (!shouldExecute) return@Runnable
            if (!SensorSearchSettings.isPeriodicResearchEnabled(context)) {
                synchronized(lock) {
                    lastScheduledDelayByDeviceId.remove(deviceId)
                }
                onDisabledGiveUp?.invoke()
                return@Runnable
            }
            onRetryDue()
        }
        synchronized(lock) {
            if (isShutDown) return false
            pendingRunnablesByDeviceId.remove(deviceId)?.let { cancelScheduledFn(it) }
            val previous = lastScheduledDelayByDeviceId[deviceId]
            val delayMs = SensorReconnectBackoffPolicy.nextPeriodicResearchDelayMs(previous, finalPeriodMs)
            lastScheduledDelayByDeviceId[deviceId] = delayMs
            pendingRunnablesByDeviceId[deviceId] = runnable
            scheduleDelayedFn(delayMs, runnable)
        }
        return true
    }

    fun onDeviceSearchSucceeded(deviceId: Long) {
        cancelDevice(deviceId)
    }

    fun cancelDevice(deviceId: Long) {
        val toCancel = synchronized(lock) {
            lastScheduledDelayByDeviceId.remove(deviceId)
            pendingRunnablesByDeviceId.remove(deviceId)
        }
        if (toCancel != null) {
            cancelScheduledFn(toCancel)
        }
    }

    fun cancelAllPending() {
        val toCancel = synchronized(lock) {
            val runnables = pendingRunnablesByDeviceId.values.toList()
            pendingRunnablesByDeviceId.clear()
            lastScheduledDelayByDeviceId.clear()
            runnables
        }
        for (runnable in toCancel) {
            cancelScheduledFn(runnable)
        }
    }

    fun shutDown() {
        isShutDown = true
        cancelAllPending()
    }

    @VisibleForTesting
    fun getLastDelayMsForTesting(deviceId: Long): Long? = synchronized(lock) {
        lastScheduledDelayByDeviceId[deviceId]
    }

    @VisibleForTesting
    fun hasPendingRetryForTesting(deviceId: Long): Boolean = synchronized(lock) {
        pendingRunnablesByDeviceId.containsKey(deviceId)
    }
}
