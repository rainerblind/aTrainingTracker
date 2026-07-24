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

        // 2. Observe workouts and trigger background re-aggregation only if needed
        scope.launch {
            // Signal immediate rebuild start if cache is not ready
            val isFinished = withContext(Dispatchers.IO) { 
                val finished = dbManager.isSyncFinished()
                Log.d(TAG, "Init sync check: isFinished=$finished")
                finished
            }
            if (!isFinished) {
                Log.d(TAG, "Sync not finished, setting migrationProgress to 0.0")
                _migrationProgress.value = 0.0f
            }

            workoutRepo.allWorkouts.collectLatest { workouts ->
                Log.d(TAG, "workoutRepo.allWorkouts emitted ${workouts.size} items.")
                if (workouts.isNotEmpty()) {
                    val needsRebuild = withContext(Dispatchers.IO) { !dbManager.isSyncFinished() }
                    Log.d(TAG, "Needs rebuild check: $needsRebuild")
                    rebuildDatabase(workouts, needsRebuild)
                } else {
                    Log.d(TAG, "Workouts empty, skipping rebuild.")
                }
            }
        }
    }

    private fun loadFromDatabase() {
        scope.launch {
            val isFinished = withContext(Dispatchers.IO) { dbManager.isSyncFinished() }
            if (!isFinished) {
                Log.d(TAG, "Sync not finished. Showing empty list while rebuild runs.")
                return@launch
            }

            val workouts = workoutRepo.allWorkouts.value
            
            val dailyRaw = dbManager.getPeriodsByType(PeriodType.DAY)
            val weeklyRaw = dbManager.getPeriodsByType(PeriodType.WEEK)
            val monthlyRaw = dbManager.getPeriodsByType(PeriodType.MONTH)
            val yearlyRaw = dbManager.getPeriodsByType(PeriodType.YEAR)
            
            if (workouts.isNotEmpty()) {
                enrichAndEmit(listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw), workouts)
            } else {
                _groupedPeriods.value = listOf(dailyRaw, weeklyRaw, monthlyRaw, yearlyRaw)
            }
        }
    }

    /**
     * ATT-346: Rebuild the periods database using the newest-first incremental algorithm.
     * Implements streaming UI updates so stats appear one-by-one.
     */
    private suspend fun rebuildDatabase(workouts: List<WorkoutData>, showProgress: Boolean) = withContext(Dispatchers.Default) {
        rebuildMutex.withLock {
            if (showProgress) _migrationProgress.value = 0.01f
            Log.i(TAG, "Starting newest-first streaming rebuild for ${workouts.size} workouts.")
            val startTime = System.currentTimeMillis()

            // 1. Sort workouts newest first for logical list growth
            val sortedWorkouts = workouts.sortedByDescending { it.startTimeS }
            
            // 2. High-speed RAM buffer for streaming UI
            val dayMap = LinkedHashMap<Long, PeriodSummary>()
            val weekMap = LinkedHashMap<Long, PeriodSummary>()
            val monthMap = LinkedHashMap<Long, PeriodSummary>()
            val yearMap = LinkedHashMap<Long, PeriodSummary>()

            withContext(Dispatchers.IO) {
                dbManager.runInTransaction { db ->
                    dbManager.deleteAll(db)
                    dbManager.setSyncFinished(db, false)

                    sortedWorkouts.forEachIndexed { index, workout ->
                        // Calculate keys and update our RAM maps incrementally
                        updateMapsForWorkout(dayMap, weekMap, monthMap, yearMap, workout)

                        // Write to DB cache (background persistence)
                        // Note: For extreme speed, we could batch DB writes, but O(1) upsert is fine.
                        upsertWorkoutToDB(db, workout)

                        // 3. PUMP UI: Every 20 workouts, emit the current RAM state to the UI
                        if (index % 20 == 0 || index == sortedWorkouts.size - 1) {
                            if (showProgress) {
                                val progress = index.toFloat() / sortedWorkouts.size.toFloat()
                                Log.v(TAG, "Rebuild progress: $progress ($index/${sortedWorkouts.size})")
                                _migrationProgress.value = progress
                            }
                            
                            // Emit currently aggregated RAM data (enriched for Map previews)
                            val currentLevels = listOf(
                                dayMap.values.toList(),
                                weekMap.values.toList(),
                                monthMap.values.toList(),
                                yearMap.values.toList()
                            )
                            Log.v(TAG, "Emitting incremental state to UI (index=$index)")
                            enrichAndEmit(currentLevels, workouts)
                        }
                    }
                    dbManager.setSyncFinished(db, true)
                }
            }
            
            Log.i(TAG, "Streaming rebuild finished in ${System.currentTimeMillis() - startTime}ms.")
            if (showProgress) _migrationProgress.value = null
        }
    }

    private fun updateMapsForWorkout(
        days: MutableMap<Long, PeriodSummary>,
        weeks: MutableMap<Long, PeriodSummary>,
        months: MutableMap<Long, PeriodSummary>,
        years: MutableMap<Long, PeriodSummary>,
        workout: WorkoutData
    ) {
        val ldt = workout.localDateTime
        val zoneOffset = OffsetDateTime.now().offset

        val dayStart = ldt.toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val dayEnd = dayStart + 86399
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val weekEnd = weekStart + (7 * 86400) - 1
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val monthEnd = ldt.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).toEpochSecond(zoneOffset)
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val yearEnd = ldt.with(TemporalAdjusters.lastDayOfYear()).toLocalDate().atTime(23, 59, 59).toEpochSecond(zoneOffset)

        // Process all 4 levels in RAM
        updateSingleMap(days, PeriodType.DAY, dayStart, dayEnd, workout)
        updateSingleMap(weeks, PeriodType.WEEK, weekStart, weekEnd, workout)
        updateSingleMap(months, PeriodType.MONTH, monthStart, monthEnd, workout)
        updateSingleMap(years, PeriodType.YEAR, yearStart, yearEnd, workout)
    }

    private fun updateSingleMap(map: MutableMap<Long, PeriodSummary>, type: PeriodType, start: Long, end: Long, w: WorkoutData) {
        val existing = map[start]
        val updated = if (existing == null) {
            initPeriodFromWorkout(w, type, start, end)
        } else {
            mergeWorkoutToPeriod(existing, w)
        }
        map[start] = updated
    }

    private fun upsertWorkoutToDB(db: android.database.sqlite.SQLiteDatabase, workout: WorkoutData) {
        // Reuse the logic from updateWorkoutToPeriods but for DB only
        val ldt = workout.localDateTime
        val zoneOffset = OffsetDateTime.now().offset
        
        val dayStart = ldt.toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val dayEnd = dayStart + 86399
        val weekStart = ldt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val weekEnd = weekStart + (7 * 86400) - 1
        val monthStart = ldt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val monthEnd = ldt.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(23, 59, 59).toEpochSecond(zoneOffset)
        val yearStart = ldt.with(TemporalAdjusters.firstDayOfYear()).toLocalDate().atStartOfDay().toEpochSecond(zoneOffset)
        val yearEnd = ldt.with(TemporalAdjusters.lastDayOfYear()).toLocalDate().atTime(23, 59, 59).toEpochSecond(zoneOffset)

        listOf(
            Triple(PeriodType.DAY, dayStart, dayEnd),
            Triple(PeriodType.WEEK, weekStart, weekEnd),
            Triple(PeriodType.MONTH, monthStart, monthEnd),
            Triple(PeriodType.YEAR, yearStart, yearEnd)
        ).forEach { (type, start, end) ->
            val existing = dbManager.getPeriodSummary(db, type, start)
            val updated = if (existing == null) {
                initPeriodFromWorkout(workout, type, start, end)
            } else {
                mergeWorkoutToPeriod(existing, workout)
            }
            dbManager.upsertPeriod(db, updated)
        }
    }

    private fun initPeriodFromWorkout(w: WorkoutData, type: PeriodType, start: Long, end: Long): PeriodSummary {
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
        
        val minLat = minOf(w.startLatLng?.latitude ?: 90.0, w.endLatLng?.latitude ?: 90.0)
        val maxLat = maxOf(w.startLatLng?.latitude ?: -90.0, w.endLatLng?.latitude ?: -90.0)
        val minLng = minOf(w.startLatLng?.longitude ?: 180.0, w.endLatLng?.longitude ?: 180.0)
        val maxLng = maxOf(w.startLatLng?.longitude ?: -180.0, w.endLatLng?.longitude ?: -180.0)

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
            minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng,
            longestId = w.id, longestDurationS = w.activeTimeSec,
            northId = w.id, southId = w.id, eastId = w.id, westId = w.id
        )
    }

    private fun mergeWorkoutToPeriod(p: PeriodSummary, w: WorkoutData): PeriodSummary {
        // Update Spatial Anchors & Bounds
        var minLat = p.minLat; var maxLat = p.maxLat; var minLng = p.minLng; var maxLng = p.maxLng
        var nId = p.northId; var sId = p.southId; var eId = p.eastId; var wId = p.westId
        
        listOfNotNull(w.startLatLng, w.endLatLng).forEach { pos ->
            if (pos.latitude > maxLat) { maxLat = pos.latitude; nId = w.id }
            if (pos.latitude < minLat) { minLat = pos.latitude; sId = w.id }
            if (pos.longitude > maxLng) { maxLng = pos.longitude; eId = w.id }
            if (pos.longitude < minLng) { minLng = pos.longitude; wId = w.id }
        }

        // Longest Overall Comparison
        val longestId = if (w.activeTimeSec > p.longestDurationS) w.id else p.longestId
        val longestDuration = if (w.activeTimeSec > p.longestDurationS) w.activeTimeSec else p.longestDurationS

        // Merge Sport Stats
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
            
            // Compare longest in sport
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
                "$year-${week.toString().padStart(2, '0')}"
            }
            PeriodType.MONTH -> w.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            PeriodType.YEAR -> w.localDateTime.year.toString()
        }
    }

    private fun enrichAndEmit(rawLevels: List<List<PeriodSummary>>, workouts: List<WorkoutData>) {
        val dayGroups = workouts.groupBy { it.localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE) }
        val weekGroups = workouts.groupBy { 
            val week = it.localDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val year = it.localDateTime.get(IsoFields.WEEK_BASED_YEAR)
            "$year-${week.toString().padStart(2, '0')}"
        }
        val monthGroups = workouts.groupBy { it.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM")) }
        val yearGroups = workouts.groupBy { it.localDateTime.year.toString() }

        _groupedPeriods.value = listOf(
            rawLevels[0].map { enrich(it, dayGroups[it.sortKey] ?: emptyList()) },
            rawLevels[1].map { enrich(it, weekGroups[it.sortKey] ?: emptyList()) },
            rawLevels[2].map { enrich(it, monthGroups[it.sortKey] ?: emptyList()) },
            rawLevels[3].map { enrich(it, yearGroups[it.sortKey] ?: emptyList()) }
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
