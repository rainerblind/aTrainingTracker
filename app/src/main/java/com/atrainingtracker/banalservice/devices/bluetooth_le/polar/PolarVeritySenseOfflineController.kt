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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import fi.polar.remote.representation.protobuf.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Controller for Polar Verity Sense (and compatible optical Polar sensors) offline HR recording
 * using `PolarOfflineRecordingApi` (`ATT-7923`, `REQ-SET-108`) and post-workout HR backfill /
 * Aftermath deferred retry (`ATT-7924`, `REQ-SET-109`).
 *
 * Enforces Android 13+ (API 33) isolation, GATT arbitration with [MyBTLEDevice], device clock
 * synchronization, non-blocking `SYSTEM_BUSY` resilience, single-shot session arming,
 * 1-Hz gap-only `SensorType.HR` SQLite patching, strict HRV (`WorkoutRr.db`) exclusion, and
 * post-commit single-entry flash cleanup.
 */
@Suppress("LargeClass", "TooGenericExceptionCaught")
object PolarVeritySenseOfflineController {

    private const val TAG = "PolarVerityOfflineCtrl"
    const val ARMING_TIMEOUT_MS = 15_000L
    const val FETCH_TIMEOUT_MS = 30_000L
    const val BACKFILL_TIMEOUT_MS = 45_000L
    const val POLAR_VERITY_SYNC_NOTIFICATION_ID = 8046
    const val VERITY_EXERCISE_PREFIX = "verity:"
    const val VERITY_SWIM_BUSY_PREFIX = "verity:swim_busy:"
    const val POLAR_SPORT_POOL_SWIMMING = 103L
    private const val ENTRY_MATCH_TOLERANCE_MS = 180_000L
    private const val PENDING_MATCH_TOLERANCE_MS = 600_000L
    private const val MIN_VALID_HR_BPM = 25
    private const val MAX_VALID_HR_BPM = 250
    private const val MILLIS_PER_SECOND = 1000L
    private const val SYSTEM_BUSY_ERROR_NAME = "SYSTEM_BUSY"

    private val REQUIRED_ARMING_FEATURES = setOf(
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
    )

