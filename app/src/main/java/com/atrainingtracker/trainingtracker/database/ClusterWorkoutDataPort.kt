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

import android.content.ContentValues
import android.content.Context
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.database.inTransaction
import com.google.android.gms.maps.model.LatLng

/**
 * Persistence-layer snapshot of workout metadata consumed by [WorkoutClusterEngine]
 * so `database/` avoids upward imports from `ui.aftermath` (IF-SWE-05, REQ-PRO-130.2).
 */
data class ClusterWorkoutSnapshot(
    val id: Long,
    val finished: Boolean = true,
    val clusterId: Long = -1L,
    val sportId: Long = -1L,
    val workoutName: String = "",
    val totalDistance: Double = 0.0,
    val startTimeS: Long = 0L,
    val mapPolyline: String = "",
    val startLatLng: LatLng? = null,
    val endLatLng: LatLng? = null,
    val maxDisplacementLatLng: LatLng? = null,
    val minLat: Double? = null,
    val minLng: Double? = null,
    val maxLat: Double? = null,
    val maxLng: Double? = null,
    val minAltitudeLatLng: LatLng? = null,
    val maxAltitudeLatLng: LatLng? = null
)

/**
 * Persistence and extrema data port consumed by [WorkoutClusterEngine] (`IF-SWE-05`, REQ-PRO-130.2).
 * Implemented by [com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository] in runtime UI
 * flows (propagating in-memory cache updates) and backed by [SummariesManagerClusterWorkoutDataPort]
 * when invoked purely within the database layer.
 */
interface ClusterWorkoutDataPort {
    fun getExtremaPosition(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType): LatLng?
    fun updateExtremaValue(
        workoutId: Long,
        sensorType: SensorType,
        extremaType: ExtremaType,
        value: Double,
        position: LatLng? = null
    )
    fun <T> runInTransaction(block: () -> T): T
    fun getWorkoutBounds(workoutId: Long): Map<String, Double>
    fun getWorkoutClusterId(workoutId: Long): Long
    fun getWorkoutName(workoutId: Long): String?
    fun getWorkoutFileBaseName(workoutId: Long): String?
    fun updateWorkoutSummary(workoutId: Long, values: ContentValues): Int
    fun getWorkoutCountForCluster(clusterId: Long): Int
    fun getWorkoutCountsForAllClusters(): Map<Long, Int>
    fun getWorkoutPolyline(workoutId: Long): String?
    fun getWorkoutSportString(workoutId: Long): String?
    fun getWorkoutAvgSpeed(workoutId: Long): Double?
    fun getWorkoutDistance(workoutId: Long): Double?
    fun getWorkoutSportId(workoutId: Long): Long
    fun getMostFrequentSportIdForCluster(clusterId: Long): Long
    fun reassignClusterWorkouts(sourceId: Long, targetId: Long): Int
    fun applyInferredIdentity(workoutId: Long, identity: EquipmentAndSportTypeDiscoveryManager.InferredIdentity)
    fun forEachWorkoutBoundsForCluster(clusterId: Long, block: (WorkoutBoundsDto) -> Unit)
    fun forEachWorkoutPolylineCandidate(block: (WorkoutPolylineCandidateDto) -> Unit)
    fun forEachWorkoutDistanceCandidateForCluster(clusterId: Long, block: (WorkoutDistanceCandidateDto) -> Unit)
    fun forEachWorkoutAscWithProgress(block: (candidate: ClusterWorkoutCandidateDto, count: Int, total: Int) -> Unit)
    fun forEachRemainingWorkoutForClusterDesc(clusterId: Long, block: (ClusterWorkoutCandidateDto) -> Unit)
}

/**
 * SQLite-backed fallback implementation of [ClusterWorkoutDataPort] delegating directly to
 * [WorkoutSummariesDatabaseManager] inside the `database/` layer.
 */
