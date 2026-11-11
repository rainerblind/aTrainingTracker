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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data model representing a per-second telemetry sample row from workout samples database (REQ-SET-101).
 */
data class WorkoutTelemetrySample(
    val sampleIdx: Long,
    val timeString: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val heartRate: Int? = null,
    val powerWatts: Double? = null,
    val speedMps: Double? = null,
    val cadenceRpm: Double? = null
)

/**
 * Repository providing asynchronous, encapsulated access to raw workout samples database (REQ-SET-101, ATT-6426).
 */
open class WorkoutSamplesRepository(
    private val context: Context,
    private val dbManagerProvider: () -> WorkoutSamplesDatabaseManager = { WorkoutSamplesDatabaseManager.getInstance(context) }
) {
    companion object {
        private const val TAG = "WorkoutSamplesRepo"

        @Volatile
        private var instance: WorkoutSamplesRepository? = null

        fun getInstance(context: Context): WorkoutSamplesRepository {
            return instance ?: synchronized(this) {
                instance ?: WorkoutSamplesRepository(context.applicationContext).also { instance = it }
            }
        }

        @VisibleForTesting
        fun setInstanceForTesting(repo: WorkoutSamplesRepository?) {
            instance = repo
        }
    }

    /**
     * Reads all telemetry samples for the given workout [fileBaseName] on [Dispatchers.IO].
     */
    open suspend fun getSamples(fileBaseName: String): List<WorkoutTelemetrySample> = withContext(Dispatchers.IO) {
        if (fileBaseName.isBlank()) return@withContext emptyList()
        val dbManager = dbManagerProvider()
        val db = dbManager.database ?: return@withContext emptyList()
        if (!db.isOpen) return@withContext emptyList()

        val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        val samples = mutableListOf<WorkoutTelemetrySample>()

        try {
            db.query(tableName, null, null, null, null, null, null).use { cursor ->
                val timeCol = cursor.getColumnIndex(WorkoutSamplesDbHelper.TIME)
                val latCol = cursor.getColumnIndex(SensorType.LATITUDE.name)
                val lonCol = cursor.getColumnIndex(SensorType.LONGITUDE.name)
                val altCol = cursor.getColumnIndex(SensorType.ALTITUDE.name)
                val hrCol = cursor.getColumnIndex(SensorType.HR.name)
                val pwrCol = cursor.getColumnIndex(SensorType.POWER.name)
                val spdCol = cursor.getColumnIndex(SensorType.SPEED_mps.name)
                val cadCol = cursor.getColumnIndex(SensorType.CADENCE.name)

                var idx = 0L
                while (cursor.moveToNext()) {
                    val timeStr = if (timeCol >= 0 && !cursor.isNull(timeCol)) cursor.getString(timeCol) else null
                    val lat = if (latCol >= 0 && !cursor.isNull(latCol)) cursor.getDouble(latCol) else null
                    val lon = if (lonCol >= 0 && !cursor.isNull(lonCol)) cursor.getDouble(lonCol) else null
                    val alt = if (altCol >= 0 && !cursor.isNull(altCol)) cursor.getDouble(altCol) else null
                    val hr = if (hrCol >= 0 && !cursor.isNull(hrCol)) cursor.getInt(hrCol) else null
                    val pwr = if (pwrCol >= 0 && !cursor.isNull(pwrCol)) cursor.getDouble(pwrCol) else null
                    val spd = if (spdCol >= 0 && !cursor.isNull(spdCol)) cursor.getDouble(spdCol) else null
                    val cad = if (cadCol >= 0 && !cursor.isNull(cadCol)) cursor.getDouble(cadCol) else null

                    samples.add(
                        WorkoutTelemetrySample(
                            sampleIdx = idx++,
                            timeString = timeStr,
                            latitude = lat,
                            longitude = lon,
                            altitude = alt,
                            heartRate = hr,
                            powerWatts = pwr,
                            speedMps = spd,
                            cadenceRpm = cad
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying samples table '$tableName': ${e.message}")
        }

        samples
    }
}
