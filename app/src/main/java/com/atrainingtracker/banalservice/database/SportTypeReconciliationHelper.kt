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

package com.atrainingtracker.banalservice.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Diagnostic and data healing helper for sport types (REQ-MIG-044).
 * Reconciles orphan sport type references, manages placeholder lifecycle,
 * and prunes unreferenced placeholder rows.
 */
object SportTypeReconciliationHelper {

    private const val TAG = "SportTypeReconciliationHelper"
    private val DEBUG = TrainingApplication.getDebug(true)

    /**
     * Reconciles orphan sport IDs referenced by workouts, equipment, or clusters
     * by inserting placeholder rows into SportTypes table (REQ-MIG-044.2).
     * Uses database manager singletons and respects BackupManager concurrency locks (REQ-MIG-043).
     *
     * @param context Application context used for localization resources.
     * @param sportDb Writable database instance for SportType.db.
     * @param summariesDb Readable database instance for WorkoutSummaries.db (optional; resolved if null).
     * @param equipmentDb Readable database instance for Equipment.db / SportTypeLinks.db (optional).
     * @param clusterDb Readable database instance for RouteClusters.db (optional).
     * @param dispatcher Coroutine dispatcher for background I/O operations (defaults to Dispatchers.IO).
     * @return Number of placeholder rows inserted.
     */
    suspend fun reconcileOrphanSportTypes(
        context: Context,
        sportDb: SQLiteDatabase,
        summariesDb: SQLiteDatabase? = null,
        equipmentDb: SQLiteDatabase? = null,
        clusterDb: SQLiteDatabase? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Int = withContext(dispatcher) {
        val backupLock = try {
            com.atrainingtracker.trainingtracker.migration.BackupManager.acquireLock(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire BackupManager lock", e)
            null
        }
        try {
            val referencedSportIds = mutableSetOf<Long>()

            // 1. Scan WorkoutSummaries.db via singleton or injected db
            val activeSummariesDb = summariesDb ?: try {
                com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.getInstance(context).database
            } catch (e: Exception) {
                Log.w(TAG, "Could not access WorkoutSummaries.db for orphan sport scan", e)
                null
            }

            activeSummariesDb?.let { sDb ->
                try {
                    sDb.rawQuery(
                        "SELECT DISTINCT sportId FROM WorkoutSummaries WHERE sportId > ?",
                        arrayOf(SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD.toString())
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            if (id > SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD) {
                                referencedSportIds.add(id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query WorkoutSummaries for distinct sportId", e)
                }
            }

            // 2. Scan Equipment links via singleton or injected db
            val activeEquipDb = equipmentDb ?: try {
                com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager.getInstance(context).getDatabase()
            } catch (e: Exception) {
                Log.w(TAG, "Could not access SportTypeLinks.db for orphan sport scan", e)
                null
            }

            activeEquipDb?.let { eDb ->
                try {
                    eDb.rawQuery(
                        "SELECT DISTINCT sport_type_id FROM SportTypeEquipmentLink WHERE sport_type_id > ?",
                        arrayOf(SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD.toString())
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            if (id > SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD) {
                                referencedSportIds.add(id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query SportTypeEquipmentLink for distinct sport_type_id", e)
                }
            }

            // 3. Scan RouteClusters.db via singleton or injected db
            val activeClusterDb = clusterDb ?: try {
                com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.getInstance(context).getDatabase()
            } catch (e: Exception) {
                Log.w(TAG, "Could not access RouteClusters.db for orphan sport scan", e)
                null
            }

            activeClusterDb?.let { cDb ->
                try {
                    cDb.rawQuery(
                        "SELECT DISTINCT probable_sport_id FROM RouteClusters WHERE probable_sport_id > ?",
                        arrayOf(SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD.toString())
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            if (id > SportTypeDatabaseManager.SYSTEM_SPORT_ID_THRESHOLD) {
                                referencedSportIds.add(id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query RouteClusters for distinct probable_sport_id", e)
                }
            }

            if (referencedSportIds.isEmpty()) {
                return@withContext 0
            }

            // 4. Query existing IDs in SportTypes table
            val existingSportIds = mutableSetOf<Long>()
            try {
                sportDb.rawQuery(
                    "SELECT ${SportTypeDatabaseManager.SportType.C_ID} FROM ${SportTypeDatabaseManager.SportType.TABLE}",
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        existingSportIds.add(cursor.getLong(0))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read existing sport type IDs", e)
                return@withContext 0
            }

            val orphanIds = referencedSportIds.filter { it !in existingSportIds }.sorted()
            if (orphanIds.isEmpty()) {
                return@withContext 0
            }

            var insertedCount = 0
            sportDb.beginTransaction()
            try {
                for (orphanId in orphanIds) {
                    val displayName = try {
                        context.getString(R.string.sport_type_restored_placeholder, orphanId)
                    } catch (e: Exception) {
                        "Restored Sport $orphanId"
                    }

                    val cv = ContentValues().apply {
                        put(SportTypeDatabaseManager.SportType.C_ID, orphanId)
                        put(SportTypeDatabaseManager.SportType.UI_NAME, displayName)
                        put(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE, BSportType.UNKNOWN.name)
                        put(SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME, SportTypeDatabaseManager.SportType.DEFAULT_GOLDEN_CHEETAH_NAME)
                        put(SportTypeDatabaseManager.SportType.TCX_NAME, SportTypeDatabaseManager.SportType.DEFAULT_TCX_NAME)
                        put(SportTypeDatabaseManager.SportType.STRAVA_NAME, SportTypeDatabaseManager.SportType.DEFAULT_STRAVA_NAME)
                        put(SportTypeDatabaseManager.SportType.RUNKEEPER_NAME, SportTypeDatabaseManager.SportType.DEFAULT_RUNKEEPER_NAME)
                        put(SportTypeDatabaseManager.SportType.TRAINING_PEAKS_NAME, SportTypeDatabaseManager.SportType.DEFAULT_TRAINING_PEAKS_NAME)
                        put(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED, SportTypeDatabaseManager.SportType.DEFAULT_MIN_AVG_SPEED)
                        put(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED, SportTypeDatabaseManager.SportType.DEFAULT_MAX_AVG_SPEED)
                        put(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER, 1)
                    }

                    val rowId = sportDb.insertWithOnConflict(
                        SportTypeDatabaseManager.SportType.TABLE,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                    if (rowId != -1L) {
                        insertedCount++
                        if (DEBUG) Log.i(TAG, "Resurrected orphan sport placeholder id=$orphanId name=$displayName")
                    }
                }
                sportDb.setTransactionSuccessful()
            } finally {
                sportDb.endTransaction()
            }

            insertedCount
        } finally {
            try {
                backupLock?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release BackupManager lock", e)
            }
        }
    }

    /**
     * Claims and configures a placeholder sport type (REQ-MIG-044.4).
     * Validates case-insensitive name uniqueness before transitioning is_placeholder = 0.
     *
     * @param sportDb Writable database instance for SportType.db.
     * @param sportId Primary key ID of the sport type to claim.
     * @param newName New display name for the sport type.
     * @param bSportType Base sport type enum.
     * @param minSpeed Optional minimum average speed (m/s).
     * @param maxSpeed Optional maximum average speed (m/s).
     * @param stravaName Optional Strava activity type mapping.
     * @param tcxName Optional TCX sport mapping.
     * @param gcName Optional GoldenCheetah activity mapping.
     * @throws IllegalArgumentException if newName is empty or conflicts with an existing sport type name.
     */
    fun claimPlaceholder(
        sportDb: SQLiteDatabase,
        sportId: Long,
        newName: String,
        bSportType: BSportType = BSportType.UNKNOWN,
        minSpeed: Double? = null,
        maxSpeed: Double? = null,
        stravaName: String? = null,
        tcxName: String? = null,
        gcName: String? = null
    ) {
        val trimmedName = newName.trim()
        require(trimmedName.isNotEmpty()) { "Sport type name cannot be blank" }

        // Enforce case-insensitive name uniqueness
        val isNameDuplicate = sportDb.query(
            SportTypeDatabaseManager.SportType.TABLE,
            arrayOf(SportTypeDatabaseManager.SportType.C_ID),
            "${SportTypeDatabaseManager.SportType.UI_NAME} = ? COLLATE NOCASE AND ${SportTypeDatabaseManager.SportType.C_ID} != ?",
            arrayOf(trimmedName, sportId.toString()),
            null, null, null
        ).use { cursor ->
            cursor.moveToFirst()
        }

        if (isNameDuplicate) {
            throw IllegalArgumentException("A sport type with name '$trimmedName' already exists")
        }

        val cv = ContentValues().apply {
            put(SportTypeDatabaseManager.SportType.UI_NAME, trimmedName)
            put(SportTypeDatabaseManager.SportType.BASE_SPORT_TYPE, bSportType.name)
            put(SportTypeDatabaseManager.SportType.IS_PLACEHOLDER, 0)
            minSpeed?.let { put(SportTypeDatabaseManager.SportType.MIN_AVG_SPEED, it) }
            maxSpeed?.let { put(SportTypeDatabaseManager.SportType.MAX_AVG_SPEED, it) }
            stravaName?.let { put(SportTypeDatabaseManager.SportType.STRAVA_NAME, it) }
            tcxName?.let { put(SportTypeDatabaseManager.SportType.TCX_NAME, it) }
            gcName?.let { put(SportTypeDatabaseManager.SportType.GOLDEN_CHEETAH_NAME, it) }
        }

        sportDb.update(
            SportTypeDatabaseManager.SportType.TABLE,
            cv,
            "${SportTypeDatabaseManager.SportType.C_ID} = ?",
            arrayOf(sportId.toString())
        )
    }

    /**
     * Prunes unreferenced placeholder sport types (is_placeholder == 1) that have zero
     * referencing workouts, equipment links, or clusters (REQ-MIG-044).
     *
     * @param context Application context.
     * @param sportDb Writable database instance for SportType.db.
     * @param summariesDb Readable database instance for WorkoutSummaries.db (optional).
     * @param equipmentDb Readable database instance for SportTypeLinks.db (optional).
     * @param clusterDb Readable database instance for RouteClusters.db (optional).
     * @return Number of pruned placeholder rows deleted.
     */
    fun pruneUnreferencedPlaceholders(
        context: Context,
        sportDb: SQLiteDatabase,
        summariesDb: SQLiteDatabase? = null,
        equipmentDb: SQLiteDatabase? = null,
        clusterDb: SQLiteDatabase? = null
    ): Int {
        val backupLock = try {
            com.atrainingtracker.trainingtracker.migration.BackupManager.acquireLock(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire BackupManager lock", e)
            null
        }
        try {
            val placeholderIds = mutableListOf<Long>()
            try {
                sportDb.query(
                    SportTypeDatabaseManager.SportType.TABLE,
                    arrayOf(SportTypeDatabaseManager.SportType.C_ID),
                    "${SportTypeDatabaseManager.SportType.IS_PLACEHOLDER} = 1",
                    null, null, null, null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        placeholderIds.add(cursor.getLong(0))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query placeholders for pruning", e)
                return 0
            }

            if (placeholderIds.isEmpty()) return 0

            // Gather all currently referenced sport IDs
            val activeSportIds = mutableSetOf<Long>()

            val activeSummariesDb = summariesDb ?: try {
                com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.getInstance(context).database
            } catch (_: Exception) { null }

            try {
                activeSummariesDb?.let { sDb ->
                    sDb.rawQuery("SELECT DISTINCT sportId FROM WorkoutSummaries", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            activeSportIds.add(cursor.getLong(0))
                        }
                    }
                }
            } catch (_: Exception) {
            }

            val activeEquipDb = equipmentDb ?: try {
                com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager.getInstance(context).getDatabase()
            } catch (_: Exception) { null }

            try {
                activeEquipDb?.let { eDb ->
                    eDb.rawQuery("SELECT DISTINCT sport_type_id FROM SportTypeEquipmentLink", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            activeSportIds.add(cursor.getLong(0))
                        }
                    }
                }
            } catch (_: Exception) {
            }

            val activeClusterDb = clusterDb ?: try {
                com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.getInstance(context).getDatabase()
            } catch (_: Exception) { null }

            try {
                activeClusterDb?.let { cDb ->
                    cDb.rawQuery("SELECT DISTINCT probable_sport_id FROM RouteClusters", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            activeSportIds.add(cursor.getLong(0))
                        }
                    }
                }
            } catch (_: Exception) {
            }

            val unreferencedPlaceholders = placeholderIds.filter { it !in activeSportIds }
            if (unreferencedPlaceholders.isEmpty()) return 0

            var deletedCount = 0
            sportDb.beginTransaction()
            try {
                for (id in unreferencedPlaceholders) {
                    val rows = sportDb.delete(
                        SportTypeDatabaseManager.SportType.TABLE,
                        "${SportTypeDatabaseManager.SportType.C_ID} = ? AND ${SportTypeDatabaseManager.SportType.IS_PLACEHOLDER} = 1",
                        arrayOf(id.toString())
                    )
                    deletedCount += rows
                }
                sportDb.setTransactionSuccessful()
            } finally {
                sportDb.endTransaction()
            }

            return deletedCount
        } finally {
            try {
                backupLock?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release BackupManager lock", e)
            }
        }
    }
}
