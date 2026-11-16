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
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Controller for Polar H10 offline RR interval exercise recording (ATT-7762, REQ-SET-104)
 * and workout stop backfill / export gating (ATT-7918, REQ-SET-105).
 *
 * Implements Android 13+ (API 33) isolation, GATT handoff/arbitration with [MyBTLEDevice],
 * automated device clock synchronization, stale exercise cleanup, single-shot [PolarH10OfflineExerciseApi.SampleType.RR]
 * recording arming, zero-gap fast path bypass, gap-only SQLite patching, post-backfill HRV summary
 * recomputation, and transactional slot cleanup.
 */
@Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooGenericExceptionCaught",
    "ReturnCount",
    "MaxLineLength",
    "MagicNumber",
    "PackageNaming",
    "UnusedParameter"
)
object PolarH10OfflineController {

    private const val TAG = "PolarH10OfflineCtrl"
    const val ARMING_TIMEOUT_MS = 15_000L
    const val BACKFILL_TIMEOUT_MS = 30_000L
    const val POLAR_SYNC_NOTIFICATION_ID = 8045

    private val isArmed = AtomicBoolean(false)
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sessionStartTimeMs: Long = 0L
    private var armedDeviceId: String? = null
    private var armedExerciseId: String? = null

    @VisibleForTesting
    var dispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    var apiOverride: PolarBleApi? = null

    @VisibleForTesting
    var sdkIntOverride: Int? = null

    internal fun getEffectiveSdkInt(): Int = sdkIntOverride ?: Build.VERSION.SDK_INT

    @JvmStatic
    fun isRecordingArmed(): Boolean = isArmed.get()

    @JvmStatic
    fun setSessionStartTimeMs(timeMs: Long) {
        sessionStartTimeMs = timeMs
    }

    @JvmStatic
    fun getSessionStartTimeMs(): Long = sessionStartTimeMs

    @VisibleForTesting
    fun setArmedForTesting(armed: Boolean) {
        isArmed.set(armed)
    }

    @JvmStatic
    fun getArmedDeviceId(): String? = armedDeviceId

    @JvmStatic
    fun getArmedExerciseId(): String? = armedExerciseId

    @VisibleForTesting
    fun setArmedMetadataForTesting(deviceId: String?, exerciseId: String?) {
        armedDeviceId = deviceId
        armedExerciseId = exerciseId
    }

