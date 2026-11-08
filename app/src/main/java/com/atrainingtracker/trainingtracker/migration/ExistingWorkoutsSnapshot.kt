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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory snapshot of existing workout identifiers (REQ-MIG-050.1).
 *
 * Pre-loaded before executing bulk import batches (Strava export ZIPs, local SAF multi-file imports,
 * Dropbox/Google Drive cloud recovery) to enable zero-I/O upstream deduplication in O(1) time
 * without stream parsing, GZIP decompression, or temporary file copying.
 */
class ExistingWorkoutsSnapshot(
    initialBaseNames: Set<String> = emptySet(),
    initialStravaIds: Set<String> = emptySet()
) {
    // Thread-safe concurrent sets for safe access across concurrent coroutine workers
    private val baseNames: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val stravaIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    init {
        initialBaseNames.forEach { name ->
            if (name.isNotBlank()) {
                baseNames.add(normalizeBaseName(name))
            }
        }
        initialStravaIds.forEach { id ->
            if (id.isNotBlank()) {
                stravaIds.add(id.trim())
            }
        }
    }

    /**
     * Checks if a workout with the given [baseFileName] already exists in the snapshot (case-insensitive).
     */
    fun containsFileBaseName(baseFileName: String?): Boolean {
        if (baseFileName.isNullOrBlank()) return false
        return baseNames.contains(normalizeBaseName(baseFileName))
    }

    /**
     * Checks if a Strava activity with the given [activityId] already exists in the snapshot.
     */
    fun containsStravaActivityId(activityId: String?): Boolean {
        if (activityId.isNullOrBlank()) return false
        return stravaIds.contains(activityId.trim())
    }

    /**
     * Checks whether a workout is already recorded in the snapshot by base name or Strava activity ID.
     */
    fun isDuplicate(baseFileName: String?, stravaActivityId: String? = null): Boolean {
        if (containsFileBaseName(baseFileName)) return true
        if (containsStravaActivityId(stravaActivityId)) return true
        return false
    }

    /**
     * Registers a newly imported workout's identifiers into the snapshot.
     */
    fun register(baseFileName: String?, stravaActivityId: String? = null) {
        if (!baseFileName.isNullOrBlank()) {
            baseNames.add(normalizeBaseName(baseFileName))
        }
        if (!stravaActivityId.isNullOrBlank()) {
            stravaIds.add(stravaActivityId.trim())
        }
    }

    val totalBaseNames: Int
        get() = baseNames.size

    val totalStravaIds: Int
        get() = stravaIds.size

    companion object {
        fun normalizeBaseName(name: String): String = name.trim().lowercase(Locale.ROOT)

        /**
         * Factory method to load an [ExistingWorkoutsSnapshot] from persistence in a single pass.
         */
        fun create(context: Context): ExistingWorkoutsSnapshot {
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)
            val stravaDb = StravaUploadDbHelper.getInstance(context)
            return create(summaryDb, stravaDb)
        }

        fun create(
            summaryDb: WorkoutSummariesDatabaseManager,
            stravaDb: StravaUploadDbHelper
        ): ExistingWorkoutsSnapshot {
            val baseNames = summaryDb.allFileBaseNames
            val stravaIds = stravaDb.allActivityIds
            return ExistingWorkoutsSnapshot(baseNames, stravaIds)
        }
    }
}
