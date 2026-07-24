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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Acts as the single source of truth for period-level data.
 * Orchestrates synchronization between workout history and the normalized periods database.
 */
class PeriodsRepository private constructor(private val application: Application) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dbManager = PeriodSummariesDatabaseManager.getInstance(application)
    private val workoutRepo = WorkoutRepository.getInstance(application)

    private val _groupedPeriods = MutableStateFlow<List<List<PeriodSummary>>>(listOf(emptyList(), emptyList(), emptyList(), emptyList()))
    val groupedPeriods: StateFlow<List<List<PeriodSummary>>> = _groupedPeriods.asStateFlow()

    private val _migrationProgress = MutableStateFlow<Float?>(null)
    val migrationProgress: StateFlow<Float?> = _migrationProgress.asStateFlow()

    private val rebuildMutex = Mutex()

    private var lastWorkoutCount: Int = -1
    private var lastNewestId: Long = -1L

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
        // 1. Instant load from cache (if sync was finished)
        loadFromDatabase()

        // 2. Observe workouts and trigger background updates
        scope.launch {
            // Signal immediate rebuild start if cache is not ready
            val isFinishedOnStart = withContext(Dispatchers.IO) { 
                val finished = dbManager.isSyncFinished()
                Log.d(TAG, "Init sync check: isFinished=$finished")
                finished
            }
            if (!isFinishedOnStart) {
                Log.d(TAG, "Sync not finished, setting migrationProgress to 0.0")
                _migrationProgress.value = 0.0f
            }

            workoutRepo.allWorkouts.collectLatest { workouts ->
                Log.d(TAG, "workoutRepo.allWorkouts emitted ${workouts.size} items.")
                if (workouts.isNotEmpty()) {
                    val currentCount = workouts.size
                    val newestId = workouts.first().id
                    
                    val needsFullRebuild = withContext(Dispatchers.IO) { !dbManager.isSyncFinished() }
                    
                    if (needsFullRebuild) {
                        Log.i(TAG, "Cache incomplete. Starting migration.")
                        rebuildDatabase(workouts, true)
                    } else if (lastWorkoutCount != -1 && currentCount == lastWorkoutCount + 1 && newestId != lastNewestId) {
                        // SUGGESTED ALGORITHM: 'Add only' the latest workout
                        Log.i(TAG, "Incremental update for newest workout: $newestId")
                        performIncrementalUpdate(workouts.first())
                    } else if (lastWorkoutCount != -1 && (currentCount != lastWorkoutCount || newestId != lastNewestId)) {
                        // History changed significantly (delete, edit of old workout, or bulk import)
                        Log.i(TAG, "History changed. Re-syncing periods.")
                        rebuildDatabase(workouts, false)
                    } else if (lastWorkoutCount == -1) {
                        // First emission after app start, and DB is already finished.
                        // Just ensure UI matches memory state (e.g. enrichment).
                        enrichAndEmitFromMemory(workouts)
                    }
                    
                    lastWorkoutCount = currentCount
                    lastNewestId = newestId
                } else {
                    Log.d(TAG, "Workouts empty, clearing periods.")
                    withContext(Dispatchers.IO) { dbManager.runInTransaction { db -> dbManager.deleteAll(db) } }
                    _groupedPeriods.value = listOf(emptyList(), emptyList(), emptyList(), emptyList())
                    lastWorkoutCount = 0
                    lastNewestId = -1L
                }
            }
        }
    }

    private suspend fun performIncrementalUpdate(workout: WorkoutData) {
        val bounds = calculateWorkoutBounds(workout)
        withContext(Dispatchers.IO) {
            dbManager.runInTransaction { db ->
                updateWorkoutToPeriods(db, workout, bounds)
            }
        }
        // Instantly refresh UI by joining DB summaries with RAM enrichment
        loadFromDatabase()
    }

    private fun enrichAndEmitFromMemory(workouts: List<WorkoutData>) {
        scope.launch {
            val dailyRaw = dbManager.getPeriodsByType(PeriodType.DAY)
            val weeklyRaw = dbManager.getPeriodsByType(PeriodType.WEEK)
            val monthlyRaw = dbManager.getPeriodsByType(PeriodType.MONTH)
            val yearlyRaw = dbManager.getPeriodsByType(PeriodType.YEAR)
            val groups = precalculateGroups(workouts)
            enrichAndEmit(listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw), groups)
        }
    }

    private fun loadFromDatabase() {
        scope.launch {
            val isFinished = withContext(Dispatchers.IO) { 
                val finished = dbManager.isSyncFinished()
                Log.d(TAG, "loadFromDatabase check: isFinished=$finished")
                finished
            }
            
            if (!isFinished) {
                return@launch
            }

            val workouts = workoutRepo.allWorkouts.value
            
            Log.d(TAG, "Loading lightweight periods from DB...")
            val dailyRaw = dbManager.getPeriodsByType(PeriodType.DAY)
            val weeklyRaw = dbManager.getPeriodsByType(PeriodType.WEEK)
            val monthlyRaw = dbManager.getPeriodsByType(PeriodType.MONTH)
            val yearlyRaw = dbManager.getPeriodsByType(PeriodType.YEAR)
            
            Log.d(TAG, "Loaded from DB: D:${dailyRaw.size} W:${weeklyRaw.size} M:${monthlyRaw.size} Y:${yearlyRaw.size}")

            if (workouts.isNotEmpty()) {
                val groups = precalculateGroups(workouts)
                enrichAndEmit(listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw), groups)
            } else {
                _groupedPeriods.value = listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw)
            }
        }
    }

    /**
     * ATT-346: Rebuild the periods database using the newest-first incremental algorithm.
     * Implements O(N) streaming UI updates so stats appear one-by-one instantly.
     */
    private suspend fun rebuildDatabase(workouts: List<WorkoutData>, showProgress: Boolean) = withContext(Dispatchers.Default) {
        rebuildMutex.withLock {
            if (showProgress) _migrationProgress.value = 0.01f
            Log.i(TAG, "Starting newest-first O(N) streaming rebuild for ${workouts.size} workouts.")
            val startTime = System.currentTimeMillis()

            // 1. Pre-calculate groups once to avoid O(N^2) complexity in the loop
            val workoutGroups = precalculateGroups(workouts)
            
            // 2. Sort workouts newest first for logical list growth
            val sortedWorkouts = workouts.sortedByDescending { it.startTimeS }
            
            // 3. High-speed RAM buffer for streaming UI
            val dayMap = LinkedHashMap<Long, PeriodSummary>()
            val weekMap = LinkedHashMap<Long, PeriodSummary>()
            val monthMap = LinkedHashMap<Long, PeriodSummary>()
            val yearMap = LinkedHashMap<Long, PeriodSummary>()

            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    dbManager.deleteAll(db)
                    dbManager.setSyncFinished(db, false)

                    sortedWorkouts.forEachIndexed { index, workout ->
                        // Cache decoded bounds to avoid redundant expensive PolyUtil calls
                        val wBounds = calculateWorkoutBounds(workout)

                        // Update RAM maps incrementally
                        updateMapsForWorkout(dayMap, weekMap, monthMap, yearMap, workout, wBounds)

                        // Write to DB cache (background persistence)
                        updateWorkoutToPeriods(db, workout, wBounds)

                        // 4. PUMP UI: Every 20 workouts (and at the start) emit state to UI
                        if (index == 0 || index % 20 == 0 || index == sortedWorkouts.size - 1) {
                            if (showProgress) {
                                val progress = index.toFloat() / sortedWorkouts.size.toFloat()
                                Log.v(TAG, "Rebuild progress: $progress ($index/${sortedWorkouts.size})")
                                _migrationProgress.value = progress
                            }
                            
                            val currentLevels = listOf(
                                dayMap.values.toList(),
                                weekMap.values.toList(),
                                monthMap.values.toList(),
                                yearMap.values.toList()
                            )
                            enrichAndEmit(currentLevels, workoutGroups)
                        }
                    }
                    dbManager.setSyncFinished(db, true)
                }
            }
            
            Log.i(TAG, "Streaming rebuild finished in ${System.currentTimeMillis() - startTime}ms.")
            if (showProgress) _migrationProgress.value = null
        }
    }

    private data class WorkoutGroups(
        val days: Map<String, List<WorkoutData>>,
        val weeks: Map<String, List<WorkoutData>>,
        val months: Map<String, List<WorkoutData>>,
        val years: Map<String, List<WorkoutData>>
    )

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

    private fun calculateWorkoutBounds(w: WorkoutData): LatLngBounds {
        val decoded = PolyUtil.decode(w.mapPolyline)
        val minLat = decoded.minOfOrNull { it.latitude } ?: w.startLatLng?.latitude ?: 90.0
        val maxLat = decoded.maxOfOrNull { it.latitude } ?: w.startLatLng?.latitude ?: -90.0
        val minLng = decoded.minOfOrNull { it.longitude } ?: w.startLatLng?.longitude ?: 180.0
        val maxLng = decoded.maxOfOrNull { it.longitude } ?: w.startLatLng?.longitude ?: -180.0
        return LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
    }

    private fun updateMapsForWorkout(
        days: MutableMap<Long, PeriodSummary>,
        weeks: MutableMap<Long, PeriodSummary>,
        months: MutableMap<Long, PeriodSummary>,
        years: MutableMap<Long, PeriodSummary>,
        workout: WorkoutData,
        bounds: LatLngBounds
    ) {
        val ldt = workout.localDateTime
        val zoneOffset = OffsetDateTime.now().offset

        val dayStart = ldt.toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)

        // Process all 4 levels in RAM
        updateSingleMap(days, PeriodType.DAY, dayStart, dayStart + 86399, workout, bounds)
        updateSingleMap(weeks, PeriodType.WEEK, weekStart, weekStart + (7 * 86400) - 1, workout, bounds)
        updateSingleMap(months, PeriodType.MONTH, monthStart, 0, workout, bounds)
        updateSingleMap(years, PeriodType.YEAR, yearStart, 0, workout, bounds)
    }

    private fun updateSingleMap(map: MutableMap<Long, PeriodSummary>, type: PeriodType, start: Long, end: Long, w: WorkoutData, b: LatLngBounds) {
        val existing = map[start]
        val updated = if (existing == null) {
            initPeriodFromWorkout(w, type, start, end, b)
        } else {
            mergeWorkoutToPeriod(existing, w, b)
        }
        map[start] = updated
    }

    private fun updateWorkoutToPeriods(db: android.database.sqlite.SQLiteDatabase, workout: WorkoutData, bounds: LatLngBounds) {
        val ldt = workout.localDateTime
        val zoneOffset = OffsetDateTime.now().offset
        
        val dayStart = ldt.toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)

        listOf(
            Triple(PeriodType.DAY, dayStart, dayStart + 86399),
            Triple(PeriodType.WEEK, weekStart, weekStart + (7 * 86400) - 1),
            Triple(PeriodType.MONTH, monthStart, 0L),
            Triple(PeriodType.YEAR, yearStart, 0L)
        ).forEach { (type, start, end) ->
            val existing = dbManager.getPeriodSummary(db, type, start)
            val updated = if (existing == null) {
                initPeriodFromWorkout(workout, type, start, end, bounds)
            } else {
                mergeWorkoutToPeriod(existing, workout, bounds)
            }
            dbManager.upsertPeriod(db, updated)
        }
    }

    private fun initPeriodFromWorkout(w: WorkoutData, type: PeriodType, start: Long, end: Long, b: LatLngBounds): PeriodSummary {
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
        val range = if (type == PeriodType.WEEK) "${w.formattedDate} - ${w.formattedDate}" else ""
        
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
            minLat = b.southwest.latitude, minLng = b.southwest.longitude, 
            maxLat = b.northeast.latitude, maxLng = b.northeast.longitude,
            longestId = w.id, longestDurationS = w.activeTimeSec,
            northId = w.id, southId = w.id, eastId = w.id, westId = w.id
        )
    }

    private fun mergeWorkoutToPeriod(p: PeriodSummary, w: WorkoutData, b: LatLngBounds): PeriodSummary {
        val minLat = minOf(p.minLat, b.southwest.latitude)
        val maxLat = maxOf(p.maxLat, b.northeast.latitude)
        val minLng = minOf(p.minLng, b.southwest.longitude)
        val maxLng = maxOf(p.maxLng, b.northeast.longitude)

        // Identification of anchor workouts
        var nId = p.northId; var sId = p.southId; var eId = p.eastId; var wId = p.westId
        if (b.northeast.latitude > p.maxLat) nId = w.id
        if (b.southwest.latitude < p.minLat) sId = w.id
        if (b.northeast.longitude > p.maxLng) eId = w.id
        if (b.southwest.longitude < p.minLng) wId = w.id

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

    private fun createSortKey(w: WorkoutData, type: PeriodType): String {
        return when (type) {
            PeriodType.DAY -> w.localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
            PeriodType.WEEK -> {
                val week = w.localDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val year = w.localDateTime.get(IsoFields.WEEK_BASED_YEAR)
                "$year-W${week.toString().padStart(2, '0')}"
            }
            PeriodType.MONTH -> w.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            PeriodType.YEAR -> w.localDateTime.year.toString()
        }
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
}
