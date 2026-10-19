/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.util.safeEnumValueOf
import java.io.File

/**
 * Handles incremental merging of workouts from an .attbackup archive.
 * Enforces atomic cross-database transactions via SQLite ATTACH, insertOrThrow,
 * static schema mapping, and cooperative coroutine cancellation (REQ-MIG-043).
 */
object ImportEngine {
    private const val TAG = "ImportEngine"

    interface ProgressListener {
        fun onProgress(current: Int, total: Int, name: String)
        fun onStatus(message: String)
    }

    data class SportTypeInfo(
        val id: Long,
        val name: String,
        val bSportType: BSportType
    )

    data class EquipmentInfo(
        val id: Long,
        val name: String,
        val bSportType: BSportType
    )

    data class AnalysisResult(
        val sportTypes: List<SportTypeInfo>,
        val equipment: List<EquipmentInfo>
    )

    internal val ALLOWED_EXTREMA_COLUMNS = setOf(
        WorkoutSummaries.WORKOUT_ID,
        WorkoutSummaries.EXTREMA_TYPE,
        WorkoutSummaries.SENSOR_TYPE,
        WorkoutSummaries.VALUE,
        WorkoutSummaries.LATITUDE,
        WorkoutSummaries.LONGITUDE,
        WorkoutSummaries.SAMPLES_COLUMN_ID,
        WorkoutSummaries.COUNT
    )

    internal val ALLOWED_ACCUMULATED_COLUMNS = setOf(
        WorkoutSummaries.WORKOUT_ID,
        WorkoutSummaries.SENSOR_TYPE
    )

    internal val ALLOWED_LAPS_COLUMNS = setOf(
        LapsDatabaseManager.Laps.WORKOUT_ID,
        LapsDatabaseManager.Laps.LAP_NR,
        LapsDatabaseManager.Laps.TIME_START,
        LapsDatabaseManager.Laps.TIME_TOTAL_s,
        LapsDatabaseManager.Laps.DISTANCE_TOTAL_m,
        LapsDatabaseManager.Laps.SPEED_AVERAGE_mps,
        LapsDatabaseManager.Laps.NAME,
        LapsDatabaseManager.Laps.DESCRIPTION
    )

