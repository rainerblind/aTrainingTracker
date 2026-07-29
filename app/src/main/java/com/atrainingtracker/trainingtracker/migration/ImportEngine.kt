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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Handles incremental merging of workouts from an .attbackup archive.
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

    /**
     * Analyzes the backup file to find all unique sport types and equipment used in the workouts.
     */
    fun analyzeBackup(context: Context, backupFile: File): AnalysisResult {
        val sportTypes = mutableMapOf<Long, SportTypeInfo>()
        val equipment = mutableMapOf<Long, EquipmentInfo>()
        
        try {
            val tempDir = File(context.cacheDir, "import_analysis")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            unzipBackup(backupFile, tempDir)

            val srcSummariesDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/WorkoutSummaries.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val srcSportDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/SportType.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val srcEquipFile = File(tempDir, "databases/Equipment.db")
            val srcEquipDb = if (srcEquipFile.exists()) {
                SQLiteDatabase.openDatabase(srcEquipFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
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
                        val bSport = try { BSportType.valueOf(cursor.getString(1)) } catch (e: Exception) { BSportType.UNKNOWN }
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
                            val bSport = try { BSportType.valueOf(cursor.getString(1)) } catch (e: Exception) { BSportType.UNKNOWN }
                            equipment[id] = EquipmentInfo(id, name, bSport)
                        }
                    }
                }
            }

            srcSummariesDb.close()
            srcSportDb.close()
            srcEquipDb?.close()
            tempDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
        }
        return AnalysisResult(sportTypes.values.toList(), equipment.values.toList())
    }

    fun performIncrementalImport(
        context: Context, 
        backupFile: File, 
        sportTypeMapping: Map<Long, Long>,
        equipmentMapping: Map<Long, Long>,
        listener: ProgressListener? = null
    ): Int {
        Log.i(TAG, "Starting incremental import from ${backupFile.absolutePath}")
        var importedCount = 0

        try {
            // 1. Unzip to temporary directory
            listener?.onStatus("Unpacking backup...")
            val tempDir = File(context.cacheDir, "import_temp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            unzipBackup(backupFile, tempDir)

            // 2. Open source databases
            listener?.onStatus("Opening databases...")
            val srcSummariesDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/WorkoutSummaries.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val srcSamplesDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/WorkoutSamples.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val srcLapsDb = SQLiteDatabase.openDatabase(File(tempDir, "databases/Laps.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            // 3. Open target databases
            val targetSummariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
            val targetSamplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database
            val targetLapsDb = LapsDatabaseManager.getInstance(context).database

            // 4. Iterate through source workouts
            srcSummariesDb.query(WorkoutSummaries.TABLE, null, null, null, null, null, null).use { cursor ->
                val total = cursor.count
                var current = 0
                
                while (cursor.moveToNext()) {
                    current++
                    val fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME))
                    val workoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
                    
                    listener?.onProgress(current, total, workoutName)

                    if (!existsInTarget(targetSummariesDb, fileBaseName)) {
                        val oldId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))
                        
                        // Start transaction for this workout
                        targetSummariesDb.beginTransaction()
                        try {
                            // a. Insert Summary with resolution (SCRUM-117)
                            val newId = insertSummaryWithResolution(context, targetSummariesDb, cursor, sportTypeMapping, equipmentMapping)
                            
                            // b. Copy Extrema
                            copyRelatedRows(srcSummariesDb, targetSummariesDb, WorkoutSummaries.TABLE_EXTREMA_VALUES, oldId, newId)
                            
                            // c. Copy Accumulated Sensors
                            copyRelatedRows(srcSummariesDb, targetSummariesDb, WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, oldId, newId)
                            
                            // d. Copy Laps
                            copyLaps(srcLapsDb, targetLapsDb, oldId, newId)
                            
                            // e. Copy Samples Table
                            copySamplesTable(srcSamplesDb, targetSamplesDb, fileBaseName)
                            
                            targetSummariesDb.setTransactionSuccessful()
                            importedCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to import workout $fileBaseName", e)
                        } finally {
                            targetSummariesDb.endTransaction()
                        }
                    }
                }
            }

            srcSummariesDb.close()
            srcSamplesDb.close()
            srcLapsDb.close()
            
            // 5. Update Clusters (SCRUM-117 refinement)
            if (importedCount > 0) {
                listener?.onStatus("Updating workout clusters...")
                WorkoutClusterEngine.getInstance(context).migrateHistory(context)
            }
            
            Log.i(TAG, "Import finished. Imported $importedCount new workouts.")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
        }
        return importedCount
    }

    private fun unzipBackup(backupFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!entry.isDirectory) {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun existsInTarget(db: SQLiteDatabase, fileBaseName: String): Boolean {
        db.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID), "${WorkoutSummaries.FILE_BASE_NAME} = ?", arrayOf(fileBaseName), null, null, null).use {
            return it.count > 0
        }
    }

    private fun insertSummaryWithResolution(
        context: Context,
        db: SQLiteDatabase, 
        cursor: Cursor, 
        sportTypeMapping: Map<Long, Long>,
        equipmentMapping: Map<Long, Long>
    ): Long {
        val values = ContentValues()
        val srcEquipId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
        val srcSportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))

        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            if (name == WorkoutSummaries.C_ID) continue
            
            // RESOLUTION LOGIC (SCRUM-117): Handle cross-device ID mismatches
            if (name == WorkoutSummaries.SPORT_ID) {
                // Use the user-provided mapping
                val localSportId = sportTypeMapping[srcSportId] ?: SportTypeDatabaseManager.getDefaultSportTypeId()
                values.put(name, localSportId)
                continue
            }

            if (name == WorkoutSummaries.EQUIPMENT_ID) {
                // Use the user-provided mapping
                val localEquipId = equipmentMapping[srcEquipId] ?: -1L
                if (localEquipId == -1L) values.putNull(name) else values.put(name, localEquipId)
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
        return db.insert(WorkoutSummaries.TABLE, null, values)
    }

    private fun copyRelatedRows(srcDb: SQLiteDatabase, targetDb: SQLiteDatabase, table: String, oldId: Long, newId: Long) {
        srcDb.query(table, null, "${WorkoutSummaries.WORKOUT_ID} = ?", arrayOf(oldId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues()
                for (i in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(i)
                    if (name == WorkoutSummaries.C_ID) continue
                    if (name == WorkoutSummaries.WORKOUT_ID) {
                        values.put(name, newId)
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
                targetDb.insert(table, null, values)
            }
        }
    }

    private fun copyLaps(srcDb: SQLiteDatabase, targetDb: SQLiteDatabase, oldId: Long, newId: Long) {
        srcDb.query(LapsDatabaseManager.Laps.TABLE, null, "${LapsDatabaseManager.Laps.WORKOUT_ID} = ?", arrayOf(oldId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues()
                for (i in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(i)
                    if (name == LapsDatabaseManager.Laps.C_ID) continue
                    if (name == LapsDatabaseManager.Laps.WORKOUT_ID) {
                        values.put(name, newId)
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
                targetDb.insert(LapsDatabaseManager.Laps.TABLE, null, values)
            }
        }
    }

    private fun copySamplesTable(srcDb: SQLiteDatabase, targetDb: SQLiteDatabase, fileBaseName: String) {
        val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        
        // 1. Get CREATE TABLE statement from source
        var createSql: String? = null
        srcDb.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=$tableName", null).use {
            if (it.moveToFirst()) {
                createSql = it.getString(0)
            }
        }
        
        if (createSql != null) {
            targetDb.execSQL("DROP TABLE IF EXISTS $tableName")
            targetDb.execSQL(createSql)
            
            // 2. Copy data
            srcDb.query(tableName, null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> values.put(name, cursor.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(i))
                            Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                        }
                    }
                    targetDb.insert(tableName, null, values)
                }
            }
        }
    }
}
