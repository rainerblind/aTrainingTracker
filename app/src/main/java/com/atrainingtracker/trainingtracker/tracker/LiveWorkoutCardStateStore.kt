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

package com.atrainingtracker.trainingtracker.tracker

import android.content.ContentValues
import android.content.Context
import androidx.compose.runtime.Immutable
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.atrainingtracker.trainingtracker.ui.util.MetricFormatterContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToLong

/**
 * Lightweight, immutable 1-Hz snapshot of the currently recording workout session
 * (`REQ-PER-047.1`).
 *
 * Scalar fields are nullable so that paused or resumed sessions never overwrite persisted
 * [WorkoutData] values with zeros before an active sample updates that metric.
 */
@Immutable
data class LiveWorkoutCardSnapshot(
    val workoutId: Long,
    val activeTimeSec: Long? = null,
    val totalTimeSec: Long? = null,
    val distanceM: Double? = null,
    val avgSpeedMps: Double? = null,
    val ascentM: Long? = null,
    val descentM: Long? = null,
    val avgHeartRateBpm: Double? = null,
    val avgPowerWatts: Double? = null,
    val avgCadenceRpm: Double? = null,
    val laps: List<LapData> = emptyList(),
    val isPaused: Boolean = false
)

/**
 * Localized labels and unit strings used when overlaying live averages onto [ExtremaDataRow]s
 * inside the active workout summary card (`REQ-PER-047.3`).
 */
@Immutable
data class LiveExtremaLabels(
    val hrLabel: String = "HR",
    val hrUnit: String = "bpm",
    val speedLabel: String = "Speed",
    val speedUnit: String = "km/h",
    val paceLabel: String = "Pace",
    val paceUnit: String = "min/km",
    val powerLabel: String = "Power",
    val powerUnit: String = "W",
    val cadenceLabel: String = "Cadence",
    val cadenceUnit: String = "rpm"
) {
    companion object {
        val Default: LiveExtremaLabels = LiveExtremaLabels()

        fun from(context: Context): LiveExtremaLabels {
            return LiveExtremaLabels(
                hrLabel = SensorType.HR.getShortName(context),
                hrUnit = context.getString(MyHelper.getUnitsId(SensorType.HR)),
                speedLabel = SensorType.SPEED_mps.getShortName(context),
                speedUnit = context.getString(MyHelper.getUnitsId(SensorType.SPEED_mps)),
                paceLabel = SensorType.PACE_spm.getShortName(context),
                paceUnit = context.getString(MyHelper.getUnitsId(SensorType.PACE_spm)),
                powerLabel = SensorType.POWER.getShortName(context),
                powerUnit = context.getString(MyHelper.getUnitsId(SensorType.POWER)),
                cadenceLabel = SensorType.CADENCE.getShortName(context),
                cadenceUnit = context.getString(MyHelper.getUnitsId(SensorType.CADENCE))
            )
        }
    }
}

/**
 * Dedicated O(1) in-memory reactive channel for live active workout card updates (`REQ-PER-047`).
 *
 * Updates strictly in memory at ~1 Hz without touching `WorkoutRepository._allWorkouts`,
 * without SQLite reloads, and without triggering downstream period/profile/location observers
 * (`REQ-STB-020`).
 */
class LiveWorkoutCardStateStore {

    private val lock = Any()
    private val _snapshot = MutableStateFlow<LiveWorkoutCardSnapshot?>(null)
    val snapshot: StateFlow<LiveWorkoutCardSnapshot?> = _snapshot.asStateFlow()

    private var activeSessionWorkoutId: Long = -1L
    private var isSessionPaused: Boolean = false

    private var hrSum: Double = 0.0
    private var hrCount: Long = 0L
    private var powerSum: Double = 0.0
    private var powerCount: Long = 0L
    private var cadenceSum: Double = 0.0
    private var cadenceCount: Long = 0L
    private val sessionLaps: MutableList<LapData> = mutableListOf()

