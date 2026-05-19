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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import kotlin.collections.emptyList

class PeriodsViewModel(application: Application) : AndroidViewModel(application) {

    private val workoutRepo = WorkoutRepository.getInstance(application)

    val groups = listOf(
        application.getString(R.string.workout_periods__days),
        application.getString(R.string.workout_periods__weeks),
        application.getString(R.string.workout_periods__months),
        application.getString(R.string.workout_periods__years)
    )

    enum class PeriodGroupLevel {
        DAY, WEEK, MONTH, YEAR
    }

    // Formatters for labels
    private val dayFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")

    // 1. Observe raw workouts from Repository
    private val _rawWorkouts = workoutRepo.allWorkouts.asFlow()

    // 2. The Main StateFlow: A list containing 4 lists (one for each grouping level)
    val groupedPeriods: StateFlow<List<List<PeriodSummary>>> = _rawWorkouts
        .map { workouts: List<WorkoutData> ->
            if (workouts.isEmpty()) {
                // Explicitly return the nested list type
                listOf<List<PeriodSummary>>(emptyList(), emptyList(), emptyList(), emptyList())
            } else {
                listOf<List<PeriodSummary>>(
                    // Tab 0: Daily
                    groupWorkouts(workouts, PeriodGroupLevel.DAY) { it.localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE) },

                    // Tab 1: Weekly (ISO Week based)
                    groupWorkouts(workouts, PeriodGroupLevel.WEEK) {
                        val week = it.localDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                        val year = it.localDateTime.get(IsoFields.WEEK_BASED_YEAR)
                        "$year-$week"
                    },

                    // Tab 2: Monthly
                    groupWorkouts(workouts, PeriodGroupLevel.MONTH) { it.localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM")) },

                    // Tab 3: Yearly
                    groupWorkouts(workouts, PeriodGroupLevel.YEAR) { it.localDateTime.year.toString() }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(emptyList(), emptyList(), emptyList(), emptyList())
        )

    /**
     * Generic grouping and aggregation logic
     */
    private fun groupWorkouts(
        workouts: List<WorkoutData>,
        level: PeriodGroupLevel,
        keySelector: (WorkoutData) -> String
    ): List<PeriodSummary> {
        return workouts.groupBy(keySelector)
            .map { (key, items) ->
                aggregateToPeriod(key, items, level)
            }
            // Ensure periods are shown in descending order (newest first)
            .sortedByDescending { it.sortKey }
    }

    private fun aggregateToPeriod(key: String, items: List<WorkoutData>, level: PeriodGroupLevel): PeriodSummary {
        val firstItem = items.first()

        // Generate nice labels based on the key type
        val (label, range) = when (level) {
            PeriodGroupLevel.DAY ->  { // Daily
                Pair(firstItem.localDateTime.format(dayFormatter), "")
            }
            PeriodGroupLevel.WEEK -> { // Weekly
                Pair(key, "${items.last().formattedDate} - ${items.first().formattedDate}")
            }
            PeriodGroupLevel.MONTH -> { // Monthly
                Pair(firstItem.localDateTime.format(monthFormatter), "")
            }
            PeriodGroupLevel.YEAR -> { // Yearly
                Pair(firstItem.localDateTime.format(yearFormatter), "")
            }
        }

        // Aggregate Sport Stats
        val sportStatsMap = items.groupBy { it.bSportType }.mapValues { (_, sportWorkouts) ->
            SportStats(
                count = sportWorkouts.size,
                totalDurationSec = sportWorkouts.sumOf { it.detailsData.activeTimeSec.toLong() },
                totalDistanceMeters = sportWorkouts.sumOf { it.detailsData.totalDistance },
                totalAscentMeters = sportWorkouts.sumOf { it.ascentMeters }
            )
        }

        return PeriodSummary(
            periodLabel = label,
            periodDateRange = range,
            totalWorkouts = items.size,
            totalDurationSec = items.sumOf { it.detailsData.activeTimeSec.toLong() },
            sportStats = sportStatsMap,
            polylines = items.mapNotNull { it.mapPolyline }.filter { it.isNotEmpty() },
            sortKey = key // Used for descending sort
        )
    }

    fun loadPeriods() {
        // This triggers the repository to refresh from DB if necessary
        viewModelScope.launch {
            workoutRepo.loadAllWorkouts()
        }
    }
}