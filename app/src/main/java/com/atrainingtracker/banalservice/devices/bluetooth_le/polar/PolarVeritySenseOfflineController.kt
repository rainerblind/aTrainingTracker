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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Controller for Polar Verity Sense (and compatible optical Polar sensors) offline HR recording
 * using `PolarOfflineRecordingApi` (`ATT-7923`, `REQ-SET-108`).
 *
 * Enforces Android 13+ (API 33) isolation, GATT arbitration with [MyBTLEDevice], device clock
 * synchronization, pre-existing HR recording cleanup, non-blocking `SYSTEM_BUSY` resilience, and
 * single-shot session arming (mid-workout `0x180D` BLE flaps never interrupt PFTP recording).
 */
@Suppress("TooGenericExceptionCaught")
object PolarVeritySenseOfflineController {

    private const val TAG = "PolarVerityOfflineCtrl"
    const val ARMING_TIMEOUT_MS = 15_000L
    const val FETCH_TIMEOUT_MS = 30_000L
    private const val ENTRY_MATCH_TOLERANCE_MS = 180_000L
    private const val SYSTEM_BUSY_ERROR_NAME = "SYSTEM_BUSY"

    private val REQUIRED_ARMING_FEATURES = setOf(
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
    )

    private val REQUIRED_OFFLINE_FEATURES = setOf(
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
    )

    /**
     * Diagnostic outcomes recorded during non-blocking arming attempts.
     */
    enum class ArmingOutcome {
        ARMED,
        SKIPPED_UNSUPPORTED_API,
        SKIPPED_ALREADY_ARMED,
        SKIPPED_NO_DEVICE,
        ERROR_FEATURE_TIMEOUT,
        ERROR_SYSTEM_BUSY,
        ERROR_FAILED
    }

    /**
     * Result container returned by [stopAndFetchOfflineHr] for downstream post-workout backfill (`ATT-7924`).
     */
    data class VerityOfflineHrFetchResult(
        val entry: PolarOfflineRecordingEntry?,
        val data: PolarOfflineRecordingData.HrOfflineRecording?,
        val stoppedSuccessfully: Boolean,
        val errorMessage: String? = null
    )

    private val isArmed = AtomicBoolean(false)
    private val isArmingInFlight = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var sessionStartTimeMs: Long = 0L

    @Volatile
    private var armedDeviceId: String? = null

    @Volatile
    private var armedMacAddress: String? = null

    @Volatile
    private var lastArmingOutcome: ArmingOutcome? = null

    @Volatile
    private var lastArmingError: String? = null

    @VisibleForTesting
    var dispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    var apiOverride: PolarBleApi? = null

    @VisibleForTesting
    var sdkIntOverride: Int? = null

    @VisibleForTesting
    var featureTimeoutMs: Long = PolarOfflineGattCoordinator.FEATURE_READY_TIMEOUT_MS

    internal fun getEffectiveSdkInt(): Int = sdkIntOverride ?: Build.VERSION.SDK_INT

    @JvmStatic
    fun isRecordingArmed(): Boolean = isArmed.get()

    @JvmStatic
    fun setSessionStartTimeMs(timeMs: Long) {
        sessionStartTimeMs = timeMs
    }

    @JvmStatic
    fun getSessionStartTimeMs(): Long = sessionStartTimeMs

    @JvmStatic
    fun getArmedDeviceId(): String? = armedDeviceId

    @JvmStatic
    fun getArmedMacAddress(): String? = armedMacAddress

    @JvmStatic
    fun getLastArmingOutcome(): ArmingOutcome? = lastArmingOutcome

    @JvmStatic
    fun getLastArmingError(): String? = lastArmingError

    @VisibleForTesting
    fun setArmedForTesting(armed: Boolean) {
        isArmed.set(armed)
    }

