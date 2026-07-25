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
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Acts as the single source of truth for period-level data.
 * Orchestrates hierarchical synchronization between workout history and the persistent Periods database.
 */
class PeriodsRepository private constructor(private val application: Application) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dbManager = PeriodSummariesDatabaseManager.getInstance(application)
    private val workoutRepo = WorkoutRepository.getInstance(application)
    private val workoutSummariesManager = WorkoutSummariesDatabaseManager.getInstance(application)

    private val _groupedPeriods = MutableStateFlow<List<List<PeriodSummary>>>(listOf(emptyList(), emptyList(), emptyList(), emptyList()))
    val groupedPeriods: StateFlow<List<List<PeriodSummary>>> = _groupedPeriods.asStateFlow()

    private val _migrationProgress = MutableStateFlow<Float?>(null)
    val migrationProgress: StateFlow<Float?> = _migrationProgress.asStateFlow()

    private val rebuildMutex = Mutex()

    private val dayFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")

    companion object {
        private const val TAG = "PeriodsRepository"
        @Volatile
        private var instance: PeriodsRepository? = null

        @JvmStatic
        fun getInstance(application: Application): PeriodsRepository {
            return instance ?: synchronized(this) {
                instance ?: PeriodsRepository(application).also { instance = it }
            }
        }
    }

    init {
        // 1. Instant load from cache
        loadFromDatabase()

        // 2. Initial Migration (Prioritized Hierarchical Scan)
        scope.launch {
            val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
            if (!isFinished) {
                performHierarchicalMigration()
            }
        }
    }

    /**
     * ATT-346: Hierarchical aggregation (Workout -> Day -> Week/Month -> Year).
     * Processes history in newest-first month buckets for instant UI feedback.
     */
    private suspend fun performHierarchicalMigration() = withContext(Dispatchers.Default) {
        rebuildMutex.withLock {
            _migrationProgress.value = 0.01f
            Log.i(TAG, "Starting Hierarchical Initial Migration.")
            val startTime = System.currentTimeMillis()

            val mapper = com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper(
                application, workoutSummariesManager,
                com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(application),
                com.atrainingtracker.trainingtracker.database.EquipmentDbHelper(application),
                com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper(application)
            )

            withContext(Dispatchers.IO) {
                val cursor = workoutSummariesManager.getCursorForAllWorkouts()
                if (cursor.count == 0) {
                    dbManager.runInTransaction { db -> dbManager.setSyncFinished(db, true) }
                    cursor.close()
                    return@withContext
                }

                val allWorkouts = mutableListOf<WorkoutData>()
                while (cursor.moveToNext()) { allWorkouts.add(mapper.fromCursor(cursor)) }
                cursor.close()

                val monthBuckets = allWorkouts.groupBy { it.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM")) }
                    .toList().sortedByDescending { it.first }

                dbManager.runInTransaction { db ->
                    dbManager.deleteAll(db)
                    dbManager.setSyncFinished(db, false)

                    monthBuckets.forEachIndexed { index, (_, workouts) ->
                        // 1. Build DAYS from Workouts
                        val daysInMonth = workouts.groupBy { 
                            it.localDateTime.toLocalDate().atStartOfDay().toEpochSecond(OffsetDateTime.now().offset) 
                        }
                        daysInMonth.forEach { (startS, dayWorkouts) ->
                            val daySummary = aggregateWorkoutsToDay(dayWorkouts, startS)
                            dbManager.upsertPeriod(db, daySummary)
                        }

                        // 2. Roll up WEEKs and MONTH from these DAYs
                        val firstW = workouts.first()
                        val monthStart = firstW.localDateTime.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().toEpochSecond(OffsetDateTime.now().offset)
                        val monthEnd = firstW.localDateTime.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).toEpochSecond(OffsetDateTime.now().offset)
                        rollupDaysToParentPeriods(db, monthStart, monthEnd)

                        _migrationProgress.value = (index + 1).toFloat() / monthBuckets.size.toFloat()
                        // Periodic UI Update
                        loadFromDatabase()
                    }

                    // 3. Final Step: Roll up YEARs from MONTHs
                    rollupMonthsToYears(db)
                    dbManager.setSyncFinished(db, true)
                }
            }
            Log.i(TAG, "Hierarchical Migration finished in ${System.currentTimeMillis() - startTime}ms.")
            _migrationProgress.value = null
            loadFromDatabase()
        }
    }

    /**
     * O(1) Hierarchical Add: Workout -> Day -> Week/Month -> Year.
     */
    fun onWorkoutFinished(workout: WorkoutData) {
        scope.launch {
            val zoneOffset = OffsetDateTime.now().offset
            val ldt = workout.localDateTime
            val dayStart = ldt.toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)

            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    // 1. Update Day
                    val workoutsInDay = fetchWorkoutsInDay(dayStart)
                    dbManager.upsertPeriod(db, aggregateWorkoutsToDay(workoutsInDay, dayStart))

                    // 2. Propagate Upwards
                    rollupDayToParents(db, ldt, zoneOffset)
                }
            }
            loadFromDatabase()
        }
    }

    /**
     * Hierarchical Deletion: Recalculate Day -> Propagate Up.
     */
    fun onWorkoutDeleted(workout: WorkoutData) {
        scope.launch {
            val zoneOffset = OffsetDateTime.now().offset
            val ldt = workout.localDateTime
            val dayStart = ldt.toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)

            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    val workoutsRemaining = fetchWorkoutsInDay(dayStart)
                    if (workoutsRemaining.isEmpty()) {
                        dbManager.deletePeriod(db, PeriodType.DAY, dayStart)
                    } else {
                        dbManager.upsertPeriod(db, aggregateWorkoutsToDay(workoutsRemaining, dayStart))
                    }
                    rollupDayToParents(db, ldt, zoneOffset)
                }
            }
            loadFromDatabase()
        }
    }

    /**
     * Handles sport changes by recalculating the affected hierarchy.
     */
    fun onWorkoutSportChanged(newW: WorkoutData, oldW: WorkoutData) {
        onWorkoutFinished(newW) // Logic is identical: recalculate the affected Day and roll up
    }

    private fun rollupDayToParents(db: android.database.sqlite.SQLiteDatabase, ldt: java.time.LocalDateTime, offset: java.time.ZoneOffset) {
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(offset)
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().toEpochSecond(offset)
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().toEpochSecond(offset)

        // 1. Recalculate Week from Days
        val weekEnd = weekStart + (7 * 86400) - 1
        val daysInWeek = dbManager.getSummariesInRange(PeriodType.DAY, weekStart, weekEnd)
        if (daysInWeek.isEmpty()) dbManager.deletePeriod(db, PeriodType.WEEK, weekStart)
        else dbManager.upsertPeriod(db, aggregateChildrenToParent(daysInWeek, PeriodType.WEEK, weekStart, weekEnd))

        // 2. Recalculate Month from Days
        val monthEnd = ldt.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).toEpochSecond(offset)
        val daysInMonth = dbManager.getSummariesInRange(PeriodType.DAY, monthStart, monthEnd)
        if (daysInMonth.isEmpty()) dbManager.deletePeriod(db, PeriodType.MONTH, monthStart)
        else dbManager.upsertPeriod(db, aggregateChildrenToParent(daysInMonth, PeriodType.MONTH, monthStart, monthEnd))

        // 3. Recalculate Year from Months
        val yearEnd = ldt.with(TemporalAdjusters.lastDayOfYear()).toLocalDate().atTime(23, 59, 59).toEpochSecond(offset)
        val monthsInYear = dbManager.getSummariesInRange(PeriodType.MONTH, yearStart, yearEnd)
        if (monthsInYear.isEmpty()) dbManager.deletePeriod(db, PeriodType.YEAR, yearStart)
        else dbManager.upsertPeriod(db, aggregateChildrenToParent(monthsInYear, PeriodType.YEAR, yearStart, yearEnd))
    }

    private fun rollupDaysToParentPeriods(db: android.database.sqlite.SQLiteDatabase, startS: Long, endS: Long) {
        val days = dbManager.getSummariesInRange(PeriodType.DAY, startS, endS)
        if (days.isEmpty()) return

        // Month Rollup
        dbManager.upsertPeriod(db, aggregateChildrenToParent(days, PeriodType.MONTH, startS, endS))

        // Week Rollups
        val weekStarts = days.map { OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(it.startTimestampS), java.time.ZoneId.systemDefault()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(OffsetDateTime.now().offset) }.distinct()
        weekStarts.forEach { ws ->
            val daysInWeek = dbManager.getSummariesInRange(PeriodType.DAY, ws, ws + (7 * 86400) - 1)
            dbManager.upsertPeriod(db, aggregateChildrenToParent(daysInWeek, PeriodType.WEEK, ws, ws + (7 * 86400) - 1))
        }
    }

    private fun rollupMonthsToYears(db: android.database.sqlite.SQLiteDatabase) {
        val months = dbManager.getPeriodsByType(PeriodType.MONTH)
        val yearGroups = months.groupBy { OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(it.startTimestampS), java.time.ZoneId.systemDefault()).year }
        yearGroups.forEach { (year, yearMonths) ->
            val start = java.time.LocalDate.of(year, 1, 1).atStartOfDay().toEpochSecond(OffsetDateTime.now().offset)
            val end = java.time.LocalDate.of(year, 12, 31).atTime(23, 59, 59).toEpochSecond(OffsetDateTime.now().offset)
            dbManager.upsertPeriod(db, aggregateChildrenToParent(yearMonths, PeriodType.YEAR, start, end))
        }
    }

    private fun aggregateWorkoutsToDay(workouts: List<WorkoutData>, startS: Long): PeriodSummary {
        val w = workouts.first()
        val bounds = calculateWorkoutsBounds(workouts)
        val longest = workouts.maxBy { it.activeTimeSec }
        
        val sportStats = workouts.groupBy { it.bSportType }.mapValues { (_, sportWorkouts) ->
            val detailed = sportWorkouts.groupBy { it.sportName }.mapValues { (_, detW) ->
                DetailedStats(detW.size, detW.sumOf { it.activeTimeSec }, detW.sumOf { it.totalDistance }, detW.sumOf { it.ascentMeters })
            }
            val l = sportWorkouts.maxBy { it.activeTimeSec }
            SportStats(sportWorkouts.size, sportWorkouts.sumOf { it.activeTimeSec }, sportWorkouts.sumOf { it.totalDistance }, sportWorkouts.sumOf { it.ascentMeters }, detailed, LongestWorkout(l.id, l.workoutName, l.activeTimeSec, l.totalDistance, l.ascentMeters))
        }

        return PeriodSummary(
            periodLabel = w.localDateTime.format(dayFormatter), periodDateRange = "", periodType = PeriodType.DAY,
            startTimestampS = startS, endTimestampS = startS + 86399, totalWorkouts = workouts.size,
            totalDurationSec = workouts.sumOf { it.activeTimeSec }, totalDistance = workouts.sumOf { it.totalDistance },
            sportStats = sportStats, sortKey = w.localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),
            minLat = bounds.southwest.latitude, minLng = bounds.southwest.longitude, maxLat = bounds.northeast.latitude, maxLng = bounds.northeast.longitude,
            longestId = longest.id, longestDurationS = longest.activeTimeSec,
            northId = longest.id, southId = longest.id, eastId = longest.id, westId = longest.id
        )
    }

    private fun aggregateChildrenToParent(children: List<PeriodSummary>, type: PeriodType, start: Long, end: Long): PeriodSummary {
        val totalWorkouts = children.sumOf { it.totalWorkouts }
        val totalTime = children.sumOf { it.totalDurationSec }
        val totalDist = children.sumOf { it.totalDistance }
        
        // Merge Sport Stats
        val allSports = children.flatMap { it.sportStats.keys }.distinct()
        val mergedSportStats = allSports.associateWith { sport ->
            val sportChildren = children.mapNotNull { it.sportStats[sport] }
            val allDetailedNames = sportChildren.flatMap { it.detailedSportStats.keys }.distinct()
            val mergedDetailed = allDetailedNames.associateWith { name ->
                val detChildren = sportChildren.mapNotNull { it.detailedSportStats[name] }
                DetailedStats(detChildren.sumOf { it.count }, detChildren.sumOf { it.totalDurationSec }, detChildren.sumOf { it.totalDistanceMeters }, detChildren.sumOf { it.totalAscentMeters })
            }
            val longestInSport = sportChildren.mapNotNull { it.longestWorkout }.maxByOrNull { it.durationSec }
            SportStats(sportChildren.sumOf { it.count }, sportChildren.sumOf { it.totalDurationSec }, sportChildren.sumOf { it.totalDistanceMeters }, sportChildren.sumOf { it.totalAscentMeters }, mergedDetailed, longestInSport)
        }

        val longest = children.maxBy { it.longestDurationS }
        val minLat = children.minOf { it.minLat }; val maxLat = children.maxOf { it.maxLat }
        val minLng = children.minOf { it.minLng }; val maxLng = children.maxOf { it.maxLng }

        val label = when(type) {
            PeriodType.WEEK -> "Week ${OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault()).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
            PeriodType.MONTH -> OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault()).format(monthFormatter)
            PeriodType.YEAR -> OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault()).format(yearFormatter)
            else -> ""
        }

        return PeriodSummary(
            periodLabel = label, periodDateRange = "", periodType = type,
            startTimestampS = start, endTimestampS = end, totalWorkouts = totalWorkouts,
            totalDurationSec = totalTime, totalDistance = totalDist, sportStats = mergedSportStats,
            sortKey = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE),
            minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng,
            longestId = longest.longestId, longestDurationS = longest.longestDurationS,
            northId = longest.longestId, southId = longest.longestId, eastId = longest.longestId, westId = longest.longestId
        )
    }

    private fun fetchWorkoutsInDay(startS: Long): List<WorkoutData> {
        val cursor = workoutSummariesManager.getWorkoutsInRangeCursor(startS, startS + 86399)
        val mapper = com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper(
            application, workoutSummariesManager,
            com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(application),
            com.atrainingtracker.trainingtracker.database.EquipmentDbHelper(application),
            com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper(application)
        )
        val list = mutableListOf<WorkoutData>()
        if (cursor.moveToFirst()) { do { list.add(mapper.fromCursor(cursor)) } while (cursor.moveToNext()) }
        cursor.close()
        return list
    }

    private fun calculateWorkoutsBounds(workouts: List<WorkoutData>): LatLngBounds {
        val builder = LatLngBounds.Builder()
        workouts.forEach { w ->
            PolyUtil.decode(w.mapPolyline).forEach { builder.include(it) }
            w.startLatLng?.let { builder.include(it) }
        }
        return try { builder.build() } catch(e: Exception) { LatLngBounds(LatLng(0.0,0.0), LatLng(0.0,0.0)) }
    }

    private fun loadFromDatabase() {
        scope.launch {
            val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
            if (!isFinished) return@launch

            val workouts = workoutRepo.allWorkouts.value
            val dailyRaw = dbManager.getPeriodsByType(PeriodType.DAY)
            val weeklyRaw = dbManager.getPeriodsByType(PeriodType.WEEK)
            val monthlyRaw = dbManager.getPeriodsByType(PeriodType.MONTH)
            val yearlyRaw = dbManager.getPeriodsByType(PeriodType.YEAR)
            
            if (workouts.isNotEmpty()) {
                val groups = precalculateGroups(workouts)
                enrichAndEmit(listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw), groups)
            } else {
                _groupedPeriods.value = listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw)
            }
        }
    }

    private fun precalculateGroups(workouts: List<WorkoutData>): WorkoutGroups {
        return WorkoutGroups(
            days = workouts.groupBy { it.localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE) },
            weeks = workouts.groupBy { 
                val week = it.localDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val year = it.localDateTime.get(IsoFields.WEEK_BASED_YEAR)
                "$year-W${week.toString().padStart(2, '0')}"
            },
            months = workouts.groupBy { it.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM")) },
            years = workouts.groupBy { it.localDateTime.year.toString() }
        )
    }

    private fun enrichAndEmit(rawLevels: List<List<PeriodSummary>>, groups: WorkoutGroups) {
        _groupedPeriods.value = listOf(
            rawLevels[0].map { enrich(it, groups.days[it.sortKey] ?: emptyList()) },
            rawLevels[1].map { enrich(it, groups.weeks[it.sortKey] ?: emptyList()) },
            rawLevels[2].map { enrich(it, groups.months[it.sortKey] ?: emptyList()) },
            rawLevels[3].map { enrich(it, groups.years[it.sortKey] ?: emptyList()) }
        )
    }

    private fun enrich(summary: PeriodSummary, groupWorkouts: List<WorkoutData>): PeriodSummary {
        if (groupWorkouts.isEmpty()) return summary
        val anchorIds = setOf(summary.longestId, summary.northId, summary.southId, summary.eastId, summary.westId).filter { it != -1L }
        val anchorWorkouts = groupWorkouts.filter { it.id in anchorIds }
        return summary.copy(
            polylines = anchorWorkouts.map { it.mapPolyline }.filter { it.isNotEmpty() },
            workoutIdToPolylineMap = anchorWorkouts.associate { it.id to it.mapPolyline },
            workoutIdToSportMap = anchorWorkouts.associate { it.id to it.bSportType },
            extremaMarkers = anchorWorkouts.flatMap { workout ->
                val markers = mutableListOf<PeriodPeakMarker>()
                workout.startLatLng?.let { markers.add(PeriodPeakMarker(workout.id, it, R.drawable.control_start, "${workout.workoutName}: Start", PeriodMarkerType.START)) }
                workout.endLatLng?.let { markers.add(PeriodPeakMarker(workout.id, it, R.drawable.control_stop, "${workout.workoutName}: End", PeriodMarkerType.END)) }
                markers
            }
        )
    }

    private data class WorkoutGroups(
        val days: Map<String, List<WorkoutData>>,
        val weeks: Map<String, List<WorkoutData>>,
        val months: Map<String, List<WorkoutData>>,
        val years: Map<String, List<WorkoutData>>
    )
}
