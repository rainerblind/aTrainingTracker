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
import android.database.sqlite.SQLiteDatabase
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Manager coordinating workout photo filesystem storage, EXIF metadata extraction,
 * GPS trackpoint interpolation, and persistence (REQ-DAT-038 / ATT-43).
 */
object WorkoutPhotoManager {

    private const val TAG = "WorkoutPhotoManager"

    private val EXIF_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    data class ExifMetadata(
        val takenAtMs: Long?,
        val latitude: Double?,
        val longitude: Double?
    )

    private data class TrackSamplePoint(
        val seconds: Long,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * Resolves and creates the app-private directory for storing a workout's photos.
     */
    fun getWorkoutPhotosDir(context: Context, workoutId: Long): File {
        val dir = File(context.filesDir, "workout_photos/$workoutId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Parses standard EXIF date/time string into epoch milliseconds.
     */
    fun parseExifDateTime(dateTimeStr: String?): Long? {
        if (dateTimeStr.isNullOrBlank()) return null
        return try {
            EXIF_DATE_FORMAT.parse(dateTimeStr)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts EXIF capture timestamp and GPS coordinates from a file.
     */
    fun extractExifMetadata(file: File): ExifMetadata {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val takenAt = parseExifDateTime(dateStr)
            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)
            val lat = if (hasGps) latLong[0].toDouble() else null
            val lng = if (hasGps) latLong[1].toDouble() else null
            ExifMetadata(takenAt, lat, lng)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF from ${file.name}", e)
            ExifMetadata(null, null, null)
        }
    }

    /**
     * Interpolates geographic coordinates (lat, lon) from `WorkoutSamples.db` trackpoints
     * for a given epoch timestamp.
     */
    fun interpolateCoordinatesAtTimestamp(
        samplesDb: SQLiteDatabase,
        baseFileName: String,
        targetTimestampMs: Long
    ): LatLng? {
        val tableName = try {
            WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        } catch (e: Exception) {
            return null
        }
        val targetSeconds = targetTimestampMs / 1000L

        var pointBefore: TrackSamplePoint? = null
        val sqlBefore = "SELECT CAST(strftime('%s', time) AS INTEGER) AS sec, " +
                "${SensorType.LATITUDE.name}, ${SensorType.LONGITUDE.name} FROM $tableName " +
                "WHERE ${SensorType.LATITUDE.name} IS NOT NULL AND ${SensorType.LONGITUDE.name} IS NOT NULL " +
                "AND CAST(strftime('%s', time) AS INTEGER) <= ? ORDER BY time DESC LIMIT 1"
        try {
            samplesDb.rawQuery(sqlBefore, arrayOf(targetSeconds.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    val sec = cursor.getLong(0)
                    val lat = cursor.getDouble(1)
                    val lng = cursor.getDouble(2)
                    pointBefore = TrackSamplePoint(sec, lat, lng)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying point before $targetSeconds in $tableName", e)
        }

        var pointAfter: TrackSamplePoint? = null
        val sqlAfter = "SELECT CAST(strftime('%s', time) AS INTEGER) AS sec, " +
                "${SensorType.LATITUDE.name}, ${SensorType.LONGITUDE.name} FROM $tableName " +
                "WHERE ${SensorType.LATITUDE.name} IS NOT NULL AND ${SensorType.LONGITUDE.name} IS NOT NULL " +
                "AND CAST(strftime('%s', time) AS INTEGER) >= ? ORDER BY time ASC LIMIT 1"
        try {
            samplesDb.rawQuery(sqlAfter, arrayOf(targetSeconds.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    val sec = cursor.getLong(0)
                    val lat = cursor.getDouble(1)
                    val lng = cursor.getDouble(2)
                    pointAfter = TrackSamplePoint(sec, lat, lng)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying point after $targetSeconds in $tableName", e)
        }

        if (pointBefore != null && pointAfter != null) {
            val t1 = pointBefore!!.seconds
            val t2 = pointAfter!!.seconds
            if (t1 == t2 || t2 <= t1) {
                return LatLng(pointBefore!!.latitude, pointBefore!!.longitude)
            }
            val fraction = (targetSeconds - t1).toDouble() / (t2 - t1).toDouble()
            val lat = pointBefore!!.latitude + fraction * (pointAfter!!.latitude - pointBefore!!.latitude)
            val lng = pointBefore!!.longitude + fraction * (pointAfter!!.longitude - pointBefore!!.longitude)
            return LatLng(lat, lng)
        } else if (pointBefore != null) {
            return LatLng(pointBefore!!.latitude, pointBefore!!.longitude)
        } else if (pointAfter != null) {
            return LatLng(pointAfter!!.latitude, pointAfter!!.longitude)
        }
        return null
    }

    /**
     * Linearly interpolates coordinates from an in-memory list of [PathPoint]s.
     */
    fun interpolateCoordinatesFromPoints(
        points: List<PathPoint>,
        targetTimestampSec: Long
    ): LatLng? {
        if (points.isEmpty()) return null
        val before = points.filter { it.timeSec <= targetTimestampSec }.maxByOrNull { it.timeSec }
        val after = points.filter { it.timeSec >= targetTimestampSec }.minByOrNull { it.timeSec }
        if (before != null && after != null) {
            val t1 = before.timeSec
            val t2 = after.timeSec
            if (t1 == t2) return before.latLng
            val fraction = (targetTimestampSec - t1).toDouble() / (t2 - t1).toDouble()
            val lat = before.latLng.latitude + fraction * (after.latLng.latitude - before.latLng.latitude)
            val lng = before.latLng.longitude + fraction * (after.latLng.longitude - before.latLng.longitude)
            return LatLng(lat, lng)
        } else if (before != null) {
            return before.latLng
        } else if (after != null) {
            return after.latLng
        }
        return null
    }

    /**
     * Copies an athlete-selected photo URI into app storage, extracts metadata,
     * interpolates GPS coordinates if needed, and persists the entry into SQLite.
     */
    suspend fun addPhoto(
        context: Context,
        workoutId: Long,
        sourceUri: Uri,
        caption: String? = null
    ): WorkoutPhotoEntry? = withContext(Dispatchers.IO) {
        try {
            val dir = getWorkoutPhotosDir(context, workoutId)
            val destFile = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val exif = extractExifMetadata(destFile)
            var takenAtMs = exif.takenAtMs
            var latitude = exif.latitude
            var longitude = exif.longitude

            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
            val baseFileName = summariesDb.getBaseFileName(workoutId)
            val startTimeStr = summariesDb.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
            val startTimeMs: Long = startTimeStr?.let { str ->
                runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(str)?.time
                }.getOrNull() ?: runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(str)?.time
                }.getOrNull()
            } ?: 0L

            if (takenAtMs == null) {
                takenAtMs = if (startTimeMs > 0) startTimeMs else System.currentTimeMillis()
            }

            if ((latitude == null || longitude == null) && baseFileName != null && startTimeMs > 0) {
                val totalTimeSec = summariesDb.getInt(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s) ?: 0
                val endTimeMs = startTimeMs + totalTimeSec * 1000L
                if (takenAtMs in (startTimeMs - 300_000L)..(endTimeMs + 300_000L)) {
                    try {
                        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database
                        val interpolated = interpolateCoordinatesAtTimestamp(samplesDb, baseFileName, takenAtMs)
                        if (interpolated != null) {
                            latitude = interpolated.latitude
                            longitude = interpolated.longitude
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to interpolate coordinates for ${destFile.name}", e)
                    }
                }
            }

            val id = summariesDb.insertPhoto(workoutId, destFile.absolutePath, takenAtMs, latitude, longitude, caption)
            if (id > 0) {
                WorkoutPhotoEntry(id, workoutId, destFile.absolutePath, takenAtMs, latitude, longitude, caption)
            } else {
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding photo from $sourceUri for workout $workoutId", e)
            null
        }
    }

    fun getPhotosForWorkout(context: Context, workoutId: Long): List<WorkoutPhotoEntry> {
        return WorkoutSummariesDatabaseManager.getInstance(context).getPhotosForWorkout(workoutId)
    }

    fun deletePhoto(context: Context, photoId: Long): Boolean {
        return WorkoutSummariesDatabaseManager.getInstance(context).deletePhoto(photoId)
    }

    fun deletePhotosForWorkout(context: Context, workoutId: Long): Boolean {
        return WorkoutSummariesDatabaseManager.getInstance(context).deletePhotosForWorkout(workoutId)
    }
}