    @VisibleForTesting
    fun resetForTesting() {
        isArmed.set(false)
        isArmingInFlight.set(false)
        apiOverride = null
        sdkIntOverride = null
        dispatcher = Dispatchers.IO
        featureTimeoutMs = PolarOfflineGattCoordinator.FEATURE_READY_TIMEOUT_MS
        sessionStartTimeMs = 0L
        armedDeviceId = null
        armedMacAddress = null
        lastArmingOutcome = null
        lastArmingError = null
    }

    /**
     * Non-blocking workout-start hook triggered from `TrackerService` (`REQ-SET-108.2`, `REQ-SET-108.3`).
     * Enforces single-shot arming: mid-workout `0x180D` flaps or repeated callbacks never interrupt PFTP.
     */
    @JvmStatic
    fun onWorkoutStarted(context: Context) {
        val readyToLaunch = when {
            getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU -> {
                lastArmingOutcome = ArmingOutcome.SKIPPED_UNSUPPORTED_API
                false
            }
            isArmed.get() -> {
                lastArmingOutcome = ArmingOutcome.SKIPPED_ALREADY_ARMED
                Log.d(TAG, "Polar Verity Sense already armed; preserving mid-workout fringe-flap invariant.")
                false
            }
            !isArmingInFlight.compareAndSet(false, true) -> {
                Log.d(TAG, "Polar Verity Sense arming already in flight; skipping duplicate start trigger.")
                false
            }
            else -> true
        }
        if (readyToLaunch) {
            val appContext = context.applicationContext
            scope.launch(dispatcher) {
                try {
                    armForActiveWorkout(appContext)
                } catch (t: Throwable) {
                    recordFailure(t)
                } finally {
                    isArmingInFlight.set(false)
                }
            }
        }
    }

    /**
     * Workout-stop lifecycle hook resetting session arming state cleanly (`REQ-SET-108.2`).
     */
    @Suppress("UnusedParameter")
    @JvmStatic
    fun onWorkoutStopped(context: Context) {
        val wasArmed = isArmed.getAndSet(false)
        armedDeviceId = null
        armedMacAddress = null
        if (!wasArmed) {
            apiOverride?.let { api ->
                try {
                    api.shutDown()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error shutting down test PolarBleApi: ${t.message}", t)
                }
            }
        }
    }

    /**
     * Resolves an enabled Polar Verity Sense / optical offline-capable HRM and arms HR offline recording.
     */
    suspend fun armForActiveWorkout(context: Context): Boolean {
        val guardResult = checkPreArmGuard()
        if (guardResult != null) {
            return guardResult
        }

        val targetDevice = PolarOfflineGattCoordinator.findEnabledPolarDevice(context) { name, mac ->
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(context, name, macAddress = mac)
        }
        return if (targetDevice == null) {
            lastArmingOutcome = ArmingOutcome.SKIPPED_NO_DEVICE
            false
        } else {
            executeArming(context, targetDevice.macAddress, targetDevice.polarId)
        }
    }

