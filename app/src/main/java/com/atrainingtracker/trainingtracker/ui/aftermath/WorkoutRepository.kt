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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.R
import com.atrainingtracker.database.DatabaseConstants
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.climbs.ClimbDetector
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.physiology.WorkoutTrainingLoadSample
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoEntry
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.routes.GeoUtils
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutDeletionNotificationManager
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupData
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMath
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.ClusterWorkoutCandidateDto
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneSample
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * Acts as the centralized data hub and single source of truth for all workout-related history.
 *
 * This repository orchestrates the flow of data between the persistent SQLite databases
 * (Summaries and Samples) and the UI layer. It provides reactive StateFlows for observing
 * the complete workout list and individual sessions, while handling complex background
 * operations such as progressive history loading, spatial path decoding, and atomic
 * database synchronization.
 *
 * Architectural Role: Data access layer with state management.
 * Threading: Most operations are offloaded to [Dispatchers.IO] using a [SupervisorJob].
 */
class WorkoutRepository private constructor(private val context: Context) : CoroutineScope {

    private constructor(application: Application) : this(application as Context)

    private val application get() = context
    private val app get() = (context as? Application) ?: (context.applicationContext as? Application)

    companion object {
        private val TAG = WorkoutRepository::class.java.simpleName
        private val DEBUG = TrainingApplication.getDebug(true)

        @JvmStatic
        val cropMutex get() = com.atrainingtracker.trainingtracker.domain.crop.CropCoordinator.mutex

        const val MIN_FREE_STORAGE_MB: Long = 50L

        @Volatile
        private var INSTANCE: WorkoutRepository? = null

        /**
         * Provides a thread-safe singleton instance of the repository.
         * Uses double-check locking to ensure performance and safety.
         *
         * @param application The application context used for initialization.
         */
        @JvmStatic
        fun getInstance(application: Application): WorkoutRepository {
            return getInstance(application as Context)
        }

        @JvmStatic
        fun getInstance(context: Context): WorkoutRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext ?: context
                    val newInstance = WorkoutRepository(appContext)
                    INSTANCE = newInstance
                    newInstance
                }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTesting(newInstance: WorkoutRepository? = null) {
            INSTANCE = newInstance
        }

        /**
         * Resolves the elapsed seconds offset of an ISO datetime timestamp relative to [initialEpochSec].
         *
         * @param timeStr Timestamp string in "yyyy-MM-dd HH:mm:ss" format.
         * @param initialEpochSec The anchor timestamp in epoch seconds, or null if this is the first sample.
         * @return A pair of (relativeElapsedSec, resolvedAnchorEpochSec). If parsing fails, returns (null, initialEpochSec).
         */
        @JvmStatic
        internal fun parseTimestampOffset(timeStr: String?, initialEpochSec: Long?): Pair<Long?, Long?> {
            if (timeStr.isNullOrBlank()) return Pair(null, initialEpochSec)
            return try {
                val formatted = if (timeStr.length == 19 && timeStr[10] == ' ') {
                    timeStr.replace(' ', 'T')
                } else {
                    timeStr
                }
                val epoch = java.time.LocalDateTime.parse(formatted).toEpochSecond(java.time.ZoneOffset.UTC)
                val anchor = initialEpochSec ?: epoch
                Pair((epoch - anchor).coerceAtLeast(0L), anchor)
            } catch (e: Exception) {
                Pair(null, initialEpochSec)
            }
        }

