package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries

/**
 * Non-destructive diagnostic utility to inspect and report orphaned database tables and rows (REQ-MIG-043.3).
 *
 * Scans for:
 * 1. Unreferenced sample tables in WorkoutSamples.db that have no matching summary entry in WorkoutSummaries.db.
 * 2. Orphan child rows in extrema_values, accumulated_sensors, and tblLaps with invalid or non-existent workout IDs.
 */
object OrphanDataDiagnosticHelper {
    private const val TAG = "OrphanDiagnosticHelper"

    data class OrphanReport(
        val unreferencedSampleTables: List<String>,
        val orphanExtremaRows: Int,
        val orphanAccumulatedSensorsRows: Int,
        val orphanLapRows: Int
    ) {
        val hasOrphans: Boolean
            get() = unreferencedSampleTables.isNotEmpty() ||
                    orphanExtremaRows > 0 ||
                    orphanAccumulatedSensorsRows > 0 ||
                    orphanLapRows > 0
    }

    /**
     * Executes a non-destructive scan across WorkoutSamples.db, WorkoutSummaries.db, and Laps.db.
     * Logs diagnostic findings and returns an [OrphanReport].
     */
    fun scanForOrphans(context: Context): OrphanReport {
        val summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        val lapsDbManager = LapsDatabaseManager.getInstance(context)

        val existingBaseNames = mutableSetOf<String>()
        val existingWorkoutIds = mutableSetOf<Long>()

        // 1. Snapshot valid summaries base names and IDs
        summariesDbManager.database.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.C_ID, WorkoutSummaries.FILE_BASE_NAME),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID)
            val baseNameIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val baseName = cursor.getString(baseNameIdx)
                if (id > 0) existingWorkoutIds.add(id)
                if (!baseName.isNullOrBlank()) existingBaseNames.add(baseName.lowercase())
            }
        }

        // 2. Scan WorkoutSamples.db tables
        val unreferencedTables = mutableListOf<String>()
        samplesDbManager.database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val tableName = cursor.getString(0) ?: continue
                val cleanName = tableName.removeSurrounding("\"").replace("\"\"", "\"")
                if (cleanName.startsWith("android_", ignoreCase = true) || cleanName.startsWith("sqlite_", ignoreCase = true)) {
                    continue
                }
                if (!existingBaseNames.contains(cleanName.lowercase())) {
                    unreferencedTables.add(tableName)
                }
            }
        }

        // 3. Scan Extrema Values
        var orphanExtrema = 0
        summariesDbManager.database.query(
            WorkoutSummaries.TABLE_EXTREMA_VALUES,
            arrayOf(WorkoutSummaries.WORKOUT_ID),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val wIdIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_ID)
            while (cursor.moveToNext()) {
                val wId = cursor.getLong(wIdIdx)
                if (wId <= 0 || !existingWorkoutIds.contains(wId)) {
                    orphanExtrema++
                }
            }
        }

        // 4. Scan Accumulated Sensors
        var orphanAccumulated = 0
        summariesDbManager.database.query(
            WorkoutSummaries.TABLE_ACCUMULATED_SENSORS,
            arrayOf(WorkoutSummaries.WORKOUT_ID),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val wIdIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_ID)
            while (cursor.moveToNext()) {
                val wId = cursor.getLong(wIdIdx)
                if (wId <= 0 || !existingWorkoutIds.contains(wId)) {
                    orphanAccumulated++
                }
            }
        }

        // 5. Scan Laps
        var orphanLaps = 0
        lapsDbManager.database.query(
            LapsDatabaseManager.Laps.TABLE,
            arrayOf(LapsDatabaseManager.Laps.WORKOUT_ID),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val wIdIdx = cursor.getColumnIndexOrThrow(LapsDatabaseManager.Laps.WORKOUT_ID)
            while (cursor.moveToNext()) {
                val wId = cursor.getLong(wIdIdx)
                if (wId <= 0 || !existingWorkoutIds.contains(wId)) {
                    orphanLaps++
                }
            }
        }

        val report = OrphanReport(
            unreferencedSampleTables = unreferencedTables,
            orphanExtremaRows = orphanExtrema,
            orphanAccumulatedSensorsRows = orphanAccumulated,
            orphanLapRows = orphanLaps
        )

        if (report.hasOrphans) {
            Log.w(
                TAG,
                "Diagnostic Orphan Report: ${report.unreferencedSampleTables.size} unreferenced sample tables, " +
                        "${report.orphanExtremaRows} orphan extrema rows, " +
                        "${report.orphanAccumulatedSensorsRows} orphan accumulated sensor rows, " +
                        "${report.orphanLapRows} orphan lap rows."
            )
        } else {
            Log.i(TAG, "Diagnostic Orphan Report: Clean database state, zero orphans found.")
        }

        return report
    }

    data class OrphanPurgeResult(
        val droppedSampleTables: List<String>,
        val deletedExtremaRows: Int,
        val deletedAccumulatedRows: Int,
        val deletedLapRows: Int
    )

    fun findOrphanRecords(context: Context): OrphanReport = scanForOrphans(context)

    fun purgeOrphanRecords(context: Context): OrphanPurgeResult = purgeOrphans(context)

    /**
     * Purges orphaned child rows and unreferenced sample tables across all three databases.
     */
    fun purgeOrphans(context: Context): OrphanPurgeResult {
        val summariesDbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        val lapsDbManager = LapsDatabaseManager.getInstance(context)

        val report = scanForOrphans(context)
        val droppedTables = mutableListOf<String>()
        for (table in report.unreferencedSampleTables) {
            val safeTable = if (table.startsWith("\"") && table.endsWith("\"")) table else "\"$table\""
            samplesDbManager.database.execSQL("DROP TABLE IF EXISTS $safeTable")
            droppedTables.add(table)
        }

        val existingWorkoutIds = mutableSetOf<Long>()
        summariesDbManager.database.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.C_ID),
            null, null, null, null, null
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (id > 0) existingWorkoutIds.add(id)
            }
        }

        val whereClause = if (existingWorkoutIds.isEmpty()) {
            "1 = 1"
        } else {
            "${WorkoutSummaries.WORKOUT_ID} NOT IN (${existingWorkoutIds.joinToString(", ")})"
        }

        val deletedExtrema = summariesDbManager.database.delete(
            WorkoutSummaries.TABLE_EXTREMA_VALUES,
            whereClause,
            null
        )

        val deletedAccumulated = summariesDbManager.database.delete(
            WorkoutSummaries.TABLE_ACCUMULATED_SENSORS,
            whereClause,
            null
        )

        val lapsWhereClause = if (existingWorkoutIds.isEmpty()) {
            "1 = 1"
        } else {
            "${LapsDatabaseManager.Laps.WORKOUT_ID} NOT IN (${existingWorkoutIds.joinToString(", ")})"
        }

        val deletedLaps = lapsDbManager.database.delete(
            LapsDatabaseManager.Laps.TABLE,
            lapsWhereClause,
            null
        )

        Log.i(TAG, "Purged ${droppedTables.size} sample tables, $deletedExtrema extrema rows, $deletedAccumulated accumulated rows, $deletedLaps lap rows.")

        return OrphanPurgeResult(
            droppedSampleTables = droppedTables,
            deletedExtremaRows = deletedExtrema,
            deletedAccumulatedRows = deletedAccumulated,
            deletedLapRows = deletedLaps
        )
    }
}