class SummariesManagerClusterWorkoutDataPort(
    private val context: Context
) : ClusterWorkoutDataPort {

    private val summariesManager get() = WorkoutSummariesDatabaseManager.getInstance(context)

    override fun getExtremaPosition(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType): LatLng? =
        summariesManager.getExtremaPosition(workoutId, sensorType, extremaType)

    override fun updateExtremaValue(
        workoutId: Long,
        sensorType: SensorType,
        extremaType: ExtremaType,
        value: Double,
        position: LatLng?
    ) {
        summariesManager.updateExtremaValue(workoutId, sensorType, extremaType, value, position)
    }

    override fun <T> runInTransaction(block: () -> T): T {
        return summariesManager.database.inTransaction { block() }
    }

    override fun getWorkoutBounds(workoutId: Long): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT)
            ?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT] = it }
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG)
            ?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG] = it }
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT)
            ?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT] = it }
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG)
            ?.let { map[WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG] = it }
        return map
    }

    override fun getWorkoutClusterId(workoutId: Long): Long =
        summariesManager.getLong(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID) ?: -1L

    override fun getWorkoutName(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)

    override fun getWorkoutFileBaseName(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME)

    override fun updateWorkoutSummary(workoutId: Long, values: ContentValues): Int =
        summariesManager.database.update(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            values,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        )

    override fun getWorkoutCountForCluster(clusterId: Long): Int =
        summariesManager.getWorkoutCountForCluster(clusterId)

    override fun getWorkoutCountsForAllClusters(): Map<Long, Int> =
        summariesManager.workoutCountsForAllClusters

    override fun getWorkoutPolyline(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE)

    override fun getWorkoutSportString(workoutId: Long): String? =
        summariesManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.B_SPORT)

    override fun getWorkoutAvgSpeed(workoutId: Long): Double? =
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.SPEED_AVERAGE_mps)

    override fun getWorkoutDistance(workoutId: Long): Double? =
        summariesManager.getDouble(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m)

    override fun getWorkoutSportId(workoutId: Long): Long =
        summariesManager.getLong(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID) ?: -1L

    override fun getMostFrequentSportIdForCluster(clusterId: Long): Long =
        summariesManager.getMostFrequentSportIdForCluster(clusterId)

    override fun reassignClusterWorkouts(sourceId: Long, targetId: Long): Int =
        summariesManager.reassignClusterWorkouts(sourceId, targetId)

    override fun applyInferredIdentity(
        workoutId: Long,
        identity: EquipmentAndSportTypeDiscoveryManager.InferredIdentity
    ) {
        summariesManager.applyInferredIdentity(workoutId, identity)
    }

    override fun forEachWorkoutBoundsForCluster(clusterId: Long, block: (WorkoutBoundsDto) -> Unit) {
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
            null,
            null,
            null
        ).use { c ->
            while (c.moveToNext()) {
                block(WorkoutBoundsDto.fromCursor(c))
            }
        }
    }

    override fun forEachWorkoutPolylineCandidate(block: (WorkoutPolylineCandidateDto) -> Unit) {
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} IS NOT NULL AND ${WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE} != ''"
        val projection = arrayOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE
        )
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            projection,
            selection,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                block(WorkoutPolylineCandidateDto.fromCursor(cursor))
            }
        }
    }

    override fun forEachWorkoutDistanceCandidateForCluster(
        clusterId: Long,
        block: (WorkoutDistanceCandidateDto) -> Unit
    ) {
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            arrayOf(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
                WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
            ),
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?",
            arrayOf(clusterId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                block(WorkoutDistanceCandidateDto.fromCursor(cursor))
            }
        }
    }

    override fun forEachWorkoutAscWithProgress(
        block: (candidate: ClusterWorkoutCandidateDto, count: Int, total: Int) -> Unit
    ) {
        summariesManager.getCursorForAllWorkoutsAsc()?.use { cursor ->
            val total = cursor.count
            var count = 0
            while (cursor.moveToNext()) {
                count++
                block(ClusterWorkoutCandidateDto.fromCursor(cursor), count, total)
            }
        }
    }

    override fun forEachRemainingWorkoutForClusterDesc(
        clusterId: Long,
        block: (ClusterWorkoutCandidateDto) -> Unit
    ) {
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?",
            arrayOf(clusterId.toString()),
            null,
            null,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                block(ClusterWorkoutCandidateDto.fromCursor(cursor))
            }
        }
    }
}

/**
 * Registry decoupling [WorkoutClusterEngine] from `ui.aftermath.WorkoutRepository` (`IF-SWE-05`, REQ-PRO-130.2).
 */
object ClusterWorkoutDataPortRegistry {
    @Volatile
    private var provider: ((Context) -> ClusterWorkoutDataPort)? = null

    @Volatile
    private var testOverride: ClusterWorkoutDataPort? = null

    @Volatile
    private var resetHook: (() -> Unit)? = null

    fun registerProvider(factory: (Context) -> ClusterWorkoutDataPort, onReset: (() -> Unit)? = null) {
        provider = factory
        if (onReset != null) {
            resetHook = onReset
        }
    }

    fun setTestOverride(port: ClusterWorkoutDataPort?) {
        testOverride = port
    }

    fun resolve(context: Context): ClusterWorkoutDataPort {
        return testOverride ?: provider?.invoke(context) ?: SummariesManagerClusterWorkoutDataPort(context)
    }

    fun resetForTesting() {
        testOverride = null
        resetHook?.invoke()
    }
}
