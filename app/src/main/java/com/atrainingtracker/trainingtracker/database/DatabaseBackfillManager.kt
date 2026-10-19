package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages asynchronous, idempotent backfills of legacy SQLite database columns
 * (polylines, numerical streams, spatial bounds, extrema positions, and cluster altitude extrema)
 * off the UI thread (Dispatchers.IO) pursuant to REQ-MIG-046.2 and REQ-MIG-046.3.
 */
object DatabaseBackfillManager {
    private const val TAG = "DatabaseBackfillManager"

    /**
     * Checks whether any legacy rows remain unpopulated across WorkoutSummaries,
     * WorkoutSummaries_ExtremaValues, or RouteClusters.
     */
    @JvmStatic
    fun hasPendingBackfills(context: Context): Boolean {
        try {
            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
            if (hasPendingSpatialBounds(summariesDb) ||
                hasPendingExtremaPositions(summariesDb) ||
                hasPendingExistingWorkouts(summariesDb)
            ) {
                return true
            }

            val clusterDb = WorkoutClusterDatabaseManager.getInstance(context).getDatabase()
            if (hasPendingClusterAltitudeExtrema(clusterDb)) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pending backfills", e)
        }
        return false
    }

    @JvmStatic
    fun hasPendingSpatialBounds(db: SQLiteDatabase): Boolean {
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT} IS NULL AND " +
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} IS NOT NULL AND " +
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} != ''"
        return queryCount(db, WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, selection) > 0
    }

    @JvmStatic
    fun hasPendingExtremaPositions(db: SQLiteDatabase): Boolean {
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.LATITUDE} IS NULL AND " +
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.EXTREMA_TYPE} != '${ExtremaType.AVG.name}'"
        return queryCount(db, WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE_EXTREMA_VALUES, selection) > 0
    }

    @JvmStatic
    fun hasPendingExistingWorkouts(db: SQLiteDatabase): Boolean {
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} IS NULL OR " +
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} = ''"
        return queryCount(db, WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, selection) > 0
    }

    @JvmStatic
    fun hasPendingClusterAltitudeExtrema(db: SQLiteDatabase): Boolean {
        val selection = "${WorkoutClusterDatabaseManager.WorkoutClusterContract.COLUMN_MIN_ALT_LAT} IS NULL"
        return queryCount(db, WorkoutClusterDatabaseManager.WorkoutClusterContract.TABLE_NAME, selection) > 0
    }

    private fun queryCount(db: SQLiteDatabase, table: String, selection: String): Int {
        try {
            db.query(table, arrayOf("COUNT(*)"), selection, null, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count unpopulated rows in $table", e)
        }
        return 0
    }

    /**
     * Dispatches all pending backfills asynchronously off the UI thread (Dispatchers.IO).
     * Strictly idempotent: only rows with unpopulated/NULL fields are processed.
     */
    suspend fun executePendingBackfills(context: Context) = withContext(Dispatchers.IO) {
        executePendingBackfillsSynchronous(context)
    }

    /**
     * Synchronous implementation invoked by [executePendingBackfills] or unit tests.
     */
    @JvmStatic
    fun executePendingBackfillsSynchronous(context: Context) {
        Log.i(TAG, "Starting database backfill execution...")
        try {
            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database

            // 1. Existing workouts stream & polyline backfill (if any)
            WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.migrateExistingWorkouts(context, summariesDb, false)

            // 2. Spatial bounds backfill
            WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.migrateSpatialBoundsStatic(summariesDb)

            // 3. Extrema positions backfill
            WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.migrateExtremaPositions(context, summariesDb)

            // 4. Route cluster altitude extrema backfill
            val clusterDb = WorkoutClusterDatabaseManager.getInstance(context).getDatabase()
            WorkoutClusterDatabaseManager.backfillAltitudeExtrema(context, clusterDb)

            Log.i(TAG, "Database backfill execution completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during backfill execution", e)
        }
    }
}
