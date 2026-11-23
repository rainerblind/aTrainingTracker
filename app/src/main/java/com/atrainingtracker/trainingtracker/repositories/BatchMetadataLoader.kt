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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import android.location.Location
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutAutoNamingHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.google.android.gms.maps.model.LatLng
import kotlin.math.roundToInt

/**
 * Shared batch metadata loader for [WorkoutDataMapper] (ATT-6038 / REQ-DAT-045.3).
 * Eliminates N+1 per-workout SQLite queries across [WorkoutRepository],
 * [WorkoutClusterRepository], and single-item [WorkoutDataMapper.fromCursor] delegation without
 * exposing raw database managers or cursors outside the data layer.
 */
class BatchMetadataLoader(
    private val context: Context,
    private val summariesManagerProvider: () -> WorkoutSummariesDatabaseManager = {
        WorkoutSummariesDatabaseManager.getInstance(context)
    },
    private val sportTypeDbProvider: () -> SportTypeDatabaseManager = {
        SportTypeDatabaseManager.getInstance(context)
    },
    private val equipmentDbProvider: () -> EquipmentDbHelper = {
        EquipmentDbHelper.getInstance(context)
    },
    private val stravaUploadDbProvider: () -> StravaUploadDbHelper = {
        StravaUploadDbHelper.getInstance(context)
    },
    private val clusterDbProvider: () -> WorkoutClusterDatabaseManager? = {
        safeCall { WorkoutClusterDatabaseManager.getInstance(context) }
    },
    private val lapsDbProvider: () -> LapsDatabaseManager? = {
        safeCall { LapsDatabaseManager.getInstance(context) }
    },
    private val knownLocationsDbProvider: () -> KnownLocationsDatabaseManager? = {
        safeCall { KnownLocationsDatabaseManager.getInstance(context) }
    },
    private val knownLocationsRepoProvider: () -> KnownLocationsRepository? = {
        safeCall { KnownLocationsRepository.getInstance(context) }
    },
    private val athleteMetricsDbProvider: () -> AthleteMetricsDatabaseManager? = {
        safeCall { AthleteMetricsDatabaseManager.getInstance(context) }
    },
    private val fallbackFtpProvider: () -> Double = {
        safeCall { SettingsDataStoreJavaHelper.getFtp(context)?.takeIf { it > 0 }?.toDouble() } ?: 0.0
    },
    private val fallbackWeightKgProvider: () -> Double = {
        safeCall { SettingsDataStoreJavaHelper.getAthleteWeightKg(context).toDouble() } ?: 0.0
    }
) {

    /**
     * Loads all 9 [WorkoutDataMapper.BatchMetadata] maps in O(1) batch SQLite queries for [snapshots].
     */
    fun loadForSnapshots(snapshots: List<WorkoutDataMapper.RawCursorSnapshot>): WorkoutDataMapper.BatchMetadata {
        if (snapshots.isEmpty()) {
            return WorkoutDataMapper.BatchMetadata(
                extrema = emptyMap(),
                stravaData = emptyMap()
            )
        }

        val summariesManager = summariesManagerProvider()
        val sportTypeDb = sportTypeDbProvider()
        val equipmentDb = equipmentDbProvider()
        val stravaUploadDb = stravaUploadDbProvider()

        val chunkIds = snapshots.map { it.workoutId }
        val chunkNames = snapshots.mapNotNull { it.fileBaseName }
        val chunkClusterIds = snapshots.mapNotNull { if (it.clusterId != -1L) it.clusterId else null }.toSet()
        val sportIds = snapshots.map { it.sportId }.toSet()

        val extremaList = summariesManager.getExtremaForWorkouts(chunkIds)
        val stravaDataMap = if (chunkNames.isNotEmpty()) {
            stravaUploadDb.getStravaActivityDataForWorkouts(chunkNames)
        } else {
            emptyMap()
        }
        val clusterNamesMap = if (chunkClusterIds.isNotEmpty()) {
            safeCall { clusterDbProvider()?.getClusterNamesForIds(chunkClusterIds) } ?: emptyMap()
        } else {
            emptyMap()
        }
        val lapsMap = safeCall { lapsDbProvider()?.getLapsForWorkouts(chunkIds) } ?: emptyMap()
        val cardioPowerMap = safeCall { summariesManager.getCardioPowerDynamicsForWorkouts(chunkIds) } ?: emptyMap()

        val sportTypesMap = sportIds.associateWith { id ->
            WorkoutDataMapper.SportTypeInfo(
                bSportType = sportTypeDb.getBSportType(id),
                sportName = sportTypeDb.getUIName(id),
                stravaSportName = sportTypeDb.getStravaName(id)
            )
        }

        val equipmentMap = safeCall {
            equipmentDb.getEquipmentItems().associate { it.id to it.name }
        } ?: emptyMap()

        val locationNamesMap = resolveLocationNamesBatch(extremaList)
        val athleteMetricsMap = resolveAthleteMetricsBatch(snapshots)

        return WorkoutDataMapper.BatchMetadata(
            extrema = extremaList.groupBy { it.workoutId },
            stravaData = stravaDataMap,
            clusterNames = clusterNamesMap,
            laps = lapsMap,
            sportTypes = sportTypesMap,
            equipmentNames = equipmentMap,
            locationNames = locationNamesMap,
            athleteMetrics = athleteMetricsMap,
            cardioPowerDynamics = cardioPowerMap,
            hasEquipmentNames = true,
            hasLocationNames = true,
            hasCardioPowerDynamics = true
        )
    }

    /**
     * Loads a complete [WorkoutDataMapper.BatchMetadata] for a single workout [snapshot]
     * used by [WorkoutDataMapper.fromCursor] delegation (`REQ-DAT-045.4`).
     */
    fun loadForSingleWorkout(snapshot: WorkoutDataMapper.RawCursorSnapshot): WorkoutDataMapper.BatchMetadata {
        val summariesManager = summariesManagerProvider()
        val workoutId = snapshot.workoutId

        val extremaList = summariesManager.getExtremaForWorkouts(listOf(workoutId))
        val stravaData = snapshot.fileBaseName?.let { fileBaseName ->
            safeCall { stravaUploadDbProvider().getStravaActivityData(fileBaseName) }
                ?.let { mapOf(fileBaseName to it) }
        } ?: emptyMap()

        val clusterNames = if (snapshot.clusterId != -1L) {
            safeCall { clusterDbProvider()?.getClusterNameById(snapshot.clusterId) }
                ?.let { mapOf(snapshot.clusterId to it) }
                ?: emptyMap()
        } else {
            emptyMap()
        }

        val laps = safeCall { lapsDbProvider()?.getLaps(workoutId) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { mapOf(workoutId to it) }
            ?: emptyMap()

        val cardioPowerDynamics = safeCall { summariesManager.getCardioPowerDynamics(workoutId) }
            ?.let { mapOf(workoutId to it) }
            ?: emptyMap()

        return WorkoutDataMapper.BatchMetadata(
            extrema = extremaList.groupBy { it.workoutId },
            stravaData = stravaData,
            clusterNames = clusterNames,
            laps = laps,
            cardioPowerDynamics = cardioPowerDynamics,
            hasCardioPowerDynamics = true
        )
    }

    private fun resolveLocationNamesBatch(
        extremaList: List<WorkoutSummariesDatabaseManager.ExtremaRecord>
    ): Map<LatLng, String> {
        val knownLocs = safeCall {
            knownLocationsDbProvider()?.allLocations
                ?: knownLocationsRepoProvider()?.allLocations
        } ?: emptyList()
        if (knownLocs.isEmpty()) return emptyMap()

        val locationNamesMap = mutableMapOf<LatLng, String>()
        val startLoc = Location("")
        val candidateLoc = Location("")
        for (record in extremaList) {
            if (record.sensorType == SensorType.LATITUDE && record.position != null) {
                val pos = record.position
                if (!locationNamesMap.containsKey(pos)) {
                    candidateLoc.latitude = pos.latitude
                    candidateLoc.longitude = pos.longitude
                    var minDistance = Double.MAX_VALUE
                    var bestLoc: KnownLocationsDatabaseManager.MyLocation? = null
                    for (loc in knownLocs) {
                        startLoc.latitude = loc.latLng.latitude
                        startLoc.longitude = loc.latLng.longitude
                        val d = candidateLoc.distanceTo(startLoc)
                        if (d < loc.radius && d < minDistance) {
                            minDistance = d.toDouble()
                            bestLoc = loc
                        }
                    }
                    val name = bestLoc?.let { WorkoutAutoNamingHelper.getDisplayName(context, it) }
                    if (name != null) {
                        locationNamesMap[pos] = name
                    }
                }
            }
        }
        return locationNamesMap
    }

    private fun resolveAthleteMetricsBatch(
        snapshots: List<WorkoutDataMapper.RawCursorSnapshot>
    ): Map<Long, WorkoutDataMapper.AthleteMetricsInfo> {
        val db = athleteMetricsDbProvider()
        val ftpEntries = safeCall { db?.getEntriesByType(AthleteMetricType.FTP_WATTS) } ?: emptyList()
        val weightEntries = safeCall { db?.getEntriesByType(AthleteMetricType.WEIGHT_KG) } ?: emptyList()
        val fallbackFtp = fallbackFtpProvider()
        val fallbackWeight = fallbackWeightKgProvider()

        return snapshots.associate { snapshot ->
            val timestampMs = snapshot.startTimeS * 1000L
            val effectiveFtp = resolveEffectiveFtp(ftpEntries, timestampMs, fallbackFtp)
            val effectiveWeight = resolveEffectiveWeight(weightEntries, timestampMs, fallbackWeight)
            snapshot.workoutId to WorkoutDataMapper.AthleteMetricsInfo(
                effectiveFtpWatts = effectiveFtp,
                effectiveWeightKg = effectiveWeight
            )
        }
    }

    private fun resolveEffectiveFtp(
        entriesDesc: List<AthleteMetricEntry>,
        timestampMs: Long,
        fallbackFtp: Double
    ): Double {
        if (entriesDesc.isNotEmpty()) {
            val entry = if (timestampMs > 0L) {
                entriesDesc.firstOrNull { it.timestampMs <= timestampMs } ?: entriesDesc.firstOrNull()
            } else {
                null
            }
            if (entry != null && entry.value > 0.0) {
                val rounded = entry.value.roundToInt()
                if (rounded > 0) return rounded.toDouble()
            }
        }
        return fallbackFtp
    }

    private fun resolveEffectiveWeight(
        entriesDesc: List<AthleteMetricEntry>,
        timestampMs: Long,
        fallbackWeight: Double
    ): Double {
        if (entriesDesc.isNotEmpty()) {
            val entry = if (timestampMs > 0L) {
                entriesDesc.firstOrNull { it.timestampMs <= timestampMs } ?: entriesDesc.firstOrNull()
            } else {
                null
            }
            if (entry != null && entry.value > 0.0) {
                return entry.value.toFloat().toDouble()
            }
        }
        return fallbackWeight
    }

    companion object {
        private const val TAG = "BatchMetadataLoader"

        private inline fun <T> safeCall(block: () -> T?): T? {
            return try {
                block()
            } catch (_: Throwable) {
                null
            }
        }
    }
}