    /**
     * Executes PFTP time-sync, stale HR cleanup, and `PolarDeviceDataType.HR` offline recording start
     * under coordinated `MyBTLEDevice` GATT yield (`REQ-SET-108.2`, `REQ-SET-108.3`).
     */
    suspend fun executeArming(context: Context, macAddress: String, polarId: String): Boolean {
        val guardResult = checkPreArmGuard()
        if (guardResult != null) {
            return guardResult
        }

        return try {
            val result = PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = macAddress,
                polarId = polarId,
                features = REQUIRED_ARMING_FEATURES,
                timeoutMs = ARMING_TIMEOUT_MS,
                apiOverride = apiOverride
            ) { api ->
                performArmingSequence(context, api, macAddress, polarId)
            }
            if (result == null) {
                isArmed.set(false)
                lastArmingOutcome = ArmingOutcome.ERROR_FAILED
                lastArmingError = "Arming timed out after $ARMING_TIMEOUT_MS ms"
                Log.w(TAG, "Arming Polar Verity Sense timed out after $ARMING_TIMEOUT_MS ms")
                false
            } else {
                result
            }
        } catch (t: Throwable) {
            recordFailure(t)
            false
        }
    }

    private fun checkPreArmGuard(): Boolean? {
        return when {
            getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU -> {
                lastArmingOutcome = ArmingOutcome.SKIPPED_UNSUPPORTED_API
                false
            }
            isArmed.get() -> {
                lastArmingOutcome = ArmingOutcome.SKIPPED_ALREADY_ARMED
                true
            }
            else -> null
        }
    }

    /**
     * Stops active HR offline recording, locates the matching [PolarOfflineRecordingEntry] for
     * `[windowStartMs, windowEndMs]`, and fetches [PolarOfflineRecordingData.HrOfflineRecording] (`ATT-7924`).
     */
    suspend fun stopAndFetchOfflineHr(
        context: Context,
        macAddress: String?,
        polarId: String,
        windowStartMs: Long,
        windowEndMs: Long
    ): VerityOfflineHrFetchResult {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            return VerityOfflineHrFetchResult(null, null, stoppedSuccessfully = false)
        }

        return try {
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = macAddress,
                polarId = polarId,
                features = REQUIRED_OFFLINE_FEATURES,
                timeoutMs = FETCH_TIMEOUT_MS,
                apiOverride = apiOverride
            ) { api ->
                api.connectToDevice(polarId)
                api.waitForConnection(polarId)
                val ready = PolarOfflineGattCoordinator.awaitFeaturesReady(
                    api, polarId, REQUIRED_OFFLINE_FEATURES, featureTimeoutMs
                )
                if (!ready) {
                    return@withGattYieldAndPolarApi VerityOfflineHrFetchResult(
                        null, null, stoppedSuccessfully = false,
                        errorMessage = "Timed out waiting for FEATURE_POLAR_OFFLINE_RECORDING"
                    )
                }

                val stopped = stopActiveHrIfRunning(api, polarId)
                val entries = api.listOfflineRecordings(polarId).toList()
                val match = selectMatchingHrEntry(entries, windowStartMs, windowEndMs)
                val hrData = if (match != null) {
                    api.getOfflineRecord(polarId, match, secret = null)
                        as? PolarOfflineRecordingData.HrOfflineRecording
                } else {
                    null
                }
                VerityOfflineHrFetchResult(match, hrData, stoppedSuccessfully = stopped)
            } ?: VerityOfflineHrFetchResult(
                null, null, stoppedSuccessfully = false,
                errorMessage = "Timed out after $FETCH_TIMEOUT_MS ms"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed stopAndFetchOfflineHr for $polarId: ${t.message}", t)
            VerityOfflineHrFetchResult(null, null, stoppedSuccessfully = false, errorMessage = t.message)
        }
    }

    /**
     * Removes a previously fetched offline recording [entry] from the Polar optical sensor (`ATT-7924`).
     */
    suspend fun removeOfflineRecord(
        context: Context,
        macAddress: String?,
        polarId: String,
        entry: PolarOfflineRecordingEntry
    ): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        return try {
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = macAddress,
                polarId = polarId,
                features = REQUIRED_OFFLINE_FEATURES,
                timeoutMs = FETCH_TIMEOUT_MS,
                apiOverride = apiOverride
            ) { api ->
                api.connectToDevice(polarId)
                api.waitForConnection(polarId)
                val ready = PolarOfflineGattCoordinator.awaitFeaturesReady(
                    api, polarId, REQUIRED_OFFLINE_FEATURES, featureTimeoutMs
                )
                if (!ready) {
                    false
                } else {
                    api.removeOfflineRecord(polarId, entry)
                    true
                }
            } ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to remove offline record ${entry.path} on $polarId: ${t.message}", t)
            false
        }
    }

    private suspend fun performArmingSequence(
        context: Context,
        api: PolarBleApi,
        macAddress: String,
        polarId: String
    ): Boolean {
        api.connectToDevice(polarId)
        api.waitForConnection(polarId)

        val featuresReady = PolarOfflineGattCoordinator.awaitFeaturesReady(
            api, polarId, REQUIRED_ARMING_FEATURES, featureTimeoutMs
        )
        if (!featuresReady) {
            isArmed.set(false)
            lastArmingOutcome = ArmingOutcome.ERROR_FEATURE_TIMEOUT
            lastArmingError = "Timed out waiting for Polar offline & time-setup features"
            Log.w(TAG, "Timed out waiting for Polar offline features on $polarId")
            return false
        }

        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, macAddress)
        api.setLocalTime(polarId, LocalDateTime.now())
        stopActiveHrIfRunning(api, polarId)
        api.startOfflineRecording(
            identifier = polarId,
            feature = PolarBleApi.PolarDeviceDataType.HR,
            settings = null,
            secret = null
        )

        armedDeviceId = polarId
        armedMacAddress = macAddress
        isArmed.set(true)
        lastArmingOutcome = ArmingOutcome.ARMED
        lastArmingError = null
        Log.i(TAG, "Armed Polar Verity Sense offline HR recording on $polarId")
        return true
    }

    private suspend fun stopActiveHrIfRunning(api: PolarBleApi, polarId: String): Boolean {
        return try {
            val activeTypes = api.getOfflineRecordingStatus(polarId)
            if (activeTypes.contains(PolarBleApi.PolarDeviceDataType.HR)) {
                api.stopOfflineRecording(polarId, PolarBleApi.PolarDeviceDataType.HR)
            }
            true
        } catch (t: Throwable) {
            if (isSystemBusyError(t)) {
                throw t
            }
            Log.w(TAG, "Non-fatal error inspecting/stopping offline HR status on $polarId: ${t.message}")
            false
        }
    }

    private fun selectMatchingHrEntry(
        entries: List<PolarOfflineRecordingEntry>,
        windowStartMs: Long,
        windowEndMs: Long
    ): PolarOfflineRecordingEntry? {
        val hrEntries = entries.filter { it.type == PolarBleApi.PolarDeviceDataType.HR }
        if (hrEntries.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val windowMatches = hrEntries.filter { entry ->
            val entryMs = entry.date.atZone(zone).toInstant().toEpochMilli()
            entryMs in (windowStartMs - ENTRY_MATCH_TOLERANCE_MS)..(windowEndMs + ENTRY_MATCH_TOLERANCE_MS)
        }
        val candidates = if (windowMatches.isNotEmpty()) windowMatches else hrEntries
        return candidates.minByOrNull { entry ->
            val entryMs = entry.date.atZone(zone).toInstant().toEpochMilli()
            abs(entryMs - windowStartMs)
        }
    }

    private fun recordFailure(t: Throwable) {
        isArmed.set(false)
        if (isSystemBusyError(t)) {
            lastArmingOutcome = ArmingOutcome.ERROR_SYSTEM_BUSY
            lastArmingError = t.message ?: SYSTEM_BUSY_ERROR_NAME
            Log.w(TAG, "Polar Verity Sense reported SYSTEM_BUSY during offline arming; continuing workout start.")
        } else {
            lastArmingOutcome = ArmingOutcome.ERROR_FAILED
            lastArmingError = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "Non-blocking failure during Polar Verity Sense offline arming: ${t.message}", t)
        }
    }

    internal fun isSystemBusyError(t: Throwable): Boolean {
        var current: Throwable? = t
        var found = false
        while (current != null && !found) {
            val isPftpBusy = current is BlePsFtpUtils.PftpResponseError &&
                current.errorName.equals(SYSTEM_BUSY_ERROR_NAME, ignoreCase = true)
            val hasBusyMessage = current.message?.contains(SYSTEM_BUSY_ERROR_NAME, ignoreCase = true) == true
            found = isPftpBusy || hasBusyMessage
            current = current.cause
        }
        return found
    }
}
