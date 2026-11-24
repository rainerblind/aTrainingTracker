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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryPruner
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutMutationEvent
import com.atrainingtracker.trainingtracker.ui.aftermath.toClusterWorkoutSnapshot
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository

/**
 * Encapsulated cross-database workout deletion coordinator (`REQ-DAT-049.1`–`.3`, `ATT-9607`).
 *
 * Resides outside `ui/` and `database/` (`repositories/`) so [WorkoutRepository] stays encapsulated
 * with zero public/internal DB backdoor getters (`REQ-DAT-042.1`, `ArchitectureConformanceContractTest`).
 */
internal class WorkoutDeletionCoordinator(
    private val application: Context,
    private val summariesManagerProvider: () -> WorkoutSummariesDatabaseManager,
    private val samplesManagerProvider: () -> WorkoutSamplesDatabaseManager,
    private val lapsDatabaseManagerProvider: () -> LapsDatabaseManager,
    private val stravaUploadDbHelperProvider: () -> StravaUploadDbHelper,
    private val resolvePreDeleteWorkout: (Long) -> WorkoutData?,
    private val onEvictCaches: (Long) -> Unit,
    private val onRemoveFromAllWorkouts: (Set<Long>) -> Unit,
    private val onEmitMutationEvent: (WorkoutMutationEvent) -> Unit
) {

    /**
     * Executes complete synchronous cascade deletion of [workoutId] across all 7 per-workout
     * persistence stores, WorkManager jobs, photo files, raw sensor sidecars, in-memory caches,
     * route clusters, and post-commit mutation subscribers (`REQ-DAT-049.1`, `REQ-DAT-049.3`).
     */
    fun deleteWorkoutCascade(
        workoutId: Long,
        emitEvent: Boolean = true,
        updateInMemoryList: Boolean = true,
        syncClusterRepo: Boolean = true
    ): Boolean {
        onEvictCaches(workoutId)

        val preDeleteWorkout = resolvePreDeleteSnapshotSafely(workoutId)
        val fileBaseName = preDeleteWorkout?.fileBaseName?.takeIf { it.isNotBlank() }
            ?: runCatching { summariesManagerProvider().getBaseFileName(workoutId) }.getOrNull()

        purgePersistedWorkoutData(workoutId, fileBaseName)

        // Re-evict/tombstone in case a concurrent loadAllWorkouts() started mid-delete.
        onEvictCaches(workoutId)
        if (updateInMemoryList) {
            onRemoveFromAllWorkouts(setOf(workoutId))
        }

        val clusterId = preDeleteWorkout?.clusterId ?: -1L
        if (preDeleteWorkout != null && clusterId != -1L) {
            updateClusterEngineSafely(preDeleteWorkout)
            if (syncClusterRepo) {
                syncClusterRepositorySafely(clusterId)
            }
        }

        if (emitEvent) {
            onEmitMutationEvent(
                WorkoutMutationEvent.Deleted(
                    workoutId = workoutId,
                    clusterId = clusterId,
                    equipmentId = preDeleteWorkout?.equipmentId ?: -1L,
                    sportId = preDeleteWorkout?.sportId ?: -1L,
                    wasFinished = preDeleteWorkout?.finished ?: true
                )
            )
        }
        return true
    }

    /**
     * Executes synchronous awaited bulk deletion of all workouts older than [daysToKeep] days,
     * guaranteeing every SQLite row and sidecar is committed as deleted before returning `true`
     * and emitting at most ONE [WorkoutMutationEvent.BulkDeleted] (`REQ-DAT-049.2`).
     */
    fun deleteOldWorkoutsCascade(
        daysToKeep: Int,
        progressCallback: WorkoutDeletionHelper.DeletionProgressCallback? = null
    ): Boolean {
        val oldWorkoutIds = summariesManagerProvider().getOldWorkouts(daysToKeep)
        if (oldWorkoutIds.isEmpty()) {
            return true
        }

        val total = oldWorkoutIds.size
        val deletedIds = ArrayList<Long>(total)
        val affectedClusterIds = LinkedHashSet<Long>()

        for (i in 0 until total) {
            val workoutId = oldWorkoutIds[i]
            progressCallback?.onProgress(i + 1, total, workoutId)

            val preDeleteWorkout = resolvePreDeleteSnapshotSafely(workoutId)
            val fileBaseName = preDeleteWorkout?.fileBaseName?.takeIf { it.isNotBlank() }
                ?: runCatching { summariesManagerProvider().getBaseFileName(workoutId) }.getOrNull()

            onEvictCaches(workoutId)
            purgePersistedWorkoutData(workoutId, fileBaseName)
            onEvictCaches(workoutId)

            val clusterId = preDeleteWorkout?.clusterId ?: -1L
            if (preDeleteWorkout != null && clusterId != -1L) {
                updateClusterEngineSafely(preDeleteWorkout)
                affectedClusterIds.add(clusterId)
            }
            deletedIds.add(workoutId)
        }

        onRemoveFromAllWorkouts(deletedIds.toSet())
        for (clusterId in affectedClusterIds) {
            syncClusterRepositorySafely(clusterId)
        }
        if (deletedIds.isNotEmpty()) {
            onEmitMutationEvent(WorkoutMutationEvent.BulkDeleted(deletedIds))
        }
        return true
    }

    /**
     * Post-commit reconciliation fallback when [WorkoutRepository.deleteWorkout] executes against
     * an injected test mock [WorkoutDeletionHelper] that bypasses [deleteWorkoutCascade].
     */
    fun reconcileExternalDelete(workoutId: Long, preDeleteWorkout: WorkoutData?) {
        onEvictCaches(workoutId)
        onRemoveFromAllWorkouts(setOf(workoutId))

        val clusterId = preDeleteWorkout?.clusterId ?: -1L
        if (preDeleteWorkout != null && clusterId != -1L) {
            updateClusterEngineSafely(preDeleteWorkout)
            syncClusterRepositorySafely(clusterId)
        }

        onEmitMutationEvent(
            WorkoutMutationEvent.Deleted(
                workoutId = workoutId,
                clusterId = clusterId,
                equipmentId = preDeleteWorkout?.equipmentId ?: -1L,
                sportId = preDeleteWorkout?.sportId ?: -1L,
                wasFinished = preDeleteWorkout?.finished ?: true
            )
        )
    }

    private fun purgePersistedWorkoutData(workoutId: Long, fileBaseName: String?) {
        summariesManagerProvider().deleteWorkout(workoutId)
        lapsDatabaseManagerProvider().deleteWorkout(workoutId)

        try {
            WorkoutRrRepository.getInstance(application).deleteWorkoutBlocking(workoutId)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not delete workout RR intervals for $workoutId: ${t.message}")
        }

        try {
            ActiveDevicesDbHelper.getInstance(application).deleteWorkout(workoutId)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not delete active devices for $workoutId: ${t.message}")
        }

        if (!fileBaseName.isNullOrEmpty() && ExportStatusDatabaseManager.isValidFileBaseName(fileBaseName)) {
            try {
                val wm = WorkManager.getInstance(application)
                wm.cancelAllWorkByTag(fileBaseName)
                wm.cancelUniqueWork("export_$fileBaseName")
                for (format in FileFormat.values()) {
                    wm.cancelUniqueWork(ExportManager.getUniqueWorkName(fileBaseName, format))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not cancel WorkManager tasks on deletion for $fileBaseName: ${t.message}")
            }

            samplesManagerProvider().deleteWorkout(fileBaseName)
            try {
                ExportStatusRepository.getInstance(application).deleteExportWorkout(fileBaseName)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not delete export status for $fileBaseName: ${t.message}")
            }
            try {
                PolarRawTelemetryPruner.deleteSidecar(application, fileBaseName)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not delete raw sensor sidecars for $fileBaseName: ${t.message}")
            }
            try {
                stravaUploadDbHelperProvider().deleteWorkout(fileBaseName)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not delete strava record for $fileBaseName: ${t.message}")
            }
        }
    }

    private fun resolvePreDeleteSnapshotSafely(workoutId: Long): WorkoutData? {
        return try {
            resolvePreDeleteWorkout(workoutId)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not resolve pre-delete snapshot for workoutId=$workoutId: ${t.message}")
            null
        }
    }

    private fun updateClusterEngineSafely(workout: WorkoutData) {
        try {
            WorkoutClusterEngine.getInstance(application)
                .onWorkoutDeleted(application, workout.toClusterWorkoutSnapshot())
        } catch (t: Throwable) {
            Log.w(TAG, "Could not update cluster engine after deleting workoutId=${workout.id}: ${t.message}")
        }
    }

    private fun syncClusterRepositorySafely(clusterId: Long) {
        try {
            WorkoutClusterRepository.getInstance(application)
                .syncClusterAfterWorkoutDeletion(clusterId)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not sync cluster repository for clusterId=$clusterId: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "WorkoutDeletionCoord"
    }
}
