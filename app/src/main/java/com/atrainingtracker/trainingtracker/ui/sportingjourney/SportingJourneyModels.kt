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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

/**
 * Filter horizon for the retrospective discovery engine (REQ-SPT-002).
 */
sealed interface SportingHorizon {
    val key: String
    val label: String

    data object AllTime : SportingHorizon {
        override val key: String = "ALL_TIME"
        override val label: String = "All-Time"
    }

    data class Year(val year: Int) : SportingHorizon {
        override val key: String = year.toString()
        override val label: String = year.toString()
    }
}

/**
 * Sport filter selection (REQ-SPT-002).
 */
enum class SportingSportFilter {
    ALL,
    CYCLING,
    RUNNING
}

/**
 * Aggregated hero totals and planetary equivalents (REQ-SPT-002, Pillar 1).
 */
data class SportingHeroTotals(
    val totalDistanceKm: Double = 0.0,
    val equatorPercentage: Double = 0.0,
    val totalWorkouts: Int = 0,
    val totalActiveHours: Double = 0.0,
    val nonstopDays: Double = 0.0,
    val totalElevationM: Int = 0,
    val everestMultiplier: Double = 0.0,
    val favoriteRoutesCount: Int = 0
)

/**
 * Biological and mechanical Hidden Gems discovered from SQLite telemetry (REQ-SPT-003, Pillar 2).
 */
data class SportingHiddenGems(
    val pedalRevolutions: Long = 0L,
    val cadenceTrackedRidesCount: Int = 0,
    val estimatedHeartbeats: Long = 0L,
    val hrTrackedWorkoutsCount: Int = 0,
    val mechanicalKwh: Double = 0.0,
    val mechanicalKj: Double = 0.0,
    val powerTrackedWorkoutsCount: Int = 0,
    val ebikeKmEquivalent: Double = 0.0,
    val espressosEquivalent: Int = 0,
    val phoneChargesEquivalent: Int = 0,
    val mostActiveHourStart: Int? = null,
    val mostActiveDayOfWeek: Int? = null,
    val ritualWorkoutsCount: Int = 0,
    val explorerWorkoutsCount: Int = 0,
    val ritualPercentage: Int = 0
)

/**
 * Milestone entry celebrating single workout highlights (REQ-SPT-003, Pillar 3).
 */
data class SportingMilestone(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val value: String,
    val workoutId: Long? = null
)

/**
 * Unlocked or lock-progress achievement trophy (REQ-SPT-003, Pillar 4).
 */
data class SportingAchievement(
    val id: String,
    val emoji: String,
    val name: String,
    val subtitle: String,
    val isUnlocked: Boolean,
    val storyTitle: String,
    val storyText: String,
    val workoutId: Long? = null
)

/**
 * Complete immutable aggregate state passed to [SportingJourneySheet] (REQ-SPT-001..004).
 */
data class SportingJourneyData(
    val horizon: SportingHorizon = SportingHorizon.AllTime,
    val sportFilter: SportingSportFilter = SportingSportFilter.ALL,
    val availableYears: List<Int> = emptyList(),
    val heroTotals: SportingHeroTotals = SportingHeroTotals(),
    val hiddenGems: SportingHiddenGems = SportingHiddenGems(),
    val milestones: List<SportingMilestone> = emptyList(),
    val achievements: List<SportingAchievement> = emptyList(),
    val firstWorkoutDateFormatted: String? = null,
    val daysSinceFirstWorkout: Long = 0L,
    val athleteName: String? = null,
    val isLoading: Boolean = false
)