        /**
         * Quotes a SQL column identifier using standard double quotes, escaping embedded quotes.
         * Ensures source-qualified location columns like "LATITUDE (Google Fused)" are parsed
         * as identifiers rather than expressions or syntax errors by SQLiteQueryBuilder.
         * (ATT-4707 / REQ-DAT-033.4)
         */
        @JvmStatic
        internal fun quoteSqlIdentifier(col: String): String = "\"${col.replace("\"", "\"\"")}\""
    }

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Helper instances, initialized lazily
    @VisibleForTesting
    internal var deletionHelperInstance: WorkoutDeletionHelper? = null
    private val deletionHelper get() = deletionHelperInstance ?: WorkoutDeletionHelper(application)
    private val summariesManager get() = WorkoutSummariesDatabaseManager.getInstance(application)
    private val samplesManager get() = WorkoutSamplesDatabaseManager.getInstance(application)
    private val equipmentDbHelper get() = EquipmentDbHelper.getInstance(application)
    private val sportTypeDatabaseManager get() = SportTypeDatabaseManager.getInstance(application)
    private val exportManager get() = ExportManager(application)
    private val stravaUploadDbHelper get() = StravaUploadDbHelper.getInstance(application)
    private val lapsDatabaseManager get() = LapsDatabaseManager.getInstance(application)

    fun getLaps(workoutId: Long): List<LapData> = lapsDatabaseManager.getLaps(workoutId) ?: emptyList()
    fun getLap(workoutId: Long, lapNr: Long): LapData? = getLaps(workoutId).find { it.lapNr == lapNr }
    fun getExtremaPosition(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType): LatLng? =
        summariesManager.getExtremaPosition(workoutId, sensorType, extremaType)
    fun getExtremaValue(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType): Double? =
        summariesManager.getExtremaValue(workoutId, sensorType, extremaType)
    fun getBaseFileName(workoutId: Long): String? = summariesManager.getBaseFileName(workoutId)
    fun getOldWorkouts(daysToKeep: Int): List<Long> = summariesManager.getOldWorkouts(daysToKeep)
    fun getWorkoutTrainingLoadRecords(): List<WorkoutTrainingLoadSample> = summariesManager.workoutTrainingLoadRecords
    fun getCardioPowerDynamicsFitnessTrendSamples(minR2: Double): List<CardioPowerTrendSample> =
        summariesManager.getCardioPowerDynamicsFitnessTrendSamples(minR2)
    val allWorkoutStartLocations: List<LatLng> get() = summariesManager.allWorkoutStartLocations
    fun deleteWorkoutSummary(workoutId: Long) = summariesManager.deleteWorkout(workoutId)
    fun deleteWorkoutLaps(workoutId: Long) = lapsDatabaseManager.deleteWorkout(workoutId)
    fun deleteWorkoutSamples(fileBaseName: String) = samplesManager.deleteWorkout(fileBaseName)
    fun reassignClusterWorkouts(sourceId: Long, targetId: Long) = summariesManager.reassignClusterWorkouts(sourceId, targetId)
    fun getUploadToStravaStatus(workoutId: Long): Int {
        return try {
            summariesManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.UPLOAD_TO_STRAVA),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString()),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query uploadToStrava for workout $workoutId", e)
            -1
        }
    }

    /**
     * Memory-bounded streaming callback for sample export (REQ-DAT-042.2).
     */
    fun streamSamples(fileBaseName: String, consumer: SampleConsumer) {
        val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        try {
            samplesManager.database.query(tableName, null, null, null, null, null, null).use { cursor ->
                if (cursor == null) return

                fun col(name: String): Int = try {
                    cursor.getColumnIndexOrThrow(name)
                } catch (e: Exception) {
                    cursor.getColumnIndex(name)
                }

                val timeIdx = col(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
                val lapIdx = col(SensorType.LAP_NR.name)
                val latIdx = col(SensorType.LATITUDE.name)
                val lngIdx = col(SensorType.LONGITUDE.name)
                val altIdx = col(SensorType.ALTITUDE.name)
                val distIdx = col(SensorType.DISTANCE_m.name)
                val spdIdx = col(SensorType.SPEED_mps.name)
                val hrIdx = col(SensorType.HR.name)
                val cadIdx = col(SensorType.CADENCE.name)
                val pwrIdx = col(SensorType.POWER.name)
                val tempIdx = col(SensorType.TEMPERATURE.name)

                fun getDoubleOrNull(idx: Int): Double? =
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        try { cursor.getDouble(idx) } catch (e: Exception) { null }
                    } else null

                fun getIntOrNull(idx: Int): Int? =
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        try { cursor.getInt(idx) } catch (e: Exception) { null }
                    } else null

                while (cursor.moveToNext()) {
                    val rawLap = if (lapIdx >= 0) {
                        try { cursor.getInt(lapIdx) } catch (e: Exception) { 1 }
                    } else 1
                    val lapVal = if (rawLap > 0) rawLap else 1

                    val timeVal = if (timeIdx >= 0) {
                        try { cursor.getString(timeIdx) } catch (e: Exception) { null }
                    } else null

                    val record = WorkoutSampleRecord(
                        time = timeVal,
                        lapNr = lapVal,
                        latitude = getDoubleOrNull(latIdx),
                        longitude = getDoubleOrNull(lngIdx),
                        altitude = getDoubleOrNull(altIdx),
                        distanceM = getDoubleOrNull(distIdx),
                        speedMps = getDoubleOrNull(spdIdx),
                        hr = getIntOrNull(hrIdx),
                        cadence = getDoubleOrNull(cadIdx),
                        power = getDoubleOrNull(pwrIdx),
                        temperature = getDoubleOrNull(tempIdx)
                    )
                    consumer.accept(record)
                }
            }
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            Log.w(TAG, "streamSamples failed for $fileBaseName", e)
            throw e
        }
    }

    /**
     * Retrieves workout export header metadata as a domain DTO (REQ-DAT-042.3).
     */
    fun getWorkoutExportHeader(fileBaseName: String): WorkoutExportHeader? {
        try {
            summariesManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                null,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
                arrayOf(fileBaseName),
                null, null, null
            ).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null

                fun col(name: String): Int = try {
                    cursor.getColumnIndexOrThrow(name)
                } catch (e: Exception) {
                    cursor.getColumnIndex(name)
                }

                fun myGet(name: String, defaultVal: String): String {
                    val idx = col(name)
                    return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else defaultVal
                }

                val idIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID)
                val sportIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID)
                val trainerIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.TRAINER)
                val calIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES)
                val ascIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING)
                val descIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING)
                val spdIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPEED_AVERAGE_mps)

                return WorkoutExportHeader(
                    workoutId = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getLong(idIdx) else DatabaseConstants.INVALID_ID,
                    fileBaseName = fileBaseName,
                    startTime = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START, ""),
                    totalTime = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s, "0"),
                    data = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.GC_DATA, "--------"),
                    goal = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.GOAL, ""),
                    method = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.METHOD, ""),
                    totalDistance = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m, "0"),
                    description = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCRIPTION, ""),
                    workoutName = myGet(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, ""),
                    sportTypeId = if (sportIdx >= 0 && !cursor.isNull(sportIdx)) cursor.getLong(sportIdx) else DatabaseConstants.INVALID_ID,
                    indoorTrainerSession = trainerIdx >= 0 && !cursor.isNull(trainerIdx) && cursor.getInt(trainerIdx) > 0,
                    calories = if (calIdx >= 0 && !cursor.isNull(calIdx)) cursor.getInt(calIdx) else null,
                    totalAscent = if (ascIdx >= 0 && !cursor.isNull(ascIdx)) cursor.getInt(ascIdx) else null,
                    totalDescent = if (descIdx >= 0 && !cursor.isNull(descIdx)) cursor.getInt(descIdx) else null,
                    avgSpeedMps = if (spdIdx >= 0 && !cursor.isNull(spdIdx)) cursor.getDouble(spdIdx) else null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWorkoutExportHeader failed for $fileBaseName", e)
            return null
        }
    }

    /**
     * Retrieves workout metadata for Strava update payload (REQ-DAT-042.3).
     */
    fun getWorkoutSummaryUpdateData(fileBaseName: String): WorkoutSummaryUpdateData? {
        try {
            summariesManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                null,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
                arrayOf(fileBaseName),
                null, null, null
            ).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null

                fun col(name: String): Int = try {
                    cursor.getColumnIndexOrThrow(name)
                } catch (e: Exception) {
                    cursor.getColumnIndex(name)
                }

                val sportId = cursor.getLong(col(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID))
                val nameIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
                val descIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCRIPTION)
                val trainerIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.TRAINER)
                val commuteIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.COMMUTE)
                val raceIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.RACE)
                val bSportIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT)
                val eqIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID)

                val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else ""
                val desc = if (descIdx >= 0 && !cursor.isNull(descIdx)) cursor.getString(descIdx) else ""
                val trainer = trainerIdx >= 0 && !cursor.isNull(trainerIdx) && cursor.getInt(trainerIdx) > 0
                val commute = commuteIdx >= 0 && !cursor.isNull(commuteIdx) && cursor.getInt(commuteIdx) > 0
                val race = raceIdx >= 0 && !cursor.isNull(raceIdx) && cursor.getInt(raceIdx) > 0
                val bSportString = if (bSportIdx >= 0 && !cursor.isNull(bSportIdx)) cursor.getString(bSportIdx) else ""
                val bSportType = try {
                    BSportType.valueOf(bSportString)
                } catch (e: Exception) {
                    SportTypesRepository.getInstance(application).getBSportType(sportId)
                }
                val eqId = if (eqIdx >= 0 && !cursor.isNull(eqIdx)) cursor.getLong(eqIdx) else null

                return WorkoutSummaryUpdateData(
                    sportId = sportId,
                    workoutName = name,
                    description = desc,
                    trainer = trainer,
                    commute = commute,
                    race = race,
                    bSportString = bSportString,
                    bSportType = bSportType,
                    equipmentId = eqId
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWorkoutSummaryUpdateData failed for $fileBaseName", e)
            return null
        }
    }

    /**
     * Retrieves duplicate check metadata for Strava duplicate evaluation (REQ-DAT-042.3).
     */
    fun getWorkoutDuplicateCheckMetadata(fileBaseName: String): WorkoutDuplicateMetadata {
        try {
            val projection = arrayOf(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s
            )
            summariesManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                projection,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
                arrayOf(fileBaseName),
                null, null, null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    fun col(name: String): Int = try {
                        cursor.getColumnIndexOrThrow(name)
                    } catch (e: Exception) {
                        cursor.getColumnIndex(name)
                    }

                    val startIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
                    val timeStartStr = if (startIdx >= 0 && !cursor.isNull(startIdx)) cursor.getString(startIdx) else null
                    val startEpochSeconds = timeStartStr?.let {
                        try {
                            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            fmt.parse(it)?.time?.div(1000L)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val bSportIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT)
                    val sportIdIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID)
                    val bSportStr = if (bSportIdx >= 0 && !cursor.isNull(bSportIdx)) cursor.getString(bSportIdx) else null
                    val sportId = if (sportIdIdx >= 0 && !cursor.isNull(sportIdIdx)) cursor.getLong(sportIdIdx) else -1L

                    val bSportType = try {
                        if (bSportStr != null) BSportType.valueOf(bSportStr) else null
                    } catch (e: Exception) {
                        null
                    } ?: SportTypesRepository.getInstance(application).getBSportType(sportId)

                    val activeIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s)
                    val totalIdx = col(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s)
                    val activeTime = if (activeIdx >= 0 && !cursor.isNull(activeIdx)) cursor.getLong(activeIdx) else null
                    val totalTime = if (totalIdx >= 0 && !cursor.isNull(totalIdx)) cursor.getLong(totalIdx) else null
                    val duration = activeTime?.takeIf { it > 0 } ?: totalTime

                    return WorkoutDuplicateMetadata(
                        timeStartStr = timeStartStr,
                        startEpochSeconds = startEpochSeconds,
                        bSportType = bSportType,
                        durationSeconds = duration
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWorkoutDuplicateCheckMetadata failed for $fileBaseName", e)
        }
        return WorkoutDuplicateMetadata(null, null, null, null)
    }

    /**
     * Executes cascade deletion of a workout across all persistence tables (REQ-DAT-042.5).
     */
    fun deleteWorkoutCascade(workoutId: Long): Boolean {
        val fileBaseName = summariesManager.getBaseFileName(workoutId)

        summariesManager.deleteWorkout(workoutId)
        lapsDatabaseManager.deleteWorkout(workoutId)
        try {
            WorkoutRrRepository.getInstance(application).deleteWorkoutBlocking(workoutId)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not delete workout RR intervals for $workoutId: ${t.message}")
        }

        if (!fileBaseName.isNullOrEmpty() && ExportStatusDatabaseManager.isValidFileBaseName(fileBaseName)) {
            try {
                val wm = androidx.work.WorkManager.getInstance(application)
                wm.cancelAllWorkByTag(fileBaseName)
                wm.cancelUniqueWork("export_$fileBaseName")
                for (format in com.atrainingtracker.trainingtracker.exporter.FileFormat.values()) {
                    wm.cancelUniqueWork(ExportManager.getUniqueWorkName(fileBaseName, format))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not cancel WorkManager tasks on deletion for $fileBaseName: ${t.message}")
            }

            samplesManager.deleteWorkout(fileBaseName)
            ExportStatusRepository.getInstance(application).deleteExportWorkout(fileBaseName)
            try {
                stravaUploadDbHelper.deleteWorkout(fileBaseName)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not delete strava record for $fileBaseName: ${t.message}")
            }
        }
        return true
    }

    /**
     * Bulk deletes old workouts with progress tracking (REQ-DAT-042.5).
     */
    fun deleteOldWorkoutsCascade(
        daysToKeep: Int,
        progressCallback: WorkoutDeletionHelper.DeletionProgressCallback? = null
    ): Boolean {
        val oldWorkoutIds = summariesManager.getOldWorkouts(daysToKeep)
        val total = oldWorkoutIds.size
        for (i in 0 until total) {
            val workoutId = oldWorkoutIds[i]
            progressCallback?.onProgress(i + 1, total, workoutId)
            deleteWorkout(workoutId)
        }
        return true
    }

    /**
     * Checks if a workout exists by file base name or start time (REQ-DAT-042.4).
     */
    fun isWorkoutExisting(
        fileBaseName: String,
        timeStart: String? = null,
        bSportType: BSportType? = null
    ): Boolean {
        if (timeStart.isNullOrBlank()) {
            return summariesManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE",
                arrayOf(fileBaseName),
                null, null, null
            ).use { it.count > 0 }
        }

        val selection = if (bSportType != null && bSportType != BSportType.UNKNOWN) {
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE OR " +
                    "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} = ? OR " +
                    "(${WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT} = ? AND ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
        } else {
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE OR " +
                    "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} = ? OR " +
                    "(${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
        }

        val selectionArgs = if (bSportType != null && bSportType != BSportType.UNKNOWN) {
            arrayOf(fileBaseName, timeStart, bSportType.name, timeStart)
        } else {
            arrayOf(fileBaseName, timeStart, timeStart)
        }

        return summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID),
            selection,
            selectionArgs,
            null, null, null
        ).use { it.count > 0 }
    }

    fun getWorkoutIdByBaseFileName(fileBaseName: String): Long {
        return summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID),
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME} = ? COLLATE NOCASE",
            arrayOf(fileBaseName),
            null, null, null
        ).use { if (it.moveToFirst()) it.getLong(0) else -1L }
    }

    fun insertWorkoutSummary(values: ContentValues): Long {
        return summariesManager.database.insert(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null, values)
    }

    fun insertImportedWorkout(
        fileBaseName: String,
        workoutName: String,
        timeStart: String,
        sportId: Long,
        bSportType: BSportType,
        equipmentId: Long,
        workoutNotes: String?,
        goal: String?,
        isCommute: Boolean,
        source: String,
        uploadToStrava: Boolean
    ): Long {
        val summaryValues = ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, workoutName)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START, timeStart)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID, sportId)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT, bSportType.name)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID, if (equipmentId > 0) equipmentId else -1L)
            if (!workoutNotes.isNullOrBlank()) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCRIPTION, workoutNotes.trim())
            }
            if (!goal.isNullOrBlank()) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.GOAL, goal.trim())
            }
            if (isCommute) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.COMMUTE, 1)
            }
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED, 1)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SOURCE, source)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.UPLOAD_TO_STRAVA, if (uploadToStrava) 1 else 0)
        }
        return summariesManager.database.insert(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null, summaryValues)
    }

    fun updateImportedWorkoutStats(
        workoutId: Long,
        totalDistance: Double,
        activeTime: Int,
        totalTime: Int,
        avgSpeed: Double,
        calories: Int?,
        workoutNotes: String?,
        workoutName: String?,
        bSportType: BSportType,
        lapCount: Int,
        gcData: String,
        ascending: Int?,
        descending: Int?,
        polyline: String,
        altitudeStream: String?,
        distanceStream: String?,
        minLat: Double?,
        minLng: Double?,
        maxLat: Double?,
        maxLng: Double?
    ): Int {
        val values = ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m, totalDistance)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s, activeTime)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s, totalTime)
            if (activeTime > 0) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPEED_AVERAGE_mps, avgSpeed)
            }
            if (calories != null && calories > 0) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES, calories)
            }
            if (!workoutNotes.isNullOrBlank()) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCRIPTION, workoutNotes.trim())
            }
            if (!workoutName.isNullOrBlank()) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, workoutName.trim())
            }
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT, bSportType.name)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS, lapCount)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.GC_DATA, gcData)
            if (ascending != null) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING, ascending)
            }
            if (descending != null) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING, descending)
            }
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE, polyline)
            if (!altitudeStream.isNullOrBlank()) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM, altitudeStream)
            }
            if (!distanceStream.isNullOrBlank()) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_STREAM, distanceStream)
            }
            if (minLat != null && minLng != null && maxLat != null && maxLng != null) {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT, minLat)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG, minLng)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT, maxLat)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG, maxLng)
            }
            put("extremumValuesCalculated", 1)
        }
        return updateWorkoutSummary(workoutId, values)
    }

    fun updateWorkoutSummary(workoutId: Long, values: ContentValues): Int {
        return summariesManager.database.update(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            values,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        )
    }

    fun updateWorkoutSummary(fileBaseName: String, values: ContentValues): Int {
        return summariesManager.database.update(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            values,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
            arrayOf(fileBaseName)
        )
    }

    /**
     * Enriches local workout summary with Strava cloud metadata (REQ-EXP-008/009 / REQ-DAT-042.3).
     */
    fun enrichWorkoutFromStrava(
        fileBaseName: String,
        workoutName: String? = null,
        equipmentId: Long? = null,
        sportId: Long? = null
    ) {
        val values = ContentValues()
        if (workoutName != null) {
            values.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, workoutName)
        }
        if (equipmentId != null && equipmentId > 0) {
            values.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID, equipmentId)
        }
        if (sportId != null && sportId > 0) {
            values.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID, sportId)
        }
        if (values.size() > 0) {
            updateWorkoutSummary(fileBaseName, values)
        }
    }

    fun insertWorkoutSamplesBatch(
        baseFileName: String,
        sensorTypes: List<SensorType>,
        bufferedSamples: List<ContentValues>
    ) {
        samplesManager.createNewTable(baseFileName, sensorTypes)
        val targetDb = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        targetDb.beginTransaction()
        try {
            bufferedSamples.forEach { sampleValues ->
                val rowId = targetDb.insert(tableName, null, sampleValues)
                require(rowId >= 0) { "Failed to insert sample into $tableName" }
            }
            targetDb.setTransactionSuccessful()
        } finally {
            targetDb.endTransaction()
        }
    }

    fun saveLap(
        workoutId: Long,
        lapNr: Long,
        startTime: String?,
        duration: Int,
        distance: Double,
        avgSpeed: Double,
        name: String? = null,
        description: String? = null
    ) {
        if (name != null || description != null) {
            lapsDatabaseManager.saveLap(workoutId, lapNr, startTime, duration, distance, avgSpeed, name, description)
        } else {
            lapsDatabaseManager.saveLap(workoutId, lapNr, startTime, duration, distance, avgSpeed)
        }
    }

    fun calculateAndSaveExtrema(
        workoutId: Long,
        baseFileName: String,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null
    ) {
        val sensorsToCalculate = listOf(
            SensorType.HR, SensorType.CADENCE, SensorType.POWER, SensorType.SPEED_mps,
            SensorType.ALTITUDE, SensorType.TEMPERATURE
        )
        val extremaTypes = listOf(ExtremaType.MIN, ExtremaType.MAX, ExtremaType.AVG)

        sensorsToCalculate.forEach { sensor ->
            extremaTypes.forEach { type ->
                val value = samplesManager.calcExtremaValue(summariesManager, baseFileName, type, sensor)
                if (value != null && !value.isNaN()) {
                    val pos = if (sensor == SensorType.ALTITUDE) {
                        when (type) {
                            ExtremaType.MIN -> minAltPos
                            ExtremaType.MAX -> maxAltPos
                            else -> null
                        }
                    } else null
                    summariesManager.updateExtremaValue(workoutId, sensor, type, value, pos)
                }
            }
        }
    }

    fun updateSpatialExtrema(
        workoutId: Long,
        start: LatLng,
        end: LatLng,
        apex: LatLng,
        maxDisp: Double
    ) {
        summariesManager.updateExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.START, start.latitude, start)
        summariesManager.updateExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.START, start.longitude, start)
        summariesManager.updateExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.END, end.latitude, end)
        summariesManager.updateExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.END, end.longitude, end)
        summariesManager.updateExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, maxDisp, apex)
    }

    fun getPowerSpeedHrSamples(baseFileName: String): PowerSpeedHrSamples {
        val powerSamples = mutableListOf<Double?>()
        val speedSamples = mutableListOf<Double?>()
        val hrSamples = mutableListOf<Double?>()
        if (!samplesManager.existsTable(baseFileName)) {
            return PowerSpeedHrSamples(emptyList(), emptyList(), emptyList())
        }
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        try {
            samplesManager.database.query(tableName, null, null, null, null, null, null).use { cursor ->
                if (cursor != null) {
                    val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
                    val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
                    val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
                    while (cursor.moveToNext()) {
                        val p = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getDouble(powerIdx) else null
                        val s = if (speedIdx != -1 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null
                        val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getDouble(hrIdx) else null
                        powerSamples.add(p)
                        speedSamples.add(s)
                        hrSamples.add(hr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getPowerSpeedHrSamples failed for $baseFileName", e)
        }
        return PowerSpeedHrSamples(powerSamples, speedSamples, hrSamples)
    }

    fun executeCompensatoryImportCleanup(baseFileName: String, workoutId: Long) {
        try {
            samplesManager.deleteWorkout(baseFileName)
        } catch (t: Throwable) {
            Log.e(TAG, "Compensatory cleanup failed to delete samples table for $baseFileName", t)
        }
        try {
            if (workoutId > 0) {
                summariesManager.deleteWorkout(workoutId)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Compensatory cleanup failed to delete summary row for $workoutId", t)
        }
    }

    fun getWorkoutClusterId(workoutId: Long): Long {
        return summariesManager.getLong(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID) ?: -1L
    }

    fun setWorkoutClusterId(workoutId: Long, clusterId: Long) {
        val values = ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, clusterId)
        }
        updateWorkoutSummary(workoutId, values)
    }

    fun getWorkoutSportId(workoutId: Long): Long {
        return summariesManager.getLong(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID) ?: -1L
    }

    fun getWorkoutStartTime(workoutId: Long): String? {
        return summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
    }

    fun updateWorkoutSport(workoutId: Long, sportId: Long, bSportType: BSportType) {
        val updateVals = ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID, sportId)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT, bSportType.name)
        }
        updateWorkoutSummary(workoutId, updateVals)
    }

    fun updateWorkoutName(workoutId: Long, name: String) {
        val updateVals = ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, name)
        }
        updateWorkoutSummary(workoutId, updateVals)
    }

    fun getWorkoutBounds(workoutId: Long): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT)?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT] = it }
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG)?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG] = it }
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT)?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT] = it }
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG)?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG] = it }
        return map
    }

    fun forEachWorkoutAsc(block: (ClusterWorkoutCandidateDto) -> Unit) {
        summariesManager.getCursorForAllWorkoutsAsc()?.use { cursor ->
            while (cursor.moveToNext()) {
                val candidate = ClusterWorkoutCandidateDto.fromCursor(cursor)
                block(candidate)
            }
        }
    }

    fun forEachWorkoutAscWithProgress(block: (candidate: ClusterWorkoutCandidateDto, count: Int, total: Int) -> Unit) {
        summariesManager.getCursorForAllWorkoutsAsc()?.use { cursor ->
            val total = cursor.count
            var count = 0
            while (cursor.moveToNext()) {
                count++
                val candidate = ClusterWorkoutCandidateDto.fromCursor(cursor)
                block(candidate, count, total)
            }
        }
    }

    fun getWorkoutName(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)

    fun getWorkoutFileBaseName(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME)

    fun getWorkoutCountForCluster(clusterId: Long): Int =
        summariesManager.getWorkoutCountForCluster(clusterId)

    fun getWorkoutCountsForAllClusters(): Map<Long, Int> =
        summariesManager.workoutCountsForAllClusters

    fun getWorkoutPolyline(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE)

    fun getWorkoutSportString(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT)

    fun getWorkoutAvgSpeed(workoutId: Long): Double? =
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.SPEED_AVERAGE_mps)

    fun getWorkoutDistance(workoutId: Long): Double? =
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m)

    fun applyInferredIdentity(workoutId: Long, identity: EquipmentAndSportTypeDiscoveryManager.InferredIdentity) =
        summariesManager.applyInferredIdentity(workoutId, identity)

    fun getMostFrequentSportIdForCluster(clusterId: Long): Long =
        summariesManager.getMostFrequentSportIdForCluster(clusterId)

    fun forEachWorkoutBoundsForCluster(clusterId: Long, block: (com.atrainingtracker.trainingtracker.database.WorkoutBoundsDto) -> Unit) {
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            arrayOf(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG
            ),
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?",
            arrayOf(clusterId.toString()),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                block(com.atrainingtracker.trainingtracker.database.WorkoutBoundsDto.fromCursor(c))
            }
        }
    }

    fun forEachWorkoutPolylineCandidate(block: (com.atrainingtracker.trainingtracker.database.WorkoutPolylineCandidateDto) -> Unit) {
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} IS NOT NULL AND ${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} != ''"
        val projection = arrayOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE
        )
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            projection, selection, null, null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                block(com.atrainingtracker.trainingtracker.database.WorkoutPolylineCandidateDto.fromCursor(cursor))
            }
        }
    }

    fun forEachWorkoutDistanceCandidateForCluster(clusterId: Long, block: (com.atrainingtracker.trainingtracker.database.WorkoutDistanceCandidateDto) -> Unit) {
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            arrayOf(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
            ),
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?",
            arrayOf(clusterId.toString()),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                block(com.atrainingtracker.trainingtracker.database.WorkoutDistanceCandidateDto.fromCursor(cursor))
            }
        }
    }

    fun forEachRemainingWorkoutForClusterDesc(clusterId: Long, block: (ClusterWorkoutCandidateDto) -> Unit) {
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?",
            arrayOf(clusterId.toString()),
            null, null,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                block(ClusterWorkoutCandidateDto.fromCursor(cursor))
            }
        }
    }

    fun <T> runInTransaction(block: () -> T): T {
        val db = summariesManager.database
        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    private val mapper by lazy {
        WorkoutDataMapper(
            context = application,
            workoutSummariesDatabaseManager = summariesManager,
            sportTypeDatabaseManager = sportTypeDatabaseManager,
            equipmentDbHelper = equipmentDbHelper,
            stravaUploadDbHelper = stravaUploadDbHelper
        )
    }

    // --- StateFlow for Data and LiveData for Progress ---

    /**
     * A reactive StateFlow containing the complete list of workouts currently in memory.
     * This list is populated progressively by [loadAllWorkouts].
     */
    private val _allWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val allWorkouts: StateFlow<List<WorkoutData>> = _allWorkouts.asStateFlow()
    private val activeReloadWorkoutIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val dirtyReloadWorkoutIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /**
     * Provides a reactive stream for a specific workout ID.
     *
     * Implementation: Transforms the global [allWorkouts] flow to isolate the target session.
     * Use this in detail screens to receive updates when a specific workout is edited or enriched.
     *
     * @param id The unique identifier of the workout session.
     * @return A Flow that emits the workout data or null if not found.
     */
    fun getWorkoutById(id: Long): kotlinx.coroutines.flow.Flow<WorkoutData?> {
        return allWorkouts.map { list ->
            list.find { it.id == id }
        }
    }

    /**
     * Emits a one-time event when a workout has been successfully loaded into memory.
     */
    private val _initialWorkoutLoaded = SingleLiveEvent<WorkoutData>()
    val initialWorkoutLoaded: LiveData<WorkoutData> = _initialWorkoutLoaded

    /**
     * Exposes granular progress updates during bulk deletion operations.
     */
    private val _deletionProgress = MutableLiveData<DeletionProgress>(DeletionProgress.Idle)
    val deletionProgress: LiveData<DeletionProgress> = _deletionProgress

    @VisibleForTesting
    var notificationManagerProvider: ((Context) -> WorkoutDeletionNotificationManager)? = null



    val saveFinishedEvent = SingleLiveEvent<Pair<Long, Boolean>>()
    val deleteFinishedEvent = SingleLiveEvent<Pair<Long, Boolean>>()

    /**
     * Checks if there is an unfinished workout recorded in SQLite (REQ-UI-507.5).
     */
    fun hasUnfinishedWorkout(): Boolean = summariesManager.hasUnfinishedWorkout()

    /**
     * Discards or finishes any unfinished workout in SQLite (REQ-UI-507.5).
     */
    fun discardOrFinishUnfinishedWorkout() {
        summariesManager.discardOrFinishUnfinishedWorkout()
    }

    /**
     * Gets aggregate lifetime equipment statistics (REQ-UI-507.5).
     */
    fun getEquipmentStats(equipmentId: Long): WorkoutSummariesDatabaseManager.Stats {
        return summariesManager.getEquipmentStats(equipmentId)
    }

    /**
     * Gets aggregate equipment statistics for a specific time period (REQ-UI-507.5).
     */
    fun getEquipmentStatsForPeriod(equipmentId: Long, startS: Long, endS: Long): WorkoutSummariesDatabaseManager.Stats {
        return summariesManager.getEquipmentStatsForPeriod(equipmentId, startS, endS)
    }


    /**
     * Listens for workout state changes (Updates, Completion) broadcast by the [TrackerService].
     */
    private val workoutUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val workoutId = intent.getLongExtra(TrackerService.WORKOUT_ID, -1L)
            
            when (intent.action) {
                TrackerService.WORKOUT_UPDATED_INTENT, TrackerService.TRACKING_FINISHED_INTENT -> {
                    if (workoutId != -1L) {
                        if (DEBUG) Log.d(TAG, "Workout update broadcast received for workoutId=$workoutId. Reloading.")
                        launch { reloadWorkoutData(workoutId) }
                    }
                }
            }
        }
    }

    /**
     * Listens for changes in file export and cloud upload status.
     */
    private val exportStatusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val fileName = intent.getStringExtra(ExportStatusChangedBroadcaster.EXTRA_FILE_BASE_NAME)
            if (fileName != null) {
                if (DEBUG) Log.d(TAG, "Export status changed for $fileName. Reloading statuses in memory.")
                reloadExportStatusesFor(fileName)
            }
        }
    }

    /**
     * Refreshes the export and upload status markers for a specific workout in memory.
     *
     * Implementation: Performs a fresh query of export logs in the IO context and
     * atomically updates the relevant session in the [allWorkouts] list.
     *
     * @param fileName The unique file base name associated with the session.
     */
    private fun reloadExportStatusesFor(fileName: String) {
        launch(Dispatchers.IO) {
            val exportStatuses: MutableList<ExportStatusGroupData> = mutableListOf()
            for (type in orderedExportTypes) {
                val groupData = exportStatusDataProvider.createGroupData(fileName, type)
                if (groupData.hasContent) {
                    exportStatuses.add(groupData)
                }
            }

            val stravaActivityData = stravaUploadDbHelper.getStravaActivityData(fileName)

            val current = allWorkouts.value.find { it.fileBaseName == fileName } ?: return@launch
            val freshWorkout = summariesManager.getWorkoutCursor(current.id)?.use { cursor ->
                if (cursor.moveToFirst()) mapper.fromCursor(cursor) else null
            }
            Log.i(TAG, "update from reloadExportStatusesFor")
            if (freshWorkout != null) {
                updateWorkoutInList(current.id, freshWorkout.copy(
                    exportStatuses = exportStatuses,
                    stravaActivityData = stravaActivityData
                ))
            } else {
                val freshName = summariesManager.getString(current.id, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
                val updatedName = if (!freshName.isNullOrBlank()) freshName else current.workoutName
                updateWorkoutInList(current.id, current.copy(
                    workoutName = updatedName,
                    exportStatuses = exportStatuses,
                    stravaActivityData = stravaActivityData
                ))
            }
        }
    }

    /**
     * Retrieves the exhaustive list of GPS/Sensor points for a specific workout track.
     *
     * Implementation: Accesses the high-fidelity Samples database for the session.
     * It dynamically selects the coordinate columns based on the requested [TrackType].
     *
     * @param workoutId The unique identifier of the workout.
     * @param trackType The specific track provider to retrieve (BEST, GPS, FUSED, NETWORK).
     * @return A list of [PathPoint] objects containing distance, location, and altitude.
     */
    /**
     * DTO containing consolidated telemetry tracks and zone distributions (ATT-4003 / REQ-DAT-033 / ATT-5487).
     */
    data class WorkoutDetailTelemetry @JvmOverloads constructor(
        val tracks: Map<TrackType, List<PathPoint>>,
        val hrZoneDistribution: ZoneDistributionData?,
        val powerZoneDistribution: ZoneDistributionData?,
        val hrThresholds: HeartRateZoneThresholds? = null,
        val powerThresholds: PowerZoneThresholds? = null,
        val gradeZoneDistribution: ZoneDistributionData? = null
    )

    private fun getExistingColumns(db: android.database.sqlite.SQLiteDatabase, tableName: String): Set<String> {
        val cols = mutableSetOf<String>()
        try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { c ->
                val nameIdx = c.getColumnIndex("name")
                if (nameIdx >= 0) {
                    while (c.moveToNext()) {
                        cols.add(c.getString(nameIdx))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect columns for table $tableName", e)
        }
        return cols
    }

    suspend fun getWorkoutTrackPoints(
        workoutId: Long,
        trackType: TrackType
    ): List<PathPoint> = withContext(Dispatchers.IO) {

        val points = mutableListOf<PathPoint>()

        // 1. Get the base file name (same as TrackOnMapHelper)
        val baseFileName = summariesManager.getBaseFileName(workoutId)
            ?: return@withContext emptyList()

        // 2. Access the Samples database
        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        if (!samplesManager.existsTable(baseFileName)) {
            return@withContext emptyList()
        }

        val existingColumns = getExistingColumns(db, tableName)
        val latName = trackType.latitudeColumn
        val lonName = trackType.longitudeColumn
        if (latName !in existingColumns || lonName !in existingColumns) {
            return@withContext emptyList()
        }

        val candidateCols = listOf(
            latName, lonName,
            SensorType.ALTITUDE.name,
            SensorType.DISTANCE_m.name,
            SensorType.TIME_ACTIVE.name,
            SensorType.TIME_TOTAL.name,
            WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME,
            SensorType.HR.name,
            SensorType.POWER.name,
            SensorType.SPEED_mps.name,
            SensorType.SLOPE.name,
            SensorType.W_PRIME_BAL.name,
            SensorType.HRV_DFA_A1.name,
            SensorType.HRV_RMSSD.name,
            SensorType.HRV_ARTIFACT_RATE.name
        )
        val projection = candidateCols.filter { it in existingColumns }
            .map { quoteSqlIdentifier(it) }
            .toTypedArray()

        db.query(tableName, projection, null, null, null, null, null).use { cursor ->

            val latIdx = cursor.getColumnIndex(latName)
            val lonIdx = cursor.getColumnIndex(lonName)
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)
            val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)
            val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
            val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
            val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
            val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
            val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
            val slopeIdx = cursor.getColumnIndex(SensorType.SLOPE.name)
            val wPrimeIdx = cursor.getColumnIndex(SensorType.W_PRIME_BAL.name)
            val hrvDfaIdx = cursor.getColumnIndex(SensorType.HRV_DFA_A1.name)
            val hrvRmssdIdx = cursor.getColumnIndex(SensorType.HRV_RMSSD.name)
            val hrvArtifactIdx = cursor.getColumnIndex(SensorType.HRV_ARTIFACT_RATE.name)

            var initialEpochSec: Long? = null
            var sampleIndex = 0L

            // 3. Replicate the Roughness stepSize logic and extract full-fidelity telemetry
            while (cursor.moveToNext()) {
                val timeSec = when {
                    timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                    timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                    timeIdx != -1 && !cursor.isNull(timeIdx) -> {
                        val (offset, anchor) = parseTimestampOffset(cursor.getString(timeIdx), initialEpochSec)
                        initialEpochSec = anchor
                        offset ?: sampleIndex
                    }
                    else -> sampleIndex
                }

                if (latIdx != -1 && lonIdx != -1 && !cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                    val dist = if (distIdx != -1 && !cursor.isNull(distIdx)) cursor.getDouble(distIdx) else 0.0
                    val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null
                    val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getInt(hrIdx) else null
                    val power = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getInt(powerIdx) else null
                    val speed = if (speedIdx != -1 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null
                    val slope = if (slopeIdx != -1 && !cursor.isNull(slopeIdx)) cursor.getDouble(slopeIdx) else null
                    val wPrimeBal = if (wPrimeIdx != -1 && !cursor.isNull(wPrimeIdx)) cursor.getDouble(wPrimeIdx) else null
                    val hrvDfaA1 = if (hrvDfaIdx != -1 && !cursor.isNull(hrvDfaIdx)) cursor.getDouble(hrvDfaIdx) else null
                    val hrvRmssd = if (hrvRmssdIdx != -1 && !cursor.isNull(hrvRmssdIdx)) cursor.getDouble(hrvRmssdIdx) else null
                    val hrvArtifactRate = if (hrvArtifactIdx != -1 && !cursor.isNull(hrvArtifactIdx)) cursor.getDouble(hrvArtifactIdx) else null

                    points.add(
                        PathPoint(
                            distance = dist,
                            latLng = LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)),
                            altitude = alt,
                            timeSec = timeSec,
                            hr = hr,
                            power = power,
                            speedMps = speed,
                            slope = slope,
                            wPrimeBalKj = wPrimeBal,
                            hrvDfaA1 = hrvDfaA1,
                            hrvRmssd = hrvRmssd,
                            hrvArtifactRate = hrvArtifactRate
                        )
                    )
                }
                sampleIndex++
            }
        }
        enrichPointsWithWPrimeBal(points)
    }

    /**
     * Extracts continuous sensor telemetry points (HR, Power, Speed) across the Time domain
     * for trackless workouts where GPS coordinate fixes are absent (REQ-UI-235 / ATT-2006).
     *
     * @param workoutId The database ID of the workout summary.
     * @return Decimated list of [PathPoint] containing continuous time-domain telemetry.
     */
    suspend fun getWorkoutTelemetryPoints(
        workoutId: Long
    ): List<PathPoint> = withContext(Dispatchers.IO) {
        val points = mutableListOf<PathPoint>()

        val baseFileName = summariesManager.getBaseFileName(workoutId)
            ?: return@withContext emptyList()

        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        if (!samplesManager.existsTable(baseFileName)) {
            return@withContext emptyList()
        }

        db.query(tableName, null, null, null, null, null, null).use { cursor ->
            val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)
            val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
            val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
            val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
            val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
            val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)
            val wPrimeIdx = cursor.getColumnIndex(SensorType.W_PRIME_BAL.name)
            val hrvDfaIdx = cursor.getColumnIndex(SensorType.HRV_DFA_A1.name)
            val hrvRmssdIdx = cursor.getColumnIndex(SensorType.HRV_RMSSD.name)
            val hrvArtifactIdx = cursor.getColumnIndex(SensorType.HRV_ARTIFACT_RATE.name)

            var initialEpochSec: Long? = null
            var sampleIndex = 0L

            while (cursor.moveToNext()) {
                val timeSec = when {
                    timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                    timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                    timeIdx != -1 && !cursor.isNull(timeIdx) -> {
                        val (offset, anchor) = parseTimestampOffset(cursor.getString(timeIdx), initialEpochSec)
                        initialEpochSec = anchor
                        offset ?: sampleIndex
                    }
                    else -> sampleIndex
                }

                val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getInt(hrIdx) else null
                val power = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getInt(powerIdx) else null
                val speed = if (speedIdx != -1 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null
                val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null
                val dist = if (distIdx != -1 && !cursor.isNull(distIdx)) cursor.getDouble(distIdx) else 0.0
                val wPrimeBal = if (wPrimeIdx != -1 && !cursor.isNull(wPrimeIdx)) cursor.getDouble(wPrimeIdx) else null
                val hrvDfaA1 = if (hrvDfaIdx != -1 && !cursor.isNull(hrvDfaIdx)) cursor.getDouble(hrvDfaIdx) else null
                val hrvRmssd = if (hrvRmssdIdx != -1 && !cursor.isNull(hrvRmssdIdx)) cursor.getDouble(hrvRmssdIdx) else null
                val hrvArtifactRate = if (hrvArtifactIdx != -1 && !cursor.isNull(hrvArtifactIdx)) cursor.getDouble(hrvArtifactIdx) else null

                if (hr != null || power != null || speed != null || alt != null || hrvDfaA1 != null || hrvRmssd != null) {
                    points.add(
                        PathPoint(
                            distance = dist,
                            latLng = LatLng(0.0, 0.0),
                            altitude = alt,
                            timeSec = timeSec,
                            hr = hr,
                            power = power,
                            speedMps = speed,
                            slope = null,
                            wPrimeBalKj = wPrimeBal,
                            hrvDfaA1 = hrvDfaA1,
                            hrvRmssd = hrvRmssd,
                            hrvArtifactRate = hrvArtifactRate
                        )
                    )
                }
                sampleIndex++
            }
        }

        val enrichedPoints = enrichPointsWithWPrimeBal(points)
        if (enrichedPoints.size > 800) {
            val step = kotlin.math.ceil(enrichedPoints.size / 800.0).toInt()
            enrichedPoints.filterIndexed { index, _ ->
                index == 0 || index == enrichedPoints.lastIndex || index % step == 0
            }
        } else {
            enrichedPoints
        }
    }

    /**
     * Enriches [points] with calculated Skiba $W'_{\text{bal}}$ (`kJ`) values when power telemetry
     * is present and `wPrimeBalKj` was not directly stored in the raw samples table (`REQ-UI-399.3`).
     */
    private fun enrichPointsWithWPrimeBal(points: List<PathPoint>): List<PathPoint> {
        if (points.isEmpty() || points.any { it.wPrimeBalKj != null }) return points
        if (points.none { (it.power ?: 0) > 0 }) return points
        val effectiveCp = SettingsDataStoreJavaHelper.getEffectiveCpWatts(application) ?: return points
        val effectiveWPrimeKj = SettingsDataStoreJavaHelper.getEffectiveWPrimeKj(application).toDouble()
        if (effectiveCp <= 0 || effectiveWPrimeKj <= 0.0) return points

        val summary = com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeWPrimeBalanceSeries(
            powerSamplesWatts = points.map { it.power?.toDouble() },
            cpWatts = effectiveCp.toDouble(),
            wPrimeKj = effectiveWPrimeKj
        )
        if (summary.seriesKj.size != points.size) return points
        return points.mapIndexed { idx, pt ->
            pt.copy(wPrimeBalKj = summary.seriesKj[idx]?.toDouble())
        }
    }

    /**
     * Resolves the workout start timestamp in milliseconds from WorkoutSummaries (REQ-SET-082.3).
     */
    internal fun getWorkoutStartTimeMs(workoutId: Long): Long {
        val dateStr = summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
        if (!dateStr.isNullOrBlank()) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                return format.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    /**
     * Calculates the 5-zone heart rate distribution for a workout from stored samples,
     * resolved against the zone thresholds active at workout time (REQ-SET-082.3).
     *
     * @param workoutId The database ID of the workout summary.
     * @param bSportType The sport type of the workout (used to determine bike vs run HR zones).
     * @return [ZoneDistributionData] or null if no heart rate telemetry is available.
     */
    suspend fun getHeartRateZoneDistribution(
        workoutId: Long,
        bSportType: BSportType?
    ): ZoneDistributionData? = withContext(Dispatchers.IO) {
        val baseFileName = summariesManager.getBaseFileName(workoutId) ?: return@withContext null
        if (!samplesManager.existsTable(baseFileName)) return@withContext null

        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        val existingColumns = getExistingColumns(db, tableName)
        if (SensorType.HR.name !in existingColumns) return@withContext null

        val zoneType = if (bSportType == BSportType.BIKE) SettingsDataStore.ZoneType.HR_BIKE else SettingsDataStore.ZoneType.HR_RUN

        val workoutStartTimeMs = getWorkoutStartTimeMs(workoutId)
        val bounds = SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(application, zoneType, workoutStartTimeMs)
        val z1 = bounds.getOrElse(0) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1) }
        val z2 = bounds.getOrElse(1) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2) }
        val z3 = bounds.getOrElse(2) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3) }
        val z4 = bounds.getOrElse(3) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4) }
        val thresholds = HeartRateZoneThresholds(z1, z2, z3, z4)

        val candidateCols = listOf(
            SensorType.HR.name,
            SensorType.TIME_ACTIVE.name,
            SensorType.TIME_TOTAL.name,
            WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME
        )
        val projection = candidateCols.filter { it in existingColumns }
            .map { quoteSqlIdentifier(it) }
            .toTypedArray()
        val samples = mutableListOf<ZoneSample>()

        db.query(tableName, projection, null, null, null, null, null).use { cursor ->
            val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
            if (hrIdx == -1) return@withContext null

            val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
            val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)

            var initialEpochSec: Long? = null
            var sampleIndex = 0L

            while (cursor.moveToNext()) {
                val timeSec = when {
                    timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                    timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                    timeIdx != -1 && !cursor.isNull(timeIdx) -> {
                        val (offset, anchor) = parseTimestampOffset(cursor.getString(timeIdx), initialEpochSec)
                        initialEpochSec = anchor
                        offset ?: sampleIndex
                    }
                    else -> sampleIndex
                }

                if (!cursor.isNull(hrIdx)) {
                    val hr = cursor.getInt(hrIdx)
                    if (hr > 0) {
                        samples.add(ZoneSample(timeActiveSec = timeSec, value = hr))
                    }
                }
                sampleIndex++
            }
        }

        ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)
    }

    /**
     * Extracts power samples from the workout's samples table and calculates
     * a 5-zone time distribution based on athlete cycling power thresholds.
     *
     * @param workoutId The session identifier.
     * @return [ZoneDistributionData] or null if no power telemetry is available.
     */
    suspend fun getPowerZoneDistribution(
        workoutId: Long
    ): ZoneDistributionData? = withContext(Dispatchers.IO) {
        val baseFileName = summariesManager.getBaseFileName(workoutId) ?: return@withContext null
        if (!samplesManager.existsTable(baseFileName)) return@withContext null

        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        val existingColumns = getExistingColumns(db, tableName)
        if (SensorType.POWER.name !in existingColumns) return@withContext null

        val zoneType = SettingsDataStore.ZoneType.PWR_BIKE

        val workoutStartTimeMs = getWorkoutStartTimeMs(workoutId)
        val bounds = SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(application, zoneType, workoutStartTimeMs)
        val z1 = bounds.getOrElse(0) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1) }
        val z2 = bounds.getOrElse(1) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2) }
        val z3 = bounds.getOrElse(2) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3) }
        val z4 = bounds.getOrElse(3) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4) }
        val z5 = bounds.getOrElse(4) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 5) }
        val z6 = bounds.getOrElse(5) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 6) }
        val thresholds = PowerZoneThresholds(z1, z2, z3, z4, z5, z6)

        val candidateCols = listOf(
            SensorType.POWER.name,
            SensorType.TIME_ACTIVE.name,
            SensorType.TIME_TOTAL.name,
            WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME
        )
        val projection = candidateCols.filter { it in existingColumns }
            .map { quoteSqlIdentifier(it) }
            .toTypedArray()
        val samples = mutableListOf<ZoneSample>()

        db.query(tableName, projection, null, null, null, null, null).use { cursor ->
            val pwrIdx = cursor.getColumnIndex(SensorType.POWER.name)
            if (pwrIdx == -1) return@withContext null

            val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
            val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)

            var initialEpochSec: Long? = null
            var sampleIndex = 0L

            while (cursor.moveToNext()) {
                val timeSec = when {
                    timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                    timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                    timeIdx != -1 && !cursor.isNull(timeIdx) -> {
                        val (offset, anchor) = parseTimestampOffset(cursor.getString(timeIdx), initialEpochSec)
                        initialEpochSec = anchor
                        offset ?: sampleIndex
                    }
                    else -> sampleIndex
                }

                if (!cursor.isNull(pwrIdx)) {
                    val pwr = cursor.getInt(pwrIdx)
                    if (pwr >= 0) {
                        samples.add(ZoneSample(timeActiveSec = timeSec, value = pwr))
                    }
                }
                sampleIndex++
            }
        }

        ZoneDistributionCalculator.calculatePowerDistribution(samples, thresholds)
    }

    /**
     * Calculates 7-zone slope / grade distribution data for a workout (REQ-UI-453 / ATT-5487).
     */
    suspend fun getGradeZoneDistribution(
        workoutId: Long
    ): ZoneDistributionData? = withContext(Dispatchers.IO) {
        val trackPoints = getWorkoutTrackPoints(workoutId, TrackType.BEST)
        ZoneDistributionCalculator.calculateGradeDistribution(trackPoints)
    }

    /**
     * ATT-4003 / REQ-DAT-033: Consolidated single-pass extraction of tracks and zone telemetry.
     * Replaces multiple unprojected table scans across track types and zone distributions
     * with a single projected query over the workout's samples table.
     */
    suspend fun loadWorkoutDetailTelemetry(
        workoutId: Long,
        bSportType: BSportType?
    ): WorkoutDetailTelemetry = withContext(Dispatchers.IO) {
        val baseFileName = summariesManager.getBaseFileName(workoutId)
            ?: return@withContext WorkoutDetailTelemetry(emptyMap(), null, null)

        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        if (!samplesManager.existsTable(baseFileName)) {
            return@withContext WorkoutDetailTelemetry(emptyMap(), null, null)
        }

        val existingColumns = getExistingColumns(db, tableName)

        val availableTrackTypes = TrackType.entries.filter { type ->
            type.latitudeColumn in existingColumns && type.longitudeColumn in existingColumns
        }

        val hasHr = SensorType.HR.name in existingColumns
        val hasPower = SensorType.POWER.name in existingColumns

        if (availableTrackTypes.isEmpty() && !hasHr && !hasPower) {
            return@withContext WorkoutDetailTelemetry(emptyMap(), null, null)
        }

        val workoutStartTimeMs = getWorkoutStartTimeMs(workoutId)

        val hrThresholds = if (hasHr) {
            val zoneType = if (bSportType == BSportType.BIKE) SettingsDataStore.ZoneType.HR_BIKE else SettingsDataStore.ZoneType.HR_RUN
            val bounds = SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(application, zoneType, workoutStartTimeMs)
            val z1 = bounds.getOrElse(0) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1) }
            val z2 = bounds.getOrElse(1) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2) }
            val z3 = bounds.getOrElse(2) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3) }
            val z4 = bounds.getOrElse(3) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4) }
            HeartRateZoneThresholds(z1, z2, z3, z4)
        } else null

        val powerThresholds = if (hasPower) {
            val zoneType = SettingsDataStore.ZoneType.PWR_BIKE
            val bounds = SettingsDataStoreJavaHelper.getEffectiveZoneBoundaries(application, zoneType, workoutStartTimeMs)
            val z1 = bounds.getOrElse(0) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1) }
            val z2 = bounds.getOrElse(1) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2) }
            val z3 = bounds.getOrElse(2) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3) }
            val z4 = bounds.getOrElse(3) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4) }
            val z5 = bounds.getOrElse(4) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 5) }
            val z6 = bounds.getOrElse(5) { SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 6) }
            PowerZoneThresholds(z1, z2, z3, z4, z5, z6)
        } else null

        val projectionSet = mutableSetOf<String>()
        for (type in availableTrackTypes) {
            projectionSet.add(type.latitudeColumn)
            projectionSet.add(type.longitudeColumn)
        }
        val commonCols = listOf(
            SensorType.ALTITUDE.name,
            SensorType.DISTANCE_m.name,
            SensorType.TIME_ACTIVE.name,
            SensorType.TIME_TOTAL.name,
            WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME,
            SensorType.HR.name,
            SensorType.POWER.name,
            SensorType.SPEED_mps.name,
            SensorType.SLOPE.name,
            SensorType.W_PRIME_BAL.name,
            SensorType.HRV_DFA_A1.name,
            SensorType.HRV_RMSSD.name,
            SensorType.HRV_ARTIFACT_RATE.name
        )
        for (col in commonCols) {
            if (col in existingColumns) projectionSet.add(col)
        }

        val projection = projectionSet.map { quoteSqlIdentifier(it) }.toTypedArray()
        val trackPointsMap = availableTrackTypes.associateWith { ArrayList<PathPoint>() }
        val hrSamples = if (hasHr) mutableListOf<ZoneSample>() else null
        val powerSamples = if (hasPower) mutableListOf<ZoneSample>() else null

        db.query(tableName, projection, null, null, null, null, null).use { cursor ->
            val colIndices = availableTrackTypes.associateWith { type ->
                Pair(cursor.getColumnIndex(type.latitudeColumn), cursor.getColumnIndex(type.longitudeColumn))
            }
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)
            val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)
            val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
            val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
            val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
            val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
            val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
            val slopeIdx = cursor.getColumnIndex(SensorType.SLOPE.name)
            val wPrimeIdx = cursor.getColumnIndex(SensorType.W_PRIME_BAL.name)
            val hrvDfaIdx = cursor.getColumnIndex(SensorType.HRV_DFA_A1.name)
            val hrvRmssdIdx = cursor.getColumnIndex(SensorType.HRV_RMSSD.name)
            val hrvArtifactIdx = cursor.getColumnIndex(SensorType.HRV_ARTIFACT_RATE.name)

            var initialEpochSec: Long? = null
            var sampleIndex = 0L

            while (cursor.moveToNext()) {
                val timeSec = when {
                    timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                    timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                    timeIdx != -1 && !cursor.isNull(timeIdx) -> {
                        val (offset, anchor) = parseTimestampOffset(cursor.getString(timeIdx), initialEpochSec)
                        initialEpochSec = anchor
                        offset ?: sampleIndex
                    }
                    else -> sampleIndex
                }

                val dist = if (distIdx != -1 && !cursor.isNull(distIdx)) cursor.getDouble(distIdx) else 0.0
                val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null
                val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getInt(hrIdx) else null
                val power = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getInt(powerIdx) else null
                val speed = if (speedIdx != -1 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null
                val slope = if (slopeIdx != -1 && !cursor.isNull(slopeIdx)) cursor.getDouble(slopeIdx) else null
                val wPrimeBal = if (wPrimeIdx != -1 && !cursor.isNull(wPrimeIdx)) cursor.getDouble(wPrimeIdx) else null
                val hrvDfaA1 = if (hrvDfaIdx != -1 && !cursor.isNull(hrvDfaIdx)) cursor.getDouble(hrvDfaIdx) else null
                val hrvRmssd = if (hrvRmssdIdx != -1 && !cursor.isNull(hrvRmssdIdx)) cursor.getDouble(hrvRmssdIdx) else null
                val hrvArtifactRate = if (hrvArtifactIdx != -1 && !cursor.isNull(hrvArtifactIdx)) cursor.getDouble(hrvArtifactIdx) else null

                for (type in availableTrackTypes) {
                    val (latIdx, lonIdx) = colIndices[type] ?: continue
                    if (latIdx != -1 && lonIdx != -1 && !cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                        trackPointsMap[type]?.add(
                            PathPoint(
                                distance = dist,
                                latLng = LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)),
                                altitude = alt,
                                timeSec = timeSec,
                                hr = hr,
                                power = power,
                                speedMps = speed,
                                slope = slope,
                                wPrimeBalKj = wPrimeBal,
                                hrvDfaA1 = hrvDfaA1,
                                hrvRmssd = hrvRmssd,
                                hrvArtifactRate = hrvArtifactRate
                            )
                        )
                    }
                }

                if (hr != null && hr > 0) {
                    hrSamples?.add(ZoneSample(timeActiveSec = timeSec, value = hr))
                }
                if (power != null && power >= 0) {
                    powerSamples?.add(ZoneSample(timeActiveSec = timeSec, value = power))
                }
                sampleIndex++
            }
        }

        val enrichedTracks = availableTrackTypes.associateWith { type ->
            enrichPointsWithWPrimeBal(trackPointsMap[type] ?: emptyList())
        }

        val hrDistribution = if (hrSamples != null && hrThresholds != null) {
            ZoneDistributionCalculator.calculateHeartRateDistribution(hrSamples, hrThresholds)
        } else null

        val powerDistribution = if (powerSamples != null && powerThresholds != null) {
            ZoneDistributionCalculator.calculatePowerDistribution(powerSamples, powerThresholds)
        } else null

        val elevationPoints = enrichedTracks[TrackType.BEST] ?: enrichedTracks.values.firstOrNull() ?: emptyList()
        val gradeDistribution = ZoneDistributionCalculator.calculateGradeDistribution(elevationPoints)

        WorkoutDetailTelemetry(
            tracks = enrichedTracks,
            hrZoneDistribution = hrDistribution,
            powerZoneDistribution = powerDistribution,
            hrThresholds = hrThresholds,
            powerThresholds = powerThresholds,
            gradeZoneDistribution = gradeDistribution
        )
    }

    private val extremaSensorTypes = arrayOf(
        SensorType.ALTITUDE, SensorType.TEMPERATURE,
        SensorType.HR, SensorType.POWER, SensorType.LINE_DISTANCE_m, SensorType.SPEED_mps
    )

    /**
     * Generates a list of geographical markers for a workout, including Start, Stop, and extrema.
     *
     * Implementation: Combines primary spatial data from [WorkoutData] with detailed sensor
     * peaks retrieved from the Extrema database table.
     *
     * @param workoutData The session to generate markers for.
     * @return A list of [LocationMarker] objects ready for map display.
     */
    suspend fun getWorkoutMarkers(workoutData: WorkoutData): List<LocationMarker> = withContext(Dispatchers.IO) {
        val workoutId = workoutData.id
        val markerList = mutableListOf<LocationMarker>()

        // 1. Primary spatial markers (Fast, from WorkoutData)
        workoutData.startLatLng?.takeIf { GeoUtils.isValidGpsCoordinate(it.latitude, it.longitude) }?.let {
            val startTitle = if (!workoutData.startLocationName.isNullOrBlank()) {
                "${application.getString(R.string.Start)}: ${workoutData.startLocationName}"
            } else {
                application.getString(R.string.Start)
            }
            markerList.add(LocationMarker(it, R.drawable.control_start, startTitle))
        }
        workoutData.endLatLng?.takeIf { GeoUtils.isValidGpsCoordinate(it.latitude, it.longitude) }?.let {
            val stopTitle = if (!workoutData.endLocationName.isNullOrBlank()) {
                "${application.getString(R.string.Stop)}: ${workoutData.endLocationName}"
            } else {
                application.getString(R.string.Stop)
            }
            markerList.add(LocationMarker(it, R.drawable.control_stop, stopTitle))
        }
        workoutData.maxDisplacementLatLng?.takeIf { GeoUtils.isValidGpsCoordinate(it.latitude, it.longitude) }?.let {
            markerList.add(LocationMarker(it, R.drawable.ic_distance, application.getString(R.string.max_line_distance)))
        }


        // 2. Sensor Max/Min Markers (from Extremum table)
        extremaSensorTypes.forEach { sensor ->
            // Skip line distance as it's already added as the Apex above
            if (sensor != SensorType.LINE_DISTANCE_m) {
                addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MAX, markerList)
                if (sensor == SensorType.ALTITUDE || sensor == SensorType.TEMPERATURE) {
                    addExtremaMarkerIfPresent(workoutId, sensor, ExtremaType.MIN, markerList)
                }
            }
        }

        // 3. Photo Location Markers (REQ-UI-448.3 / ATT-43 / ATT-5479)
        val photos = getWorkoutPhotos(workoutId)
        photos.forEach { photo ->
            if (GeoUtils.isValidGpsCoordinate(photo.latitude, photo.longitude)) {
                val title = photo.caption ?: application.getString(R.string.workout_photos_marker_title)
                markerList.add(
                    LocationMarker(
                        position = LatLng(photo.latitude!!, photo.longitude!!),
                        iconResId = R.drawable.ic_place,
                        title = title
                    )
                )
            }
        }

        markerList
    }

    /**
     * Retrieves all photos attached to the given workout (REQ-DAT-038 / ATT-43 / ATT-5479).
     */
    fun getWorkoutPhotos(workoutId: Long): List<WorkoutPhotoEntry> {
        val rawPhotos = summariesManager.getPhotosForWorkout(workoutId)
        return WorkoutPhotoManager.selfHealPhotos(application, workoutId, rawPhotos)
    }

    /**
     * Attaches an athlete-selected photo URI to the workout.
     */
    suspend fun addWorkoutPhoto(workoutId: Long, sourceUri: Uri, caption: String? = null): WorkoutPhotoEntry? {
        return WorkoutPhotoManager.addPhoto(application, workoutId, sourceUri, caption)
    }

    /**
     * Deletes a photo from the workout database and disk.
     */
    fun deleteWorkoutPhoto(photoId: Long): Boolean {
        return WorkoutPhotoManager.deletePhoto(application, photoId)
    }

    /**
     * Saves or updates 1st-order dynamical Power-HR parameters for a workout (REQ-REV-009 / ATT-3904).
     */
    fun saveCardioPowerDynamics(workoutId: Long, record: CardioPowerDynamicsRecord) {
        summariesManager.saveCardioPowerDynamics(workoutId, record)
    }

    /**
     * Retrieves stored 1st-order dynamical Power-HR parameters for a workout (REQ-REV-009 / ATT-3904).
     */
    fun getCardioPowerDynamics(workoutId: Long): CardioPowerDynamicsRecord? {
        return summariesManager.getCardioPowerDynamics(workoutId)
    }

    /**
     * Lazily computes and persists 1st-order dynamical Power-HR parameters for historical workouts
     * if not already cached in the database (REQ-REV-009 / ATT-3904).
     */
    suspend fun getOrComputeCardioPowerDynamics(workoutId: Long): CardioPowerDynamicsRecord? = withContext(Dispatchers.IO) {
        val cached = summariesManager.getCardioPowerDynamics(workoutId)
        if (cached != null) return@withContext cached

        val baseFileName = summariesManager.getBaseFileName(workoutId) ?: return@withContext null
        if (!samplesManager.existsTable(baseFileName)) return@withContext null

        val db = samplesManager.database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)

        val powerSamples = ArrayList<Double?>()
        val hrSamples = ArrayList<Double?>()

        try {
            db.query(tableName, null, null, null, null, null, null).use { cursor ->
                val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
                val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
                while (cursor.moveToNext()) {
                    val p = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getDouble(powerIdx) else null
                    val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getDouble(hrIdx) else null
                    powerSamples.add(p)
                    hrSamples.add(hr)
                }
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not query samples for cardio-power lazy backfill: ${e.message}")
            return@withContext null
        }

        val calculated = PowerHrDynamicsCalculator.calculate(powerSamples, hrSamples, workoutId = workoutId)
        if (calculated != null) {
            summariesManager.saveCardioPowerDynamics(workoutId, calculated)
        }
        calculated
    }

    /**
     * Helper to conditionally add a sensor extremum marker if it has valid spatial data.
     *
     * Implementation: Queries the summary database for the peak value and its associated
     * coordinate. If found, a new marker is appended to the provided list.
     */
    private fun addExtremaMarkerIfPresent(
        workoutId: Long,
        sensor: SensorType,
        extremaType: ExtremaType,
        markerList: MutableList<LocationMarker>
    ) {
        // Try to get both from the summary record first (efficient, new way)
        var value: Double? = summariesManager.getExtremaValue(workoutId, sensor, extremaType)
        var pos: LatLng? = summariesManager.getExtremaPosition(workoutId, sensor, extremaType)

        // Fallback for legacy data if position is missing in the summary table
        if (pos == null) {
            val legacyExtrema = samplesManager.getExtremaPosition(summariesManager, workoutId, sensor, extremaType)
            if (legacyExtrema != null) {
                pos = legacyExtrema.latLng
                value = legacyExtrema.value
            }
        }

        if (value != null && pos != null) {
            val title = application.getString(
                R.string.location_extrema_format,
                extremaType.toString(), // Use localized name ("Max", "Min")
                sensor.getFullName(application),
                sensor.myFormatter.format(value),
                application.getString(MyHelper.getShortUnitsId(sensor))
            )
            markerList.add(LocationMarker(pos, getExtremaIcon(sensor, extremaType), title))
        }
    }

    /**
     * Identifies the appropriate technical icon for a sensor peak.
     */
    private fun getExtremaIcon(sensor: SensorType, type: ExtremaType): Int {
        return when (sensor) {
            SensorType.ALTITUDE -> if (type == ExtremaType.MAX) { R.drawable.ic_altitude_max} else { R.drawable.ic_altitude_min }
            SensorType.TEMPERATURE -> if (type == ExtremaType.MAX) R.drawable.ic_temp_max else R.drawable.ic_temp_min
            SensorType.HR -> R.drawable.ic_heart_rate
            SensorType.POWER -> R.drawable.ic_power
            SensorType.LINE_DISTANCE_m -> R.drawable.ic_distance
            SensorType.SPEED_mps -> R.drawable.ic_speed
            else -> -1
        }
    }


    init {
        // --- EVENT REGISTRATION ---
        // Registers receivers for workout state changes and export status updates.
        try {
            val filter = IntentFilter()
            filter.addAction(TrackerService.WORKOUT_UPDATED_INTENT)
            filter.addAction(TrackerService.TRACKING_FINISHED_INTENT)
            LocalBroadcastManager.getInstance(application).registerReceiver(workoutUpdateReceiver, filter)

            val exportFilter = IntentFilter(ExportStatusChangedBroadcaster.EXPORT_STATUS_CHANGED_INTENT)
            ContextCompat.registerReceiver(application, exportStatusUpdateReceiver, exportFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

            reconcilePendingCropRecovery()

            if (DEBUG) Log.d(TAG, "WorkoutRepository initialized and receivers registered.")
        } catch (e: Throwable) {
            // Defensive guard for non-Robolectric local unit tests where Android IntentFilter/BroadcastManager is unmocked
        }
    }

    /**
     * Checks and removes any orphaned ephemeral crop snapshots left behind by an aborted process,
     * verifying database integrity and reloading workout data before deletion.
     *
     * @return [Job] representing the background recovery task.
     */
    fun reconcilePendingCropRecovery(): Job = launch(Dispatchers.IO) {
            try {
                val backupFiles = application.cacheDir.listFiles { file ->
                    file.name.startsWith("crop_backup_") && file.name.endsWith(".json")
                }
                backupFiles?.forEach { file ->
                    Log.w(TAG, "reconcilePendingCropRecovery: Found pending/orphaned crop backup: ${file.name}")
                    try {
                        val text = file.readText()
                        val json = org.json.JSONObject(text)
                        val workoutId = json.optLong("workoutId", -1L)
                        if (workoutId != -1L) {
                            Log.i(TAG, "reconcilePendingCropRecovery: Verifying integrity and reloading workoutId=$workoutId")
                            reloadWorkoutData(workoutId)
                        }
                        file.delete()
                    } catch (ex: Exception) {
                        Log.e(TAG, "reconcilePendingCropRecovery: Failed to verify/recover for ${file.name}. Preserving backup file.", ex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in reconcilePendingCropRecovery", e)
            }
        }

    /**
     * Retrieves lightweight ordered sample points for crop visualization and boundary calculation.
     */
    suspend fun getCropSamplePoints(workoutId: Long): List<com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint> = withContext(Dispatchers.IO) {
        val baseFileName = summariesManager.getBaseFileName(workoutId) ?: return@withContext emptyList()
        val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        if (!samplesManager.existsTable(baseFileName)) return@withContext emptyList()

        val points = mutableListOf<com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint>()
        val db = samplesManager.database
        db.query(tableName, null, null, null, null, null, "${android.provider.BaseColumns._ID} ASC").use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(android.provider.BaseColumns._ID)
            val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
            val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
            val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
            val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)
            val latIdx = cursor.getColumnIndex(SensorType.LATITUDE.name)
            val lonIdx = cursor.getColumnIndex(SensorType.LONGITUDE.name)
            val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)
            val lapNrIdx = cursor.getColumnIndex(SensorType.LAP_NR.name)

            var idx = 0
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(idIdx)
                val timeStr = if (timeIdx != -1 && !cursor.isNull(timeIdx)) cursor.getString(timeIdx) else null
                val timeTotal = if (timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx)) cursor.getLong(timeTotalIdx) else 0L
                val timeActive = if (timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx)) cursor.getLong(timeActiveIdx) else 0L
                val dist = if (distIdx != -1 && !cursor.isNull(distIdx)) cursor.getDouble(distIdx) else 0.0
                val latLng = if (latIdx != -1 && lonIdx != -1 && !cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                    LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx))
                } else null
                val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null
                val lapNr = if (lapNrIdx != -1 && !cursor.isNull(lapNrIdx)) cursor.getLong(lapNrIdx) else 1L

                points.add(
                    com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint(
                        rowId = rowId,
                        index = idx++,
                        timeStr = timeStr,
                        timeTotalS = timeTotal,
                        timeActiveS = timeActive,
                        distanceTotalM = dist,
                        latLng = latLng,
                        altitude = alt,
                        lapNr = lapNr
                    )
                )
            }
        }
        points
    }

    /**
     * Executes atomic workout cropping across multiple domain database managers:
     *
     * Architectural Atomicity & Invariant Guarantees:
     * 1. Multi-Database Architecture: [WorkoutSamples.db], [Laps.db], and [WorkoutSummaries.db]
     *    are distinct SQLite database files maintained by independent SQLiteOpenHelper instances.
     * 2. Transaction Scope: Each underlying operation ([WorkoutSamplesDatabaseManager.pruneAndShiftSamples],
     *    [LapsDatabaseManager.replaceCroppedLaps], and [WorkoutSummariesDatabaseManager.updateCroppedWorkoutSummary])
     *    executes within an ACID transaction (`beginTransaction` / `setTransactionSuccessful` / `endTransaction`).
     * 3. Cross-Database Synchronization & Invariants:
     *    - [cropMutex] non-blocking serialization prevents concurrent mutations or conflicting exports.
     *    - Ephemeral snapshot `crop_backup_<workoutId>_<timestamp>.json` persists recovery metadata to disk.
     *    - Linear phase ordering (Samples -> Laps -> Summary) guarantees referential integrity.
     *    - In event of failure, defensive reload [reloadWorkoutData] resynchronizes in-memory caches.
     *    - [reconcilePendingCropRecovery] detects aborted operations across app restarts.
     *
     * @param workoutId The database ID of the workout.
     * @param startIndex 0-based sample start index to retain.
     * @param endIndex 0-based sample end index to retain.
     * @throws IllegalStateException If samples table does not exist or database integrity verification fails.
     * @throws com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropStorageException If insufficient storage space exists.
     * @return Result.success on success, Result.failure on storage or validation errors.
     */
    suspend fun cropWorkout(workoutId: Long, startIndex: Int, endIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        cropMutex.withLock {
            val baseFileName = summariesManager.getBaseFileName(workoutId)
                ?: return@withContext Result.failure(IllegalArgumentException("Workout not found: $workoutId"))
            val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
            if (!samplesManager.existsTable(baseFileName)) {
                return@withContext Result.failure(IllegalStateException("Samples table does not exist for $baseFileName"))
            }

            // 1. Dynamic Storage Pre-Flight Check (>= max(50 MB, 5 * dbFileSize))
            val dbFile = application.getDatabasePath(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.DB_NAME)
            val dbFileSize = if (dbFile.exists()) dbFile.length() else 0L
            val requiredBytes = maxOf(MIN_FREE_STORAGE_MB * 1024 * 1024, 5L * dbFileSize)
            val stat = android.os.StatFs(application.filesDir.path)
            if (stat.availableBytes < requiredBytes) {
                Log.e(TAG, "Insufficient storage for crop: available=${stat.availableBytes}, required=$requiredBytes")
                return@withContext Result.failure(com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropStorageException())
            }

            val samples = getCropSamplePoints(workoutId)
            val totalRows = samples.size
            if (samples.isEmpty() || startIndex < 0 || endIndex >= totalRows || startIndex >= endIndex) {
                return@withContext Result.failure(IllegalArgumentException("Invalid crop indices: start=$startIndex, end=$endIndex, total=$totalRows"))
            }

            val durationSec = (samples[endIndex].timeTotalS - samples[startIndex].timeTotalS).toDouble()
            if (!com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath.isCropValid(startIndex, endIndex, totalRows, durationSec)) {
                return@withContext Result.failure(IllegalArgumentException("Crop invalid: duration=$durationSec, retained=${endIndex - startIndex + 1}"))
            }

            val startSample = samples[startIndex]
            val endSample = samples[endIndex]
            val startRowId = startSample.rowId
            val endRowId = endSample.rowId
            val expectedRetainedRows = endIndex - startIndex + 1
            val lapOffset = startSample.lapNr.toInt()
            val newStartLatLng = startSample.latLng

            // 2. Pre-commit defensive cache invalidation
            exportManager.invalidateWorkoutExport(baseFileName)

            // 3. Ephemeral snapshot for crash recovery
            val backupFile = java.io.File(application.cacheDir, "crop_backup_${workoutId}_${System.currentTimeMillis()}.json")
            try {
                backupFile.writeText("{\"workoutId\":$workoutId,\"fileBaseName\":\"$baseFileName\",\"startIndex\":$startIndex,\"endIndex\":$endIndex}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not write ephemeral backup file", e)
            }

            try {
                // 4. Calculate updated lap metrics
                val retainedSamples = samples.subList(startIndex, endIndex + 1)
                val existingLaps = lapsDatabaseManager.getLaps(workoutId)
                val retainedByLap = retainedSamples.groupBy { it.lapNr }

                val recalculatedLaps = mutableListOf<LapData>()
                var newLapSequence = 1L
                for (existingLap in existingLaps) {
                    val lapPoints = retainedByLap[existingLap.lapNr] ?: continue
                    if (lapPoints.isEmpty()) continue
                    val firstPt = lapPoints.first()
                    val lastPt = lapPoints.last()
                    val lapDurationS = (lastPt.timeTotalS - firstPt.timeTotalS).coerceAtLeast(0L).toInt()
                    val lapDistanceM = (lastPt.distanceTotalM - firstPt.distanceTotalM).coerceAtLeast(0.0)
                    if (lapDurationS <= 0 && lapDistanceM <= 0.0) continue

                    val lapAvgSpeed = com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath.calculateAverageSpeed(lapDistanceM, lapDurationS)
                    val lapTimeStart = firstPt.timeStr ?: existingLap.timeStart

                    recalculatedLaps.add(
                        LapData(
                            id = 0L,
                            workoutId = workoutId,
                            lapNr = newLapSequence++,
                            timeStart = lapTimeStart,
                            timeTotalS = lapDurationS,
                            distanceTotalM = lapDistanceM,
                            speedAverageMps = lapAvgSpeed,
                            name = existingLap.name,
                            description = existingLap.description
                        )
                    )
                }

                val newTimeStart = startSample.timeStr
                val newTimeTotalS = (endSample.timeTotalS - startSample.timeTotalS).coerceAtLeast(0L)
                val newTimeActiveS = (endSample.timeActiveS - startSample.timeActiveS).coerceAtLeast(0L)
                val newDistanceTotalM = (endSample.distanceTotalM - startSample.distanceTotalM).coerceAtLeast(0.0)
                val newSpeedAverageMps = com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath.calculateAverageSpeed(newDistanceTotalM, newTimeActiveS.toInt())

                if (recalculatedLaps.isEmpty() && (newTimeTotalS > 0L || newDistanceTotalM > 0.0)) {
                    recalculatedLaps.add(
                        LapData(
                            id = 0L,
                            workoutId = workoutId,
                            lapNr = 1L,
                            timeStart = newTimeStart,
                            timeTotalS = newTimeTotalS.toInt(),
                            distanceTotalM = newDistanceTotalM,
                            speedAverageMps = newSpeedAverageMps,
                            name = existingLaps.firstOrNull()?.name,
                            description = existingLaps.firstOrNull()?.description
                        )
                    )
                }

                // 5. Streams, Polyline & Extrema
                val retainedLatLngs = retainedSamples.mapNotNull { it.latLng }
                val newPolyline = if (retainedLatLngs.isNotEmpty()) com.google.maps.android.PolyUtil.encode(retainedLatLngs) else null

                var minLat: Double? = null
                var minLng: Double? = null
                var maxLat: Double? = null
                var maxLng: Double? = null
                if (retainedLatLngs.isNotEmpty()) {
                    minLat = retainedLatLngs.minOf { it.latitude }
                    maxLat = retainedLatLngs.maxOf { it.latitude }
                    minLng = retainedLatLngs.minOf { it.longitude }
                    maxLng = retainedLatLngs.maxOf { it.longitude }
                }

                // Downsample streams for summary
                val hasAnyAltitude = retainedSamples.any { it.altitude != null && !it.altitude.isNaN() }
                val sampledAltitudes = mutableListOf<Double>()
                val sampledDistances = mutableListOf<Double>()
                if (hasAnyAltitude) {
                    for (i in retainedSamples.indices) {
                        if (i % WorkoutSummariesDatabaseManager.WorkoutSummaries.ENCODING_STEP_SIZE == 0 || i == retainedSamples.lastIndex) {
                            val s = retainedSamples[i]
                            val alt = s.altitude ?: run {
                                var prevAlt: Double? = null
                                var prevDist = 0.0
                                for (k in i - 1 downTo 0) {
                                    val a = retainedSamples[k].altitude
                                    if (a != null && !a.isNaN()) {
                                        prevAlt = a
                                        prevDist = retainedSamples[k].distanceTotalM
                                        break
                                    }
                                }
                                var nextAlt: Double? = null
                                var nextDist = 0.0
                                for (k in i + 1 until retainedSamples.size) {
                                    val a = retainedSamples[k].altitude
                                    if (a != null && !a.isNaN()) {
                                        nextAlt = a
                                        nextDist = retainedSamples[k].distanceTotalM
                                        break
                                    }
                                }
                                when {
                                    prevAlt != null && nextAlt != null -> {
                                        val distSpan = nextDist - prevDist
                                        if (distSpan > 1e-6) {
                                            prevAlt + (nextAlt - prevAlt) * ((s.distanceTotalM - prevDist) / distSpan)
                                        } else {
                                            prevAlt
                                        }
                                    }
                                    prevAlt != null -> prevAlt
                                    nextAlt != null -> nextAlt
                                    else -> 0.0
                                }
                            }
                            sampledAltitudes.add(alt)
                            sampledDistances.add((s.distanceTotalM - startSample.distanceTotalM).coerceAtLeast(0.0))
                        }
                    }
                } else {
                    for (i in retainedSamples.indices) {
                        if (i % WorkoutSummariesDatabaseManager.WorkoutSummaries.ENCODING_STEP_SIZE == 0 || i == retainedSamples.lastIndex) {
                            val s = retainedSamples[i]
                            sampledDistances.add((s.distanceTotalM - startSample.distanceTotalM).coerceAtLeast(0.0))
                        }
                    }
                }
                val encodedAltitudes = if (sampledAltitudes.isNotEmpty()) com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils.encodeDoubles(sampledAltitudes) else null
                val encodedDistances = if (sampledDistances.isNotEmpty()) com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils.encodeDoubles(sampledDistances) else null

                // Ascent / Descent calculation
                var ascending = 0.0
                var descending = 0.0
                for (i in 1 until retainedSamples.size) {
                    val prevAlt = retainedSamples[i - 1].altitude
                    val currAlt = retainedSamples[i].altitude
                    if (prevAlt != null && currAlt != null) {
                        val diff = currAlt - prevAlt
                        if (diff > 0) ascending += diff else descending += -diff
                    }
                }

                // Extrema recalculation
                val extremaUpdates = mutableListOf<WorkoutSummariesDatabaseManager.ExtremaUpdateRecord>()
                if (newStartLatLng != null) {
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.LATITUDE, ExtremaType.START, newStartLatLng.latitude, newStartLatLng))
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.LONGITUDE, ExtremaType.START, newStartLatLng.longitude, newStartLatLng))
                }
                val newEndLatLng = endSample.latLng
                if (newEndLatLng != null) {
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.LATITUDE, ExtremaType.END, newEndLatLng.latitude, newEndLatLng))
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.LONGITUDE, ExtremaType.END, newEndLatLng.longitude, newEndLatLng))
                }

                // Max Line distance and apex
                if (newStartLatLng != null) {
                    var maxDisp = 0.0
                    var apex = newStartLatLng
                    for (pt in retainedLatLngs) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            newStartLatLng.latitude, newStartLatLng.longitude,
                            pt.latitude, pt.longitude, results
                        )
                        if (results[0] > maxDisp) {
                            maxDisp = results[0].toDouble()
                            apex = pt
                        }
                    }
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.LINE_DISTANCE_m, ExtremaType.MAX, maxDisp, apex))
                }

                // Recompute min/max for altitude
                val retainedAltitudes = retainedSamples.mapNotNull { it.altitude }
                if (retainedAltitudes.isNotEmpty()) {
                    val minAlt = retainedAltitudes.minOrNull() ?: 0.0
                    val maxAlt = retainedAltitudes.maxOrNull() ?: 0.0
                    val avgAlt = retainedAltitudes.average()
                    val minAltPt = retainedSamples.firstOrNull { it.altitude == minAlt }?.latLng
                    val maxAltPt = retainedSamples.firstOrNull { it.altitude == maxAlt }?.latLng
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.ALTITUDE, ExtremaType.MIN, minAlt, minAltPt))
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.ALTITUDE, ExtremaType.MAX, maxAlt, maxAltPt))
                    extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.ALTITUDE, ExtremaType.AVG, avgAlt, null))
                }

                // Speed avg extrema
                extremaUpdates.add(WorkoutSummariesDatabaseManager.ExtremaUpdateRecord(SensorType.SPEED_mps, ExtremaType.AVG, newSpeedAverageMps, null))

                // Build summary ContentValues
                val summaryValues = android.content.ContentValues().apply {
                    newTimeStart?.let { put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START, it) }
                    put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s, newTimeTotalS)
                    put(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s, newTimeActiveS)
                    put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m, newDistanceTotalM)
                    put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPEED_AVERAGE_mps, newSpeedAverageMps)
                    put(WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING, ascending)
                    put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING, descending)
                    newPolyline?.let { put(WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE, it) }
                    encodedAltitudes?.let { put(WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM, it) }
                    encodedDistances?.let { put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_STREAM, it) }
                    if (minLat != null) {
                        put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT, minLat)
                        put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG, minLng)
                        put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT, maxLat)
                        put(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG, maxLng)
                    }
                }

                // 6. Execute atomic database operations
                samplesManager.pruneAndShiftSamples(baseFileName, startRowId, endRowId, expectedRetainedRows, lapOffset, newStartLatLng)
                lapsDatabaseManager.replaceCroppedLaps(workoutId, recalculatedLaps)
                summariesManager.updateCroppedWorkoutSummary(workoutId, summaryValues, extremaUpdates)

                // 7. Cleanup ephemeral backup upon transaction success
                try {
                    backupFile.delete()
                } catch (e: Exception) {
                    // Ignore
                }

                // 8. Reload workout data in cache and UI
                reloadWorkoutData(workoutId)

                // 9. Enqueue fresh export post-commit
                try {
                    val updatedWorkout = allWorkouts.value.find { it.id == workoutId }
                    if (updatedWorkout != null) {
                        exportManager.exportWorkout(updatedWorkout)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error triggering post-crop export for workout $workoutId", e)
                }

                // 10. Background fragmentation check / incremental vacuum
                val deletedRows = totalRows - expectedRetainedRows
                if (deletedRows > 0.20 * totalRows) {
                    launch(Dispatchers.IO) {
                        samplesManager.vacuumIfFragmented(deletedRows, totalRows)
                    }
                }

                Result.success(Unit)
            } catch (e: android.database.sqlite.SQLiteFullException) {
                Log.e(TAG, "Disk full during crop operation for workout $workoutId", e)
                reloadWorkoutData(workoutId)
                Result.failure(e)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error during crop operation for workout $workoutId", t)
                reloadWorkoutData(workoutId)
                Result.failure(t)
            }
        }
    }

    // --- Public API for ViewModels ---

    /**
     * Loads a single workout by its ID into the repository's LiveData and memory cache.
     *
     * Implementation: Performs a direct database query and atomically updates the
     * in-memory workout list to ensure UI consistency.
     *
     * @param id The primary key of the session to load.
     */
    suspend fun loadWorkout(id: Long) {
        withContext(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(id).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    val workout = mapper.fromCursor(cursor)
                    _initialWorkoutLoaded.postValue(workout)

                    _allWorkouts.update { currentList ->
                        val newList = currentList.toMutableList()
                        val index = newList.indexOfFirst { it.id == id }

                        if (index != -1) {
                            newList[index] = workout
                        } else {
                            newList.add(workout)
                            newList.sortByDescending { it.headerData.startTimeS }
                        }
                        newList
                    }
                }
            }
        }
    }


    val exportStatusDataProvider = ExportStatusDataProvider(application)
    val orderedExportTypes = listOf(ExportType.FILE, ExportType.DROPBOX, ExportType.GOOGLE_DRIVE, ExportType.COMMUNITY)

    private val isListLoading = java.util.concurrent.atomic.AtomicBoolean(false)
    private val deletedDuringLoadIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /**
     * Merges freshly streamed workout entities from local persistence into [_allWorkouts],
     * strictly excluding sessions deleted via [deleteWorkout] during loading and preserving
     * active in-memory transient fields (REQ-STB-018.2).
     */
    private fun emitMergedWorkouts(loadedWorkouts: List<WorkoutData>) {
        val deleted = deletedDuringLoadIds.toSet()
        _allWorkouts.update { currentMemoryList ->
            val memoryMap = currentMemoryList.associateBy { it.id }
            val mergedLoaded = loadedWorkouts
                .filterNot { it.id in deleted }
                .map { loaded ->
                    val existing = memoryMap[loaded.id]
                    if (existing != null) {
                        loaded.copy(
                            mapPolyline = if (loaded.mapPolyline.isEmpty()) existing.mapPolyline else loaded.mapPolyline,
                            encodedAltitudes = if (loaded.encodedAltitudes.isEmpty()) existing.encodedAltitudes else loaded.encodedAltitudes,
                            encodedDistances = if (loaded.encodedDistances.isEmpty()) existing.encodedDistances else loaded.encodedDistances,
                            extremaRows = if (loaded.extremaRows.isEmpty()) existing.extremaRows else loaded.extremaRows,
                            exportStatuses = if (loaded.exportStatuses.isEmpty()) existing.exportStatuses else loaded.exportStatuses
                        )
                    } else {
                        loaded
                    }
                }
            val loadedIds = mergedLoaded.map { it.id }.toSet()
            val unstreamed = currentMemoryList.filter { it.id !in deleted && it.id !in loadedIds }
            if (unstreamed.isEmpty()) {
                mergedLoaded
            } else {
                (unstreamed + mergedLoaded).sortedByDescending { it.headerData.startTimeS }
            }
        }
    }

    /**
     * Performs a progressive background scan of the entire workout history.
     *
     * Implementation:
     * 1. Acquires a cursor for all workouts sorted descending (newest first).
     * 2. Iterates through the cursor in chunks (100 sessions per batch) to maintain UI responsiveness.
     * 3. For each chunk, vectorized queries are used to fetch metadata (Strava logs, extrema).
     * 4. Batches are emitted into [allWorkouts] as they are ready, providing immediate feedback.
     */
    suspend fun loadAllWorkouts() {
        if (!isListLoading.compareAndSet(false, true)) {
            if (DEBUG) Log.d(TAG, "loadAllWorkouts: already in progress, skipping redundant call.")
            return
        }

        Log.i(TAG, "loadAllWorkouts: starting optimized progressive streaming load.")
        deletedDuringLoadIds.clear()
        try {
            withContext(Dispatchers.IO) {
                val cursor = summariesManager.getCursorForAllWorkouts()
                val allLoadedWorkouts = mutableListOf<WorkoutData>()
                
                cursor.use { c ->
                    if (!c.moveToFirst()) return@withContext

                    val batchSize = 50
                    var processedCount = 0
                    val totalCount = c.count

                    while (!c.isAfterLast) {
                        // 1. Gather raw snapshots for the next chunk in a single forward-only pass (ATT-2309 / REQ-STB-013)
                        val snapshots = mutableListOf<WorkoutDataMapper.RawCursorSnapshot>()
                        var i = 0
                        while (i < batchSize && !c.isAfterLast) {
                            try {
                                val snapshot = mapper.readCursorSnapshot(c)
                                snapshots.add(snapshot)
                            } catch (e: Exception) {
                                Log.e(TAG, "loadAllWorkouts: error reading row at position ${c.position}, skipping", e)
                            }
                            c.moveToNext()
                            i++
                        }

                        if (snapshots.isEmpty()) {
                            continue
                        }

                        // 2. Fetch Metadata for the chunk in vectorized queries (ATT-359/388)
                        val chunkIds = snapshots.map { it.workoutId }
                        val chunkNames = snapshots.mapNotNull { it.fileBaseName }
                        val chunkClusterIds = snapshots.mapNotNull { if (it.clusterId != -1L) it.clusterId else null }.toSet()

                        val extremaList = summariesManager.getExtremaForWorkouts(chunkIds)
                        val stravaDataMap = stravaUploadDbHelper.getStravaActivityDataForWorkouts(chunkNames)
                        val clusterNamesMap = WorkoutClusterDatabaseManager.getInstance(application).getClusterNamesForIds(chunkClusterIds)
                        val lapsMap = LapsDatabaseManager.getInstance(application).getLapsForWorkouts(chunkIds)
                        val cardioPowerMap = summariesManager.getCardioPowerDynamicsForWorkouts(chunkIds)

                        val batchMetadata = WorkoutDataMapper.BatchMetadata(
                            extrema = extremaList.groupBy { it.workoutId },
                            stravaData = stravaDataMap,
                            clusterNames = clusterNamesMap,
                            laps = lapsMap,
                            cardioPowerDynamics = cardioPowerMap
                        )

                        // 3. Map the chunk in memory without cursor seeking (ATT-2309 / REQ-STB-018.2)
                        for (snapshot in snapshots) {
                            try {
                                val workoutData = mapper.fromSnapshot(snapshot, batchMetadata)

                                // Add Export Statuses (Fast from memory-only providers if possible)
                                val exportStatuses: MutableList<ExportStatusGroupData> = mutableListOf()
                                if (workoutData.fileBaseName != null) {
                                    for (type in orderedExportTypes) {
                                        try {
                                            val groupData = exportStatusDataProvider.createGroupData(workoutData.fileBaseName, type)
                                            if (groupData.hasContent) exportStatuses.add(groupData)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "loadAllWorkouts: error creating export status group data for type $type", e)
                                        }
                                    }
                                }

                                allLoadedWorkouts.add(workoutData.copy(exportStatuses = exportStatuses))
                                processedCount++
                            } catch (e: Exception) {
                                Log.e(TAG, "loadAllWorkouts: error mapping snapshot for workout ${snapshot.workoutId}, skipping", e)
                            }
                        }

                        // 4. PROGRESSIVE UI PUMP (ATT-346 Style)
                        // Emit updates to provide immediate feedback
                        if (processedCount <= 10 || processedCount % batchSize == 0 || processedCount == totalCount) {
                            Log.d(TAG, "Streaming batch to UI: $processedCount items.")
                            emitMergedWorkouts(allLoadedWorkouts)
                        }
                    }
                }
                // Unconditional terminal batch publication (REQ-STB-018.2)
                emitMergedWorkouts(allLoadedWorkouts)
                Log.i(TAG, "Load complete. Total workouts: ${allLoadedWorkouts.size}")
            }
        } finally {
            deletedDuringLoadIds.clear()
            isListLoading.set(false)
        }
    }

    // --- Public API for Direct Memory Updates (Avoids DB Read) ---

    /**
     * Replaces the entire polyline for a workout in memory.
     */
    fun setMapPolyline(workoutId: Long, polyline: String) {
        updateWorkoutInMemory(workoutId) { it.copy(mapPolyline = polyline) }
    }

    /**
     * Appends a new segment to an existing workout polyline in memory.
     */
    fun appendMapPolyline(workoutId: Long, polylineSuffix: String) {
        updateWorkoutInMemory(workoutId) { it.copy(mapPolyline = it.mapPolyline + polylineSuffix) }
    }

    /**
     * Replaces the altitude and distance streams for a workout in memory.
     */
    fun setElevationStreams(workoutId: Long, altitudes: String, distances: String) {
        updateWorkoutInMemory(workoutId) { it.copy(encodedAltitudes = altitudes, encodedDistances = distances) }
    }

    /**
     * Appends new data to existing altitude and distance streams in memory.
     */
    fun appendElevationStreams(workoutId: Long, altitudeSuffix: String, distanceSuffix: String) {
        updateWorkoutInMemory(workoutId) {
            it.copy(
                encodedAltitudes = it.encodedAltitudes + altitudeSuffix,
                encodedDistances = it.encodedDistances + distanceSuffix
            )
        }
    }

    /**
     * Updates the commute and trainer flags for a workout in memory.
     */
    fun setCommuteAndTrainer(workoutId: Long, commute: Boolean, trainer: Boolean) {
        updateWorkoutInMemory(workoutId) { it.copy(commute = commute, trainer = trainer) }
    }

    /**
     * Updates the display name of a workout in memory.
     */
    fun setWorkoutName(workoutId: Long, name: String) {
        updateWorkoutInMemory(workoutId) { it.copy(workoutName = name) }
    }

    /**
     * Updates the diagnostic recording errors payload for a workout in memory (REQ-TRK-053.3).
     */
    fun setRecordingErrors(workoutId: Long, recordingErrors: String?) {
        updateWorkoutInMemory(workoutId) { it.copy(recordingErrors = recordingErrors) }
    }

    /**
     * Clears diagnostic recording errors for a workout both in database and in memory (REQ-TRK-053.1, REQ-TRK-053.3).
     */
    suspend fun clearRecordingErrors(workoutId: Long) {
        withContext(Dispatchers.IO) {
            summariesManager.clearRecordingErrors(workoutId)
        }
        updateWorkoutInMemory(workoutId) { it.copy(recordingErrors = null) }
    }

    /**
     * Marks an old unfinished workout as finished in database and memory,
     * and rolls its metrics into historical period summaries (ATT-987 / REQ-UI-146).
     */
    fun markWorkoutFinished(workoutId: Long) {
        launch {
            withContext(Dispatchers.IO) {
                summariesManager.setWorkoutFinished(workoutId)
            }
            updateWorkoutInMemory(workoutId) { current ->
                current.copy(finished = true)
            }
            val updatedWorkout = _allWorkouts.value.firstOrNull { it.id == workoutId }
            if (updatedWorkout != null) {
                app?.let { PeriodsRepository.getInstance(it).onWorkoutFinished(updatedWorkout) }
            }
        }
    }

    /**
     * Updates a specific sensor peak (Extremum) for a workout in memory.
     *
     * Implementation: Updates both the flat [WorkoutData] fields and the specific
     * row in the [WorkoutData.extremaRows] list to ensure the UI reflects the change immediately.
     */
    fun updateExtremaValue(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType, value: Double, position: LatLng? = null) {
        summariesManager.updateExtremaValue(workoutId, sensorType, extremaType, value, position)
        val formattedValue = sensorType.myFormatter.format(value)
        updateWorkoutInMemory(workoutId) { workout ->
            var updated = workout

            // 1. Update specific raw fields
            when (sensorType) {
                SensorType.LINE_DISTANCE_m -> if (extremaType == ExtremaType.MAX) updated = updated.copy(maxDisplacement = value)
                SensorType.ALTITUDE -> {
                    when (extremaType) {
                        ExtremaType.MIN -> updated = updated.copy(minAltitude = value)
                        ExtremaType.MAX -> updated = updated.copy(maxAltitude = value)
                        else -> {}
                    }
                }
                else -> {}
            }

            // 2. Update the ExtremaDataRow in the list
            val sensorLabel = application.getString(sensorType.shortNameId)
            val updatedRows = updated.extremaRows.map { row ->
                if (row.sensorLabel == sensorLabel) {
                    when (extremaType) {
                        ExtremaType.MIN -> row.copy(minValue = formattedValue, minLatLng = position)
                        ExtremaType.AVG -> row.copy(avgValue = formattedValue)
                        ExtremaType.MAX -> row.copy(maxValue = formattedValue, maxLatLng = position)
                        else -> row
                    }
                } else row
            }
            updated.copy(extremaRows = updatedRows)
        }
    }

    /**
     * Updates the custom name and description for a specific lap (ATT-511).
     * Persists changes to LapsDatabaseManager on Dispatchers.IO and atomically
     * updates the in-memory cache so UI updates reactively without reloading.
     */
    suspend fun updateLapDetails(workoutId: Long, lapNr: Long, name: String?, description: String?) {
        withContext(Dispatchers.IO) {
            LapsDatabaseManager.getInstance(application).updateLapDetails(workoutId, lapNr, name, description)
        }
        updateWorkoutInMemory(workoutId) { current ->
            val updatedLaps = current.laps.map { lap ->
                if (lap.lapNr == lapNr) lap.copy(name = name, description = description) else lap
            }
            current.copy(laps = updatedLaps)
        }
    }

    /**
     * Splits a lap at the specified sample index in both WorkoutSamples.db and Laps.db (REQ-UI-449.3 / ATT-907).
     *
     * @param workoutId The ID of the workout.
     * @param splitSampleIndex The 0-based sample index within the full workout samples list where the split begins.
     * @return Result.success with updated List<LapData>, or Result.failure with descriptive error.
     */
    suspend fun splitLapAtSample(workoutId: Long, splitSampleIndex: Int): Result<List<LapData>> = withContext(Dispatchers.IO) {
        try {
            val baseFileName = summariesManager.getBaseFileName(workoutId)
                ?: return@withContext Result.failure(IllegalStateException("No baseFileName found for workout $workoutId"))
            if (!samplesManager.existsTable(baseFileName)) {
                return@withContext Result.failure(IllegalStateException("Samples table does not exist for $baseFileName"))
            }

            val samples = getCropSamplePoints(workoutId)
            if (samples.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No samples found for workout $workoutId"))
            }
            if (splitSampleIndex !in samples.indices) {
                return@withContext Result.failure(IndexOutOfBoundsException("Split index $splitSampleIndex out of bounds [0, ${samples.size})"))
            }

            val existingLaps = lapsDatabaseManager.getLaps(workoutId)
            val validation = com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator.validateSplit(
                samples = samples,
                splitSampleIndex = splitSampleIndex,
                existingLaps = existingLaps
            )
            if (!validation.isValid) {
                return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid split point"))
            }

            val splitPoint = samples[splitSampleIndex]

            // 1. Transactional update in WorkoutSamples.db: Increment LAP_NR for all rows >= splitPoint.rowId
            samplesManager.incrementLapNumbersFromSample(baseFileName, splitPoint.rowId)

            // 2. Map updated samples with new lap numbers
            val updatedSamples = samples.map { pt ->
                if (pt.index >= splitSampleIndex) pt.copy(lapNr = pt.lapNr + 1) else pt
            }

            // 3. Recalculate all laps
            val newLaps = com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator.recalculateLapsAfterSplit(
                workoutId = workoutId,
                existingLaps = existingLaps,
                updatedSamples = updatedSamples
            )

            // 4. Persist updated laps to Laps.db atomically
            lapsDatabaseManager.replaceCroppedLaps(workoutId, newLaps)

            // 5. Invalidate export cache
            try {
                val updatedWorkout = allWorkouts.value.find { it.id == workoutId }
                if (updatedWorkout != null) {
                    exportManager.exportWorkout(updatedWorkout)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal export cache refresh exception after split lap: ${e.message}")
            }

            // 6. Refresh in-memory reactive state
            reloadWorkoutData(workoutId)

            Result.success(newLaps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to split lap at sample index $splitSampleIndex for workout $workoutId", e)
            Result.failure(e)
        }
    }

    /**
     * Moves an existing boundary between [boundaryLapNr] (Lap k) and [boundaryLapNr] + 1 (Lap k+1)
     * to [newBoundarySampleIndex] (REQ-UI-450.4 / ATT-908).
     *
     * Atomically reassigns sample lap numbers in WorkoutSamples.db for the shifted range,
     * updates affected lap summaries in Laps.db via replaceCroppedLaps, invalidates export cache,
     * and refreshes in-memory workout data.
     */
    suspend fun moveLapBoundary(
        workoutId: Long,
        boundaryLapNr: Long,
        newBoundarySampleIndex: Int
    ): Result<List<LapData>> = withContext(Dispatchers.IO) {
        try {
            val baseFileName = summariesManager.getBaseFileName(workoutId)
                ?: return@withContext Result.failure(IllegalStateException("No base file name for workout $workoutId"))
            if (!samplesManager.existsTable(baseFileName)) {
                return@withContext Result.failure(IllegalStateException("Samples table does not exist for $baseFileName"))
            }

            val samples = getCropSamplePoints(workoutId)
            if (samples.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No samples for workout $workoutId"))
            }

            val existingLaps = lapsDatabaseManager.getLaps(workoutId)
            val validation = com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator.validateBoundaryMove(
                samples = samples,
                boundaryLapNr = boundaryLapNr,
                newBoundaryIndex = newBoundarySampleIndex,
                existingLaps = existingLaps
            )
            if (!validation.isValid) {
                return@withContext Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid boundary position"))
            }

            val lapKPlus1Indices = samples.indices.filter { samples[it].lapNr == boundaryLapNr + 1L }
            if (lapKPlus1Indices.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Lap ${boundaryLapNr + 1} not found in samples"))
            }
            val currentBoundaryIndex = lapKPlus1Indices.first()
            val targetBoundaryIndex = validation.clampedSampleIndex

            if (targetBoundaryIndex != currentBoundaryIndex) {
                if (targetBoundaryIndex > currentBoundaryIndex) {
                    // Shifted forward: samples in [currentBoundaryIndex, targetBoundaryIndex - 1] move from lap k+1 to lap k
                    val startRowId = samples[currentBoundaryIndex].rowId
                    val endRowId = samples[targetBoundaryIndex - 1].rowId
                    samplesManager.updateLapNumberForSampleRange(baseFileName, startRowId, endRowId, boundaryLapNr)
                } else {
                    // Shifted backward: samples in [targetBoundaryIndex, currentBoundaryIndex - 1] move from lap k to lap k+1
                    val startRowId = samples[targetBoundaryIndex].rowId
                    val endRowId = samples[currentBoundaryIndex - 1].rowId
                    samplesManager.updateLapNumberForSampleRange(baseFileName, startRowId, endRowId, boundaryLapNr + 1L)
                }
            }

            // Recalculate laps
            val newLaps = com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator.recalculateLapsAfterBoundaryMove(
                workoutId = workoutId,
                existingLaps = existingLaps,
                samples = samples,
                boundaryLapNr = boundaryLapNr,
                newBoundarySampleIndex = targetBoundaryIndex
            )

            // Persist updated laps to Laps.db atomically
            lapsDatabaseManager.replaceCroppedLaps(workoutId, newLaps)

            // Invalidate export cache
            try {
                val updatedWorkout = allWorkouts.value.find { it.id == workoutId }
                if (updatedWorkout != null) {
                    exportManager.exportWorkout(updatedWorkout)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal export cache refresh exception after move lap boundary: ${e.message}")
            }

            // Refresh in-memory reactive state
            reloadWorkoutData(workoutId)

            Result.success(newLaps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move lap boundary for lap $boundaryLapNr at index $newBoundarySampleIndex for workout $workoutId", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a lap boundary and merges the adjacent laps in both WorkoutSamples.db and Laps.db (REQ-UI-454.3 / ATT-5482).
     *
     * Removes the boundary between [removedBoundaryLapNr] (Lap k) and Lap k+1, merging them into Lap k,
     * decrementing LAP_NR for all subsequent samples in WorkoutSamples.db, recalculating contiguous 1..N-1
     * lap summaries, persisting to Laps.db via replaceCroppedLaps, invalidating export cache, and reloading WorkoutData.
     *
     * @param workoutId The ID of the workout.
     * @param removedBoundaryLapNr The 1-based lap number of the first lap in the merged pair (1 <= k < N).
     * @return Result.success with updated List<LapData>, or Result.failure with descriptive error.
     */
    suspend fun deleteLap(workoutId: Long, removedBoundaryLapNr: Long): Result<List<LapData>> = withContext(Dispatchers.IO) {
        try {
            val baseFileName = summariesManager.getBaseFileName(workoutId)
                ?: return@withContext Result.failure(IllegalStateException("No baseFileName found for workout $workoutId"))
            if (!samplesManager.existsTable(baseFileName)) {
                return@withContext Result.failure(IllegalStateException("Samples table does not exist for $baseFileName"))
            }

            val existingLaps = lapsDatabaseManager.getLaps(workoutId)
            if (existingLaps.size < 2) {
                return@withContext Result.failure(IllegalStateException("Cannot delete lap: workout must have at least 2 laps"))
            }
            if (removedBoundaryLapNr < 1L || removedBoundaryLapNr >= existingLaps.size.toLong()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid boundary lap number $removedBoundaryLapNr for ${existingLaps.size} laps"))
            }

            // 1. Transactional update in WorkoutSamples.db: Decrement LAP_NR for all rows where LAP_NR > removedBoundaryLapNr
            samplesManager.decrementLapNumbersAbove(baseFileName, removedBoundaryLapNr)

            // 2. Recalculate all laps
            val newLaps = com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator.recalculateLapsAfterMerge(
                workoutId = workoutId,
                existingLaps = existingLaps,
                removedBoundaryLapNr = removedBoundaryLapNr
            )

            // 3. Persist updated laps to Laps.db atomically
            lapsDatabaseManager.replaceCroppedLaps(workoutId, newLaps)

            // 4. Invalidate export cache
            try {
                val updatedWorkout = allWorkouts.value.find { it.id == workoutId }
                if (updatedWorkout != null) {
                    exportManager.exportWorkout(updatedWorkout)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal export cache refresh exception after delete lap: ${e.message}")
            }

            // 5. Refresh in-memory reactive state
            reloadWorkoutData(workoutId)

            Result.success(newLaps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete lap boundary at lap $removedBoundaryLapNr for workout $workoutId", e)
            Result.failure(e)
        }
    }

    /**
     * Semantic alias for [deleteLap] (REQ-UI-454.3 / ATT-5482).
     */
    suspend fun mergeAdjacentLaps(workoutId: Long, removedBoundaryLapNr: Long): Result<List<LapData>> =
        deleteLap(workoutId, removedBoundaryLapNr)

    /**
     * Atomically updates a workout session in memory while handling potential race conditions.
     *
     * Implementation: If the workout is already cached, it applies the [block] transform purely in-place
     * with zero coroutine launches inside CAS (REQ-STB-018.3). If not, it fetches the session from the
     * database asynchronously outside CAS retry loops, ensuring pure functional invariants.
     */
    private fun updateWorkoutInMemory(workoutId: Long, block: (WorkoutData) -> WorkoutData) {
        val isCached = _allWorkouts.value.any { it.id == workoutId }
        if (isCached) {
            _allWorkouts.update { currentList ->
                val index = currentList.indexOfFirst { it.id == workoutId }
                if (index != -1) {
                    currentList.map { if (it.id == workoutId) block(it) else it }
                } else {
                    currentList
                }
            }
            return
        }

        // Workout is NOT in the list yet (e.g. background worker finished before UI load)
        launch(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    val freshWorkout = mapper.fromCursor(cursor)
                    val updatedWorkout = block(freshWorkout)

                    _allWorkouts.update { list ->
                        if (list.any { it.id == workoutId }) {
                            list.map { if (it.id == workoutId) updatedWorkout else it }
                        } else {
                            (list + updatedWorkout).sortedByDescending { it.headerData.startTimeS }
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a new workout to the memory cache or performs a surgical merge with an existing record.
     *
     * Implementation: To prevent overwriting real-time background data (like active tracks)
     * with stale database snapshots, this method performs a field-level merge of
     * transient metadata.
     */
    private fun addOrUpdateWorkout(workout: WorkoutData) {
        _allWorkouts.update { currentList ->
            val index = currentList.indexOfFirst { it.id == workout.id }
            if (index != -1) {
                // UPDATE & MERGE: Preserve background-calculated fields from memory
                currentList.map { existing ->
                    if (existing.id == workout.id) {
                        val mergedExtremaRows = workout.extremaRows.map { rowFromDb ->
                            val existingRow = existing.extremaRows.find { it.sensorLabel == rowFromDb.sensorLabel }
                            if (existingRow != null) {
                                rowFromDb.copy(
                                    minLatLng = if (rowFromDb.minLatLng == null) existingRow.minLatLng else rowFromDb.minLatLng,
                                    maxLatLng = if (rowFromDb.maxLatLng == null) existingRow.maxLatLng else rowFromDb.maxLatLng
                                )
                            } else {
                                rowFromDb
                            }
                        }

                        workout.copy(
                            mapPolyline = if (workout.mapPolyline.isEmpty()) existing.mapPolyline else workout.mapPolyline,
                            encodedAltitudes = if (workout.encodedAltitudes.isEmpty()) existing.encodedAltitudes else workout.encodedAltitudes,
                            encodedDistances = if (workout.encodedDistances.isEmpty()) existing.encodedDistances else workout.encodedDistances,
                            extremaRows = if (mergedExtremaRows.isEmpty()) existing.extremaRows else mergedExtremaRows,
                            exportStatuses = if (workout.exportStatuses.isEmpty()) existing.exportStatuses else workout.exportStatuses
                        )
                    } else existing
                }
            } else {
                // ADD: Append and re-sort
                (currentList + workout).sortedByDescending { it.headerData.startTimeS }
            }
        }
    }

    /**
     * Replaces a workout in the memory list with a fresh instance.
     */
    private fun updateWorkoutInList(workoutId: Long, updatedWorkout: WorkoutData) {
        if (com.atrainingtracker.BuildConfig.DEBUG) {
            val hasRoute = !updatedWorkout.mapPolyline.isNullOrEmpty()
            Log.d(TAG, "updateWorkoutInList: workoutId=$workoutId (${updatedWorkout.fileBaseName}), hasPolyline=$hasRoute, extremaRows: ${updatedWorkout.extremaRows}, exportStatuses: ${updatedWorkout.exportStatuses}")
        }
        _allWorkouts.update { currentList ->
            currentList.map {
                if (it.id == workoutId) updatedWorkout else it
            }
        }
    }

    /**
     * Public accessor to force a reload of a single workout from the database.
     *
     * Implementation: Uses a conflated reload pipeline (REQ-STB-018.1). If a reload for [workoutId]
     * is already active, concurrent reload requests are flagged as dirty rather than dropped,
     * guaranteeing a sequential re-query pass that catches trailing state transitions
     * (e.g. TRACKING_FINISHED_INTENT) and triggers lifecycle observers on finalization.
     *
     * @param workoutId The primary key of the session to reload.
     */
    suspend fun reloadWorkoutData(workoutId: Long) {
        val shouldStart = synchronized(activeReloadWorkoutIds) {
            if (!activeReloadWorkoutIds.add(workoutId)) {
                dirtyReloadWorkoutIds.add(workoutId)
                false
            } else {
                true
            }
        }
        if (!shouldStart) {
            if (DEBUG) Log.d(TAG, "reloadWorkoutData: workoutId=$workoutId already reloading, marked dirty for sequential pass")
            return
        }

        try {
            do {
                if (DEBUG) Log.i(TAG, "reloadWorkoutData: executing pass for workoutId=$workoutId")

                withContext(Dispatchers.IO) {
                    summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                        if (cursor?.moveToFirst() == true) {
                            // Get the fresh data from the database.
                            val freshWorkoutData = mapper.fromCursor(cursor)
                            
                            // --- SURGICAL PERIOD UPDATE (ATT-346) & CLUSTER UPDATE (ATT-354 / REQ-MIG-025) ---
                            val existing = _allWorkouts.value.find { it.id == workoutId }
                            val isLiveSessionFinish = (existing != null && !existing.finished) && freshWorkoutData.finished
                            val isNewImportOrFinish = (existing == null || !existing.finished) && freshWorkoutData.finished
                            
                            if (isNewImportOrFinish) {
                                app?.let { PeriodsRepository.getInstance(it).onWorkoutFinished(freshWorkoutData) }
                                extractAndPersistClimbsFromWorkout(workoutId)
                            }

                            if (isLiveSessionFinish && freshWorkoutData.clusterId != -1L) {
                                WorkoutClusterEngine.getInstance(application).onWorkoutFinished(application, freshWorkoutData)
                            }

                            addOrUpdateWorkout(freshWorkoutData)
                        }
                    }
                }
            } while (synchronized(activeReloadWorkoutIds) {
                if (dirtyReloadWorkoutIds.remove(workoutId)) {
                    true
                } else {
                    activeReloadWorkoutIds.remove(workoutId)
                    false
                }
            })
        } finally {
            synchronized(activeReloadWorkoutIds) {
                activeReloadWorkoutIds.remove(workoutId)
                dirtyReloadWorkoutIds.remove(workoutId)
            }
        }
    }

    /**
     * Extracts qualifying climbs from a finished or imported workout's track points
     * and persists them to Climbs.db with 50m spatial deduplication (REQ-MAP-064.1).
     * Refreshes LiveClimbsRepository to enable free-riding live ClimbPro.
     */
    suspend fun extractAndPersistClimbsFromWorkout(workoutId: Long): List<Long> = withContext(Dispatchers.IO) {
        try {
            val points = getWorkoutTrackPoints(workoutId, TrackType.BEST)
            if (points.size >= 2) {
                val detected = ClimbDetector.detectClimbs(points, routeId = null, context = application)
                if (detected.isNotEmpty()) {
                    val ids = ClimbsDatabaseManager.getInstance(application)
                        .insertClimbsWithDeduplicationBatch(detected, thresholdMeters = 50.0f)
                    try {
                        LiveClimbsRepository.getInstance(application).refreshClimbs()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to refresh LiveClimbsRepository after workout climb extraction", e)
                    }
                    return@withContext ids
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract and persist climbs for workout $workoutId", e)
        }
        emptyList()
    }


    /**
     * Saves the user-editable state of a WorkoutData object to the databases.
     *
     * Implementation: Performs a surgical database update and triggers a re-export of
     * the session data. Additionally, it executes the 'Learning Loop' to refine
     * Workout Cluster centroids and identities based on the new user input.
     *
     * @param userEditedWorkout The modified session data to persist.
     */
    fun saveWorkout(userEditedWorkout: WorkoutData?) {
        if (userEditedWorkout == null) return
        val workoutId = userEditedWorkout.id

        // Launch a coroutine in the IO dispatcher to perform database operations off the main thread.
        launch(Dispatchers.IO) {

            // 1. Update the Database (this method already only touches editable columns)
            summariesManager.updateWorkoutData(userEditedWorkout)

            // 2. Trigger export with the new data
            exportManager.exportWorkout(userEditedWorkout)

            // --- LEARNING LOOP (SCRUM-44 / ATT-4862, REQ-SET-081) ---
            // Only update cluster learning (centroids / sport) if the workout is already assigned to a cluster (clusterId > 0).
            // Unclustered workouts ('Offen', clusterId <= 0) strictly remain unclustered regardless of custom workout name edits.
            // Cluster creation and assignment occur exclusively via explicit user actions in EditWorkoutClusterDialog.
            if (userEditedWorkout.clusterId > 0 && userEditedWorkout.startLatLng != null && userEditedWorkout.endLatLng != null && userEditedWorkout.maxDisplacementLatLng != null) {
                val finalName = userEditedWorkout.workoutName.replace(Regex(" #\\d+$"), "").trim()
                // Only learn names that aren't the default timestamp
                if (finalName.isNotEmpty() && finalName != userEditedWorkout.fileBaseName) {
                    val engine = com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.getInstance(application)
                    engine.learnFromWorkout(
                        userEditedWorkout.startLatLng,
                        userEditedWorkout.endLatLng,
                        userEditedWorkout.maxDisplacementLatLng,
                        userEditedWorkout.totalDistance,
                        finalName,
                        userEditedWorkout.sportId,
                        userEditedWorkout.clusterId
                    )
                }
            }

            // 3. Update memory surgically - perform the merge ATOMICALLY inside update
            Log.i(TAG, "update from saveWorkout")
            val oldWorkout = _allWorkouts.value.find { it.id == workoutId }
            _allWorkouts.update { currentList ->
                currentList.map { current ->
                    if (current.id == workoutId) {
                        current.copy(
                            workoutName = userEditedWorkout.workoutName,
                            sportId = userEditedWorkout.sportId,
                            sportName = userEditedWorkout.sportName,
                            bSportType = userEditedWorkout.bSportType,
                            equipmentId = userEditedWorkout.equipmentId,
                            equipmentName = userEditedWorkout.equipmentName,
                            clusterId = userEditedWorkout.clusterId,
                            clusterName = userEditedWorkout.clusterName,
                            description = userEditedWorkout.description,
                            goal = userEditedWorkout.goal,
                            method = userEditedWorkout.method,
                            commute = userEditedWorkout.commute,
                            trainer = userEditedWorkout.trainer,
                            race = userEditedWorkout.race,
                            source = current.source,
                            uploadToStrava = userEditedWorkout.uploadToStrava
                        )
                    } else current
                }
            }

            // --- SURGICAL UPDATES (ATT-346 / ATT-354) ---
            oldWorkout?.let { old ->
                if (old.sportId != userEditedWorkout.sportId) {
                    app?.let { PeriodsRepository.getInstance(it).onWorkoutSportChanged(userEditedWorkout, old) }
                    WorkoutClusterEngine.getInstance(application).onWorkoutSportChanged(userEditedWorkout, old)
                }
            }

            saveFinishedEvent.postValue(Pair(workoutId, true))
        }
    }

    /**
     * Manually triggers a file export for a specific session.
     */
    suspend fun exportWorkoutTo(workoutId: Long, fileFormat: FileFormat) {
        withContext(Dispatchers.IO) {
            exportManager.exportWorkoutTo(workoutId, fileFormat)
        }
    }

    /**
     * Deletes a workout session from the database and memory.
     *
     * Implementation: Triggers surgical cleanup for dependent modules (Periods, Clusters)
     * and posts granular progress updates for UI feedback.
     *
     * @param id The unique identifier of the workout to delete.
     */
    fun deleteWorkout(id: Long) {
        if (isListLoading.get()) {
            deletedDuringLoadIds.add(id)
        }
        launch(Dispatchers.IO) {
            // Find the workout name *before* deleting it.
            val workout = _allWorkouts.value.find { it.id == id }
            val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $id"

            // --- START PROGRESS ---
            // Post the detailed progress to the LiveData.
            _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, id))

            var success = false
            try {
                // Perform the actual deletion in the database first
                success = deletionHelper.deleteWorkout(id)
                if (success) {
                    if (isListLoading.get()) {
                        deletedDuringLoadIds.add(id)
                    }
                    // --- SURGICAL UPDATES (ATT-346 / ATT-354) ---
                    workout?.let { 
                        app?.let { appInstance -> PeriodsRepository.getInstance(appInstance).onWorkoutDeleted(it) }
                        WorkoutClusterEngine.getInstance(application).onWorkoutDeleted(application, it)
                    }

                    // Now, update the in-memory list
                    _allWorkouts.update { currentList ->
                        currentList.filterNot { it.id == id }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteWorkout: exception deleting workoutId=$id", e)
                success = false
            } finally {
                // REQ-STB-018.4 / Rule 74 (ATT-5565): Reset progress to Idle BEFORE posting
                // deleteFinishedEvent so observers awakened by the event never observe a stale InProgress state.
                _deletionProgress.postValue(DeletionProgress.Idle)
                deleteFinishedEvent.postValue(Pair(id, success))
            }
        }
    }


    /**
     * Bulk deletes all workouts older than the specified retention period.
     *
     * Implementation: Delegates to [WorkoutDeletionHelper] and provides a callback
     * to reactively update dependent modules and the UI list.
     *
     * @param daysToKeep Number of days of workout history to preserve.
     */
    suspend fun deleteOldWorkouts(daysToKeep: Int) {
        withContext(Dispatchers.IO) {
            val notificationManager = notificationManagerProvider?.invoke(application)
                ?: WorkoutDeletionNotificationManager(application)
            try {
                // The callback invoked inside the helper to update UI progress.
                val progressCallback = WorkoutDeletionHelper.DeletionProgressCallback { current, total, workoutId ->
                    val workout = allWorkouts.value.find { it.id == workoutId }
                    val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $workoutId"
                    _deletionProgress.postValue(DeletionProgress.Deleting(current, total, workoutName, workoutId))
                    notificationManager.showProgressNotification(current, total, workoutName)
                }

                val success = deletionHelper.deleteOldWorkouts(daysToKeep, progressCallback)

                // After deleting, reload workouts and resynchronize downstream analytical caches (REQ-DAT-011, REQ-UI-427.4).
                if (success) {
                    withContext(NonCancellable) {
                        _deletionProgress.postValue(DeletionProgress.Resyncing)
                        notificationManager.showResyncNotification()

                        loadAllWorkouts()
                        PeriodsRepository.getInstance(application).resyncAllPeriods()
                        WorkoutClusterRepository.getInstance(application).refreshClusters(forceShowProgress = false, forceCheckIntegrity = true)
                        WorkoutClusterEngine.getInstance(application).enrichAllClusterMetadata(application)
                    }
                }
            } finally {
                // Reset the state to Idle when done or if an error occurs.
                _deletionProgress.postValue(DeletionProgress.Idle)
                notificationManager.cancelNotification()
            }
        }
    }

    /**
     * Converts a workout's path into a permanent Route record.
     *
     * Implementation: Fetches the high-fidelity track points and creates a new
     * [RouteSummary] entry in the Routes database.
     *
     * @param workout The source session data.
     * @return The unique ID of the newly created route.
     */
    suspend fun saveAsRoute(workout: WorkoutData): Long? = withContext(Dispatchers.IO) {
        val points = getWorkoutTrackPoints(workout.id, TrackType.BEST)
        if (points.isEmpty()) return@withContext null

        val routeSummary = RouteSummary(
            id = 0, // Auto-increment
            externalId = workout.fileBaseName ?: "",
            name = workout.workoutName,
            description = workout.description ?: "",
            isSelected = false,
            distance = workout.totalDistance,
            elevationGain = workout.ascentMeters.toDouble(),
            bSportType = workout.bSportType,
            source = RouteSource.WORKOUT
        )

        val routesRepo = RoutesRepository.getInstance(application)
        routesRepo.insertRoute(routeSummary, points)
    }

    /**
     * Manually assigns a Workout Cluster to a specific session.
     *
     * Implementation: Updates the database link and triggers a full identity
     * re-evaluation to propagate inferred sport and gear settings.
     *
     * @param workoutId Target workout session.
     * @param clusterId Target route family (cluster).
     */
    fun assignClusterToWorkout(workoutId: Long, clusterId: Long) {
        launch(Dispatchers.IO) {
            WorkoutClusterEngine.getInstance(application)
                .assignClusterToWorkout(application, workoutId, clusterId, forceIdentity = true)

            // Reload fresh data from DB to propagate inferred identity to UI
            reloadWorkoutData(workoutId)
            loadWorkout(workoutId)
        }
    }

    /**
     * Unassigns the workout from its cluster, decrementing the cluster's hit count (REQ-SET-059).
     */
    fun unassignClusterFromWorkout(workoutId: Long) {
        launch(Dispatchers.IO) {
            WorkoutClusterEngine.getInstance(application)
                .unassignClusterFromWorkout(application, workoutId)

            reloadWorkoutData(workoutId)
            loadWorkout(workoutId)
        }
    }

    /**
     * Creates a new cluster from the workout's spatial fingerprint and assigns the workout to it (REQ-SET-059, REQ-SET-072).
     */
    fun createNewClusterFromWorkout(
        workout: WorkoutData,
        customName: String? = null,
        hasCounter: Boolean = true,
        customWorkoutName: String? = null
    ) {
        launch(Dispatchers.IO) {
            WorkoutClusterEngine.getInstance(application)
                .createNewClusterFromWorkout(application, workout, customName, hasCounter, customWorkoutName)

            reloadWorkoutData(workout.id)
            loadWorkout(workout.id)
        }
    }

    /**
     * Creates a new cluster from raw workout metadata and assigns the workout to it (REQ-SET-059, REQ-SET-072).
     */
    fun createNewClusterFromWorkout(
        workoutId: Long,
        customName: String? = null,
        hasCounter: Boolean = true,
        customWorkoutName: String? = null
    ) {
        launch(Dispatchers.IO) {
            WorkoutClusterEngine.getInstance(application)
                .createNewClusterFromWorkout(application, workoutId, customName, hasCounter, customWorkoutName)

            reloadWorkoutData(workoutId)
            loadWorkout(workoutId)
        }
    }

}
