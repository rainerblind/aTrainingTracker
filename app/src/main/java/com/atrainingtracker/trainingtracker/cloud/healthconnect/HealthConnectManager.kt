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

package com.atrainingtracker.trainingtracker.cloud.healthconnect

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Manages Health Connect SDK availability, permission inspection, and
 * asynchronous workout synchronization (REQ-SET-100, REQ-SET-103).
 */
object HealthConnectManager {

    private const val TAG = "HealthConnectManager"

    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        "android.permission.health.WRITE_EXERCISE_ROUTE",
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(PowerRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
        HealthPermission.getWritePermission(StepsCadenceRecord::class)
    )

    val HRV_READ_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    )

    val SLEEP_READ_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    @VisibleForTesting
    var clientProvider: ((Context) -> HealthConnectClient)? = null

    @VisibleForTesting
    var sdkStatusOverride: Int? = null

    /**
     * Returns the Health Connect SDK availability status code.
     */
    fun getSdkStatus(context: Context): Int {
        sdkStatusOverride?.let { return it }
        return HealthConnectClient.getSdkStatus(context)
    }

    /**
     * Checks if Health Connect is available on the current device.
     */
    fun isAvailable(context: Context): Boolean {
        return getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Retrieves the HealthConnectClient instance, honoring test providers when configured.
     */
    fun getClient(context: Context): HealthConnectClient {
        return clientProvider?.invoke(context) ?: HealthConnectClient.getOrCreate(context)
    }

    /**
     * Checks whether all required write permissions are currently granted.
     */
    suspend fun hasAllPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = getClient(context)
            val granted = client.permissionController.getGrantedPermissions()
            REQUIRED_PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Health Connect permissions: ${e.message}")
            false
        }
    }

    /**
     * Checks whether minimal exercise write permission is granted.
     */
    suspend fun hasWriteExercisePermission(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = getClient(context)
            val granted = client.permissionController.getGrantedPermissions()
            HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted
        } catch (e: Exception) {
            Log.w(TAG, "Error checking exercise write permission: ${e.message}")
            false
        }
    }

    /**
     * Checks whether required Health Connect HRV read permissions are granted (REQ-PER-031.1).
     * Gates strictly on HeartRateVariabilityRmssdRecord; RestingHeartRateRecord is optional.
     */
    suspend fun hasHrvReadPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = getClient(context)
            val granted = client.permissionController.getGrantedPermissions()
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in granted
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Health Connect HRV read permissions: ${e.message}")
            false
        }
    }

    /**
     * Asynchronously synchronizes a completed workout on [Dispatchers.IO].
     */
    @JvmStatic
    @JvmOverloads
    fun syncWorkoutAsync(context: Context, workoutData: WorkoutData, force: Boolean = false) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            syncWorkout(context, workoutData, force)
        }
    }

    /**
     * Synchronizes a completed workout and its telemetry series to Android Health Connect.
     * Operates idempotently on [Dispatchers.IO] using clientRecordId deduplication (REQ-SET-103).
     *
     * @param force When true, bypasses the global [TrainingApplication.syncToHealthConnect] check
     *              for explicit manual sync invocations.
     */
    suspend fun syncWorkout(
        context: Context,
        workoutData: WorkoutData,
        force: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!force && !TrainingApplication.syncToHealthConnect()) {
            return@withContext Result.failure(IllegalStateException("Health Connect sync is disabled in settings"))
        }
        if (!isAvailable(context)) {
            return@withContext Result.failure(IllegalStateException("Health Connect is not available on this device"))
        }

        try {
            val client = getClient(context)
            val granted = client.permissionController.getGrantedPermissions()
            val writeExercisePerm = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
            if (writeExercisePerm !in granted) {
                return@withContext Result.failure(SecurityException("Missing Health Connect exercise write permission"))
            }

            val hasRoutePermission = "android.permission.health.WRITE_EXERCISE_ROUTE" in granted
            val (sessionRecord, seriesRecords) = HealthConnectRecordMapper.extractRecords(
                context = context,
                workoutData = workoutData,
                hasRoutePermission = hasRoutePermission
            )

            // Insert or update exercise session
            insertOrUpdateRecords(client, listOf(sessionRecord))

            // Filter series records to granted permissions
            val allowedSeries = seriesRecords.filter { record ->
                when (record) {
                    is HeartRateRecord -> HealthPermission.getWritePermission(HeartRateRecord::class) in granted
                    is PowerRecord -> HealthPermission.getWritePermission(PowerRecord::class) in granted
                    is SpeedRecord -> HealthPermission.getWritePermission(SpeedRecord::class) in granted
                    is DistanceRecord -> HealthPermission.getWritePermission(DistanceRecord::class) in granted
                    is ElevationGainedRecord -> HealthPermission.getWritePermission(ElevationGainedRecord::class) in granted
                    is TotalCaloriesBurnedRecord -> HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class) in granted
                    is CyclingPedalingCadenceRecord -> HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class) in granted
                    is StepsCadenceRecord -> HealthPermission.getWritePermission(StepsCadenceRecord::class) in granted
                    else -> true
                }
            }

            if (allowedSeries.isNotEmpty()) {
                insertOrUpdateRecords(client, allowedSeries)
            }

            TrainingApplication.setHealthConnectLastSyncTimestamp(System.currentTimeMillis())
            Log.i(TAG, "Successfully synced workout '${workoutData.workoutName}' (${workoutData.fileBaseName}) to Health Connect")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync workout to Health Connect: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Reads the most recent sleep session duration in minutes within the last 24 hours (`REQ-HRV-042`).
     * Returns null if Health Connect is unavailable, permissions are not granted, or no record is found.
     */
    suspend fun getLastNightSleepDurationMinutes(context: Context): Int? = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext null
        try {
            val client = getClient(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(SLEEP_READ_PERMISSIONS)) return@withContext null

            val now = Instant.now()
            val startTime = now.minus(24, ChronoUnit.HOURS)
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, now),
                ascendingOrder = false,
                pageSize = 1
            )
            val response = client.readRecords(request)
            val session = response.records.firstOrNull() ?: return@withContext null
            val durationMinutes = ChronoUnit.MINUTES.between(session.startTime, session.endTime).toInt()
            if (durationMinutes > 0) durationMinutes else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read SleepSessionRecord from Health Connect: ${e.message}")
            null
        }
    }

    private suspend fun insertOrUpdateRecords(client: HealthConnectClient, records: List<Record>) {
        try {
            client.insertRecords(records)
        } catch (insertEx: Exception) {
            // Attempt update fallback for idempotent deduplication
            try {
                client.updateRecords(records)
            } catch (updateEx: Exception) {
                Log.w(TAG, "Update fallback failed after insert failure: ${updateEx.message}")
                throw insertEx
            }
        }
    }
}
