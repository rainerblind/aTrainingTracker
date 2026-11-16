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

package com.atrainingtracker.trainingtracker.monetization

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Garage classification leagues according to fleet scale (REQ-SET-088.1).
 */
enum class GarageLeague {
    NONE,
    GOLD,
    DIAMOND
}

/**
 * Snapshot of the athlete's current equipment and sensor fleet.
 */
data class GarageStats(
    val bikeCount: Int,
    val shoeCount: Int,
    val sensorCount: Int,
    val powerMeterCount: Int,
    val isStravaSubscriber: Boolean = false
)

/**
 * Event emitted when an athlete qualifies for an N+1 Garage upgrade prompt.
 */
data class GarageNudgeEvent(
    val league: GarageLeague,
    val stats: GarageStats,
    val recommendedPlanId: String,
    val currentTier: SupporterTier
)

/**
 * Persisted per-league cooldown and migration snapshot for bounded N+1 garage nudges (REQ-SET-115.1).
 */
data class GarageNudgeCooldownState(
    val nudgeCount: Int = 0,
    val lastNudgeTimestampMs: Long = 0L,
    val lastNudgeWorkoutCount: Int = 0,
    val legacyHasShown: Boolean = false
)

/**
 * Pure evaluation engine for N+1 garage league detection, threshold comparison,
 * tier monotonicity, warm-engagement gating, and bounded seasonal cooldowns
 * (REQ-SET-088.1, REQ-SET-088.2, REQ-SET-115.1).
 */
object GarageNudgeEvaluator {
    const val PLAN_GOLD = "gold-yearly"
    const val PLAN_DIAMOND = "diamond-yearly"
    const val MIN_FINISHED_WORKOUTS_FOR_NUDGE = 8
    const val RE_NUDGE_COOLDOWN_DAYS = 180L
    const val RE_NUDGE_COOLDOWN_MS = RE_NUDGE_COOLDOWN_DAYS * 24L * 60L * 60L * 1000L
    const val RE_NUDGE_MIN_WORKOUT_DELTA = 25
    const val MAX_NUDGES_PER_LEAGUE = 3

    /**
     * Evaluates garage league qualification based on threshold hierarchy (REQ-SET-088.1 / ATT-5570).
     * Diamond League takes strict precedence over Gold League.
     * Strava Pro subscribers qualify for Gold League unconditionally, or boost Gold hardware to Diamond League.
     */
    fun evaluateLeague(
        bikeCount: Int,
        shoeCount: Int,
        sensorCount: Int,
        powerMeterCount: Int,
        isStravaSubscriber: Boolean = false
    ): GarageLeague {
        val hardwareDiamond = bikeCount >= 4 || sensorCount >= 8 || powerMeterCount >= 2
        val hardwareGold = bikeCount >= 3 || shoeCount >= 4 || sensorCount >= 5 || powerMeterCount >= 1

        // Diamond League: hardwareDiamond OR (hardwareGold && isStravaSubscriber)
        if (hardwareDiamond || (hardwareGold && isStravaSubscriber)) {
            return GarageLeague.DIAMOND
        }
        // Gold League: hardwareGold OR isStravaSubscriber
        if (hardwareGold || isStravaSubscriber) {
            return GarageLeague.GOLD
        }
        return GarageLeague.NONE
    }

    fun evaluateLeague(stats: GarageStats): GarageLeague =
        evaluateLeague(stats.bikeCount, stats.shoeCount, stats.sensorCount, stats.powerMeterCount, stats.isStravaSubscriber)

    /**
     * Evaluates whether [state] permits presenting a nudge given [nowMs] and [finishedWorkoutCount].
     */
    fun isLeagueNudgeEligible(
        state: GarageNudgeCooldownState,
        nowMs: Long,
        finishedWorkoutCount: Int
    ): Boolean {
        val gateMet = finishedWorkoutCount >= MIN_FINISHED_WORKOUTS_FOR_NUDGE &&
            state.nudgeCount < MAX_NUDGES_PER_LEAGUE
        val firstNudgeEligible = state.nudgeCount == 0 && !state.legacyHasShown
        val cooldownMet = state.nudgeCount > 0 &&
            (nowMs - state.lastNudgeTimestampMs) >= RE_NUDGE_COOLDOWN_MS &&
            (finishedWorkoutCount - state.lastNudgeWorkoutCount) >= RE_NUDGE_MIN_WORKOUT_DELTA
        return gateMet && (firstNudgeEligible || cooldownMet)
    }

    /**
     * Determines whether an upgrade nudge dialog should trigger based on league qualification,
     * tier monotonicity, warm-engagement workout gate, and bounded seasonal cooldown (REQ-SET-088.2, REQ-SET-115.1).
     */
    fun shouldTriggerNudge(
        stats: GarageStats,
        currentTier: SupporterTier,
        finishedWorkoutCount: Int,
        nowMs: Long,
        goldState: GarageNudgeCooldownState,
        diamondState: GarageNudgeCooldownState
    ): GarageNudgeEvent? {
        val league = evaluateLeague(stats)
        val (targetTier, recommendedPlanId, leagueState) = when (league) {
            GarageLeague.DIAMOND -> Triple(SupporterTier.DIAMOND, PLAN_DIAMOND, diamondState)
            GarageLeague.GOLD -> Triple(SupporterTier.GOLD, PLAN_GOLD, goldState)
            GarageLeague.NONE -> return null
        }
        val eligible = currentTier.rank < targetTier.rank &&
            isLeagueNudgeEligible(leagueState, nowMs, finishedWorkoutCount)
        return if (eligible) {
            GarageNudgeEvent(
                league = league,
                stats = stats,
                recommendedPlanId = recommendedPlanId,
                currentTier = currentTier
            )
        } else {
            null
        }
    }
}

