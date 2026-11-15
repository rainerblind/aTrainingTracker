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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller for Polar H10 offline RR interval exercise recording (ATT-7762, REQ-SET-104).
 *
 * Implements Android 13+ (API 33) isolation, GATT handoff/arbitration with [MyBTLEDevice],
 * automated device clock synchronization, stale exercise cleanup, and single-shot [PolarH10OfflineExerciseApi.SampleType.RR]
 * recording arming.
 */
object PolarH10OfflineController {

    private const val TAG = "PolarH10OfflineCtrl"
    const val ARMING_TIMEOUT_MS = 15_000L

    private val isArmed = AtomicBoolean(false)
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @VisibleForTesting
    var dispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    var apiOverride: PolarBleApi? = null

    @VisibleForTesting
    var sdkIntOverride: Int? = null

    internal fun getEffectiveSdkInt(): Int = sdkIntOverride ?: Build.VERSION.SDK_INT

    @JvmStatic
    fun isRecordingArmed(): Boolean = isArmed.get()

    @VisibleForTesting
    fun setArmedForTesting(armed: Boolean) {
        isArmed.set(armed)
    }

    @VisibleForTesting
    fun resetForTesting() {
        isArmed.set(false)
        apiOverride = null
        sdkIntOverride = null
    }

    /**
     * Entry point triggered upon workout start (e.g. from TrackerService).
     *
     * Non-blocking, preserves fringe-flap invariant (never re-arms during active session).
     */
    @JvmStatic
    fun onWorkoutStarted(context: Context) {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "API level < 33 (current: ${getEffectiveSdkInt()}); Polar H10 offline recording unavailable.")
            return
        }

        if (isArmed.get()) {
            Log.d(TAG, "Polar H10 offline recording already armed for active session; preserving fringe-flap invariance.")
            return
        }

        val appContext = context.applicationContext
        scope.launch(dispatcher) {
            try {
                armForActiveWorkout(appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "Error arming Polar H10 offline recording: ${t.message}", t)
            }
        }
    }

    /**
     * Entry point triggered upon workout stop (from TrackerService).
     */
    @JvmStatic
    fun onWorkoutStopped(context: Context) {
        isArmed.set(false)
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val api = apiOverride
        if (api != null) {
            try {
                api.shutDown()
            } catch (t: Throwable) {
                Log.w(TAG, "Error shutting down test PolarBleApi: ${t.message}", t)
            }
        }
    }

    /**
     * Finds active Polar H10 device and executes single-shot time-sync and recording arming.
     */
    suspend fun armForActiveWorkout(context: Context): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        if (isArmed.get()) {
            return true
        }

        val db = DevicesDatabaseManager.getInstance(context)
        val devices = db.allDevicesForCache
        var targetMac: String? = null
        var targetId: String? = null

        for (dev in devices) {
            if (dev.type == DeviceType.HRM && dev.protocol == Protocol.BLUETOOTH_LE) {
                val name = dev.name ?: ""
                val isH10 = name.contains("Polar H10", ignoreCase = true) ||
                        (name.contains("Polar", ignoreCase = true) && name.contains("H10", ignoreCase = true))
                if (isH10) {
                    val enabled = db.isPolarH10OfflineRecordingEnabled(dev.id)
                    if (enabled) {
                        val mac = db.getBluetoothMACAddress(dev.id)
                        if (!mac.isNullOrEmpty()) {
                            targetMac = mac
                            val match = Regex("""\b([0-9A-Fa-f]{8})\b""").find(name)
                            targetId = match?.value ?: mac
                            break
                        }
                    }
                }
            }
        }

        if (targetMac == null || targetId == null) {
            Log.d(TAG, "No enabled Polar H10 device found in database.")
            return false
        }

        return executeArming(context, targetMac, targetId)
    }

    suspend fun executeArming(context: Context, macAddress: String, polarId: String): Boolean {
        val deviceManager = BANALService.getDeviceManager()
        val btleDevice = deviceManager?.getBtleDeviceByAddress(macAddress)

        // Step 1: Pause GATT operations on MyBTLEDevice to avoid reconnect collisions
        btleDevice?.pauseGattOperations()
        try {
            return withTimeoutOrNull(ARMING_TIMEOUT_MS) {
                val api = apiOverride ?: PolarBleApiDefaultImpl.defaultImplementation(
                    context,
                    setOf(
                        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
                    )
                )

                try {
                    api.connectToDevice(polarId)
                    api.waitForConnection(polarId)

                    // 1. Synchronize device clock
                    try {
                        api.setLocalTime(polarId, LocalDateTime.now())
                        Log.d(TAG, "Polar H10 device clock synchronized for $polarId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to synchronize device clock for $polarId: ${e.message}")
                    }

                    // 2. Check and stop ongoing exercise if any
                    try {
                        val status = api.requestRecordingStatus(polarId)
                        if (status.ongoing) {
                            Log.d(TAG, "Stopping ongoing recording on $polarId (entryId: ${status.entryId})")
                            api.stopRecording(polarId)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not query/stop recording status for $polarId: ${e.message}")
                    }

                    // 3. Remove stale exercises
                    try {
                        val staleExercises = api.listExercises(polarId).toList()
                        for (entry in staleExercises) {
                            Log.d(TAG, "Removing stale exercise ${entry.path} on $polarId")
                            api.removeExercise(polarId, entry)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clear stale exercises on $polarId: ${e.message}")
                    }

                    // 4. Single-shot arm recording with SampleType.RR
                    val exerciseId = "ATT_${System.currentTimeMillis()}"
                    api.startRecording(
                        polarId,
                        exerciseId,
                        PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                        PolarH10OfflineExerciseApi.SampleType.RR
                    )
                    Log.i(TAG, "Successfully armed Polar H10 offline RR recording on $polarId (id: $exerciseId)")

                    isArmed.set(true)

                    try {
                        api.disconnectFromDevice(polarId)
                    } catch (ignored: Exception) {
                    }

                    true
                } finally {
                    if (apiOverride == null) {
                        try {
                            api.cleanup()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error cleaning up PolarBleApi: ${t.message}")
                        }
                    }
                }
            } ?: run {
                Log.w(TAG, "Arming Polar H10 offline recording timed out after $ARMING_TIMEOUT_MS ms")
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during Polar H10 arming: ${t.message}", t)
            return false
        } finally {
            // Always resume GATT operations so live 0x180D streaming is never broken
            btleDevice?.resumeGattOperations()
        }
    }
}