    private val REQUIRED_OFFLINE_FEATURES = setOf(
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA
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
    private val armedStateLock = Any()
    private val armedTokenGenerator = AtomicLong(0L)
    @Volatile private var currentArmedToken: Long = 0L
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
    var poolLengthOverrideMeters: Double? = null

    @VisibleForTesting
    var featureTimeoutMs: Long = PolarOfflineGattCoordinator.FEATURE_READY_TIMEOUT_MS

    @VisibleForTesting
    var stopConnectionTimeoutMs: Long = PolarOfflineGattCoordinator.STOP_CONNECTION_TIMEOUT_MS

    internal fun getEffectiveSdkInt(): Int = sdkIntOverride ?: Build.VERSION.SDK_INT

    @JvmStatic
    fun isRecordingArmed(): Boolean = isArmed.get()

    @JvmStatic
    fun getArmedSessionToken(): Long = currentArmedToken

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
    fun setArmedForTesting(armed: Boolean, deviceId: String? = armedDeviceId, macAddress: String? = armedMacAddress) =
        synchronized(armedStateLock) {
            isArmed.set(armed)
            currentArmedToken = if (armed) armedTokenGenerator.incrementAndGet() else 0L
            armedDeviceId = deviceId
            armedMacAddress = macAddress
        }

    @VisibleForTesting
    fun resetForTesting() {
        synchronized(armedStateLock) { isArmed.set(false); currentArmedToken = 0L }
        isArmingInFlight.set(false)
        apiOverride = null
        sdkIntOverride = null
        poolLengthOverrideMeters = null
        dispatcher = Dispatchers.IO
        featureTimeoutMs = PolarOfflineGattCoordinator.FEATURE_READY_TIMEOUT_MS
        stopConnectionTimeoutMs = PolarOfflineGattCoordinator.STOP_CONNECTION_TIMEOUT_MS
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
                    if (t is CancellationException) throw t
                    recordFailure(t)
                } finally {
                    isArmingInFlight.set(false)
                }
            }
        }
    }

    /**
     * Workout-stop lifecycle hook (`REQ-SET-108.2`, `REQ-SET-109.1`).
     * Retains armed session metadata while `isArmed` is true so `TrackerService.endWorkout()` can execute
     * synchronous stop-time backfill before resetting state; shuts down any test API when not armed.
     */
    @Suppress("UnusedParameter")
    @JvmStatic
    fun onWorkoutStopped(context: Context) {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            resetArmedState()
            return
        }
        if (!isArmed.get()) {
            armedDeviceId = null
            armedMacAddress = null
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
     * Returns true when a persisted pending-sync record belongs to Polar Verity Sense (`REQ-SET-109.3`).
     */
    @JvmStatic
    fun isVerityPendingSync(polarExerciseId: String?): Boolean {
        return polarExerciseId?.startsWith(VERITY_EXERCISE_PREFIX) == true
    }

    /**
     * Returns true when a pending-sync record indicates the sensor is busy in swimming mode (`REQ-SET-110.4`).
     */
    @JvmStatic
    fun isVeritySwimBusy(polarExerciseId: String?): Boolean {
        return polarExerciseId?.startsWith(VERITY_SWIM_BUSY_PREFIX) == true
    }

    /**
     * Evaluates whether `WorkoutSamples.db` has missing or unpopulated `SensorType.HR` samples (`REQ-SET-109.1`).
     * Strictly ignores `WorkoutRr.db` because Verity Sense optical recording covers 1-Hz HR only.
     */
    @JvmStatic
    fun shouldBackfill(context: Context, baseFileName: String?): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return WorkoutSamplesDatabaseManager.getInstance(context.applicationContext)
            .hasMissingHeartRateSamples(baseFileName)
    }

    /**
     * Synchronous blocking convenience entry point invoked by `TrackerService.endWorkout()` before exports
     * (`REQ-SET-109.1`).
     */
    @JvmStatic
    @JvmOverloads
    fun stopAndBackfillWorkoutBlocking(
        context: Context,
        workoutId: Long,
        baseFileName: String,
        startTimeMs: Long = sessionStartTimeMs
    ): Boolean {
        return runBlocking(dispatcher) {
            stopAndBackfillWorkout(context, workoutId, baseFileName, startTimeMs)
        }
    }

    /**
     * Stops active Verity Sense offline HR recording, evaluates gap continuity, fetches & aligns
     * 1-Hz `HrOfflineRecording` if gaps exist, updates `WorkoutSamples.db` and `WorkoutSummaries.db`
     * without touching `WorkoutRr.db`, and removes ONLY the matched flash entry strictly post-commit (`REQ-SET-109`, `REQ-TRK-091.1`).
     */
    suspend fun stopAndBackfillWorkout(
        context: Context,
        workoutId: Long,
        baseFileName: String,
        startTimeMs: Long = sessionStartTimeMs,
        showOwnNotification: Boolean = true,
        expectedArmedToken: Long = 0L
    ): Boolean {
        val appContext = context.applicationContext
        val (targetId, targetMac) = if (getEffectiveSdkInt() >= Build.VERSION_CODES.TIRAMISU) {
            resolveTargetIdsForStop(appContext)
        } else {
            null to null
        }
        if (targetId == null) {
            resetArmedState(expectedArmedToken)
            return false
        }

        val needsBackfill = shouldBackfill(appContext, baseFileName)
        val windowStartMs = if (startTimeMs > 0L) startTimeMs else System.currentTimeMillis()
        val windowEndMs = System.currentTimeMillis()
        if (apiOverride == null && PolarOfflineGattCoordinator.isTargetConfirmedDisconnected(targetId, targetMac)) {
            Log.i(TAG, "Polar Verity Sense $targetId confirmed disconnected at workout stop; fast-failing backfill.")
            if (needsBackfill && workoutId > 0L) {
                recordPendingSync(appContext, workoutId, targetId, null, windowStartMs, windowEndMs)
            }
            resetArmedState(expectedArmedToken)
            return false
        }
        if (showOwnNotification && needsBackfill) {
            showSyncNotification(appContext)
        }

        val backfillCtx = StopBackfillContext(
            workoutId = workoutId,
            baseFileName = baseFileName,
            polarId = targetId,
            macAddress = targetMac,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            needsBackfill = needsBackfill
        )
        var matchedPath: String? = null
        return try {
            val sessionOutcome = executeStopBackfillSession(appContext, backfillCtx)
            matchedPath = sessionOutcome.matchedPath
            if (!sessionOutcome.success && needsBackfill && workoutId > 0L) {
                recordPendingSync(appContext, workoutId, targetId, matchedPath, windowStartMs, windowEndMs)
            }
            sessionOutcome.success
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w(TAG, "Exception during Polar Verity Sense stopAndBackfillWorkout: ${t.message}", t)
            if (needsBackfill && workoutId > 0L) {
                val pathOrBusy = if (isSystemBusyError(t)) {
                    "${VERITY_SWIM_BUSY_PREFIX.removePrefix(VERITY_EXERCISE_PREFIX)}$targetId"
                } else {
                    matchedPath
                }
                recordPendingSync(appContext, workoutId, targetId, pathOrBusy, windowStartMs, windowEndMs)
            }
            false
        } finally {
            resetArmedState(expectedArmedToken)
            if (showOwnNotification && needsBackfill) {
                dismissSyncNotification(appContext)
            }
        }
    }

    /**
     * Synchronous blocking convenience wrapper for UI callers (`REQ-SET-109.3`).
     */
    @JvmStatic
    fun syncPendingWorkoutBlocking(context: Context, workoutId: Long): Boolean {
        return runBlocking(dispatcher) {
            syncPendingWorkout(context, workoutId)
        }
    }

    /**
     * Deferred manual retry invoked from the Aftermath retry banner when Polar Verity Sense was
     * powered off at workout stop and has been restarted in Blue Sensor Mode (`REQ-SET-109.3`).
     */
    suspend fun syncPendingWorkout(context: Context, workoutId: Long): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) return false

        val appContext = context.applicationContext
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(appContext)
        val pendingData = summariesDb.getPolarPendingSyncData(workoutId)
        val (targetId, targetMac) = resolveTargetIdsForRetry(appContext, pendingData?.deviceId)

        return if (targetId == null) {
            Log.w(TAG, "No target Polar Verity Sense device ID for pending workout $workoutId")
            false
        } else {
            try {
                executePendingSyncSession(
                    appContext = appContext,
                    workoutId = workoutId,
                    summariesDb = summariesDb,
                    pendingData = pendingData,
                    polarId = targetId,
                    macAddress = targetMac
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e(TAG, "Exception during Polar Verity Sense syncPendingWorkout: ${t.message}", t)
                if (isSystemBusyError(t)) {
                    recordPendingSync(
                        context = appContext,
                        workoutId = workoutId,
                        targetId = targetId,
                        matchedPath = "${VERITY_SWIM_BUSY_PREFIX.removePrefix(VERITY_EXERCISE_PREFIX)}$targetId",
                        windowStartMs = pendingData?.expectedStartMs ?: 0L,
                        windowEndMs = pendingData?.expectedEndMs ?: 0L
                    )
                }
                false
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
            if (t is CancellationException) throw t
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
                if (!PolarOfflineGattCoordinator.connectWithinTimeout(api, polarId, FETCH_TIMEOUT_MS)) {
                    return@withGattYieldAndPolarApi VerityOfflineHrFetchResult(
                        null, null, stoppedSuccessfully = false,
                        errorMessage = "Timed out connecting to $polarId"
                    )
                }
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
            if (t is CancellationException) throw t
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
                if (!PolarOfflineGattCoordinator.connectWithinTimeout(api, polarId, FETCH_TIMEOUT_MS)) {
                    return@withGattYieldAndPolarApi false
                }
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
            if (t is CancellationException) throw t
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
        if (!PolarOfflineGattCoordinator.connectWithinTimeout(api, polarId, ARMING_TIMEOUT_MS)) {
            isArmed.set(false)
            lastArmingOutcome = ArmingOutcome.ERROR_FAILED
            lastArmingError = "Connection timed out after $ARMING_TIMEOUT_MS ms"
            return false
        }

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

        checkAndPerformFtu(api, polarId)

        PolarOfflineCapabilityStore.recordSdkDeviceType(
            context,
            macAddress,
            PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
            polarId.takeIf { it.length == 8 }
        )
        api.setLocalTime(polarId, LocalDateTime.now())
        stopActiveHrIfRunning(api, polarId)
        startOfflineRecordingGuarded(api, polarId, PolarBleApi.PolarDeviceDataType.HR)

        synchronized(armedStateLock) {
            armedDeviceId = polarId
            armedMacAddress = macAddress
            currentArmedToken = armedTokenGenerator.incrementAndGet()
            isArmed.set(true)
        }
        lastArmingOutcome = ArmingOutcome.ARMED
        lastArmingError = null
        Log.i(TAG, "Armed Polar Verity Sense offline HR recording on $polarId")
        return true
    }

    /**
     * Starts an offline recording channel while enforcing Verity Sense per-channel `ACC` mutual
     * exclusion against active [PolarOnlineStreamingBridge] online `ACC` streams (`REQ-SET-112.3`).
     */
    internal suspend fun startOfflineRecordingGuarded(
        api: PolarBleApi,
        polarId: String,
        feature: PolarBleApi.PolarDeviceDataType
    ): Boolean {
        if (feature == PolarBleApi.PolarDeviceDataType.ACC &&
            !PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_OFFLINE)
        ) {
            Log.w(TAG, "Refusing offline ACC recording on $polarId: ACC channel owned by online IMU stream.")
            return false
        }
        api.startOfflineRecording(
            identifier = polarId,
            feature = feature,
            settings = null,
            secret = null
        )
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
            if (t is CancellationException || isSystemBusyError(t)) {
                throw t
            }
            Log.w(TAG, "Non-fatal error inspecting/stopping offline HR status on $polarId: ${t.message}")
            false
        }
    }

    internal suspend fun checkAndPerformFtu(api: PolarBleApi, polarId: String) {
        try {
            if (!api.isFtuDone(polarId)) {
                Log.i(TAG, "Polar Verity Sense $polarId requires FTU; provisioning default config.")
                val ftuConfig = PolarFirstTimeUseConfig(
                    gender = PolarFirstTimeUseConfig.Gender.MALE,
                    birthDate = java.time.LocalDate.of(1990, 1, 1),
                    height = 175.0f,
                    weight = 70.0f,
                    maxHeartRate = 190,
                    vo2Max = 45,
                    restingHeartRate = 60,
                    trainingBackground = 30,
                    deviceTime = LocalDateTime.now().toString(),
                    typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_MOVING,
                    sleepGoalMinutes = 480
                )
                api.doFirstTimeUse(polarId, ftuConfig)
            }
        } catch (t: Throwable) {
            if (t is CancellationException || isSystemBusyError(t)) throw t
            Log.w(TAG, "Non-fatal error checking or performing FTU on $polarId: ${t.message}")
        }
    }

    data class VeritySwimSession(
        val reference: PolarTrainingSessionReference,
        val startTimeMs: Long,
        val durationMs: Long,
        val hrSamples: List<Int>,
        val turnTimestampsMs: List<Long>
    )

    private data class StopBackfillContext(
        val workoutId: Long,
        val baseFileName: String,
        val polarId: String,
        val macAddress: String?,
        val windowStartMs: Long,
        val windowEndMs: Long,
        val needsBackfill: Boolean
    )

    private data class StopSessionOutcome(
        val success: Boolean,
        val matchedPath: String? = null
    )

    private suspend fun executeStopBackfillSession(
        appContext: Context,
        backfillCtx: StopBackfillContext
    ): StopSessionOutcome {
        var matchedPath: String? = null
        val success = PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
            context = appContext,
            macAddress = backfillCtx.macAddress,
            polarId = backfillCtx.polarId,
            features = REQUIRED_OFFLINE_FEATURES,
            timeoutMs = BACKFILL_TIMEOUT_MS,
            apiOverride = apiOverride
        ) { api ->
            val session = connectAndMatchSession(
                api = api,
                polarId = backfillCtx.polarId,
                windowStartMs = backfillCtx.windowStartMs,
                windowEndMs = backfillCtx.windowEndMs,
                preferredPath = null,
                toleranceMs = ENTRY_MATCH_TOLERANCE_MS,
                connectTimeoutMs = stopConnectionTimeoutMs
            )
            matchedPath = session.path
            handleStopBackfillMatch(api, appContext, backfillCtx, session)
        } ?: false

        return StopSessionOutcome(success = success, matchedPath = matchedPath)
    }

    private suspend fun executePendingSyncSession(
        appContext: Context,
        workoutId: Long,
        summariesDb: WorkoutSummariesDatabaseManager,
        pendingData: WorkoutSummariesDatabaseManager.PolarPendingSyncData?,
        polarId: String,
        macAddress: String?
    ): Boolean {
        val preferredPath = stripVerityPrefix(pendingData?.exerciseId)
        val windowStartMs = pendingData?.expectedStartMs ?: 0L
        val windowEndMs = pendingData?.expectedEndMs ?: 0L
        val repo = WorkoutRepository.getInstance(appContext)
        val baseFileName = repo.getBaseFileName(workoutId) ?: summariesDb.getBaseFileName(workoutId)

        val result = PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
            context = appContext,
            macAddress = macAddress,
            polarId = polarId,
            features = REQUIRED_OFFLINE_FEATURES,
            timeoutMs = BACKFILL_TIMEOUT_MS,
            apiOverride = apiOverride
        ) { api ->
            val session = connectAndMatchSession(
                api = api,
                polarId = polarId,
                windowStartMs = windowStartMs,
                windowEndMs = windowEndMs,
                preferredPath = preferredPath,
                toleranceMs = PENDING_MATCH_TOLERANCE_MS
            )
            val committed = when {
                !session.ready -> false
                session.offlineEntry != null -> {
                    val hrData = api.getOfflineRecord(polarId, session.offlineEntry, secret = null)
                        as? PolarOfflineRecordingData.HrOfflineRecording
                    if (hrData != null) {
                        applyBackfillAndCommit(
                            appContext = appContext,
                            workoutId = workoutId,
                            baseFileName = baseFileName,
                            hrData = hrData,
                            fallbackAnchorMs = windowStartMs,
                            clearPendingFlag = true
                        )
                    } else {
                        false
                    }
                }
                session.swimSession != null -> {
                    applySwimBackfillAndCommit(
                        appContext = appContext,
                        workoutId = workoutId,
                        baseFileName = baseFileName,
                        swimSession = session.swimSession,
                        fallbackAnchorMs = windowStartMs,
                        clearPendingFlag = true
                    )
                }
                else -> false
            }
            if (committed) {
                if (session.offlineEntry != null) {
                    api.removeOfflineRecord(polarId, session.offlineEntry)
                } else if (session.swimRef != null) {
                    api.deleteTrainingSession(polarId, session.swimRef)
                }
                reexportUpdatedWorkout(appContext, repo, workoutId, baseFileName)
            }
            committed
        }
        return result == true
    }

    private data class MatchedSession(
        val ready: Boolean,
        val offlineEntry: PolarOfflineRecordingEntry? = null,
        val swimRef: PolarTrainingSessionReference? = null,
        val swimSession: VeritySwimSession? = null,
        val path: String? = offlineEntry?.path ?: swimRef?.path
    )

    private suspend fun connectAndMatchSession(
        api: PolarBleApi,
        polarId: String,
        windowStartMs: Long,
        windowEndMs: Long,
        preferredPath: String?,
        toleranceMs: Long,
        connectTimeoutMs: Long = BACKFILL_TIMEOUT_MS
    ): MatchedSession {
        if (!PolarOfflineGattCoordinator.connectWithinTimeout(api, polarId, connectTimeoutMs)) {
            Log.w(TAG, "Polar Verity Sense connection timed out after $connectTimeoutMs ms for $polarId")
            return MatchedSession(ready = false)
        }
        val ready = PolarOfflineGattCoordinator.awaitFeaturesReady(
            api, polarId, REQUIRED_OFFLINE_FEATURES, featureTimeoutMs
        )
        if (!ready) {
            return MatchedSession(ready = false)
        }
        stopActiveHrIfRunning(api, polarId)

        val entries = try {
            api.listOfflineRecordings(polarId).toList()
        } catch (t: Throwable) {
            if (t is CancellationException || isSystemBusyError(t)) throw t
            Log.w(TAG, "Error listing offline recordings on $polarId: ${t.message}")
            emptyList()
        }
        val match = selectMatchingHrEntry(
            entries = entries,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            preferredPath = preferredPath,
            toleranceMs = toleranceMs
        )
        if (match != null) {
            return MatchedSession(ready = true, offlineEntry = match)
        }

        val trainingRefs = try {
            api.getTrainingSessionReferences(polarId).toList()
        } catch (t: Throwable) {
            if (t is CancellationException || isSystemBusyError(t)) throw t
            Log.w(TAG, "Error listing training session references on $polarId: ${t.message}")
            emptyList()
        }

        for (ref in trainingRefs) {
            if (preferredPath != null && ref.path != preferredPath && !ref.path.contains(preferredPath)) {
                continue
            }
            val session = try {
                api.getTrainingSession(polarId, ref)
            } catch (t: Throwable) {
                if (t is CancellationException || isSystemBusyError(t)) throw t
                Log.w(TAG, "Error fetching training session ${ref.path} on $polarId: ${t.message}")
                null
            } ?: continue
            val parsed = parseSwimTrainingSession(session)
            if (parsed != null) {
                val sessionStartMs = parsed.startTimeMs
                val inWindow = if (windowStartMs <= 0L) true else {
                    val effectiveEndMs = if (windowEndMs >= windowStartMs) windowEndMs else windowStartMs
                    sessionStartMs in (windowStartMs - toleranceMs)..(effectiveEndMs + toleranceMs)
                }
                if (inWindow) {
                    return MatchedSession(
                        ready = true,
                        swimRef = ref,
                        swimSession = parsed
                    )
                }
            }
        }

        return MatchedSession(ready = true)
    }

    private suspend fun handleStopBackfillMatch(
        api: PolarBleApi,
        appContext: Context,
        backfillCtx: StopBackfillContext,
        session: MatchedSession
    ): Boolean {
        return when {
            !session.ready -> false
            !backfillCtx.needsBackfill -> {
                // Zero-gap fast path: skip full PFTP download while stopping & cleaning matched flash entry
                if (session.offlineEntry != null) {
                    api.removeOfflineRecord(backfillCtx.polarId, session.offlineEntry)
                } else if (session.swimRef != null) {
                    api.deleteTrainingSession(backfillCtx.polarId, session.swimRef)
                }
                true
            }
            session.offlineEntry == null && session.swimSession == null -> {
                Log.w(TAG, "No matching Verity Sense HR or swim entry found for workout ${backfillCtx.workoutId}")
                false
            }
            session.offlineEntry != null -> {
                val hrData = api.getOfflineRecord(backfillCtx.polarId, session.offlineEntry, secret = null)
                    as? PolarOfflineRecordingData.HrOfflineRecording
                val committed = if (hrData != null) {
                    applyBackfillAndCommit(
                        appContext = appContext,
                        workoutId = backfillCtx.workoutId,
                        baseFileName = backfillCtx.baseFileName,
                        hrData = hrData,
                        fallbackAnchorMs = backfillCtx.windowStartMs,
                        clearPendingFlag = false
                    )
                } else {
                    false
                }
                if (committed) {
                    // Multi-session flash safety: delete ONLY the matched entry strictly AFTER SQLite commit
                    api.removeOfflineRecord(backfillCtx.polarId, session.offlineEntry)
                }
                committed
            }
            session.swimSession != null && session.swimRef != null -> {
                val committed = applySwimBackfillAndCommit(
                    appContext = appContext,
                    workoutId = backfillCtx.workoutId,
                    baseFileName = backfillCtx.baseFileName,
                    swimSession = session.swimSession,
                    fallbackAnchorMs = backfillCtx.windowStartMs,
                    clearPendingFlag = false
                )
                if (committed) {
                    api.deleteTrainingSession(backfillCtx.polarId, session.swimRef)
                }
                committed
            }
            else -> false
        }
    }

    @VisibleForTesting
    internal fun applySwimBackfillAndCommit(
        appContext: Context,
        workoutId: Long,
        baseFileName: String?,
        swimSession: VeritySwimSession,
        fallbackAnchorMs: Long,
        clearPendingFlag: Boolean
    ): Boolean {
        val anchorMs = if (swimSession.startTimeMs > 0L) swimSession.startTimeMs else fallbackAnchorMs
        val hrPairs = buildHrPairsFromIntList(swimSession.hrSamples, anchorMs)
        if (!baseFileName.isNullOrEmpty() && hrPairs.isNotEmpty()) {
            PolarOfflineGattCoordinator.patchMissingHeartRateAndRecordSensorType(
                appContext,
                workoutId,
                baseFileName,
                anchorMs,
                hrPairs
            )
            recomputeHrSummaryMetrics(appContext, workoutId, baseFileName)
        }

        // REQ-SET-111.1: Auto-assign Swim sport type
        val sportTypesRepo = SportTypesRepository.getInstance(appContext)
        val swimSportId = sportTypesRepo.ensureSwimSportTypeExists()

        // REQ-SET-111.2: Pool-length distance & turn extraction
        val poolLengthM = poolLengthOverrideMeters ?: try {
            SettingsDataStoreJavaHelper.getDefaultPoolLengthMeters(appContext).toDouble()
        } catch (_: Throwable) {
            25.0
        }
        val validTurns = swimSession.turnTimestampsMs.filter { it > anchorMs }.sorted()
        val lengthCount = validTurns.size + 1
        val totalDistanceM = lengthCount * poolLengthM
        val durationMs = if (swimSession.durationMs > 0L) {
            swimSession.durationMs
        } else if (swimSession.hrSamples.isNotEmpty()) {
            swimSession.hrSamples.size * MILLIS_PER_SECOND
        } else {
            lengthCount * 30_000L
        }

        if (!baseFileName.isNullOrEmpty()) {
            val samplesManager = WorkoutSamplesDatabaseManager.getInstance(appContext)
            samplesManager.patchSwimKinematics(baseFileName, anchorMs, durationMs, validTurns, poolLengthM)
        }

        // Persist discrete laps in Laps.db honoring ATT-896
        val lapsManager = LapsDatabaseManager.getInstance(appContext)
        lapsManager.deleteWorkout(workoutId)

        var prevTimeMs = anchorMs
        for (i in 0 until lengthCount) {
            val lapNr = (i + 1).toLong()
            val lapEndMs = if (i < validTurns.size) {
                validTurns[i]
            } else {
                anchorMs + durationMs
            }
            val lapDurSec = Math.max(1, ((lapEndMs - prevTimeMs) / 1000L).toInt())
            val lapSpeedMps = if (lapDurSec > 0) poolLengthM / lapDurSec else 0.0
            lapsManager.saveLap(workoutId, lapNr, lapDurSec, poolLengthM, lapSpeedMps)
            prevTimeMs = lapEndMs
        }

        val avgSpeedMps = if (durationMs > 0) totalDistanceM / (durationMs / 1000.0) else 0.0

        // Update WorkoutSummaries.db
        val summariesManager = WorkoutSummariesDatabaseManager.getInstance(appContext)
        summariesManager.updateSwimMetrics(
            workoutId,
            swimSportId,
            BSportType.UNKNOWN.name,
            totalDistanceM,
            lengthCount,
            avgSpeedMps
        )

        // Update WorkoutRepository sport
        val workoutRepo = WorkoutRepository.getInstance(appContext)
        workoutRepo.updateWorkoutSport(workoutId, swimSportId, BSportType.UNKNOWN)

        if (clearPendingFlag) {
            summariesManager.clearPolarSyncPending(workoutId)
        }
        workoutRepo.refreshWorkout(workoutId)
        return true
    }

    @VisibleForTesting
    internal fun buildHrPairsFromIntList(
        hrSamples: List<Int>,
        anchorMs: Long
    ): List<android.util.Pair<Long, Int>> {
        if (hrSamples.isEmpty()) return emptyList()
        val pairs = ArrayList<android.util.Pair<Long, Int>>(hrSamples.size)
        for ((index, rawBpm) in hrSamples.withIndex()) {
            if (rawBpm in MIN_VALID_HR_BPM..MAX_VALID_HR_BPM) {
                pairs.add(android.util.Pair(anchorMs + index * MILLIS_PER_SECOND, rawBpm))
            }
        }
        return pairs
    }

    @VisibleForTesting
    internal fun extractTurnTimestamps(
        distanceSamples: List<Float>,
        startMs: Long,
        sampleIntervalMs: Long = MILLIS_PER_SECOND
    ): List<Long> {
        if (distanceSamples.size < 2) return emptyList()
        val turns = mutableListOf<Long>()
        var prevDist = distanceSamples[0]
        for (i in 1 until distanceSamples.size) {
            val dist = distanceSamples[i]
            if (dist > prevDist) {
                turns.add(startMs + i * sampleIntervalMs)
                prevDist = dist
            }
        }
        return turns
    }

    @VisibleForTesting
    internal fun parsePbLocalDateTime(pbDateTime: Types.PbLocalDateTime?): Long? {
        if (pbDateTime == null || !pbDateTime.hasDate() || !pbDateTime.hasTime()) return null
        val date = pbDateTime.date
        val time = pbDateTime.time
        val ldt = LocalDateTime.of(
            date.year,
            date.month,
            date.day,
            time.hour,
            time.minute,
            time.seconds,
            time.millis * 1_000_000
        )
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @VisibleForTesting
    internal fun parsePbDuration(pbDuration: Types.PbDuration?): Long {
        if (pbDuration == null) return 0L
        return (pbDuration.hours * 3600L + pbDuration.minutes * 60L + pbDuration.seconds) * 1000L + pbDuration.millis
    }

    @VisibleForTesting
    internal fun parseSwimTrainingSession(session: PolarTrainingSession): VeritySwimSession? {
        val summary = session.sessionSummary
        val summarySport = if (summary != null && summary.hasSport()) summary.sport.value else -1L

        val exercise = session.exercises.firstOrNull { ex ->
            val exSummary = ex.exerciseSummary
            val exSport = if (exSummary != null && exSummary.hasSport()) exSummary.sport.value else -1L
            exSport == POLAR_SPORT_POOL_SWIMMING || summarySport == POLAR_SPORT_POOL_SWIMMING
        } ?: session.exercises.firstOrNull().takeIf { summarySport == POLAR_SPORT_POOL_SWIMMING }

        if (exercise == null && summarySport != POLAR_SPORT_POOL_SWIMMING) {
            return null
        }

        val exSummary = exercise?.exerciseSummary
        val startPb = when {
            exSummary != null && exSummary.hasStart() -> exSummary.start
            summary != null && summary.hasStart() -> summary.start
            else -> null
        }
        val startMs = parsePbLocalDateTime(startPb)
            ?: session.reference.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val durationPb = when {
            exSummary != null && exSummary.hasDuration() -> exSummary.duration
            summary != null && summary.hasDuration() -> summary.duration
            else -> null
        }
        val durationMs = parsePbDuration(durationPb)

        val samples = exercise?.samples
        val hrList = samples?.heartRateSamplesList ?: emptyList()
        val distanceList = samples?.distanceSamplesList ?: emptyList()
        val turns = extractTurnTimestamps(distanceList, startMs)

        return VeritySwimSession(
            reference = session.reference,
            startTimeMs = startMs,
            durationMs = durationMs,
            hrSamples = hrList,
            turnTimestampsMs = turns
        )
    }


    private fun applyBackfillAndCommit(
        appContext: Context,
        workoutId: Long,
        baseFileName: String?,
        hrData: PolarOfflineRecordingData.HrOfflineRecording,
        fallbackAnchorMs: Long,
        clearPendingFlag: Boolean
    ): Boolean {
        val anchorMs = resolveRecordingAnchorMs(hrData, fallbackAnchorMs)
        val hrPairs = buildHrPairs(hrData, anchorMs)
        if (!baseFileName.isNullOrEmpty() && hrPairs.isNotEmpty()) {
            PolarOfflineGattCoordinator.patchMissingHeartRateAndRecordSensorType(
                appContext,
                workoutId,
                baseFileName,
                anchorMs,
                hrPairs
            )
            recomputeHrSummaryMetrics(appContext, workoutId, baseFileName)
        }
        if (clearPendingFlag) {
            WorkoutSummariesDatabaseManager.getInstance(appContext).clearPolarSyncPending(workoutId)
        }
        WorkoutRepository.getInstance(appContext).refreshWorkout(workoutId)
        return true
    }

    private fun resolveRecordingAnchorMs(
        hrData: PolarOfflineRecordingData.HrOfflineRecording,
        fallbackAnchorMs: Long
    ): Long {
        val parsedMs = hrData.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return if (parsedMs > 0L) parsedMs else fallbackAnchorMs
    }

    @VisibleForTesting
    internal fun alignHrSamples(
        hrData: PolarOfflineRecordingData.HrOfflineRecording,
        anchorMs: Long
    ): List<Pair<Long, Int>> {
        val samples = hrData.data.samples
        if (samples.isEmpty()) return emptyList()
        val pairs = ArrayList<Pair<Long, Int>>(samples.size)
        for ((index, sample) in samples.withIndex()) {
            val rawBpm = if (sample.hr > 0) sample.hr else sample.correctedHr
            if (rawBpm in MIN_VALID_HR_BPM..MAX_VALID_HR_BPM) {
                pairs.add((anchorMs + index * MILLIS_PER_SECOND) to rawBpm)
            }
        }
        return pairs
    }

    private fun buildHrPairs(
        hrData: PolarOfflineRecordingData.HrOfflineRecording,
        anchorMs: Long
    ): List<android.util.Pair<Long, Int>> {
        return alignHrSamples(hrData, anchorMs).map { (timestampMs, bpm) ->
            android.util.Pair(timestampMs, bpm)
        }
    }

    private fun recomputeHrSummaryMetrics(
        appContext: Context,
        workoutId: Long,
        baseFileName: String
    ) {
        val samplesManager = WorkoutSamplesDatabaseManager.getInstance(appContext)
        val stats = samplesManager.calcHeartRateStats(baseFileName) ?: return
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(appContext)
        summariesDb.updateExtremaValue(workoutId, SensorType.HR, ExtremaType.MIN, stats.min, null)
        summariesDb.updateExtremaValue(workoutId, SensorType.HR, ExtremaType.MAX, stats.max, null)
        summariesDb.updateExtremaValue(workoutId, SensorType.HR, ExtremaType.AVG, stats.avg, null, stats.count)

        try {
            val repo = WorkoutRepository.getInstance(appContext)
            val telemetry = repo.getPowerSpeedHrSamples(baseFileName)
            val hasHrAndKinematic = telemetry.hrSamples.isNotEmpty() &&
                (telemetry.powerSamples.isNotEmpty() || telemetry.speedSamples.isNotEmpty())
            if (hasHrAndKinematic) {
                val sportName = summariesDb.getString(
                    workoutId,
                    WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT
                )
                val sportType = BSportType.entries.firstOrNull { it.name == sportName } ?: BSportType.UNKNOWN
                val aerobicResult = PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                    telemetry.powerSamples,
                    telemetry.speedSamples,
                    telemetry.hrSamples,
                    sportType
                )
                val targetSensor = if (aerobicResult.isPowerBased) SensorType.POWER else SensorType.SPEED_mps
                aerobicResult.efficiencyFactor?.let { ef ->
                    summariesDb.updateExtremaValue(workoutId, targetSensor, ExtremaType.EFFICIENCY_FACTOR, ef, null)
                }
                aerobicResult.decouplingPct?.let { dec ->
                    summariesDb.updateExtremaValue(workoutId, targetSensor, ExtremaType.AEROBIC_DECOUPLING, dec, null)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Non-fatal error recomputing HR aerobic efficiency for $workoutId: ${t.message}")
        }
    }

    private fun reexportUpdatedWorkout(
        appContext: Context,
        repo: WorkoutRepository,
        workoutId: Long,
        baseFileName: String?
    ) {
        val updatedWorkout = try {
            repo.allWorkouts.value.find { it.id == workoutId }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve refreshed workout $workoutId for re-export: ${t.message}")
            null
        } ?: return

        try {
            val exportManager = ExportManager(appContext)
            if (!baseFileName.isNullOrEmpty()) {
                exportManager.invalidateWorkoutExport(baseFileName)
            }
            exportManager.exportWorkout(updatedWorkout)
        } catch (t: Throwable) {
            Log.w(TAG, "Non-fatal export exception after Verity Sense deferred sync: ${t.message}")
        }
    }

    private fun resolveTargetIdsForStop(appContext: Context): Pair<String?, String?> {
        var targetId = armedDeviceId
        var targetMac = armedMacAddress
        if (targetId == null) {
            val target = PolarOfflineGattCoordinator.findEnabledPolarDevice(appContext) { name, mac ->
                PolarOfflineGattCoordinator.isPolarVeritySenseDevice(appContext, name, macAddress = mac)
            }
            targetId = target?.polarId
            targetMac = target?.macAddress
        }
        return targetId to targetMac
    }

    private fun resolveTargetIdsForRetry(
        appContext: Context,
        persistedDeviceId: String?
    ): Pair<String?, String?> {
        val resolvedId = persistedDeviceId?.takeIf { it.isNotBlank() }
            ?: armedDeviceId
            ?: PolarOfflineGattCoordinator.findEnabledPolarDevice(appContext) { name, mac ->
                PolarOfflineGattCoordinator.isPolarVeritySenseDevice(appContext, name, macAddress = mac)
            }?.polarId
        val resolvedMac = resolvedId?.let { id ->
            PolarOfflineGattCoordinator.findMacAddressForPolarId(appContext, id) ?: armedMacAddress
        }
        return resolvedId to resolvedMac
    }

    private fun recordPendingSync(
        context: Context,
        workoutId: Long,
        targetId: String?,
        matchedPath: String?,
        windowStartMs: Long,
        windowEndMs: Long
    ) {
        val devId = targetId ?: armedDeviceId
        val taggedExerciseId = "$VERITY_EXERCISE_PREFIX${matchedPath.orEmpty()}"
        WorkoutSummariesDatabaseManager.getInstance(context).setPolarSyncPending(
            workoutId,
            devId,
            taggedExerciseId,
            windowStartMs,
            windowEndMs
        )
    }

    private fun stripVerityPrefix(exerciseId: String?): String? {
        if (exerciseId.isNullOrBlank()) return null
        if (exerciseId.startsWith(VERITY_SWIM_BUSY_PREFIX)) {
            return null
        }
        val raw = if (exerciseId.startsWith(VERITY_EXERCISE_PREFIX)) {
            exerciseId.removePrefix(VERITY_EXERCISE_PREFIX)
        } else {
            exerciseId
        }
        return raw.takeIf { it.isNotBlank() }
    }

    private fun resetArmedState(expectedToken: Long = 0L) = synchronized(armedStateLock) {
        if (expectedToken == 0L || currentArmedToken == expectedToken) {
            isArmed.set(false); armedDeviceId = null; armedMacAddress = null
        }
    }

    private fun showSyncNotification(context: Context) {
        try {
            val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__TRACKING_2)
                .setSmallIcon(R.drawable.ic_stat_notify_logo)
                .setContentTitle(context.getString(R.string.notification_polar_verity_syncing_title))
                .setContentText(context.getString(R.string.notification_polar_verity_syncing_desc))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
            val notificationManager = NotificationManagerCompat.from(context)
            val hasPostPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPostPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(POLAR_VERITY_SYNC_NOTIFICATION_ID, builder.build())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not show Verity sync notification: ${t.message}")
        }
    }

    private fun dismissSyncNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(POLAR_VERITY_SYNC_NOTIFICATION_ID)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not dismiss Verity sync notification: ${t.message}")
        }
    }

    private fun selectMatchingHrEntry(
        entries: List<PolarOfflineRecordingEntry>,
        windowStartMs: Long,
        windowEndMs: Long,
        preferredPath: String? = null,
        toleranceMs: Long = ENTRY_MATCH_TOLERANCE_MS
    ): PolarOfflineRecordingEntry? {
        val hrEntries = entries.filter { it.type == PolarBleApi.PolarDeviceDataType.HR }
        val zone = ZoneId.systemDefault()
        val pathMatch = if (!preferredPath.isNullOrBlank()) {
            hrEntries.firstOrNull { it.path == preferredPath || it.path.contains(preferredPath) }
        } else {
            null
        }
        return when {
            hrEntries.isEmpty() -> null
            pathMatch != null -> pathMatch
            windowStartMs <= 0L -> hrEntries.minByOrNull { entry ->
                entry.date.atZone(zone).toInstant().toEpochMilli()
            }
            else -> {
                val effectiveEndMs = if (windowEndMs >= windowStartMs) windowEndMs else windowStartMs
                hrEntries.filter { entry ->
                    val entryMs = entry.date.atZone(zone).toInstant().toEpochMilli()
                    entryMs in (windowStartMs - toleranceMs)..(effectiveEndMs + toleranceMs)
                }.minByOrNull { entry ->
                    val entryMs = entry.date.atZone(zone).toInstant().toEpochMilli()
                    abs(entryMs - windowStartMs)
                }
            }
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
