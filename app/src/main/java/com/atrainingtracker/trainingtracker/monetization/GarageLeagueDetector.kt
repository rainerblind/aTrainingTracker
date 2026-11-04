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
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
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
    val powerMeterCount: Int
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
 * Pure evaluation engine for N+1 garage league detection, threshold comparison,
 * tier monotonicity, and non-spam persistence gating (REQ-SET-088.1, REQ-SET-088.2, REQ-SET-088.4).
 */
object GarageNudgeEvaluator {
    const val PLAN_GOLD = "gold-yearly"
    const val PLAN_DIAMOND = "diamond-yearly"

    /**
     * Evaluates garage league qualification based on threshold hierarchy (REQ-SET-088.1).
     * Diamond League takes strict precedence over Gold League.
     */
    fun evaluateLeague(
        bikeCount: Int,
        shoeCount: Int,
        sensorCount: Int,
        powerMeterCount: Int
    ): GarageLeague {
        // Diamond League: >= 4 bikes OR >= 8 remote sensors OR >= 2 power meters
        if (bikeCount >= 4 || sensorCount >= 8 || powerMeterCount >= 2) {
            return GarageLeague.DIAMOND
        }
        // Gold League: >= 3 bikes OR >= 4 shoes OR >= 5 remote sensors OR >= 1 power meter
        if (bikeCount >= 3 || shoeCount >= 4 || sensorCount >= 5 || powerMeterCount >= 1) {
            return GarageLeague.GOLD
        }
        return GarageLeague.NONE
    }

    fun evaluateLeague(stats: GarageStats): GarageLeague =
        evaluateLeague(stats.bikeCount, stats.shoeCount, stats.sensorCount, stats.powerMeterCount)

    /**
     * Determines whether an upgrade nudge dialog should trigger based on league qualification,
     * tier monotonicity, and persistent non-spam flags (REQ-SET-088.2, REQ-SET-088.4).
     */
    fun shouldTriggerNudge(
        stats: GarageStats,
        currentTier: SupporterTier,
        hasShownGold: Boolean,
        hasShownDiamond: Boolean
    ): GarageNudgeEvent? {
        val league = evaluateLeague(stats)
        return when (league) {
            GarageLeague.DIAMOND -> {
                if (currentTier.rank < SupporterTier.DIAMOND.rank && !hasShownDiamond) {
                    GarageNudgeEvent(
                        league = GarageLeague.DIAMOND,
                        stats = stats,
                        recommendedPlanId = PLAN_DIAMOND,
                        currentTier = currentTier
                    )
                } else null
            }
            GarageLeague.GOLD -> {
                if (currentTier.rank < SupporterTier.GOLD.rank && !hasShownGold) {
                    GarageNudgeEvent(
                        league = GarageLeague.GOLD,
                        stats = stats,
                        recommendedPlanId = PLAN_GOLD,
                        currentTier = currentTier
                    )
                } else null
            }
            GarageLeague.NONE -> null
        }
    }
}

/**
 * Domain service querying persistent equipment and devices databases to evaluate N+1 garage nudges (REQ-SET-088.3).
 */
class GarageLeagueDetector @JvmOverloads constructor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val equipmentDbHelper: EquipmentDbHelper = EquipmentDbHelper.getInstance(context),
    private val devicesDbHelper: DevicesDatabaseManager = DevicesDatabaseManager.getInstance(context),
    private val settingsDataStore: SettingsDataStore = SettingsDataStore.getInstance(context),
    private val proFeatureManager: ProFeatureManager = ProFeatureManager.getInstance(context)
) {
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
            powerMeterCount = powerMeters
        )
    }

    /**
     * Evaluates garage state and returns a [GarageNudgeEvent] if eligible, or null.
     */
    suspend fun evaluateNudge(): GarageNudgeEvent? = withContext(ioDispatcher) {
        try {
            val stats = queryGarageStats()
            val effectiveTier = proFeatureManager.effectiveTier.value
            val hasShownGold = settingsDataStore.hasShownGoldGarageNudge.first()
            val hasShownDiamond = settingsDataStore.hasShownDiamondGarageNudge.first()

            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = stats,
                currentTier = effectiveTier,
                hasShownGold = hasShownGold,
                hasShownDiamond = hasShownDiamond
            )
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Persists that the nudge was presented/dismissed for the given league level.
     */
    suspend fun markNudgeShown(league: GarageLeague) {
        when (league) {
            GarageLeague.GOLD -> settingsDataStore.setHasShownGoldGarageNudge(true)
            GarageLeague.DIAMOND -> settingsDataStore.setHasShownDiamondGarageNudge(true)
            GarageLeague.NONE -> Unit
        }
    }
}
