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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.atrainingtracker.trainingtracker.ui.util.ProgressPhase
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

    private val _migrationStatus = MutableStateFlow<MigrationStatus?>(null)
    val migrationStatus: StateFlow<MigrationStatus?> = _migrationStatus.asStateFlow()

    private val rebuildMutex = Mutex()

    private val dayFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
    private val rangeDateFormatter = DateTimeFormatter.ofPattern("MMM d")

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
        scope.launch {
            loadFromDatabase()
        }

        // 2. Initial Migration (Prioritized Hierarchical Scan)
        scope.launch {
            val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
            if (!isFinished) {
                performHierarchicalMigration()
            }
        }
    }

    /**
     * ATT-379: Performs a Dual-Phase hierarchical re-aggregation of history.
     * Phase 1: High-speed O(N) database read and global grouping.
     * Phase 2: Prioritized hierarchical sync with Transactional Pumping.
     */
    private suspend fun performHierarchicalMigration() = withContext(Dispatchers.Default) {
        rebuildMutex.withLock {
            val title = application.getString(R.string.workout_periods__migration_title)
            _migrationStatus.value = MigrationStatus(
                title,
                listOf(ProgressPhase(1, application.getString(R.string.workout_periods__migration_querying), 0.0f))
            )
            Log.i(TAG, "Starting Dual-Phase Hierarchical Migration.")
            val startTime = System.currentTimeMillis()

            val mapper = WorkoutDataMapper(
                application, workoutSummariesManager,
                com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(application),
                com.atrainingtracker.trainingtracker.database.EquipmentDbHelper(application),
                com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper(application)
            )

            // --- PHASE 1: RAPID READ & GROUPING (O(N)) ---
            val allWorkouts = withContext(Dispatchers.IO) {
                val list = mutableListOf<WorkoutData>()
                val cursor = workoutSummariesManager.getCursorForAllWorkouts() // Sorted DESC
                if (cursor.count == 0) {
                    cursor.close()
                    return@withContext emptyList<WorkoutData>()
                }

                val total = cursor.count
                val chunkSize = 100
                val stravaUploadDbHelper = com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper(application)

                cursor.use { c ->
                    if (!c.moveToFirst()) return@withContext emptyList<WorkoutData>()
                    
                    while (!c.isAfterLast) {
                        // 1. Gather IDs and names for the next chunk
                        val chunkIds = mutableListOf<Long>()
                        val chunkNames = mutableListOf<String>()
                        val currentChunkStartPos = c.position
                        
                        var i = 0
                        while (i < chunkSize && !c.isAfterLast) {
                            chunkIds.add(c.getLong(c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID)))
                            c.getString(c.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME))?.let {
                                chunkNames.add(it)
                            }
                            c.moveToNext()
                            i++
                        }

                        // 2. Fetch Metadata for the chunk in just 2 queries (ATT-359/382)
                        val extremaList = workoutSummariesManager.getExtremaForWorkouts(chunkIds)
                        val stravaDataMap = stravaUploadDbHelper.getStravaActivityDataForWorkouts(chunkNames)
                        val batchMetadata = WorkoutDataMapper.BatchMetadata(
                            extrema = extremaList.groupBy { it.workoutId },
                            stravaData = stravaDataMap
                        )

                        // 3. Map the chunk using vectorized data
                        c.moveToPosition(currentChunkStartPos)
                        var j = 0
                        while (j < chunkSize && !c.isAfterLast) {
                            list.add(mapper.fromCursor(c, batchMetadata))
                            c.moveToNext()
                            j++
                        }

                        // 4. Update Progress
                        val count = list.size
                        val msg = application.getString(R.string.workout_periods__migration_reading, count, total)
                        _migrationStatus.value = MigrationStatus(
                            title,
                            listOf(ProgressPhase(1, msg, count.toFloat() / total.toFloat()))
                        )
                    }
                }
                list
            }

            if (allWorkouts.isEmpty()) {
                dbManager.runInTransaction { db -> dbManager.setSyncFinished(db, true) }
                _migrationStatus.value = null
                return@withLock
            }

            // Phase 1 Complete
            val phase1Finished = ProgressPhase(1, application.getString(R.string.workout_periods__migration_querying), 1.0f)

            // Global Pre-grouping (O(N) exactly once)
            val globalGroups = precalculateGroups(allWorkouts)
            val monthBuckets = allWorkouts.groupBy { it.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM")) }
                .toList().sortedByDescending { it.first }

            // --- PHASE 2: PRIORITIZED SURGICAL SYNC ---
            withContext(Dispatchers.IO) {
                // Initial Reset
                dbManager.runInTransaction { db ->
                    dbManager.deleteAll(db)
                    dbManager.setSyncFinished(db, false)
                }

                monthBuckets.forEachIndexed { index, (_, workouts) ->
                    val monthLabel = workouts.first().localDateTime.format(monthFormatter)
                    val msg = application.getString(R.string.workout_periods__migration_syncing, monthLabel)
                    _migrationStatus.value = MigrationStatus(
                        title,
                        listOf(
                            phase1Finished,
                            ProgressPhase(2, msg, index.toFloat() / monthBuckets.size.toFloat())
                        )
                    )
                    
                    // Transactional Pumping: Commit per month for immediate UI visibility
                    dbManager.runInTransaction { db ->
                        processMonthBucket(db, workouts)
                    }
                    
                    // Push visible changes to UI instantly using pre-calculated groups
                    loadFromDatabase(forceIncremental = true, precalculatedGroups = globalGroups)
                }

                // Final Step: Roll up YEARs from MONTHs
                val finalMsg = application.getString(R.string.workout_periods__migration_finalizing)
                _migrationStatus.value = MigrationStatus(
                    title,
                    listOf(
                        phase1Finished,
                        ProgressPhase(2, finalMsg, 0.99f)
                    )
                )
                dbManager.runInTransaction { db ->
                    rollupMonthsToYears(db)
                    dbManager.setSyncFinished(db, true)
                }
            }

            Log.i(TAG, "Hierarchical Migration finished in ${System.currentTimeMillis() - startTime}ms.")
            _migrationStatus.value = null
            loadFromDatabase(forceIncremental = false, precalculatedGroups = globalGroups)
        }
    }

    private fun processMonthBucket(db: android.database.sqlite.SQLiteDatabase, workouts: List<WorkoutData>) {
        if (workouts.isEmpty()) return
        
        // 1. Build DAYS from Workouts
        val daysInMonth = workouts.groupBy { 
            it.localDateTime.toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() 
        }
        daysInMonth.forEach { (startS, dayWorkouts) ->
            aggregateWorkoutsToDay(dayWorkouts, startS)?.let {
                dbManager.upsertPeriod(db, it)
            }
        }

        // 2. Roll up WEEKs and MONTH from these DAYs
        val firstW = workouts.first()
        val monthStart = firstW.localDateTime.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val monthEnd = firstW.localDateTime.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        rollupDaysToParentPeriods(db, monthStart, monthEnd)
    }

    /**
     * O(1) Hierarchical Add: Workout -> Day -> Week/Month -> Year.
     */
    fun onWorkoutFinished(workout: WorkoutData) {
        scope.launch {
            val ldt = workout.localDateTime
            val dayStart = ldt.toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()

            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    // 1. Update Day
                    val workoutsInDay = fetchWorkoutsInDay(dayStart)
                    aggregateWorkoutsToDay(workoutsInDay, dayStart)?.let { 
                        dbManager.upsertPeriod(db, it)
                    }

                    // 2. Propagate Upwards
                    rollupDayToParents(db, ldt)
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
            val ldt = workout.localDateTime
            val dayStart = ldt.toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()

            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    val workoutsRemaining = fetchWorkoutsInDay(dayStart)
                    if (workoutsRemaining.isEmpty()) {
                        dbManager.deletePeriod(db, PeriodType.DAY, dayStart)
                    } else {
                        aggregateWorkoutsToDay(workoutsRemaining, dayStart)?.let {
                            dbManager.upsertPeriod(db, it)
                        }
                    }
                    rollupDayToParents(db, ldt)
                }
            }
            loadFromDatabase()
        }
    }

    /**
     * Atomic Sport Transition (ATT-346 Suggested Algorithm).
     */
    fun onWorkoutSportChanged(newWorkout: WorkoutData, oldWorkout: WorkoutData) {
        scope.launch {
            Log.d(TAG, "onWorkoutSportChanged: ${oldWorkout.bSportType} -> ${newWorkout.bSportType}")
            
            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    getAffectedPeriodRanges(newWorkout.localDateTime).forEach { (type, start, _) ->
                        val period = dbManager.getSummariesInRange(type, start, start).firstOrNull() ?: return@forEach
                        
                        // Transition logic: Subtract old metrics, then recalculate the Day
                        val subtracted = subtractWorkoutFromPeriod(period, oldWorkout)
                        // Merging new is easier by just recalculating the Day and rolling up
                        dbManager.upsertPeriod(db, subtracted)
                    }
                }
            }
            // Logic optimization: Just call finished logic to ensure the new state is rolled up correctly
            onWorkoutFinished(newWorkout)
        }
    }

    private fun mergeWorkoutToPeriod(p: PeriodSummary, w: WorkoutData, b: LatLngBounds?): PeriodSummary {
        val minLat = if (b != null) minOf(p.minLat, b.southwest.latitude) else p.minLat
        val maxLat = if (b != null) maxOf(p.maxLat, b.northeast.latitude) else p.maxLat
        val minLng = if (b != null) minOf(p.minLng, b.southwest.longitude) else p.minLng
        val maxLng = if (b != null) maxOf(p.maxLng, b.northeast.longitude) else p.maxLng

        var nId = p.northId; var sId = p.southId; var eId = p.eastId; var wId = p.westId
        if (b != null) {
            if (b.northeast.latitude > p.maxLat) nId = w.id
            if (b.southwest.latitude < p.minLat) sId = w.id
            if (b.northeast.longitude > p.maxLng) eId = w.id
            if (b.southwest.longitude < p.minLng) wId = w.id
        }

        val longestId = if (w.activeTimeSec > p.longestDurationS) w.id else p.longestId
        val longestDuration = if (w.activeTimeSec > p.longestDurationS) w.activeTimeSec else p.longestDurationS

        val newStatsMap = p.sportStats.toMutableMap()
        val currentSport = newStatsMap[w.bSportType]
        if (currentSport == null) {
            newStatsMap[w.bSportType] = SportStats(1, w.activeTimeSec, w.totalDistance, w.ascentMeters, 
                mapOf(w.sportName to DetailedStats(1, w.activeTimeSec, w.totalDistance, w.ascentMeters)), 
                LongestWorkout(w.id, w.workoutName, w.activeTimeSec, w.totalDistance, w.ascentMeters))
        } else {
            val newDetailed = currentSport.detailedSportStats.toMutableMap()
            val det = newDetailed[w.sportName] ?: DetailedStats(0, 0, 0.0, 0)
            newDetailed[w.sportName] = det.copy(count = det.count + 1, totalDurationSec = det.totalDurationSec + w.activeTimeSec, 
                totalDistanceMeters = det.totalDistanceMeters + w.totalDistance, totalAscentMeters = det.totalAscentMeters + w.ascentMeters)
            
            val longestInSport = if (w.activeTimeSec > (currentSport.longestWorkout?.durationSec ?: 0)) {
                LongestWorkout(w.id, w.workoutName, w.activeTimeSec, w.totalDistance, w.ascentMeters)
            } else currentSport.longestWorkout

            newStatsMap[w.bSportType] = currentSport.copy(
                count = currentSport.count + 1,
                totalDurationSec = currentSport.totalDurationSec + w.activeTimeSec,
                totalDistanceMeters = currentSport.totalDistanceMeters + w.totalDistance,
                totalAscentMeters = currentSport.totalAscentMeters + w.ascentMeters,
                detailedSportStats = newDetailed,
                longestWorkout = longestInSport
            )
        }

        return p.copy(
            totalWorkouts = p.totalWorkouts + 1,
            totalDurationSec = p.totalDurationSec + w.activeTimeSec,
            totalDistance = p.totalDistance + w.totalDistance,
            sportStats = newStatsMap,
            minLat = minLat, maxLat = maxLat, minLng = minLng, maxLng = maxLng,
            longestId = longestId, longestDurationS = longestDuration,
            northId = nId, southId = sId, eastId = eId, westId = wId
        )
    }

    private fun initPeriodFromWorkout(w: WorkoutData, type: PeriodType, start: Long, end: Long, b: LatLngBounds?): PeriodSummary {
        val label = when (type) {
            PeriodType.DAY -> w.localDateTime.format(dayFormatter)
            PeriodType.WEEK -> {
                val week = w.localDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val year = w.localDateTime.get(IsoFields.WEEK_BASED_YEAR)
                "$year-W${week.toString().padStart(2, '0')}"
            }
            PeriodType.MONTH -> w.localDateTime.format(monthFormatter)
            PeriodType.YEAR -> w.localDateTime.format(yearFormatter)
        }
        val range = if (type == PeriodType.WEEK) {
            val weekStart = w.localDateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)
            "${weekStart.format(rangeDateFormatter)} - ${weekEnd.format(rangeDateFormatter)}"
        } else ""
        
        val sportStats = mapOf(w.bSportType to SportStats(
            count = 1, totalDurationSec = w.activeTimeSec, totalDistanceMeters = w.totalDistance, totalAscentMeters = w.ascentMeters,
            detailedSportStats = mapOf(w.sportName to DetailedStats(1, w.activeTimeSec, w.totalDistance, w.ascentMeters)),
            longestWorkout = LongestWorkout(w.id, w.workoutName, w.activeTimeSec, w.totalDistance, w.ascentMeters)
        ))

        return PeriodSummary(
            periodLabel = label, periodDateRange = range, periodType = type,
            startTimestampS = start, endTimestampS = end, totalWorkouts = 1,
            totalDurationSec = w.activeTimeSec, totalDistance = w.totalDistance,
            sportStats = sportStats, sortKey = createSortKey(w, type),
            minLat = b?.southwest?.latitude ?: 90.0, minLng = b?.southwest?.longitude ?: 180.0, 
            maxLat = b?.northeast?.latitude ?: -90.0, maxLng = b?.northeast?.longitude ?: -180.0,
            longestId = w.id, longestDurationS = w.activeTimeSec,
            northId = w.id, southId = w.id, eastId = w.id, westId = w.id
        )
    }

    private fun getPeriodSortKey(startTimestampS: Long, type: PeriodType): String {
        val dt = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(startTimestampS), java.time.ZoneId.systemDefault())
        return when (type) {
            PeriodType.DAY -> dt.format(DateTimeFormatter.ISO_LOCAL_DATE)
            PeriodType.WEEK -> {
                val week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val year = dt.get(IsoFields.WEEK_BASED_YEAR)
                "$year-W${week.toString().padStart(2, '0')}"
            }
            PeriodType.MONTH -> dt.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            PeriodType.YEAR -> dt.year.toString()
        }
    }

    private fun createSortKey(w: WorkoutData, type: PeriodType): String {
        return getPeriodSortKey(w.startTimeS, type)
    }

    private fun subtractWorkoutFromPeriod(p: PeriodSummary, w: WorkoutData): PeriodSummary {
        val newStatsMap = p.sportStats.toMutableMap()
        val currentSport = newStatsMap[w.bSportType]
        
        if (currentSport != null) {
            val newDetailed = currentSport.detailedSportStats.toMutableMap()
            val det = newDetailed[w.sportName] ?: DetailedStats(0, 0, 0.0, 0)
            
            newDetailed[w.sportName] = det.copy(
                count = (det.count - 1).coerceAtLeast(0),
                totalDurationSec = (det.totalDurationSec - w.activeTimeSec).coerceAtLeast(0),
                totalDistanceMeters = (det.totalDistanceMeters - w.totalDistance).coerceAtLeast(0.0),
                totalAscentMeters = (det.totalAscentMeters - w.ascentMeters).coerceAtLeast(0)
            )

            newStatsMap[w.bSportType] = currentSport.copy(
                count = (currentSport.count - 1).coerceAtLeast(0),
                totalDurationSec = (currentSport.totalDurationSec - w.activeTimeSec).coerceAtLeast(0),
                totalDistanceMeters = (currentSport.totalDistanceMeters - w.totalDistance).coerceAtLeast(0.0),
                totalAscentMeters = (currentSport.totalAscentMeters - w.ascentMeters).coerceAtLeast(0),
                detailedSportStats = newDetailed
            )
        }

        return p.copy(
            totalWorkouts = (p.totalWorkouts - 1).coerceAtLeast(0),
            totalDurationSec = (p.totalDurationSec - w.activeTimeSec).coerceAtLeast(0),
            totalDistance = (p.totalDistance - w.totalDistance).coerceAtLeast(0.0),
            sportStats = newStatsMap
        )
    }

    private fun rollupDayToParents(db: android.database.sqlite.SQLiteDatabase, ldt: java.time.LocalDateTime) {
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()

        // 1. Recalculate Week from Days
        val weekEnd = weekStart + (7 * 86400) - 1
        val daysInWeek = dbManager.getSummariesInRange(PeriodType.DAY, weekStart, weekEnd)
        if (daysInWeek.isEmpty()) dbManager.deletePeriod(db, PeriodType.WEEK, weekStart)
        else aggregateChildrenToParent(daysInWeek, PeriodType.WEEK, weekStart, weekEnd)?.let { dbManager.upsertPeriod(db, it) }

        // 2. Recalculate Month from Days
        val monthEnd = ldt.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val daysInMonth = dbManager.getSummariesInRange(PeriodType.DAY, monthStart, monthEnd)
        if (daysInMonth.isEmpty()) dbManager.deletePeriod(db, PeriodType.MONTH, monthStart)
        else aggregateChildrenToParent(daysInMonth, PeriodType.MONTH, monthStart, monthEnd)?.let { dbManager.upsertPeriod(db, it) }

        // 3. Recalculate Year from Months
        val yearEnd = ldt.with(TemporalAdjusters.lastDayOfYear()).toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val monthsInYear = dbManager.getSummariesInRange(PeriodType.MONTH, yearStart, yearEnd)
        if (monthsInYear.isEmpty()) dbManager.deletePeriod(db, PeriodType.YEAR, yearStart)
        else aggregateChildrenToParent(monthsInYear, PeriodType.YEAR, yearStart, yearEnd)?.let { dbManager.upsertPeriod(db, it) }
    }

    private fun rollupDaysToParentPeriods(db: android.database.sqlite.SQLiteDatabase, startS: Long, endS: Long) {
        val days = dbManager.getSummariesInRange(PeriodType.DAY, startS, endS)
        if (days.isEmpty()) return

        // Month Rollup
        aggregateChildrenToParent(days, PeriodType.MONTH, startS, endS)?.let { dbManager.upsertPeriod(db, it) }

        // Week Rollups
        val weekStarts = days.map { OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(it.startTimestampS), java.time.ZoneId.systemDefault()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(OffsetDateTime.now().offset) }.distinct()
        weekStarts.forEach { ws ->
            val daysInWeek = dbManager.getSummariesInRange(PeriodType.DAY, ws, ws + (7 * 86400) - 1)
            if (daysInWeek.isNotEmpty()) {
                aggregateChildrenToParent(daysInWeek, PeriodType.WEEK, ws, ws + (7 * 86400) - 1)?.let { dbManager.upsertPeriod(db, it) }
            }
        }
    }

    private fun rollupMonthsToYears(db: android.database.sqlite.SQLiteDatabase) {
        val months = dbManager.getPeriodsByType(PeriodType.MONTH)
        val yearGroups = months.groupBy { OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(it.startTimestampS), java.time.ZoneId.systemDefault()).year }
        yearGroups.forEach { (year, yearMonths) ->
            val start = java.time.LocalDate.of(year, 1, 1).atStartOfDay().toEpochSecond(OffsetDateTime.now().offset)
            val end = java.time.LocalDate.of(year, 12, 31).atTime(23, 59, 59).toEpochSecond(OffsetDateTime.now().offset)
            aggregateChildrenToParent(yearMonths, PeriodType.YEAR, start, end)?.let { dbManager.upsertPeriod(db, it) }
        }
    }

    private fun aggregateWorkoutsToDay(workouts: List<WorkoutData>, startS: Long): PeriodSummary? {
        if (workouts.isEmpty()) return null
        
        val w = workouts.first()
        val bounds = calculateWorkoutsBounds(workouts)
        val longest = workouts.maxByOrNull { it.activeTimeSec } ?: w
        
        val sportStats = workouts.groupBy { it.bSportType }.mapValues { (_, sportWorkouts) ->
            val detailed = sportWorkouts.groupBy { it.sportName }.mapValues { (_, detW) ->
                DetailedStats(detW.size, detW.sumOf { it.activeTimeSec }, detW.sumOf { it.totalDistance }, detW.sumOf { it.ascentMeters })
            }
            val l = sportWorkouts.maxByOrNull { it.activeTimeSec } ?: sportWorkouts.first()
            SportStats(sportWorkouts.size, sportWorkouts.sumOf { it.activeTimeSec }, sportWorkouts.sumOf { it.totalDistance }, sportWorkouts.sumOf { it.ascentMeters }, detailed, LongestWorkout(l.id, l.workoutName, l.activeTimeSec, l.totalDistance, l.ascentMeters))
        }

        return PeriodSummary(
            periodLabel = w.localDateTime.format(dayFormatter), periodDateRange = "", periodType = PeriodType.DAY,
            startTimestampS = startS, endTimestampS = startS + 86399, totalWorkouts = workouts.size,
            totalDurationSec = workouts.sumOf { it.activeTimeSec }, totalDistance = workouts.sumOf { it.totalDistance },
            sportStats = sportStats, sortKey = getPeriodSortKey(startS, PeriodType.DAY),
            minLat = bounds?.southwest?.latitude ?: 90.0, 
            minLng = bounds?.southwest?.longitude ?: 180.0, 
            maxLat = bounds?.northeast?.latitude ?: -90.0, 
            maxLng = bounds?.northeast?.longitude ?: -180.0,
            longestId = longest.id, longestDurationS = longest.activeTimeSec,
            northId = longest.id, southId = longest.id, eastId = longest.id, westId = longest.id
        )
    }

    private fun aggregateChildrenToParent(children: List<PeriodSummary>, type: PeriodType, start: Long, end: Long): PeriodSummary? {
        if (children.isEmpty()) return null
        
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

        val longest = children.maxByOrNull { it.longestDurationS } ?: return null
        
        // --- ATT-352/354 Refinement: Spatial Integrity for Hierarchy ---
        // Only include children that have valid spatial data (ignore sentinels)
        val spatialChildren = children.filter { it.minLat < 90.0 }
        val minLat = if (spatialChildren.isNotEmpty()) spatialChildren.minOf { it.minLat } else 90.0
        val maxLat = if (spatialChildren.isNotEmpty()) spatialChildren.maxOf { it.maxLat } else -90.0
        val minLng = if (spatialChildren.isNotEmpty()) spatialChildren.minOf { it.minLng } else 180.0
        val maxLng = if (spatialChildren.isNotEmpty()) spatialChildren.maxOf { it.maxLng } else -180.0

        val label = when(type) {
            PeriodType.WEEK -> {
                val dt = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault())
                val week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val year = dt.get(IsoFields.WEEK_BASED_YEAR)
                "$year-W${week.toString().padStart(2, '0')}"
            }
            PeriodType.MONTH -> OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault()).format(monthFormatter)
            PeriodType.YEAR -> OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault()).format(yearFormatter)
            else -> ""
        }

        val range = if (type == PeriodType.WEEK) {
            val weekStart = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(start), java.time.ZoneId.systemDefault())
            val weekEnd = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(end), java.time.ZoneId.systemDefault())
            "${weekStart.format(rangeDateFormatter)} - ${weekEnd.format(rangeDateFormatter)}"
        } else ""

        return PeriodSummary(
            periodLabel = label, periodDateRange = range, periodType = type,
            startTimestampS = start, endTimestampS = end, totalWorkouts = totalWorkouts,
            totalDurationSec = totalTime, totalDistance = totalDist, sportStats = mergedSportStats,
            sortKey = getPeriodSortKey(start, type),
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

    private fun calculateWorkoutsBounds(workouts: List<WorkoutData>): LatLngBounds? {
        val builder = LatLngBounds.Builder()
        var hasPoints = false
        workouts.forEach { w ->
            // --- ATT-352/354 Refinement: Spatial Integrity ---
            if (w.minLat != null && w.maxLat != null && w.minLng != null && w.maxLng != null && w.minLat < 90.0) {
                builder.include(LatLng(w.minLat, w.minLng))
                builder.include(LatLng(w.maxLat, w.maxLng))
                hasPoints = true
            } else {
                // Fallback for legacy (Only if startLatLng is reasonable)
                w.startLatLng?.let { 
                    if (it.latitude != 0.0 || it.longitude != 0.0) {
                        builder.include(it)
                        hasPoints = true
                    }
                }
            }
        }
        return if (hasPoints) {
            try { builder.build() } catch(e: Exception) { null }
        } else {
            null
        }
    }

    private fun calculateWorkoutBounds(w: WorkoutData): LatLngBounds? {
        // --- ATT-352: Use persisted bounds for zero-latency aggregation ---
        if (w.minLat != null && w.maxLat != null && w.minLng != null && w.maxLng != null) {
            return if (w.minLat <= w.maxLat) {
                LatLngBounds(LatLng(w.minLat, w.minLng), LatLng(w.maxLat, w.maxLng))
            } else null
        }

        // Fallback for legacy
        val decoded = try { PolyUtil.decode(w.mapPolyline) } catch (e: Exception) { emptyList<LatLng>() }
        val minLat = decoded.minOfOrNull { it.latitude } ?: w.startLatLng?.latitude
        val maxLat = decoded.maxOfOrNull { it.latitude } ?: w.startLatLng?.latitude
        val minLng = decoded.minOfOrNull { it.longitude } ?: w.startLatLng?.longitude
        val maxLng = decoded.maxOfOrNull { it.longitude } ?: w.startLatLng?.longitude

        return if (minLat != null && maxLat != null && minLng != null && maxLng != null && minLat <= maxLat) {
            LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
        } else null
    }

    private suspend fun loadFromDatabase(forceIncremental: Boolean = false, precalculatedGroups: WorkoutGroups? = null) {
        val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
        if (!isFinished && !forceIncremental) return

        val workouts = if (precalculatedGroups != null) emptyList() else workoutRepo.allWorkouts.value
        val dailyRaw = withContext(Dispatchers.IO) { dbManager.getPeriodsByType(PeriodType.DAY) }
        val weeklyRaw = withContext(Dispatchers.IO) { dbManager.getPeriodsByType(PeriodType.WEEK) }
        val monthlyRaw = withContext(Dispatchers.IO) { dbManager.getPeriodsByType(PeriodType.MONTH) }
        val yearlyRaw = withContext(Dispatchers.IO) { dbManager.getPeriodsByType(PeriodType.YEAR) }
        
        if (precalculatedGroups != null) {
            enrichAndEmit(listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw), precalculatedGroups)
        } else if (workouts.isNotEmpty()) {
            val groups = precalculateGroups(workouts)
            enrichAndEmit(listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw), groups)
        } else {
            _groupedPeriods.value = listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw)
        }
    }

    private fun precalculateGroups(workouts: List<WorkoutData>): WorkoutGroups {
        return WorkoutGroups(
            days = workouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.DAY) },
            weeks = workouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.WEEK) },
            months = workouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.MONTH) },
            years = workouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.YEAR) }
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

    private fun getAffectedPeriodRanges(ldt: java.time.LocalDateTime): List<Triple<PeriodType, Long, Long>> {
        val dayStart = ldt.toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()

        return listOf(
            Triple(PeriodType.DAY, dayStart, dayStart + 86399),
            Triple(PeriodType.WEEK, weekStart, weekStart + (7 * 86400) - 1),
            Triple(PeriodType.MONTH, monthStart, ldt.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toEpochSecond()),
            Triple(PeriodType.YEAR, yearStart, ldt.with(TemporalAdjusters.lastDayOfYear()).toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toEpochSecond())
        )
    }

    private data class WorkoutGroups(
        val days: Map<String, List<WorkoutData>>,
        val weeks: Map<String, List<WorkoutData>>,
        val months: Map<String, List<WorkoutData>>,
        val years: Map<String, List<WorkoutData>>
    )
}
