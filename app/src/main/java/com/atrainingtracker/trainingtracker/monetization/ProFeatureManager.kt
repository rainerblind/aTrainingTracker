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
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages Pro feature access, Supporter tiers, Pioneer early adopter eligibility,
 * and diagnostic debug overrides (REQ-SET-085, REQ-SET-086, REQ-SET-087).
 */
class ProFeatureManager @JvmOverloads constructor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore = SettingsDataStore.getInstance(context),
    private val workoutSummariesDatabaseManager: WorkoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val isDebugBuild: Boolean = BuildConfig.DEBUG
) {

    init {
        checkAndPersistPioneerEligibility()
    }

    /**
     * Eagerly verifies historical workouts in SQLite to detect and persist Pioneer status (REQ-SET-085).
     */
    fun checkAndPersistPioneerEligibility(): Job = coroutineScope.launch {
        try {
            val alreadyPioneer = settingsDataStore.isPioneerEarlyAdopter.first()
            if (!alreadyPioneer) {
                val qualifies = workoutSummariesDatabaseManager.hasPioneerEarlyAdopterWorkout()
                if (qualifies) {
                    settingsDataStore.setIsPioneerEarlyAdopter(true)
                }
            }
            val cachedYear = settingsDataStore.pioneerSinceYear.first()
            if (cachedYear == null) {
                val earliestYear = workoutSummariesDatabaseManager.getEarliestWorkoutYear()
                if (earliestYear != null) {
                    settingsDataStore.setPioneerSinceYear(earliestYear)
                }
            }
        } catch (e: Exception) {
            // Defensive guard against closed DB during early startup
        }
    }

    /**
     * Reactive StateFlow emitting the athlete's earliest workout year for Pioneer recognition (REQ-SET-085.3).
     */
    val pioneerSinceYear: StateFlow<Int?> = settingsDataStore.pioneerSinceYear.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    /**
     * Reactive StateFlow emitting the resolved effective SupporterTier based on the authoritative hierarchy:
     * 1. BuildConfig.DEBUG SupporterTierOverride (if not AUTO) (REQ-SET-087)
     * 2. Active Google Play Subscription tier if greater than PIONEER
     * 3. Permanent Lifetime Pioneer Status (REQ-SET-085)
     * 4. Active Google Play Subscription tier
     * 5. Fallback: FREE tier
     */
    val effectiveTier: StateFlow<SupporterTier> = combine(
        settingsDataStore.supporterTierOverride,
        settingsDataStore.isPioneerEarlyAdopter,
        settingsDataStore.supporterTier
    ) { override, isPioneer, subscriptionTier ->
        resolveEffectiveTier(override, isPioneer, subscriptionTier, isDebugBuild)
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = SupporterTier.FREE
    )

    /**
     * Reactive StateFlow emitting whether Pro privileges (unlimited sensor pairing, exclusive badge) are active.
     */
    val isProActive: StateFlow<Boolean> = combine(effectiveTier) { tier ->
        tier.first().isPro
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    /**
     * Evaluates whether pairing another device is permitted under the current quota (REQ-SET-083).
     *
     * @param currentPairedCount Total devices currently marked paired in SQLite.
     * @return true if pairing is allowed, false if quota is reached on Free tier.
     */
    fun isDevicePairingAllowed(currentPairedCount: Int): Boolean {
        if (effectiveTier.value.isPro) {
            return true
        }
        return currentPairedCount < DevicesDatabaseManager.MAX_FREE_PAIRED_DEVICES
    }

    companion object {
        private var instance: ProFeatureManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): ProFeatureManager {
            return instance ?: ProFeatureManager(context.applicationContext ?: context).also {
                instance = it
            }
        }

        @VisibleForTesting
        fun setInstanceForTesting(manager: ProFeatureManager?) {
            instance = manager
        }

        /**
         * Pure deterministic resolution function mapping preference state to effective SupporterTier.
         */
        @VisibleForTesting
        fun resolveEffectiveTier(
            override: SupporterTierOverride,
            isPioneer: Boolean,
            subscriptionTier: SupporterTier,
            isDebug: Boolean
        ): SupporterTier {
            if (isDebug) {
                val forcedTier = override.toSupporterTier()
                if (forcedTier != null) {
                    return forcedTier
                }
            }
            if (isPioneer) {
                return if (subscriptionTier.rank > SupporterTier.PIONEER.rank) {
                    subscriptionTier
                } else {
                    SupporterTier.PIONEER
                }
            }
            return subscriptionTier
        }
    }
}