/**
 * Domain service querying persistent equipment, devices, and workout summary databases
 * to evaluate engagement-gated N+1 garage nudges (REQ-SET-088.3, REQ-SET-115.1).
 */
class GarageLeagueDetector @JvmOverloads constructor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val equipmentDbHelper: EquipmentDbHelper = EquipmentDbHelper.getInstance(context),
    private val devicesDbHelper: DevicesDatabaseManager = DevicesDatabaseManager.getInstance(context),
    private val settingsDataStore: SettingsDataStore = SettingsDataStore.getInstance(context),
    private val proFeatureManager: ProFeatureManager = ProFeatureManager.getInstance(context),
    private val workoutSummariesDbManager: WorkoutSummariesDatabaseManager? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val resolvedWorkoutSummariesDbManager: WorkoutSummariesDatabaseManager by lazy {
        workoutSummariesDbManager ?: WorkoutSummariesDatabaseManager.getInstance(context)
    }

    /**
     * Queries database counts to assemble a current [GarageStats] snapshot.
     */
    fun queryGarageStats(): GarageStats {
        val bikes = equipmentDbHelper.getEquipmentItems(BSportType.BIKE, false)
        val shoes = equipmentDbHelper.getEquipmentItems(BSportType.RUN, false)
        val remoteSensors = devicesDbHelper.allRemoteSensors ?: emptyList()
        val powerMeters = remoteSensors.count { it.deviceType == DeviceType.BIKE_POWER }

        return GarageStats(
            bikeCount = bikes.size,
            shoeCount = shoes.size,
            sensorCount = remoteSensors.size,
            powerMeterCount = powerMeters,
            isStravaSubscriber = TrainingApplication.isStravaSubscriber()
        )
    }

    /**
     * Evaluates the athlete's qualified [GarageLeague] in background IO without mutating
     * nudge cooldowns (REQ-SET-115.3).
     */
    suspend fun evaluateQualifiedLeague(): GarageLeague = withContext(ioDispatcher) {
        try {
            GarageNudgeEvaluator.evaluateLeague(queryGarageStats())
        } catch (_: Throwable) {
            GarageLeague.NONE
        }
    }

    /**
     * Evaluates garage state, warm-engagement workout threshold, and bounded seasonal cooldowns,
     * returning a [GarageNudgeEvent] if eligible, or null (REQ-SET-115.1).
     */
    suspend fun evaluateNudge(): GarageNudgeEvent? = withContext(ioDispatcher) {
        try {
            val finishedWorkoutCount = resolvedWorkoutSummariesDbManager.finishedWorkoutCount
            if (finishedWorkoutCount < GarageNudgeEvaluator.MIN_FINISHED_WORKOUTS_FOR_NUDGE) {
                return@withContext null
            }

            val nowMs = timeProvider()
            val goldState = settingsDataStore.goldGarageNudgeState.first()
            val diamondState = settingsDataStore.diamondGarageNudgeState.first()

            if (goldState.legacyHasShown && goldState.nudgeCount == 0) {
                settingsDataStore.seedLegacyGarageNudgeBaseline(GarageLeague.GOLD, nowMs, finishedWorkoutCount)
            }
            if (diamondState.legacyHasShown && diamondState.nudgeCount == 0) {
                settingsDataStore.seedLegacyGarageNudgeBaseline(GarageLeague.DIAMOND, nowMs, finishedWorkoutCount)
            }

            val stats = queryGarageStats()
            val effectiveTier = proFeatureManager.effectiveTier.value

            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = stats,
                currentTier = effectiveTier,
                finishedWorkoutCount = finishedWorkoutCount,
                nowMs = nowMs,
                goldState = goldState,
                diamondState = diamondState
            )
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Persists that the nudge was presented/dismissed for the given league level with timestamp and workout baseline.
     */
    suspend fun markNudgeShown(league: GarageLeague) {
        if (league == GarageLeague.NONE) return
        val nowMs = timeProvider()
        val finishedWorkoutCount = try {
            resolvedWorkoutSummariesDbManager.finishedWorkoutCount
        } catch (_: Throwable) {
            0
        }
        settingsDataStore.recordGarageNudgeShown(league, nowMs, finishedWorkoutCount)
    }
}

/**
 * Evaluates a post-workout garage nudge only when neither a Jubilee celebration nor a
 * Free-tier quota reconciliation dialog is active, marking the nudge shown only if
 * it remains unpreempted (REQ-SET-115.2).
 */
suspend fun evaluatePostWorkoutGarageNudgeIfUnpreempted(
    detector: GarageLeagueDetector,
    isJubileeActive: () -> Boolean,
    isQuotaSheetActive: () -> Boolean
): GarageNudgeEvent? {
    val event = if (!isJubileeActive() && !isQuotaSheetActive()) {
        detector.evaluateNudge()
    } else {
        null
    }
    return if (event != null && !isJubileeActive() && !isQuotaSheetActive()) {
        detector.markNudgeShown(event.league)
        event
    } else {
        null
    }
}