    @VisibleForTesting
    fun resetForTesting() {
        isArmed.set(false)
        apiOverride = null
        sdkIntOverride = null
        sessionStartTimeMs = 0L
        armedDeviceId = null
        armedExerciseId = null
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
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            isArmed.set(false)
            return
        }
        if (!isArmed.get()) {
            val api = apiOverride
            if (api != null) {
                try {
                    api.shutDown()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error shutting down test PolarBleApi: ${t.message}", t)
                }
            }
        }
    }

    /**
     * Evaluates live telemetry continuity across WorkoutSamples.db and WorkoutRr.db (REQ-SET-105.1).
     * Returns false if 100% continuous (zero missing seconds and zero ARTIFACT_GAP), true if gaps exist.
     */
    @JvmStatic
    fun shouldBackfill(context: Context, baseFileName: String, workoutId: Long): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        val appContext = context.applicationContext

        // 1. Check WorkoutRr.db for gaps or absence of beats
        val rrRepo = WorkoutRrRepository.getInstance(appContext)
        val intervals = rrRepo.getIntervalsForWorkoutBlocking(workoutId)
        if (intervals.isEmpty()) {
            return true
        }
        if (intervals.any { it.artifactFlag == RrBeatEvent.ARTIFACT_GAP }) {
            return true
        }

        // 2. Check WorkoutSamples.db for missing/unpopulated HR samples
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(appContext)
        if (samplesDb.hasMissingHeartRateSamples(baseFileName)) {
            return true
        }

        return false
    }

    /**
     * Synchronous blocking convenience entry point for TrackerService.endWorkout() (REQ-SET-105.5).
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
     * Executes Polar H10 workout stop, zero-gap evaluation, PFTP backfill, and transactional cleanup (REQ-SET-105).
     */
    suspend fun stopAndBackfillWorkout(
        context: Context,
        workoutId: Long,
        baseFileName: String,
        startTimeMs: Long = sessionStartTimeMs
    ): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            isArmed.set(false)
            return false
        }

        val appContext = context.applicationContext
        val targetDevice = PolarOfflineGattCoordinator.findEnabledPolarDevice(appContext) { name, _ ->
            PolarOfflineGattCoordinator.isPolarH10Name(name)
        }
        if (targetDevice == null) {
            Log.d(TAG, "No enabled Polar H10 device found for backfill.")
            isArmed.set(false)
            return false
        }
        val targetMac = targetDevice.macAddress
        val targetId = targetDevice.polarId

        val needsBackfill = shouldBackfill(appContext, baseFileName, workoutId)
        Log.i(TAG, "Polar H10 stopAndBackfillWorkout: polarId=$targetId, needsBackfill=$needsBackfill")

        val deviceManager = BANALService.getDeviceManager()
        val btleDevice = deviceManager?.getBtleDeviceByAddress(targetMac)

        btleDevice?.pauseGattOperations()
        if (needsBackfill) {
            showSyncNotification(appContext)
        }

        return try {
            val sharedApi = if (apiOverride == null) PolarOfflineGattCoordinator.getActiveSession(targetId) else null
            val ownsApiLifecycle = apiOverride == null && sharedApi == null
            val timeoutResult = withTimeoutOrNull(BACKFILL_TIMEOUT_MS) {
                val api = apiOverride ?: sharedApi ?: PolarBleApiDefaultImpl.defaultImplementation(
                    appContext,
                    setOf(
                        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
                    )
                )

                try {
                    api.connectToDevice(targetId)
                    api.waitForConnection(targetId)

                    // 1. Stop ongoing recording on strap
                    try {
                        val status = api.requestRecordingStatus(targetId)
                        if (status.ongoing) {
                            api.stopRecording(targetId)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking/stopping recording for $targetId: ${e.message}")
                    }

                    // 2. Zero-gap fast path: bypass PFTP download and clean up exercises immediately
                    if (!needsBackfill) {
                        Log.i(TAG, "Zero-gap fast path: bypassing PFTP download, cleaning up exercises.")
                        try {
                            val exercises = api.listExercises(targetId).toList()
                            for (entry in exercises) {
                                api.removeExercise(targetId, entry)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error cleaning up exercises in fast path: ${e.message}")
                        }
                        return@withTimeoutOrNull true
                    }

                    // 3. Needs backfill: List exercises and fetch recorded RR samples
                    val exercises = try {
                        api.listExercises(targetId).toList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error listing exercises for backfill on $targetId: ${e.message}")
                        emptyList()
                    }

                    if (exercises.isEmpty()) {
                        Log.w(TAG, "No exercises found on Polar H10 for backfill.")
                        return@withTimeoutOrNull false
                    }

                    var allPatchedSuccessfully = true
                    for (entry in exercises) {
                        try {
                            val data = api.fetchExercise(targetId, entry)
                            val hrSamples = data.hrSamples
                            if (hrSamples.isNotEmpty()) {
                                val anchorMs = if (startTimeMs > 0) {
                                    startTimeMs
                                } else {
                                    entry.date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                }

                                val intervals = mutableListOf<WorkoutRrInterval>()
                                val hrPairs = mutableListOf<android.util.Pair<Long, Int>>()
                                var cumulativeMs = 0L
                                var beatIndex = 0

                                for (rr in hrSamples) {
                                    cumulativeMs += rr
                                    val beatTs = anchorMs + cumulativeMs
                                    val bpm = (60_000.0 / rr).roundToInt().coerceIn(30, 250)
                                    hrPairs.add(android.util.Pair(beatTs, bpm))
                                    intervals.add(
                                        WorkoutRrInterval(
                                            workoutId = workoutId,
                                            beatIndex = beatIndex++,
                                            beatTimestampMs = beatTs,
                                            rrMs = rr.toFloat(),
                                            artifactFlag = 0,
                                            gapBeats = 0
                                        )
                                    )
                                }

                                // Patch WorkoutSamples.db
                                val samplesManager = WorkoutSamplesDatabaseManager.getInstance(appContext)
                                samplesManager.patchMissingHeartRateSamples(baseFileName, anchorMs, hrPairs)

                                // Patch WorkoutRr.db
                                val rrRepo = WorkoutRrRepository.getInstance(appContext)
                                rrRepo.insertIntervals(intervals)

                                // Persist raw telemetry sidecar (REQ-SET-117.1, ATT-8019)
                                try {
                                    PolarRawTelemetryRecorder.recordOfflineExercise(
                                        appContext,
                                        baseFileName,
                                        targetId,
                                        anchorMs,
                                        hrSamples
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error recording raw telemetry sidecar for offline exercise on $targetId: ${e.message}")
                                }

                                // Recompute HRV summaries
                                HrvPostWorkoutRecomputer.recomputeWorkout(appContext, workoutId)
                                WorkoutRepository.getInstance(appContext).refreshWorkout(workoutId)

                                // Transactional slot cleanup: removeExercise strictly AFTER SQLite commit
                                api.removeExercise(targetId, entry)
                                Log.i(TAG, "Successfully backfilled and removed exercise ${entry.path} on $targetId")
                            } else {
                                api.removeExercise(targetId, entry)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed backfill processing for entry ${entry.path}: ${e.message}", e)
                            allPatchedSuccessfully = false
                        }
                    }

                    if (ownsApiLifecycle) {
                        try {
                            api.disconnectFromDevice(targetId)
                        } catch (ignored: Exception) {
                        }
                    }

                    allPatchedSuccessfully
                } finally {
                    if (ownsApiLifecycle) {
                        try {
                            api.cleanup()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error cleaning up PolarBleApi: ${t.message}")
                        }
                    }
                }
            }
            val success = timeoutResult ?: false
            if (timeoutResult == null) {
                Log.w(TAG, "Polar H10 backfill timed out after $BACKFILL_TIMEOUT_MS ms; falling back cleanly.")
            }
            if (!success && needsBackfill && workoutId > 0) {
                recordPendingSync(appContext, workoutId, targetId, startTimeMs)
            }
            success
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during Polar H10 stopAndBackfillWorkout: ${t.message}", t)
            if (needsBackfill && workoutId > 0) {
                recordPendingSync(appContext, workoutId, targetId, startTimeMs)
            }
            return false
        } finally {
            isArmed.set(false)
            if (needsBackfill) {
                dismissSyncNotification(appContext)
            }
            btleDevice?.resumeGattOperations()
        }
    }

    private fun recordPendingSync(context: Context, workoutId: Long, targetId: String?, startTimeMs: Long) {
        val devId = targetId ?: armedDeviceId
        val exId = armedExerciseId
        val startMs = if (startTimeMs > 0) startTimeMs else sessionStartTimeMs
        val endMs = System.currentTimeMillis()
        if (!devId.isNullOrEmpty()) {
            try {
                Log.i(TAG, "Persisting pending Polar H10 sync for workout $workoutId (dev=$devId, exId=$exId)")
                val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
                summariesDb.setPolarSyncPending(workoutId, devId, exId, startMs, endMs)
                WorkoutRepository.getInstance(context).refreshWorkout(workoutId)
            } catch (e: Exception) {
                Log.e(TAG, "Could not persist pending polar sync for workout $workoutId: ${e.message}", e)
            }
        }
    }

    /**
     * Synchronous blocking convenience entry point for deferred Polar H10 sync (REQ-SET-106.3, ATT-7919).
     */
    @JvmStatic
    fun syncPendingWorkoutBlocking(context: Context, workoutId: Long): Boolean {
        return runBlocking(dispatcher) {
            syncPendingWorkout(context, workoutId)
        }
    }

    /**
     * Executes deferred Polar H10 offline sync for a workout with pending sync metadata (REQ-SET-106.3, ATT-7919).
     */
    suspend fun syncPendingWorkout(context: Context, workoutId: Long): Boolean {
        if (getEffectiveSdkInt() < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "API level < 33; Polar H10 offline sync unavailable.")
            return false
        }

        val appContext = context.applicationContext
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(appContext)
        val pendingData = summariesDb.getPolarPendingSyncData(workoutId)
        if (pendingData == null || !pendingData.isPending) {
            Log.w(TAG, "No pending Polar sync found for workout $workoutId")
            return false
        }

        val targetId = pendingData.deviceId
        if (targetId.isNullOrEmpty()) {
            Log.w(TAG, "No target device ID for workout $workoutId")
            return false
        }

        val devicesDb = DevicesDatabaseManager.getInstance(appContext)
        val devices = devicesDb.allDevicesForCache
        var targetMac: String? = null
        for (dev in devices) {
            if (dev.type == DeviceType.HRM && dev.protocol == Protocol.BLUETOOTH_LE) {
                val mac = devicesDb.getBluetoothMACAddress(dev.id)
                val name = dev.name ?: ""
                if (mac == targetId || name.contains(targetId, ignoreCase = true)) {
                    targetMac = mac
                    break
                }
            }
        }
        val deviceManager = BANALService.getDeviceManager()
        val btleDevice = targetMac?.let { deviceManager?.getBtleDeviceByAddress(it) }
        btleDevice?.pauseGattOperations()

        val sharedApi = if (apiOverride == null) PolarOfflineGattCoordinator.getActiveSession(targetId) else null
        val ownsApiLifecycle = apiOverride == null && sharedApi == null
        try {
            return withTimeoutOrNull(BACKFILL_TIMEOUT_MS) {
                val api = apiOverride ?: sharedApi ?: PolarBleApiDefaultImpl.defaultImplementation(
                    appContext,
                    setOf(
                        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
                    )
                )

                try {
                    api.connectToDevice(targetId)
                    api.waitForConnection(targetId)

                    try {
                        val status = api.requestRecordingStatus(targetId)
                        if (status.ongoing) {
                            api.stopRecording(targetId)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking/stopping recording for $targetId: ${e.message}")
                    }

                    val exercises = try {
                        api.listExercises(targetId).toList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error listing exercises for pending sync on $targetId: ${e.message}")
                        emptyList()
                    }

                    if (exercises.isEmpty()) {
                        Log.w(TAG, "No exercises found on Polar H10 for pending sync.")
                        return@withTimeoutOrNull false
                    }

                    val matchingEntry = exercises.firstOrNull { entry ->
                        if (!pendingData.exerciseId.isNullOrEmpty() && entry.path.contains(pendingData.exerciseId)) {
                            true
                        } else {
                            val entryEpochMs = entry.date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            (pendingData.expectedStartMs > 0 && Math.abs(entryEpochMs - pendingData.expectedStartMs) <= 600_000L) ||
                                (pendingData.expectedEndMs > 0 && entryEpochMs in pendingData.expectedStartMs..pendingData.expectedEndMs)
                        }
                    } ?: exercises.firstOrNull()

                    if (matchingEntry == null) {
                        Log.w(TAG, "No matching exercise found for workout $workoutId")
                        return@withTimeoutOrNull false
                    }

                    val data = api.fetchExercise(targetId, matchingEntry)
                    val hrSamples = data.hrSamples
                    if (hrSamples.isNotEmpty()) {
                        val anchorMs = if (pendingData.expectedStartMs > 0) {
                            pendingData.expectedStartMs
                        } else {
                            matchingEntry.date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }

                        val intervals = mutableListOf<WorkoutRrInterval>()
                        val hrPairs = mutableListOf<android.util.Pair<Long, Int>>()
                        var cumulativeMs = 0L
                        var beatIndex = 0

                        for (rr in hrSamples) {
                            cumulativeMs += rr
                            val beatTs = anchorMs + cumulativeMs
                            val bpm = (60_000.0 / rr).roundToInt().coerceIn(30, 250)
                            hrPairs.add(android.util.Pair(beatTs, bpm))
                            intervals.add(
                                WorkoutRrInterval(
                                    workoutId = workoutId,
                                    beatIndex = beatIndex++,
                                    beatTimestampMs = beatTs,
                                    rrMs = rr.toFloat(),
                                    artifactFlag = 0,
                                    gapBeats = 0
                                )
                            )
                        }

                        val repo = WorkoutRepository.getInstance(appContext)
                        val baseFileName = repo.getBaseFileName(workoutId) ?: summariesDb.getBaseFileName(workoutId)

                        if (!baseFileName.isNullOrEmpty()) {
                            val samplesManager = WorkoutSamplesDatabaseManager.getInstance(appContext)
                            samplesManager.patchMissingHeartRateSamples(baseFileName, anchorMs, hrPairs)
                        }

                        val rrRepo = WorkoutRrRepository.getInstance(appContext)
                        rrRepo.insertIntervals(intervals)

                        HrvPostWorkoutRecomputer.recomputeWorkout(appContext, workoutId)

                        summariesDb.clearPolarSyncPending(workoutId)

                        api.removeExercise(targetId, matchingEntry)
                        Log.i(TAG, "Successfully performed deferred sync and removed exercise ${matchingEntry.path}")

                        repo.refreshWorkout(workoutId)
                        val updatedWorkout = try {
                            repo.allWorkouts.value.find { it.id == workoutId }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to resolve updated workout from repository: ${t.message}")
                            null
                        }
                        if (updatedWorkout != null) {
                            try {
                                val exportManager = com.atrainingtracker.trainingtracker.exporter.ExportManager(appContext)
                                if (baseFileName != null) {
                                    exportManager.invalidateWorkoutExport(baseFileName)
                                }
                                exportManager.exportWorkout(updatedWorkout)
                            } catch (e: Exception) {
                                Log.w(TAG, "Non-fatal export exception after deferred sync: ${e.message}")
                            }
                        }
                        true
                    } else {
                        api.removeExercise(targetId, matchingEntry)
                        summariesDb.clearPolarSyncPending(workoutId)
                        WorkoutRepository.getInstance(appContext).refreshWorkout(workoutId)
                        true
                    }
                } finally {
                    if (ownsApiLifecycle) {
                        try {
                            api.disconnectFromDevice(targetId)
                        } catch (ignored: Exception) {}
                        try {
                            api.cleanup()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error cleaning up PolarBleApi: ${t.message}")
                        }
                    }
                }
            } ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during Polar H10 syncPendingWorkout: ${t.message}", t)
            return false
        } finally {
            btleDevice?.resumeGattOperations()
        }
    }

    private fun showSyncNotification(context: Context) {
        try {
            val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__TRACKING_2)
                .setSmallIcon(R.drawable.ic_stat_notify_logo)
                .setContentTitle(context.getString(R.string.notification_polar_h10_syncing_title))
                .setContentText(context.getString(R.string.notification_polar_h10_syncing_desc))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
            val notificationManager = NotificationManagerCompat.from(context)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(POLAR_SYNC_NOTIFICATION_ID, builder.build())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not show sync notification: ${t.message}")
        }
    }

    private fun dismissSyncNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(POLAR_SYNC_NOTIFICATION_ID)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not dismiss sync notification: ${t.message}")
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

        val targetDevice = PolarOfflineGattCoordinator.findEnabledPolarDevice(context) { name, _ ->
            PolarOfflineGattCoordinator.isPolarH10Name(name)
        }
        if (targetDevice == null) {
            Log.d(TAG, "No enabled Polar H10 device found in database.")
            return false
        }

        return executeArming(context, targetDevice.macAddress, targetDevice.polarId)
    }

    suspend fun executeArming(context: Context, macAddress: String, polarId: String): Boolean {
        return try {
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = macAddress,
                polarId = polarId,
                features = setOf(
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
                ),
                timeoutMs = ARMING_TIMEOUT_MS,
                apiOverride = apiOverride
            ) { api ->
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

                armedDeviceId = polarId
                armedExerciseId = exerciseId
                isArmed.set(true)
                true
            } ?: run {
                Log.w(TAG, "Arming Polar H10 offline recording timed out after $ARMING_TIMEOUT_MS ms")
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception during Polar H10 arming: ${t.message}", t)
            false
        }
    }
}
