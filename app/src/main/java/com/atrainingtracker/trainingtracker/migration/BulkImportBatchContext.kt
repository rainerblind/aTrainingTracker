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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coroutine-scoped batch context active across bulk workout imports (Strava archives, multi-file
 * picker batches, Dropbox/Google Drive recovery, and incremental cloud auto-import) (REQ-STB-021.3).
 *
 * Defers expensive per-workout in-memory state reloads (`WorkoutRepository.reloadWorkoutData`,
 * `WorkoutClusterRepository.refreshClusters`, and `LiveClimbsRepository.refreshClimbs`) until a
 * single post-batch reconciliation pass runs, preserving public import method arities for tests.
 */
class BulkImportBatchContext : AbstractCoroutineContextElement(Key) {
    private val climbsPersisted = AtomicBoolean(false)

    val hasPersistedClimbs: Boolean
        get() = climbsPersisted.get()

    fun markClimbsPersisted() {
        climbsPersisted.set(true)
    }

    companion object Key : CoroutineContext.Key<BulkImportBatchContext>
}

/**
 * Executes [block] within a [BulkImportBatchContext] when [enabled] is true, reusing any
 * already-installed parent batch context so nested bulk coordinators share [BulkImportBatchContext.hasPersistedClimbs].
 */
internal suspend fun <T> withBulkImportBatch(
    enabled: Boolean = true,
    block: suspend (BulkImportBatchContext?) -> T
): T {
    val existing = coroutineContext[BulkImportBatchContext]
    if (!enabled || existing != null) {
        return block(existing)
    }
    val batch = BulkImportBatchContext()
    return withContext(batch) {
        block(batch)
    }
}

/**
 * Performs the single post-batch repository reconciliation after bulk imports complete (REQ-STB-021.3).
 * Runs directly on the caller's dispatcher so tests controlling dispatchers remain deterministic.
 */
internal suspend fun reconcileBulkImportBatch(
    context: Context,
    refreshClimbs: Boolean,
    logTag: String = "BulkImportBatch"
) {
    try {
        WorkoutRepository.getInstance(context).loadAllWorkouts()
        WorkoutClusterRepository.getInstance(context).refreshClusters()
        if (refreshClimbs) {
            LiveClimbsRepository.getInstance(context).refreshClimbs()
        }
    } catch (e: Exception) {
        Log.w(logTag, "Post-import batch reconciliation failed: ${e.message}")
    }
}