    /**
     * Arms the live store for [workoutId] when tracking starts or resumes.
     * Clears any stale state belonging to a different workout without flashing zeroed metrics
     * before the first tick arrives.
     */
    fun onSessionStarted(workoutId: Long, isPaused: Boolean) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            if (activeSessionWorkoutId != workoutId) {
                resetAccumulatorsLocked()
                activeSessionWorkoutId = workoutId
                if (_snapshot.value?.workoutId != workoutId) {
                    _snapshot.value = null
                }
            }
            isSessionPaused = isPaused
            val current = _snapshot.value
            if (current != null && current.workoutId == workoutId && current.isPaused != isPaused) {
                _snapshot.value = current.copy(isPaused = isPaused)
            }
        }
    }

    /**
     * Returns `true` if [workoutId] is currently armed as the active tracking session.
     */
    fun isSessionActiveFor(workoutId: Long): Boolean {
        if (workoutId <= 0L) return false
        return synchronized(lock) { activeSessionWorkoutId == workoutId }
    }

    /**
     * Updates the pause state of an already-emitted live snapshot on `TRACKING <-> PAUSED` edges.
     */
    fun onPauseStateChanged(workoutId: Long, isPaused: Boolean) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            if (activeSessionWorkoutId != workoutId) return
            isSessionPaused = isPaused
            val current = _snapshot.value
            if (current != null && current.workoutId == workoutId && current.isPaused != isPaused) {
                _snapshot.value = current.copy(isPaused = isPaused)
            }
        }
    }

    /**
     * Updates the in-memory live snapshot in O(1) on each 1-Hz active tracking tick.
     *
     * When [authoritativeAvgHrBpm] / [authoritativeAvgPowerWatts] / [authoritativeAvgCadenceRpm]
     * are supplied (e.g., from periodic `LiveWorkoutSession` extrema flushes), the running
     * accumulators resync to those authoritative counts so resumed sessions remain exact.
     */
    fun onActiveTick(
        workoutId: Long,
        activeTimeSec: Long? = null,
        totalTimeSec: Long? = null,
        distanceM: Double? = null,
        avgSpeedMps: Double? = null,
        ascentM: Long? = null,
        descentM: Long? = null,
        hrSampleBpm: Double? = null,
        powerSampleWatts: Double? = null,
        cadenceSampleRpm: Double? = null,
        authoritativeAvgHrBpm: Double? = null,
        authoritativeAvgHrCount: Int? = null,
        authoritativeAvgPowerWatts: Double? = null,
        authoritativeAvgPowerCount: Int? = null,
        authoritativeAvgCadenceRpm: Double? = null,
        authoritativeAvgCadenceCount: Int? = null,
        requireActiveSession: Boolean = false
    ) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            if (requireActiveSession && activeSessionWorkoutId != workoutId) return
            if (activeSessionWorkoutId != workoutId) {
                resetAccumulatorsLocked()
                activeSessionWorkoutId = workoutId
            }
            isSessionPaused = false

            if (authoritativeAvgHrBpm != null && authoritativeAvgHrBpm.isFinite() && authoritativeAvgHrBpm > 0.0) {
                val count = (authoritativeAvgHrCount ?: 1).coerceAtLeast(1).toLong()
                hrSum = authoritativeAvgHrBpm * count
                hrCount = count
            } else if (hrSampleBpm != null && hrSampleBpm.isFinite() && hrSampleBpm > 0.0) {
                hrSum += hrSampleBpm
                hrCount += 1L
            }

            if (authoritativeAvgPowerWatts != null && authoritativeAvgPowerWatts.isFinite() && authoritativeAvgPowerWatts >= 0.0) {
                val count = (authoritativeAvgPowerCount ?: 1).coerceAtLeast(1).toLong()
                powerSum = authoritativeAvgPowerWatts * count
                powerCount = count
            } else if (powerSampleWatts != null && powerSampleWatts.isFinite() && powerSampleWatts >= 0.0) {
                powerSum += powerSampleWatts
                powerCount += 1L
            }

            if (authoritativeAvgCadenceRpm != null && authoritativeAvgCadenceRpm.isFinite() && authoritativeAvgCadenceRpm >= 0.0) {
                val count = (authoritativeAvgCadenceCount ?: 1).coerceAtLeast(1).toLong()
                cadenceSum = authoritativeAvgCadenceRpm * count
                cadenceCount = count
            } else if (cadenceSampleRpm != null && cadenceSampleRpm.isFinite() && cadenceSampleRpm >= 0.0) {
                cadenceSum += cadenceSampleRpm
                cadenceCount += 1L
            }

            val prev = _snapshot.value?.takeIf { it.workoutId == workoutId }
            val avgHr = if (hrCount > 0L) hrSum / hrCount else prev?.avgHeartRateBpm
            val avgPower = if (powerCount > 0L) powerSum / powerCount else prev?.avgPowerWatts
            val avgCadence = if (cadenceCount > 0L) cadenceSum / cadenceCount else prev?.avgCadenceRpm

            _snapshot.value = LiveWorkoutCardSnapshot(
                workoutId = workoutId,
                activeTimeSec = activeTimeSec ?: prev?.activeTimeSec,
                totalTimeSec = totalTimeSec ?: prev?.totalTimeSec,
                distanceM = distanceM ?: prev?.distanceM,
                avgSpeedMps = avgSpeedMps ?: prev?.avgSpeedMps,
                ascentM = ascentM ?: prev?.ascentM,
                descentM = descentM ?: prev?.descentM,
                avgHeartRateBpm = avgHr,
                avgPowerWatts = avgPower,
                avgCadenceRpm = avgCadence,
                laps = sessionLaps.toList(),
                isPaused = false
            )
        }
    }

    /**
     * Bridges `WorkoutRepository.recordLiveSample` [ContentValues] and [TrackerService.ExtremaUpdate]
     * payloads into [onActiveTick] strictly when [workoutId] is the active session.
     */
    fun onLiveSamplePersisted(
        workoutId: Long,
        summaryValues: ContentValues,
        samplingValues: ContentValues,
        extremaUpdates: List<TrackerService.ExtremaUpdate>
    ) {
        if (!isSessionActiveFor(workoutId)) return

        var authHr: Double? = null
        var authHrCount: Int? = null
        var authPower: Double? = null
        var authPowerCount: Int? = null
        var authCadence: Double? = null
        var authCadenceCount: Int? = null

        for (update in extremaUpdates) {
            if (update.extrema != ExtremaType.AVG) continue
            when (update.type) {
                SensorType.HR -> {
                    authHr = update.value
                    authHrCount = update.count
                }
                SensorType.POWER -> {
                    authPower = update.value
                    authPowerCount = update.count
                }
                SensorType.CADENCE -> {
                    authCadence = update.value
                    authCadenceCount = update.count
                }
                else -> Unit
            }
        }

        onActiveTick(
            workoutId = workoutId,
            activeTimeSec = extractLong(summaryValues, WorkoutSummaries.TIME_ACTIVE_s),
            totalTimeSec = extractLong(summaryValues, WorkoutSummaries.TIME_TOTAL_s),
            distanceM = extractDouble(summaryValues, WorkoutSummaries.DISTANCE_TOTAL_m),
            avgSpeedMps = extractDouble(summaryValues, WorkoutSummaries.SPEED_AVERAGE_mps),
            ascentM = extractRoundedLong(summaryValues, WorkoutSummaries.ASCENDING),
            descentM = extractRoundedLong(summaryValues, WorkoutSummaries.DESCENDING),
            hrSampleBpm = extractSensorSample(samplingValues, SensorType.HR),
            powerSampleWatts = extractSensorSample(samplingValues, SensorType.POWER),
            cadenceSampleRpm = extractSensorSample(samplingValues, SensorType.CADENCE),
            authoritativeAvgHrBpm = authHr,
            authoritativeAvgHrCount = authHrCount,
            authoritativeAvgPowerWatts = authPower,
            authoritativeAvgPowerCount = authPowerCount,
            authoritativeAvgCadenceRpm = authCadence,
            authoritativeAvgCadenceCount = authCadenceCount,
            requireActiveSession = true
        )
    }

    /**
     * Updates total elapsed time on 1-Hz paused ticks without advancing active time or other metrics.
     */
    fun onPausedTick(
        workoutId: Long,
        totalTimeSec: Long,
        requireActiveSession: Boolean = true
    ) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            if (requireActiveSession && activeSessionWorkoutId != workoutId) return
            if (activeSessionWorkoutId != workoutId) {
                resetAccumulatorsLocked()
                activeSessionWorkoutId = workoutId
            }
            isSessionPaused = true
            val prev = _snapshot.value?.takeIf { it.workoutId == workoutId }
            _snapshot.value = (prev ?: LiveWorkoutCardSnapshot(
                workoutId = workoutId,
                laps = sessionLaps.toList(),
                isPaused = true
            )).copy(
                totalTimeSec = totalTimeSec,
                isPaused = true
            )
        }
    }

    /**
     * Records a live lap split into the in-memory snapshot for [workoutId], deduplicating by [LapData.lapNr].
     */
    fun onLapSaved(
        workoutId: Long,
        lap: LapData,
        requireActiveSession: Boolean = true
    ) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            if (requireActiveSession && activeSessionWorkoutId != workoutId) return
            if (activeSessionWorkoutId != workoutId) {
                resetAccumulatorsLocked()
                activeSessionWorkoutId = workoutId
            }
            val existingIdx = sessionLaps.indexOfFirst { it.lapNr == lap.lapNr }
            if (existingIdx >= 0) {
                sessionLaps[existingIdx] = lap
            } else {
                sessionLaps.add(lap)
                sessionLaps.sortBy { it.lapNr }
            }
            val prev = _snapshot.value?.takeIf { it.workoutId == workoutId }
            _snapshot.value = (prev ?: LiveWorkoutCardSnapshot(
                workoutId = workoutId,
                isPaused = isSessionPaused
            )).copy(laps = sessionLaps.toList())
        }
    }

    /**
     * Clears the active session and resets [snapshot] to `null` when tracking stops or tears down.
     */
    fun clear() {
        synchronized(lock) {
            activeSessionWorkoutId = -1L
            isSessionPaused = false
            resetAccumulatorsLocked()
            _snapshot.value = null
        }
    }

    /**
     * Clears the active session strictly if it corresponds to [workoutId] (e.g., when a workout
     * is marked finished).
     */
    fun clearIfMatches(workoutId: Long) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            if (activeSessionWorkoutId == workoutId || _snapshot.value?.workoutId == workoutId) {
                activeSessionWorkoutId = -1L
                isSessionPaused = false
                resetAccumulatorsLocked()
                _snapshot.value = null
            }
        }
    }

    private fun resetAccumulatorsLocked() {
        hrSum = 0.0
        hrCount = 0L
        powerSum = 0.0
        powerCount = 0L
        cadenceSum = 0.0
        cadenceCount = 0L
        sessionLaps.clear()
    }

    companion object {
        val Default: LiveWorkoutCardStateStore = LiveWorkoutCardStateStore()

        val snapshot: StateFlow<LiveWorkoutCardSnapshot?>
            get() = Default.snapshot

        private fun extractLong(values: ContentValues, key: String): Long? {
            if (!values.containsKey(key)) return null
            val raw = values.get(key) ?: return null
            return when (raw) {
                is Number -> raw.toLong()
                else -> values.getAsLong(key)
            }
        }

        private fun extractRoundedLong(values: ContentValues, key: String): Long? {
            if (!values.containsKey(key)) return null
            val raw = values.get(key) ?: return null
            return when (raw) {
                is Double -> if (raw.isFinite()) raw.roundToLong() else null
                is Float -> if (raw.isFinite()) raw.toDouble().roundToLong() else null
                is Number -> raw.toLong()
                else -> values.getAsDouble(key)?.takeIf { it.isFinite() }?.roundToLong()
            }
        }

        private fun extractDouble(values: ContentValues, key: String): Double? {
            if (!values.containsKey(key)) return null
            val raw = values.get(key) ?: return null
            val parsed = when (raw) {
                is Number -> raw.toDouble()
                else -> values.getAsDouble(key)
            }
            return parsed?.takeIf { it.isFinite() }
        }

        private val LOCATION_PROVIDER_SUFFIXES = listOf("_gps", "_network", "_google_fused", "_att_fused")

        private fun extractSensorSample(values: ContentValues, sensorType: SensorType): Double? {
            val canonical = sensorType.name
            if (values.containsKey(canonical)) {
                return extractDouble(values, canonical)
            }
            for (suffix in LOCATION_PROVIDER_SUFFIXES) {
                val providerKey = "$canonical$suffix"
                if (values.containsKey(providerKey)) {
                    val candidate = extractDouble(values, providerKey)
                    if (candidate != null) return candidate
                }
            }
            val quotedPrefix = "\"$canonical ("
            for (key in values.keySet()) {
                if (key.startsWith(quotedPrefix) && key.endsWith(")\"")) {
                    val candidate = extractDouble(values, key)
                    if (candidate != null) return candidate
                }
            }
            return null
        }
    }
}