    /**
     * Analyzes the backup file to find all unique sport types and equipment used in the workouts.
     */
    fun analyzeBackup(context: Context, backupFile: File): AnalysisResult {
        val sportTypes = mutableMapOf<Long, SportTypeInfo>()
        val equipment = mutableMapOf<Long, EquipmentInfo>()
        val tempDir = File(context.cacheDir, "import_analysis")
        try {
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            unzipBackup(backupFile, tempDir)

            val srcSummariesDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/WorkoutSummaries.db").canonicalPath, null, SQLiteDatabase.OPEN_READONLY)
            val srcSportDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/SportType.db").canonicalPath, null, SQLiteDatabase.OPEN_READONLY)
            val srcEquipFile = File(tempDir, "databases/Equipment.db")
            val srcEquipDb = if (srcEquipFile.exists()) {
                SQLiteDatabase.openDatabase(srcEquipFile.canonicalPath, null, SQLiteDatabase.OPEN_READONLY)
            } else null

            // 1. Get unique IDs from workouts
            val usedSportIds = mutableSetOf<Long>()
            val usedEquipIds = mutableSetOf<Long>()
            
            srcSummariesDb.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.SPORT_ID, WorkoutSummaries.EQUIPMENT_ID), null, null, null, null, null).use { cursor ->
                val sportIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID)
                val equipIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID)
                while (cursor.moveToNext()) {
                    usedSportIds.add(cursor.getLong(sportIdx))
                    if (!cursor.isNull(equipIdx)) {
                        val eId = cursor.getLong(equipIdx)
                        if (eId != -1L) usedEquipIds.add(eId)
                    }
                }
            }

            // 2. Look up Sport Details
            usedSportIds.forEach { id ->
                srcSportDb.query("SportTypes", arrayOf("UIName", "baseSportType"), "_id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        val bSport = safeEnumValueOf(cursor.getString(1), BSportType.UNKNOWN, TAG)
                        sportTypes[id] = SportTypeInfo(id, name, bSport)
                    }
                }
            }

            // 3. Look up Equipment Details
            if (srcEquipDb != null) {
                usedEquipIds.forEach { id ->
                    srcEquipDb.query("Equipment", arrayOf("Name", "SportType"), "_id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)
                            val bSport = safeEnumValueOf(cursor.getString(1), BSportType.UNKNOWN, TAG)
                            equipment[id] = EquipmentInfo(id, name, bSport)
                        }
                    }
                }
            }

            srcSummariesDb.close()
            srcSportDb.close()
            srcEquipDb?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security violation in analyzeBackup", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
        } finally {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        }
        return AnalysisResult(sportTypes.values.toList(), equipment.values.toList())
    }

    /**
     * Performs incremental import from an .attbackup file.
     * Enforces single cross-database transaction via SQLite ATTACH (REQ-MIG-043.1).
     *
     * @throws android.database.sqlite.SQLiteException If SQLite execution or constraint fails during import.
     * @throws InterruptedException If the import operation is cancelled or interrupted.
     * @throws SecurityException If database path verification fails.
     */
    fun performIncrementalImport(
        context: Context, 
        backupFile: File, 
        sportTypeMapping: Map<Long, Long>,
        equipmentMapping: Map<Long, Long>,
        listener: ProgressListener? = null
    ): Int {
        Log.i(TAG, "Starting incremental import from ${backupFile.absolutePath}")
        var importedCount = 0

        val backupLock = BackupManager.acquireLock(context)
            ?: throw SQLiteDatabaseLockedException("Database locked by concurrent operation")

        try {
            DatabaseConnectionManager.checkpointAll(context)

            // 1. Unzip to temporary directory
            listener?.onStatus("Unpacking backup...")
            val tempDir = File(context.cacheDir, "import_temp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            unzipBackup(backupFile, tempDir)

            // 2. Open source databases safely
            listener?.onStatus("Opening databases...")
            val srcSummariesDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/WorkoutSummaries.db").canonicalPath, null, SQLiteDatabase.OPEN_READONLY)
            val srcSamplesDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/WorkoutSamples.db").canonicalPath, null, SQLiteDatabase.OPEN_READONLY)
            val srcLapsDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/Laps.db").canonicalPath, null, SQLiteDatabase.OPEN_READONLY)

            // 3. Open dedicated scoped connection for ATTACH
            val canonicalSummariesPath = File(WorkoutSummariesDatabaseManager.getInstance(context).database.path).canonicalPath
            val canonicalSamplesPath = File(WorkoutSamplesDatabaseManager.getInstance(context).database.path).canonicalPath
            val canonicalLapsPath = File(LapsDatabaseManager.getInstance(context).database.path).canonicalPath

            validateDatabasePath(context, canonicalSummariesPath, WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
            validateDatabasePath(context, canonicalSamplesPath, WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.DB_NAME)
            validateDatabasePath(context, canonicalLapsPath, LapsDatabaseManager.LapsDbHelper.DB_NAME)

            val dedicatedDb = SQLiteDatabase.openDatabase(canonicalSummariesPath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                dedicatedDb.execSQL("ATTACH DATABASE ? AS lapsDb", arrayOf(canonicalLapsPath))
                dedicatedDb.execSQL("ATTACH DATABASE ? AS samplesDb", arrayOf(canonicalSamplesPath))

                // 4. Iterate through source workouts
                srcSummariesDb.query(WorkoutSummaries.TABLE, null, null, null, null, null, null).use { cursor ->
                    val total = cursor.count
                    var current = 0
                    
                    while (cursor.moveToNext()) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Import cancelled")
                        }
                        current++
                        val fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME))
                        val workoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
                        
                        listener?.onProgress(current, total, workoutName)

                        val timeStart = try {
                            cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START))
                        } catch (e: Exception) {
                            null
                        }

                        if (isTrueDuplicate(dedicatedDb, fileBaseName, timeStart)) {
                            Log.i(TAG, "Skipping duplicate workout $fileBaseName (start time: $timeStart)")
                            continue
                        }

                        val oldId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))
                        
                        // Single unified SQLite transaction across main, lapsDb, and samplesDb (REQ-MIG-043.1)
                        dedicatedDb.beginTransaction()
                        var effectiveBaseName = fileBaseName
                        try {
                            if (isBaseNameTakenAttached(dedicatedDb, fileBaseName)) {
                                effectiveBaseName = disambiguateBaseNameAttached(dedicatedDb, fileBaseName, timeStart)
                            }

                            // a. Insert Summary with resolution via insertOrThrow (REQ-MIG-043.4)
                            val newId = insertSummaryWithResolution(
                                context, 
                                dedicatedDb, 
                                cursor, 
                                sportTypeMapping, 
                                equipmentMapping, 
                                effectiveBaseName
                            )
                            require(newId > 0) { "Inserted summary returned non-positive id: $newId" }
                            
                            // b. Copy Extrema with static schema mapping (REQ-MIG-043.5)
                            copyRelatedRows(srcSummariesDb, dedicatedDb, WorkoutSummaries.TABLE_EXTREMA_VALUES, oldId, newId)
                            
                            // c. Copy Accumulated Sensors with static schema mapping (REQ-MIG-043.5)
                            copyRelatedRows(srcSummariesDb, dedicatedDb, WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, oldId, newId)
                            
                            // d. Copy Laps into attached lapsDb (REQ-MIG-043.1)
                            copyLapsAttached(srcLapsDb, dedicatedDb, oldId, newId)
                            
                            // e. Copy Samples Table into attached samplesDb (REQ-MIG-043.1)
                            copySamplesTableAttached(srcSamplesDb, dedicatedDb, fileBaseName, effectiveBaseName)
                            
                            dedicatedDb.setTransactionSuccessful()
                            importedCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to import workout $fileBaseName (effective: $effectiveBaseName)", e)
                            if (e is InterruptedException) throw e
                        } finally {
                            dedicatedDb.endTransaction()
                        }
                    }
                }
            } finally {
                // Hardened nested detach & close (REQ-MIG-043.1)
                try {
                    try {
                        dedicatedDb.execSQL("DETACH DATABASE lapsDb")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to detach lapsDb", e)
                    } finally {
                        try {
                            dedicatedDb.execSQL("DETACH DATABASE samplesDb")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to detach samplesDb", e)
                        }
                    }
                } finally {
                    try {
                        dedicatedDb.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close dedicatedDb", e)
                    }
                }
            }

            try { srcSummariesDb.close() } catch (_: Exception) {}
            try { srcSamplesDb.close() } catch (_: Exception) {}
            try { srcLapsDb.close() } catch (_: Exception) {}
            
            // 5. Update Clusters (SCRUM-117 refinement)
            if (importedCount > 0) {
                listener?.onStatus("Updating workout clusters...")
                WorkoutClusterEngine.getInstance(context).migrateHistory(context)
            }
            
            Log.i(TAG, "Import finished. Imported $importedCount new workouts.")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
        } finally {
            val tempDir = File(context.cacheDir, "import_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            backupLock.close()
        }
        return importedCount
    }

    private fun unzipBackup(backupFile: File, destDir: File) {
        SecureZipExtractor.extractBackupArchive(backupFile, destDir)
    }

    private fun validateDatabasePath(context: Context, canonicalPath: String, expectedDbName: String) {
        val file = File(canonicalPath)
        val expectedFile = try { context.getDatabasePath(expectedDbName) } catch (_: Throwable) { null }
        val expectedParent = expectedFile?.parentFile?.canonicalPath
        if (expectedParent != null && !file.canonicalPath.startsWith(expectedParent)) {
            throw SecurityException("Security violation: Database path '$canonicalPath' is not inside application databases directory '$expectedParent'")
        }
        if (file.name != expectedDbName) {
            throw SecurityException("Security violation: Database file '${file.name}' does not match expected '$expectedDbName'")
        }
    }

    private val importCounter = java.util.concurrent.atomic.AtomicInteger(0)

    private fun disambiguateBaseName(
        summariesDb: SQLiteDatabase,
        samplesDb: SQLiteDatabase,
        originalBaseName: String,
        timeStart: String?
    ): String {
        val epochSeconds = try {
            if (!timeStart.isNullOrBlank()) {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                (format.parse(timeStart)?.time ?: System.currentTimeMillis()) / 1000
            } else {
                System.currentTimeMillis() / 1000
            }
        } catch (e: Exception) {
            System.currentTimeMillis() / 1000
        }

        val truncatedBase = if (originalBaseName.length > 35) originalBaseName.substring(0, 35) else originalBaseName
        var candidate = "${truncatedBase}_$epochSeconds"

        while (isBaseNameTaken(summariesDb, samplesDb, candidate)) {
            val counter = importCounter.incrementAndGet()
            candidate = "${truncatedBase}_${epochSeconds}_$counter"
            if (candidate.length > 60) {
                candidate = "w_${epochSeconds}_$counter"
            }
        }

        return candidate
    }

    private fun isBaseNameTaken(summariesDb: SQLiteDatabase, samplesDb: SQLiteDatabase, baseName: String): Boolean {
        if (existsInTarget(summariesDb, baseName)) return true
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseName)
        samplesDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=$tableName", null).use {
            return it.count > 0
        }
    }

    private fun disambiguateBaseNameAttached(
        db: SQLiteDatabase,
        originalBaseName: String,
        timeStart: String?
    ): String {
        val epochSeconds = try {
            if (!timeStart.isNullOrBlank()) {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                (format.parse(timeStart)?.time ?: System.currentTimeMillis()) / 1000
            } else {
                System.currentTimeMillis() / 1000
            }
        } catch (e: Exception) {
            System.currentTimeMillis() / 1000
        }

        val truncatedBase = if (originalBaseName.length > 35) originalBaseName.substring(0, 35) else originalBaseName
        var candidate = "${truncatedBase}_$epochSeconds"

        while (isBaseNameTakenAttached(db, candidate)) {
            val counter = importCounter.incrementAndGet()
            candidate = "${truncatedBase}_${epochSeconds}_$counter"
            if (candidate.length > 60) {
                candidate = "w_${epochSeconds}_$counter"
            }
        }

        return candidate
    }

    private fun isBaseNameTakenAttached(db: SQLiteDatabase, baseName: String): Boolean {
        if (existsInTarget(db, baseName)) return true
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseName)
        db.rawQuery("SELECT name FROM samplesDb.sqlite_master WHERE type='table' AND name=$tableName", null).use {
            return it.count > 0
        }
    }

    private fun isTrueDuplicate(summariesDb: SQLiteDatabase, fileBaseName: String, timeStart: String?): Boolean {
        if (timeStart.isNullOrBlank()) {
            return existsInTarget(summariesDb, fileBaseName)
        }
        summariesDb.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.C_ID),
            "${WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE AND (${WorkoutSummaries.TIME_START} = ? OR ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)",
            arrayOf(fileBaseName, timeStart, timeStart),
            null, null, null
        ).use {
            return it.count > 0
        }
    }

    private fun existsInTarget(db: SQLiteDatabase, fileBaseName: String): Boolean {
        db.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID), "${WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE", arrayOf(fileBaseName), null, null, null).use {
            return it.count > 0
        }
    }

    private fun insertSummaryWithResolution(
        context: Context,
        db: SQLiteDatabase, 
        cursor: Cursor, 
        sportTypeMapping: Map<Long, Long>,
        equipmentMapping: Map<Long, Long>,
        targetBaseName: String? = null
    ): Long {
        val values = ContentValues()
        val srcEquipId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
        val srcSportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))

        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            if (name == WorkoutSummaries.C_ID) continue
            
            // RESOLUTION LOGIC (SCRUM-117): Handle cross-device ID mismatches
            if (name == WorkoutSummaries.SPORT_ID) {
                val localSportId = sportTypeMapping[srcSportId] ?: SportTypeDatabaseManager.getDefaultSportTypeId()
                values.put(name, localSportId)
                continue
            }

            if (name == WorkoutSummaries.EQUIPMENT_ID) {
                val localEquipId = equipmentMapping[srcEquipId] ?: -1L
                if (localEquipId == -1L) values.putNull(name) else values.put(name, localEquipId)
                continue
            }

            if (name == WorkoutSummaries.FILE_BASE_NAME && targetBaseName != null) {
                values.put(name, targetBaseName)
                continue
            }

            when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> values.put(name, cursor.getString(i))
                Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(i))
                Cursor.FIELD_TYPE_NULL -> values.putNull(name)
            }
        }
        return db.insertOrThrow(WorkoutSummaries.TABLE, null, values)
    }

    internal fun copyRelatedRows(srcDb: SQLiteDatabase, targetDb: SQLiteDatabase, table: String, oldId: Long, newId: Long) {
        require(newId > 0) { "Valid newWorkoutId (> 0) required: $newId" }
        val allowedColumns = if (table == WorkoutSummaries.TABLE_EXTREMA_VALUES) {
            ALLOWED_EXTREMA_COLUMNS
        } else {
            ALLOWED_ACCUMULATED_COLUMNS
        }

        srcDb.query(table, null, "${WorkoutSummaries.WORKOUT_ID} = ?", arrayOf(oldId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues()
                values.put(WorkoutSummaries.WORKOUT_ID, newId)
                for (i in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(i)
                    if (name == WorkoutSummaries.C_ID || name == WorkoutSummaries.WORKOUT_ID) continue
                    if (!allowedColumns.contains(name)) {
                        Log.w(TAG, "Schema evolution notice: Discarding unrecognized source column '$name' in table '$table' to preserve forward integrity.")
                        continue
                    }
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> values.put(name, cursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(i))
                        Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                    }
                }
                targetDb.insertOrThrow(table, null, values)
            }
        }
    }

    internal fun copyLapsAttached(srcDb: SQLiteDatabase, targetDb: SQLiteDatabase, oldId: Long, newId: Long) {
        require(newId > 0) { "Valid newWorkoutId (> 0) required: $newId" }
        srcDb.query(LapsDatabaseManager.Laps.TABLE, null, "${LapsDatabaseManager.Laps.WORKOUT_ID} = ?", arrayOf(oldId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues()
                values.put(LapsDatabaseManager.Laps.WORKOUT_ID, newId)
                for (i in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(i)
                    if (name == LapsDatabaseManager.Laps.C_ID || name == LapsDatabaseManager.Laps.WORKOUT_ID) continue
                    if (!ALLOWED_LAPS_COLUMNS.contains(name)) {
                        Log.w(TAG, "Schema evolution notice: Discarding unrecognized source column '$name' in table '${LapsDatabaseManager.Laps.TABLE}' to preserve forward integrity.")
                        continue
                    }
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> values.put(name, cursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(i))
                        Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                    }
                }
                targetDb.insertOrThrow("lapsDb." + LapsDatabaseManager.Laps.TABLE, null, values)
            }
        }
    }

    private fun copySamplesTable(
        srcDb: SQLiteDatabase,
        targetDb: SQLiteDatabase,
        srcBaseName: String,
        targetBaseName: String
    ) {
        copySamplesTable(srcDb, targetDb, srcBaseName, targetBaseName, "")
    }

    private fun copySamplesTable(
        srcDb: SQLiteDatabase,
        targetDb: SQLiteDatabase,
        srcBaseName: String,
        targetBaseName: String,
        targetDbPrefix: String
    ) {
        val srcTableName = WorkoutSamplesDatabaseManager.getTableName(srcBaseName)
        val targetTableName = if (targetDbPrefix.isNotEmpty()) {
            "$targetDbPrefix." + WorkoutSamplesDatabaseManager.getTableName(targetBaseName)
        } else {
            WorkoutSamplesDatabaseManager.getTableName(targetBaseName)
        }
        
        // 1. Inspect source table schema via PRAGMA table_info
        val columnDefs = mutableListOf<String>()
        val columnNames = mutableListOf<String>()
        val allowedTypes = setOf("INTEGER", "REAL", "TEXT", "BLOB", "NUMERIC")

        srcDb.rawQuery("PRAGMA table_info($srcTableName)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
            val dfltIdx = cursor.getColumnIndexOrThrow("dflt_value")
            val pkIdx = cursor.getColumnIndexOrThrow("pk")

            while (cursor.moveToNext()) {
                val colName = cursor.getString(nameIdx)
                val rawType = cursor.getString(typeIdx)?.trim()?.uppercase() ?: "TEXT"
                val colType = if (allowedTypes.contains(rawType)) rawType else "TEXT"
                val isNotNull = cursor.getInt(notNullIdx) == 1
                val dfltVal = cursor.getString(dfltIdx)
                val isPk = cursor.getInt(pkIdx) > 0

                val escapedColName = colName.replace("\"", "\"\"")
                columnNames.add(colName)
                val defBuilder = java.lang.StringBuilder("\"$escapedColName\" $colType")
                if (isPk) {
                    defBuilder.append(" PRIMARY KEY")
                }
                if (isNotNull) {
                    defBuilder.append(" NOT NULL")
                }
                if (dfltVal != null) {
                    val sanitizedDefault = when {
                        dfltVal.matches(Regex("^-?[0-9]+(\\.[0-9]+)?$")) -> dfltVal
                        dfltVal.equals("CURRENT_TIMESTAMP", ignoreCase = true) -> "CURRENT_TIMESTAMP"
                        dfltVal.equals("NULL", ignoreCase = true) -> "NULL"
                        else -> {
                            val unquoted = if (dfltVal.startsWith("'") && dfltVal.endsWith("'") && dfltVal.length >= 2) {
                                dfltVal.substring(1, dfltVal.length - 1).replace("''", "'")
                            } else {
                                dfltVal
                            }
                            "'" + unquoted.replace("'", "''") + "'"
                        }
                    }
                    defBuilder.append(" DEFAULT $sanitizedDefault")
                }
                columnDefs.add(defBuilder.toString())
            }
        }

        if (columnDefs.isNotEmpty()) {
            val ddl = "CREATE TABLE IF NOT EXISTS $targetTableName (${columnDefs.joinToString(", ")})"
            targetDb.execSQL(ddl)

            // 2. Copy rows
            srcDb.query(srcTableName, null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        if (!columnNames.contains(name)) continue
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> values.put(name, cursor.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(i))
                            Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                        }
                    }
                    targetDb.insertOrThrow(targetTableName, null, values)
                }
            }
        }
    }

    private fun copySamplesTableAttached(
        srcDb: SQLiteDatabase,
        targetDb: SQLiteDatabase,
        srcBaseName: String,
        targetBaseName: String = srcBaseName
    ) {
        copySamplesTable(srcDb, targetDb, srcBaseName, targetBaseName, targetDbPrefix = "samplesDb")
    }
}
