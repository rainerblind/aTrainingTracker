/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.common.DateTimeUtils
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Stateless reactive in-memory projection of historical period summaries (`DAY`, `WEEK`, `MONTH`,
 * `YEAR`) derived directly from [WorkoutRepository.allWorkouts] on [Dispatchers.Default]
 * (`REQ-PER-056`, `ATT-8131`).
 *
 * Replaces the legacy `PeriodSummaries.db` SQLite materialized-view cache so every finished workout
 * change (`cropWorkout`, metric edit, sport change, delete, import, session finish) is reflected
 * immediately with zero stale cache drift, while `distinctUntilChangedBy` suppresses recomputations
 * on non-period edits (`REQ-PER-016.1`) and 1-Hz unfinished live ticks (`REQ-STB-020.3`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodsRepository @VisibleForTesting internal constructor(
    @Suppress("UNUSED_PARAMETER") application: Application,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val workoutRepo: WorkoutRepository = WorkoutRepository.getInstance(application),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val localeProvider: () -> Locale = { Locale.getDefault() }
) {

    internal val scope = CoroutineScope(defaultDispatcher + SupervisorJob())

    val groupedPeriods: StateFlow<List<List<PeriodSummary>>> = workoutRepo.allWorkouts
        .map { list -> list.filter { it.finished && it.startTimeS > 0L } }
        .distinctUntilChangedBy { finished -> finished.map(WorkoutData::toPeriodProjectionKey) }
        .mapLatest { finished ->
            buildGroupedPeriods(
                workouts = finished,
                zone = zoneProvider(),
                locale = localeProvider()
            )
        }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = listOf(emptyList(), emptyList(), emptyList(), emptyList())
        )

    /** Retained as constant `null` for UI/ViewModel contract compatibility (no SQLite migration required). */
    val migrationStatus: StateFlow<MigrationStatus?> = MutableStateFlow(null)

    /**
     * Returns all finished workouts within `[startS, endS]` sorted ascending by `startTimeS`,
     * served directly from [WorkoutRepository.allWorkouts] without SQLite cursors (`REQ-PER-056.2`).
     */
    fun getWorkoutsForRange(startS: Long, endS: Long): List<WorkoutData> =
        workoutRepo.allWorkouts.value
            .asSequence()
            .filter { it.finished && it.startTimeS in startS..endS }
            .sortedBy { it.startTimeS }
            .toList()

    /**
     * Pure in-memory aggregation of finished workouts into `[DAY, WEEK, MONTH, YEAR]` lists sorted
     * by `sortKey DESC` with DST-safe bounds (`REQ-PER-015`), active-locale formatting (`REQ-PER-028`),
     * bounded mini-map metadata (`REQ-PER-039`, `REQ-PER-046`), and contribution maps (`REQ-PER-025`, `REQ-PER-027`).
     */
    @VisibleForTesting
    internal fun buildGroupedPeriods(
        workouts: List<WorkoutData>,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<List<PeriodSummary>> {
        val validWorkouts = workouts.filter { it.finished && it.startTimeS > 0L }
        if (validWorkouts.isEmpty()) {
            return listOf(emptyList(), emptyList(), emptyList(), emptyList())
        }
        val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val yearFormatter = DateTimeFormatter.ofPattern("yyyy", locale)

        return listOf(
            PeriodType.DAY,
            PeriodType.WEEK,
            PeriodType.MONTH,
            PeriodType.YEAR
        ).map { periodType ->
            validWorkouts
                .groupBy { w -> getPeriodSortKey(w.startTimeS, periodType, zone) }
                .entries
                .sortedByDescending { it.key }
                .map { (sortKey, groupWorkouts) ->
                    aggregateWorkoutsToPeriod(
                        workouts = groupWorkouts,
                        type = periodType,
                        sortKey = sortKey,
                        zone = zone,
                        locale = locale,
                        dayFormatter = dayFormatter,
                        monthFormatter = monthFormatter,
                        yearFormatter = yearFormatter
                    )
                }
        }
    }

    private fun aggregateWorkoutsToPeriod(
        workouts: List<WorkoutData>,
        type: PeriodType,
        sortKey: String,
        zone: ZoneId,
        locale: Locale,
        dayFormatter: DateTimeFormatter,
        monthFormatter: DateTimeFormatter,
        yearFormatter: DateTimeFormatter
    ): PeriodSummary {
        val sampleDate = Instant.ofEpochSecond(workouts.first().startTimeS).atZone(zone).toLocalDate()
        val (startS, endS, label, dateRange) = computePeriodRangeAndLabels(
            date = sampleDate,
            type = type,
            zone = zone,
            locale = locale,
            dayFormatter = dayFormatter,
            monthFormatter = monthFormatter,
            yearFormatter = yearFormatter
        )

        val bounds = calculateWorkoutsBounds(workouts)
        val longest = workouts.maxByOrNull { it.activeTimeSec } ?: workouts.first()
        val north = workouts.maxByOrNull { it.maxLat ?: -90.0 } ?: longest
        val south = workouts.minByOrNull { it.minLat ?: 90.0 } ?: longest
        val east = workouts.maxByOrNull { it.maxLng ?: -180.0 } ?: longest
        val west = workouts.minByOrNull { it.minLng ?: 180.0 } ?: longest

        val sportStats = workouts.groupBy { it.bSportType }.mapValues { (_, sportWorkouts) ->
            val detailed = sportWorkouts.groupBy { it.sportName }.mapValues { (_, detW) ->
                DetailedStats(
                    count = detW.size,
                    totalDurationSec = detW.sumOf { it.activeTimeSec },
                    totalDistanceMeters = detW.sumOf { it.totalDistance },
                    totalAscentMeters = detW.sumOf { it.ascentMeters }
                )
            }
            val longestInSport = sportWorkouts.maxByOrNull { it.activeTimeSec } ?: sportWorkouts.first()
            SportStats(
                count = sportWorkouts.size,
                totalDurationSec = sportWorkouts.sumOf { it.activeTimeSec },
                totalDistanceMeters = sportWorkouts.sumOf { it.totalDistance },
                totalAscentMeters = sportWorkouts.sumOf { it.ascentMeters },
                detailedSportStats = detailed,
                longestWorkout = LongestWorkout(
                    id = longestInSport.id,
                    name = longestInSport.workoutName,
                    durationSec = longestInSport.activeTimeSec,
                    distanceMeters = longestInSport.totalDistance,
                    ascentMeters = longestInSport.ascentMeters
                )
            )
        }

        val base = PeriodSummary(
            periodLabel = label,
            periodDateRange = dateRange,
            periodType = type,
            startTimestampS = startS,
            endTimestampS = endS,
            totalWorkouts = workouts.size,
            totalDurationSec = workouts.sumOf { it.activeTimeSec },
            totalDistance = workouts.sumOf { it.totalDistance },
            sportStats = sportStats,
            sortKey = sortKey,
            minLat = bounds?.southwest?.latitude ?: 90.0,
            minLng = bounds?.southwest?.longitude ?: 180.0,
            maxLat = bounds?.northeast?.latitude ?: -90.0,
            maxLng = bounds?.northeast?.longitude ?: -180.0,
            longestId = longest.id,
            longestDurationS = longest.activeTimeSec,
            northId = north.id,
            southId = south.id,
            eastId = east.id,
            westId = west.id
        )
        return enrich(base, workouts, zone)
    }

    internal fun enrich(
        summary: PeriodSummary,
        groupWorkouts: List<WorkoutData>,
        zone: ZoneId = zoneProvider()
    ): PeriodSummary {
        val trueLongestId = if (groupWorkouts.isNotEmpty()) groupWorkouts.maxByOrNull { it.activeTimeSec }?.id else summary.longestId
        val anchorIds = setOf(trueLongestId ?: summary.longestId, summary.northId, summary.southId, summary.eastId, summary.westId)
            .filter { it != -1L }
        val anchorWorkouts = if (groupWorkouts.isNotEmpty()) groupWorkouts.filter { it.id in anchorIds } else emptyList()

        val regionPreviews = if (groupWorkouts.isNotEmpty()) buildRegionPreviews(groupWorkouts) else summary.regionSummaries
        val resolvedPrimary = resolvePrimaryRegionForSports(regionPreviews, sports = null)
        val curatedPolylines = when {
            resolvedPrimary != null && resolvedPrimary.second.isNotEmpty() -> resolvedPrimary.second
            groupWorkouts.isNotEmpty() -> groupWorkouts
                .asSequence()
                .filter { it.mapPolyline.isNotEmpty() }
                .sortedByDescending { it.startTimeS }
                .take(MAX_SUMMARY_THUMBNAIL_TRACKS)
                .map { it.mapPolyline }
                .toList()
            summary.polylines.isNotEmpty() -> summary.polylines.take(MAX_SUMMARY_THUMBNAIL_TRACKS)
            else -> emptyList()
        }

        val anchorPolylineMap = if (groupWorkouts.isNotEmpty()) {
            anchorWorkouts.filter { it.mapPolyline.isNotEmpty() }.associate { it.id to it.mapPolyline }
        } else {
            summary.workoutIdToPolylineMap.filterKeys { it in anchorIds }
        }

        val enrichedSportStats = summary.sportStats.mapValues { (sport, stats) ->
            val longestWorkoutData = if (groupWorkouts.isNotEmpty()) {
                groupWorkouts.filter { it.bSportType == sport }.maxByOrNull { it.activeTimeSec }
            } else {
                val targetId = stats.longestWorkout?.id ?: -1L
                if (targetId != -1L) workoutRepo.allWorkouts.value.find { it.id == targetId } else null
            }
            if (longestWorkoutData != null) {
                stats.copy(
                    longestWorkout = LongestWorkout(
                        id = longestWorkoutData.id,
                        name = longestWorkoutData.workoutName,
                        durationSec = longestWorkoutData.activeTimeSec,
                        distanceMeters = longestWorkoutData.totalDistance,
                        ascentMeters = longestWorkoutData.ascentMeters
                    )
                )
            } else {
                stats
            }
        }

        val yearlyContributions = if (summary.periodType == PeriodType.YEAR && groupWorkouts.isNotEmpty()) {
            val calendarYear = Instant.ofEpochSecond(summary.startTimestampS).atZone(zone).year
            buildDailySportActivityMaps(groupWorkouts, zone).mapValues { (sport, agg) ->
                YearlySportContribution(
                    sportType = sport,
                    year = calendarYear,
                    dailyActivities = agg.dailyActivities,
                    maxDailyDurationSec = agg.maxDurationSec,
                    maxDailyDistanceMeters = agg.maxDistanceMeters,
                    maxDailyAscentMeters = agg.maxAscentMeters
                )
            }
        } else {
            summary.yearlySportContributions
        }

        val dailyContributions = if ((summary.periodType == PeriodType.WEEK || summary.periodType == PeriodType.MONTH) && groupWorkouts.isNotEmpty()) {
            buildDailySportActivityMaps(groupWorkouts, zone).mapValues { (sport, agg) ->
                DailySportContribution(
                    sportType = sport,
                    dailyActivities = agg.dailyActivities,
                    maxDailyDurationSec = agg.maxDurationSec,
                    maxDailyDistanceMeters = agg.maxDistanceMeters,
                    maxDailyAscentMeters = agg.maxAscentMeters
                )
            }
        } else {
            summary.dailySportContributions
        }

        return summary.copy(
            sportStats = enrichedSportStats,
            polylines = curatedPolylines,
            workoutIdToPolylineMap = anchorPolylineMap,
            workoutIdToAllPolylinesMap = emptyMap(),
            workoutIdToPathMap = emptyMap(),
            workoutIdToSportMap = if (groupWorkouts.isNotEmpty()) groupWorkouts.associate { it.id to it.bSportType } else summary.workoutIdToSportMap,
            yearlySportContributions = yearlyContributions,
            dailySportContributions = dailyContributions,
            regionSummaries = regionPreviews,
            primaryRegionSummary = resolvedPrimary?.first ?: summary.primaryRegionSummary,
            extremaMarkers = anchorWorkouts.flatMap { workout ->
                buildList {
                    workout.startLatLng?.let { add(PeriodPeakMarker(workout.id, it, R.drawable.control_start, "${workout.workoutName}: Start", PeriodMarkerType.START)) }
                    workout.endLatLng?.let { add(PeriodPeakMarker(workout.id, it, R.drawable.control_stop, "${workout.workoutName}: End", PeriodMarkerType.END)) }
                    workout.maxDisplacementLatLng?.let { add(PeriodPeakMarker(workout.id, it, R.drawable.ic_distance, "${workout.workoutName}: Apex", PeriodMarkerType.DISTANCE)) }
                    workout.maxAltitudeLatLng?.let { add(PeriodPeakMarker(workout.id, it, R.drawable.ic_altitude, "${workout.workoutName}: Max Altitude", PeriodMarkerType.ALTITUDE)) }
                }
            }
        )
    }

    private fun buildDailySportActivityMaps(
        workouts: List<WorkoutData>,
        zone: ZoneId
    ): Map<BSportType, DailySportAggregate> {
        return workouts.groupBy { it.bSportType }.mapValues { (sport, sportWorkouts) ->
            val dailyMap = linkedMapOf<LocalDate, DailySportActivity>()
            val byDate = sportWorkouts.groupBy { w ->
                if (w.startTimeS > 0L) Instant.ofEpochSecond(w.startTimeS).atZone(zone).toLocalDate()
                else w.localDateTime.toLocalDate()
            }
            for ((date, dayWorkouts) in byDate) {
                dailyMap[date] = DailySportActivity(
                    date = date,
                    bSportType = sport,
                    count = dayWorkouts.size,
                    durationSec = dayWorkouts.sumOf { it.activeTimeSec },
                    distanceMeters = dayWorkouts.sumOf { it.totalDistance },
                    ascentMeters = dayWorkouts.sumOf { it.ascentMeters },
                    workoutIds = dayWorkouts.map { it.id },
                    workoutNames = dayWorkouts.map { it.workoutName }
                )
            }
            DailySportAggregate(
                dailyActivities = dailyMap,
                maxDurationSec = dailyMap.values.maxOfOrNull { it.durationSec } ?: 0L,
                maxDistanceMeters = dailyMap.values.maxOfOrNull { it.distanceMeters } ?: 0.0,
                maxAscentMeters = dailyMap.values.maxOfOrNull { it.ascentMeters } ?: 0L
            )
        }
    }

    private fun computePeriodRangeAndLabels(
        date: LocalDate,
        type: PeriodType,
        zone: ZoneId,
        locale: Locale,
        dayFormatter: DateTimeFormatter,
        monthFormatter: DateTimeFormatter,
        yearFormatter: DateTimeFormatter
    ): PeriodMetadata = when (type) {
        PeriodType.DAY -> {
            val startS = date.atStartOfDay(zone).toEpochSecond()
            PeriodMetadata(startS, calculateDayEnd(startS, zone), date.format(dayFormatter), "")
        }
        PeriodType.WEEK -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val startS = monday.atStartOfDay(zone).toEpochSecond()
            val endS = calculateWeekEnd(startS, zone)
            val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val weekYear = date.get(IsoFields.WEEK_BASED_YEAR)
            PeriodMetadata(
                startS = startS,
                endS = endS,
                label = "$weekYear-W${week.toString().padStart(2, '0')}",
                dateRange = DateTimeUtils.formatPeriodDateRange(startS, endS, locale, zone)
            )
        }
        PeriodType.MONTH -> {
            val firstDay = date.with(TemporalAdjusters.firstDayOfMonth())
            val startS = firstDay.atStartOfDay(zone).toEpochSecond()
            val endS = firstDay.plusMonths(1).atStartOfDay(zone).toEpochSecond() - 1
            PeriodMetadata(startS, endS, firstDay.format(monthFormatter), "")
        }
        PeriodType.YEAR -> {
            val firstDay = LocalDate.of(date.year, 1, 1)
            val startS = firstDay.atStartOfDay(zone).toEpochSecond()
            val endS = firstDay.plusYears(1).atStartOfDay(zone).toEpochSecond() - 1
            PeriodMetadata(startS, endS, firstDay.format(yearFormatter), "")
        }
    }

    private fun calculateWorkoutsBounds(workouts: List<WorkoutData>): LatLngBounds? {
        val builder = LatLngBounds.Builder()
        var hasPoints = false
        workouts.forEach { w ->
            if (w.minLat != null && w.maxLat != null && w.minLng != null && w.maxLng != null && w.minLat < 90.0) {
                builder.include(LatLng(w.minLat, w.minLng))
                builder.include(LatLng(w.maxLat, w.maxLng))
                hasPoints = true
            } else {
                w.startLatLng?.let {
                    if (it.latitude != 0.0 || it.longitude != 0.0) {
                        builder.include(it)
                        hasPoints = true
                    }
                }
            }
        }
        return if (hasPoints) {
            try {
                builder.build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build period LatLngBounds", e)
                null
            }
        } else {
            null
        }
    }

    private data class PeriodMetadata(
        val startS: Long,
        val endS: Long,
        val label: String,
        val dateRange: String
    )

    private data class DailySportAggregate(
        val dailyActivities: Map<LocalDate, DailySportActivity>,
        val maxDurationSec: Long,
        val maxDistanceMeters: Double,
        val maxAscentMeters: Long
    )

    /**
     * Lightweight projection key containing only properties that impact historical period
     * aggregation and card statistics (`REQ-PER-016.1`, `REQ-STB-020.3`).
     */
    internal data class PeriodProjectionKey(
        val id: Long,
        val finished: Boolean,
        val startTimeS: Long,
        val activeTimeSec: Long,
        val totalTimeSec: Long,
        val totalDistance: Double,
        val ascentMeters: Long,
        val sportId: Long,
        val bSportType: BSportType,
        val minLat: Double?,
        val minLng: Double?,
        val maxLat: Double?,
        val maxLng: Double?,
        val polylineHash: Int
    )

    companion object {
        private const val TAG = "PeriodsRepository"
        const val PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED = "period_summaries_db_decommissioned_cleaned"
        private const val OBSOLETE_DB_PREFIX = "PeriodSummaries"

        @Volatile
        private var instance: PeriodsRepository? = null

        @JvmStatic
        fun getInstance(context: Context): PeriodsRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val app = (context as? Application) ?: (context.applicationContext as? Application)
                        ?: error("Context must be or provide an Application")
                    PeriodsRepository(app).also { instance = it }
                }
            }
        }

        @VisibleForTesting
        fun resetInstanceForTesting() {
            try {
                instance?.scope?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling scope during resetInstanceForTesting", e)
            }
            instance = null
        }

        @VisibleForTesting
        fun setInstanceForTesting(repo: PeriodsRepository?) {
            if (repo == null) {
                try {
                    instance?.scope?.cancel()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cancelling scope during setInstanceForTesting", e)
                }
            }
            instance = repo
        }

        @VisibleForTesting
        @JvmStatic
        @JvmOverloads
        fun getPeriodSortKey(
            startTimestampS: Long,
            type: PeriodType,
            zone: ZoneId = ZoneId.systemDefault()
        ): String {
            val dt = Instant.ofEpochSecond(startTimestampS).atZone(zone)
            return when (type) {
                PeriodType.DAY -> dt.format(DateTimeFormatter.ISO_LOCAL_DATE)
                PeriodType.WEEK -> {
                    val week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    val year = dt.get(IsoFields.WEEK_BASED_YEAR)
                    "$year-W${week.toString().padStart(2, '0')}"
                }
                PeriodType.MONTH -> dt.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT))
                PeriodType.YEAR -> dt.year.toString()
            }
        }

        @VisibleForTesting
        fun calculateDayEnd(startS: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochSecond(startS).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond() - 1

        @VisibleForTesting
        fun calculateWeekEnd(startS: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochSecond(startS).atZone(zone).toLocalDate().plusWeeks(1).atStartOfDay(zone).toEpochSecond() - 1

        /**
         * Deletes obsolete `PeriodSummaries*` SQLite files (`PeriodSummaries.db`, `-wal`, `-shm`,
         * `-journal`, and legacy `PeriodSummaries_v*.db*`) from the app's `databases/` directory
         * (`REQ-PER-056.4`). Gated by [PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED] unless
         * [force] is `true` (used post-restore in `MigrationEngine`).
         */
        @JvmStatic
        @JvmOverloads
        fun cleanupObsoletePeriodDatabases(context: Context, force: Boolean = false): Int {
            val prefs = try {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open SharedPreferences for obsolete PeriodSummaries cleanup", e)
                null
            }
            if (!force && prefs?.getBoolean(PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, false) == true) {
                return 0
            }

            var deletedCount = 0
            var allDeleted = true
            try {
                val databasesDir = context.applicationInfo?.dataDir
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it, "databases") }
                    ?: context.getDatabasePath("${OBSOLETE_DB_PREFIX}.db")?.parentFile
                if (databasesDir != null && databasesDir.isDirectory) {
                    val obsoleteFiles = databasesDir.listFiles { f ->
                        f.isFile && f.name.startsWith(OBSOLETE_DB_PREFIX)
                    } ?: emptyArray()
                    for (file in obsoleteFiles) {
                        if (file.delete()) {
                            deletedCount++
                            Log.i(TAG, "Deleted obsolete period summary file: ${file.name}")
                        } else {
                            allDeleted = false
                            Log.w(TAG, "Failed to delete obsolete period summary file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                allDeleted = false
                Log.w(TAG, "Error deleting obsolete PeriodSummaries database files", e)
            }

            if (allDeleted) {
                try {
                    prefs?.edit()?.putBoolean(PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, true)?.apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist obsolete PeriodSummaries cleanup flag", e)
                }
            }
            return deletedCount
        }

        /**
         * Launches [cleanupObsoletePeriodDatabases] asynchronously on a background thread when the
         * one-time cleanup flag has not yet been set (`REQ-PER-056.4`).
         */
        @JvmStatic
        fun cleanupObsoletePeriodDatabasesAsync(context: Context) {
            val appContext = context.applicationContext ?: context
            val alreadyCleaned = try {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
                    .getBoolean(PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED, false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read obsolete PeriodSummaries cleanup flag", e)
                false
            }
            if (alreadyCleaned) return

            Thread({
                cleanupObsoletePeriodDatabases(appContext, force = false)
            }, "PeriodSummariesCleanup").start()
        }
    }
}

/**
 * Maps [WorkoutData] to its period-relevant projection key (`REQ-PER-016.1`, `REQ-STB-020.3`).
 */
internal fun WorkoutData.toPeriodProjectionKey(): PeriodsRepository.PeriodProjectionKey =
    PeriodsRepository.PeriodProjectionKey(
        id = id,
        finished = finished,
        startTimeS = startTimeS,
        activeTimeSec = activeTimeSec,
        totalTimeSec = totalTimeSec,
        totalDistance = totalDistance,
        ascentMeters = ascentMeters,
        sportId = sportId,
        bSportType = bSportType,
        minLat = minLat,
        minLng = minLng,
        maxLat = maxLat,
        maxLng = maxLng,
        polylineHash = mapPolyline.hashCode()
    )