/**
 * Pure transformation overlaying a live [LiveWorkoutCardSnapshot] onto a card's [WorkoutData]
 * (`REQ-PER-047.2`, `REQ-PER-047.3`).
 *
 * Returns [workoutData] unchanged by reference whenever [snapshot] is `null`, the workout is
 * already finished, or [snapshot.workoutId] does not match [workoutData.id].
 */
fun applyLiveSnapshot(
    workoutData: WorkoutData,
    snapshot: LiveWorkoutCardSnapshot?,
    extremaLabels: LiveExtremaLabels = LiveExtremaLabels.Default,
    formatters: MetricFormatterContext = MetricFormatterContext()
): WorkoutData {
    if (snapshot == null || workoutData.finished || snapshot.workoutId != workoutData.id) {
        return workoutData
    }

    val mergedLaps = if (snapshot.laps.isEmpty()) {
        workoutData.laps
    } else {
        val byLapNr = LinkedHashMap<Long, LapData>(workoutData.laps.size + snapshot.laps.size)
        for (lap in workoutData.laps) {
            byLapNr[lap.lapNr] = lap
        }
        for (lap in snapshot.laps) {
            byLapNr[lap.lapNr] = lap
        }
        byLapNr.values.sortedBy { it.lapNr }
    }

    val validAvgSpeed = snapshot.avgSpeedMps?.takeIf { it.isFinite() && it > 0.001 }
    val validAvgHr = snapshot.avgHeartRateBpm?.takeIf { it.isFinite() && it > 0.0 }
    val validAvgPower = snapshot.avgPowerWatts?.takeIf { it.isFinite() && it >= 0.0 }
    val validAvgCadence = snapshot.avgCadenceRpm?.takeIf { it.isFinite() && it >= 0.0 }

    val mergedExtrema = if (validAvgHr == null && validAvgSpeed == null && validAvgPower == null && validAvgCadence == null) {
        workoutData.extremaRows
    } else {
        val rows = workoutData.extremaRows.toMutableList()
        if (validAvgHr != null) {
            upsertLiveExtremaAvgRow(
                rows = rows,
                sensorType = SensorType.HR,
                fallbackMatches = { it.sensorLabel == extremaLabels.hrLabel || it.iconResId == R.drawable.ic_heart_rate },
                sensorLabel = extremaLabels.hrLabel,
                unitLabel = extremaLabels.hrUnit,
                formattedAvg = SensorType.HR.myFormatter.format(validAvgHr),
                iconResId = R.drawable.ic_heart_rate,
                boldMax = true
            )
        }
        if (validAvgSpeed != null) {
            upsertLiveExtremaAvgRow(
                rows = rows,
                sensorType = SensorType.SPEED_mps,
                fallbackMatches = { it.sensorLabel == extremaLabels.speedLabel },
                sensorLabel = extremaLabels.speedLabel,
                unitLabel = extremaLabels.speedUnit,
                formattedAvg = formatters.speed.format(validAvgSpeed),
                iconResId = R.drawable.ic_speed,
                boldMax = false
            )
            if (workoutData.bSportType == BSportType.RUN) {
                upsertLiveExtremaAvgRow(
                    rows = rows,
                    sensorType = SensorType.PACE_spm,
                    fallbackMatches = { it.sensorLabel == extremaLabels.paceLabel },
                    sensorLabel = extremaLabels.paceLabel,
                    unitLabel = extremaLabels.paceUnit,
                    formattedAvg = formatters.pace.format(1.0 / validAvgSpeed),
                    iconResId = R.drawable.ic_speed,
                    boldMax = false
                )
            }
        }
        if (validAvgPower != null) {
            upsertLiveExtremaAvgRow(
                rows = rows,
                sensorType = SensorType.POWER,
                fallbackMatches = { it.sensorLabel == extremaLabels.powerLabel || it.iconResId == R.drawable.ic_power },
                sensorLabel = extremaLabels.powerLabel,
                unitLabel = extremaLabels.powerUnit,
                formattedAvg = SensorType.POWER.myFormatter.format(validAvgPower),
                iconResId = R.drawable.ic_power,
                boldMax = true
            )
        }
        if (validAvgCadence != null) {
            upsertLiveExtremaAvgRow(
                rows = rows,
                sensorType = SensorType.CADENCE,
                fallbackMatches = { it.sensorLabel == extremaLabels.cadenceLabel },
                sensorLabel = extremaLabels.cadenceLabel,
                unitLabel = extremaLabels.cadenceUnit,
                formattedAvg = SensorType.CADENCE.myFormatter.format(validAvgCadence),
                iconResId = R.drawable.ic_cadence,
                boldMax = false
            )
        }
        rows.toList()
    }

    return workoutData.copy(
        activeTimeSec = snapshot.activeTimeSec ?: workoutData.activeTimeSec,
        totalTimeSec = snapshot.totalTimeSec ?: workoutData.totalTimeSec,
        totalDistance = snapshot.distanceM ?: workoutData.totalDistance,
        avgSpeedMps = snapshot.avgSpeedMps ?: workoutData.avgSpeedMps,
        ascentMeters = snapshot.ascentM ?: workoutData.ascentMeters,
        descentMeters = snapshot.descentM ?: workoutData.descentMeters,
        extremaRows = mergedExtrema,
        laps = mergedLaps
    )
}

private fun upsertLiveExtremaAvgRow(
    rows: MutableList<ExtremaDataRow>,
    sensorType: SensorType,
    fallbackMatches: (ExtremaDataRow) -> Boolean,
    sensorLabel: String,
    unitLabel: String,
    formattedAvg: String,
    iconResId: Int,
    boldMax: Boolean
) {
    val existingIdx = rows.indexOfFirst { row ->
        row.sensorType == sensorType || (row.sensorType == null && fallbackMatches(row))
    }
    if (existingIdx >= 0) {
        rows[existingIdx] = rows[existingIdx].copy(avgValue = formattedAvg)
    } else {
        rows.add(
            ExtremaDataRow(
                sensorLabel = sensorLabel,
                unitLabel = unitLabel,
                minValue = null,
                avgValue = formattedAvg,
                maxValue = null,
                iconResId = iconResId,
                isMinRelevant = false,
                boldMin = false,
                boldAvg = true,
                boldMax = boldMax,
                sensorType = sensorType
            )
        )
    }
}
