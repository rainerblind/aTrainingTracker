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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit test suite verifying N+1 garage league detection, threshold hierarchy,
 * tier monotonicity, warm-engagement gating, bounded seasonal cooldowns, and
 * post-workout priority arbitration (TST-SET-088.1, TST-SET-088.2, TST-SET-117.1..3).
 */
// Linked test specs: TST-SET-117
class GarageLeagueDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun evaluateLeague_diamondPrecedence() {
        // >= 4 bikes -> DIAMOND
        assertEquals(
            GarageLeague.DIAMOND,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 4, shoeCount = 0, sensorCount = 0, powerMeterCount = 0)
        )

        // >= 8 sensors -> DIAMOND
        assertEquals(
            GarageLeague.DIAMOND,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 1, shoeCount = 1, sensorCount = 8, powerMeterCount = 0)
        )

        // >= 2 power meters -> DIAMOND
        assertEquals(
            GarageLeague.DIAMOND,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 2, shoeCount = 1, sensorCount = 3, powerMeterCount = 2)
        )

        // All criteria met -> DIAMOND strictly takes precedence over GOLD
        assertEquals(
            GarageLeague.DIAMOND,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 4, shoeCount = 5, sensorCount = 9, powerMeterCount = 2)
        )
    }

    @Test
    fun evaluateLeague_goldCriteria() {
        // >= 3 bikes -> GOLD
        assertEquals(
            GarageLeague.GOLD,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 3, shoeCount = 0, sensorCount = 0, powerMeterCount = 0)
        )

        // >= 4 shoes -> GOLD
        assertEquals(
            GarageLeague.GOLD,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 1, shoeCount = 4, sensorCount = 0, powerMeterCount = 0)
        )

        // >= 5 remote sensors -> GOLD
        assertEquals(
            GarageLeague.GOLD,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 1, shoeCount = 1, sensorCount = 5, powerMeterCount = 0)
        )

        // >= 1 power meter -> GOLD
        assertEquals(
            GarageLeague.GOLD,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 1, shoeCount = 1, sensorCount = 2, powerMeterCount = 1)
        )
    }

    @Test
    fun evaluateLeague_belowThreshold_returnsNone() {
        // Sub-threshold combinations
        assertEquals(
            GarageLeague.NONE,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 2, shoeCount = 3, sensorCount = 4, powerMeterCount = 0)
        )
        assertEquals(
            GarageLeague.NONE,
            GarageNudgeEvaluator.evaluateLeague(bikeCount = 0, shoeCount = 0, sensorCount = 0, powerMeterCount = 0)
        )
    }

    @Test
    fun evaluateLeague_stravaSubscriber_qualifiesGoldAndBoostsToDiamond() {
        // TST-SET-088.1 / ATT-5570
        // 1. Zero hardware + Strava subscriber -> GOLD
        val zeroHardwareSubscriber = GarageStats(
            bikeCount = 0,
            shoeCount = 0,
            sensorCount = 0,
            powerMeterCount = 0,
            isStravaSubscriber = true
        )
        assertEquals(GarageLeague.GOLD, GarageNudgeEvaluator.evaluateLeague(zeroHardwareSubscriber))

        // 2. Sub-threshold hardware + Strava subscriber -> GOLD
        val subThresholdSubscriber = GarageStats(
            bikeCount = 1,
            shoeCount = 2,
            sensorCount = 2,
            powerMeterCount = 0,
            isStravaSubscriber = true
        )
        assertEquals(GarageLeague.GOLD, GarageNudgeEvaluator.evaluateLeague(subThresholdSubscriber))

        // 3. Gold hardware (3 bikes) + Strava subscriber -> boosted to DIAMOND
        val goldHardwareSubscriber = GarageStats(
            bikeCount = 3,
            shoeCount = 0,
            sensorCount = 0,
            powerMeterCount = 0,
            isStravaSubscriber = true
        )
        assertEquals(GarageLeague.DIAMOND, GarageNudgeEvaluator.evaluateLeague(goldHardwareSubscriber))

        // 4. Gold hardware (4 shoes) + Strava subscriber -> boosted to DIAMOND
        val shoesHardwareSubscriber = GarageStats(
            bikeCount = 0,
            shoeCount = 4,
            sensorCount = 0,
            powerMeterCount = 0,
            isStravaSubscriber = true
        )
        assertEquals(GarageLeague.DIAMOND, GarageNudgeEvaluator.evaluateLeague(shoesHardwareSubscriber))

        // 5. Gold hardware (3 bikes) without Strava subscription -> remains GOLD
        val goldHardwareNonSubscriber = GarageStats(
            bikeCount = 3,
            shoeCount = 0,
            sensorCount = 0,
            powerMeterCount = 0,
            isStravaSubscriber = false
        )
        assertEquals(GarageLeague.GOLD, GarageNudgeEvaluator.evaluateLeague(goldHardwareNonSubscriber))

        // 6. Zero hardware without Strava subscription -> remains NONE
        val zeroHardwareNonSubscriber = GarageStats(
            bikeCount = 0,
            shoeCount = 0,
            sensorCount = 0,
            powerMeterCount = 0,
            isStravaSubscriber = false
        )
        assertEquals(GarageLeague.NONE, GarageNudgeEvaluator.evaluateLeague(zeroHardwareNonSubscriber))
    }

    @Test
    fun shouldTriggerNudge_goldMonotonicity() {
        val goldStats = GarageStats(bikeCount = 3, shoeCount = 1, sensorCount = 2, powerMeterCount = 0)
        val freshState = GarageNudgeCooldownState()
        val nowMs = 1_700_000_000_000L

        // Free, Pioneer, Bronze, Silver -> eligible for Gold nudge once engagement gate satisfied
        val freeNudge = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = goldStats,
            currentTier = SupporterTier.FREE,
            finishedWorkoutCount = 8,
            nowMs = nowMs,
            goldState = freshState,
            diamondState = freshState
        )
        assertNotNull(freeNudge)
        assertEquals(GarageLeague.GOLD, freeNudge?.league)
        assertEquals(GarageNudgeEvaluator.PLAN_GOLD, freeNudge?.recommendedPlanId)

        val silverNudge = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = goldStats,
            currentTier = SupporterTier.SILVER,
            finishedWorkoutCount = 10,
            nowMs = nowMs,
            goldState = freshState,
            diamondState = freshState
        )
        assertNotNull(silverNudge)

        // Gold, Diamond -> suppressed by monotonicity (already at or above Gold)
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.GOLD,
                finishedWorkoutCount = 10,
                nowMs = nowMs,
                goldState = freshState,
                diamondState = freshState
            )
        )
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.DIAMOND,
                finishedWorkoutCount = 10,
                nowMs = nowMs,
                goldState = freshState,
                diamondState = freshState
            )
        )
    }

    @Test
    fun shouldTriggerNudge_diamondMonotonicity() {
        val diamondStats = GarageStats(bikeCount = 4, shoeCount = 1, sensorCount = 3, powerMeterCount = 0)
        val shownGold = GarageNudgeCooldownState(
            nudgeCount = 1,
            lastNudgeTimestampMs = 1_000L,
            lastNudgeWorkoutCount = 8
        )
        val freshDiamond = GarageNudgeCooldownState()
        val nowMs = 1_700_000_000_000L

        // Below Diamond -> eligible for Diamond nudge
        val goldUserNudge = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = diamondStats,
            currentTier = SupporterTier.GOLD,
            finishedWorkoutCount = 10,
            nowMs = nowMs,
            goldState = shownGold,
            diamondState = freshDiamond
        )
        assertNotNull(goldUserNudge)
        assertEquals(GarageLeague.DIAMOND, goldUserNudge?.league)
        assertEquals(GarageNudgeEvaluator.PLAN_DIAMOND, goldUserNudge?.recommendedPlanId)

        // Diamond -> suppressed by monotonicity
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = diamondStats,
                currentTier = SupporterTier.DIAMOND,
                finishedWorkoutCount = 10,
                nowMs = nowMs,
                goldState = shownGold,
                diamondState = freshDiamond
            )
        )
    }

    @Test
    fun shouldTriggerNudge_nonSpamPersistence() {
        val goldStats = GarageStats(bikeCount = 3, shoeCount = 1, sensorCount = 2, powerMeterCount = 0)
        val diamondStats = GarageStats(bikeCount = 4, shoeCount = 1, sensorCount = 3, powerMeterCount = 0)
        val t0 = 1_700_000_000_000L
        val recentCooldown = GarageNudgeCooldownState(
            nudgeCount = 1,
            lastNudgeTimestampMs = t0,
            lastNudgeWorkoutCount = 10,
            legacyHasShown = true
        )

        // Immediately after Gold nudge, subsequent nudge is suppressed during cooldown
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.FREE,
                finishedWorkoutCount = 11,
                nowMs = t0 + 1_000L,
                goldState = recentCooldown,
                diamondState = GarageNudgeCooldownState()
            )
        )

        // Immediately after Diamond nudge, subsequent nudge is suppressed during cooldown
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = diamondStats,
                currentTier = SupporterTier.GOLD,
                finishedWorkoutCount = 11,
                nowMs = t0 + 1_000L,
                goldState = recentCooldown,
                diamondState = recentCooldown
            )
        )
    }

    @Test
    fun shouldTriggerNudge_requiresMinimumEightWorkouts_andNeverConsumesQuota() = runTest {
        // TST-SET-117.1
        val goldStats = GarageStats(bikeCount = 3, shoeCount = 1, sensorCount = 2, powerMeterCount = 1)
        val diamondStats = GarageStats(
            bikeCount = 4,
            shoeCount = 2,
            sensorCount = 8,
            powerMeterCount = 2,
            isStravaSubscriber = true
        )
        val freshState = GarageNudgeCooldownState()
        val nowMs = 1_700_000_000_000L

        for (count in listOf(0, 1, 7)) {
            assertNull(
                "Workouts=$count must suppress Gold nudge",
                GarageNudgeEvaluator.shouldTriggerNudge(
                    stats = goldStats,
                    currentTier = SupporterTier.FREE,
                    finishedWorkoutCount = count,
                    nowMs = nowMs,
                    goldState = freshState,
                    diamondState = freshState
                )
            )
            assertNull(
                "Workouts=$count must suppress Diamond nudge",
                GarageNudgeEvaluator.shouldTriggerNudge(
                    stats = diamondStats,
                    currentTier = SupporterTier.BRONZE,
                    finishedWorkoutCount = count,
                    nowMs = nowMs,
                    goldState = freshState,
                    diamondState = freshState
                )
            )
        }

        val mockContext = mockk<Context>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        val mockSettingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockProFeatureManager = mockk<ProFeatureManager>(relaxed = true)
        val mockWorkoutDb = mockk<WorkoutSummariesDatabaseManager>()
        every { mockWorkoutDb.finishedWorkoutCount } returns 7

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager,
            workoutSummariesDbManager = mockWorkoutDb,
            timeProvider = { nowMs }
        )

        assertNull(detector.evaluateNudge())
        coVerify(exactly = 0) { mockSettingsDataStore.recordGarageNudgeShown(any(), any(), any()) }
        coVerify(exactly = 0) { mockSettingsDataStore.seedLegacyGarageNudgeBaseline(any(), any(), any()) }
    }

    @Test
    fun shouldTriggerNudge_boundedSeasonalCooldown_requiresTimeAndWorkoutDelta() {
        // TST-SET-117.2
        val goldStats = GarageStats(bikeCount = 3, shoeCount = 0, sensorCount = 2, powerMeterCount = 1)
        val t0 = 1_700_000_000_000L
        val cooldownMs = GarageNudgeEvaluator.RE_NUDGE_COOLDOWN_MS
        val afterFirstNudge = GarageNudgeCooldownState(
            nudgeCount = 1,
            lastNudgeTimestampMs = t0,
            lastNudgeWorkoutCount = 10,
            legacyHasShown = true
        )

        // Only >= 180 days without >= 25 workouts -> null
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.BRONZE,
                finishedWorkoutCount = 34,
                nowMs = t0 + cooldownMs,
                goldState = afterFirstNudge,
                diamondState = GarageNudgeCooldownState()
            )
        )

        // Only >= 25 workouts without >= 180 days -> null
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.BRONZE,
                finishedWorkoutCount = 40,
                nowMs = t0 + cooldownMs - 1_000L,
                goldState = afterFirstNudge,
                diamondState = GarageNudgeCooldownState()
            )
        )

        // Both >= 180 days AND >= 25 workouts -> reminder #1 allowed
        val reminder1 = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = goldStats,
            currentTier = SupporterTier.BRONZE,
            finishedWorkoutCount = 35,
            nowMs = t0 + cooldownMs,
            goldState = afterFirstNudge,
            diamondState = GarageNudgeCooldownState()
        )
        assertNotNull(reminder1)
        assertEquals(GarageLeague.GOLD, reminder1?.league)
    }

    @Test
    fun shouldTriggerNudge_boundedSeasonalCooldown_capsAtMaxNudges() {
        // TST-SET-117.2
        val goldStats = GarageStats(bikeCount = 3, shoeCount = 0, sensorCount = 2, powerMeterCount = 1)
        val t0 = 1_700_000_000_000L
        val cooldownMs = GarageNudgeEvaluator.RE_NUDGE_COOLDOWN_MS

        // Reminder #2 allowed when nudgeCount == 2 and both thresholds satisfied again
        val afterSecondNudge = GarageNudgeCooldownState(
            nudgeCount = 2,
            lastNudgeTimestampMs = t0 + cooldownMs,
            lastNudgeWorkoutCount = 35,
            legacyHasShown = true
        )
        assertNotNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.BRONZE,
                finishedWorkoutCount = 60,
                nowMs = t0 + (2 * cooldownMs),
                goldState = afterSecondNudge,
                diamondState = GarageNudgeCooldownState()
            )
        )

        // Hard cap reached at MAX_NUDGES_PER_LEAGUE = 3 -> permanently suppressed
        val cappedState = GarageNudgeCooldownState(
            nudgeCount = 3,
            lastNudgeTimestampMs = t0,
            lastNudgeWorkoutCount = 10,
            legacyHasShown = true
        )
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.BRONZE,
                finishedWorkoutCount = 500,
                nowMs = t0 + (10 * cooldownMs),
                goldState = cappedState,
                diamondState = GarageNudgeCooldownState()
            )
        )
    }

    @Test
    fun evaluateNudge_migratesLegacyHasShownFlagSafely() = runTest {
        // TST-SET-117.2
        val mockContext = mockk<Context>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>()
        val mockDevicesDb = mockk<DevicesDatabaseManager>()
        val mockSettingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockProFeatureManager = mockk<ProFeatureManager>()
        val mockWorkoutDb = mockk<WorkoutSummariesDatabaseManager>()
        val nowMs = 1_700_000_000_000L

        every { mockWorkoutDb.finishedWorkoutCount } returns 12
        every { mockEquipmentDb.getEquipmentItems(BSportType.BIKE, false) } returns listOf(
            mockk(), mockk(), mockk()
        )
        every { mockEquipmentDb.getEquipmentItems(BSportType.RUN, false) } returns emptyList()
        every { mockDevicesDb.allRemoteSensors } returns emptyList()
        every { mockProFeatureManager.effectiveTier } returns MutableStateFlow(SupporterTier.BRONZE)
        every { mockSettingsDataStore.goldGarageNudgeState } returns flowOf(
            GarageNudgeCooldownState(nudgeCount = 0, legacyHasShown = true)
        )
        every { mockSettingsDataStore.diamondGarageNudgeState } returns flowOf(GarageNudgeCooldownState())

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager,
            workoutSummariesDbManager = mockWorkoutDb,
            timeProvider = { nowMs }
        )

        assertNull(detector.evaluateNudge())
        coVerify(exactly = 1) {
            mockSettingsDataStore.seedLegacyGarageNudgeBaseline(GarageLeague.GOLD, nowMs, 12)
        }
    }

    @Test
    fun evaluatePostWorkoutGarageNudgeIfUnpreempted_yieldsToJubileeAndQuota() = runTest {
        // TST-SET-117.3
        val event = GarageNudgeEvent(
            league = GarageLeague.GOLD,
            stats = GarageStats(bikeCount = 3, shoeCount = 0, sensorCount = 2, powerMeterCount = 1),
            recommendedPlanId = GarageNudgeEvaluator.PLAN_GOLD,
            currentTier = SupporterTier.BRONZE
        )
        val mockDetector = mockk<GarageLeagueDetector>(relaxed = true)
        coEvery { mockDetector.evaluateNudge() } returns event

        // Suppressed when Jubilee is active
        assertNull(
            evaluatePostWorkoutGarageNudgeIfUnpreempted(
                detector = mockDetector,
                isJubileeActive = { true },
                isQuotaSheetActive = { false }
            )
        )
        // Suppressed when Quota reconciliation sheet is active
        assertNull(
            evaluatePostWorkoutGarageNudgeIfUnpreempted(
                detector = mockDetector,
                isJubileeActive = { false },
                isQuotaSheetActive = { true }
            )
        )
        coVerify(exactly = 0) { mockDetector.markNudgeShown(any()) }

        // Emits and marks shown when neither dialog is active
        val result = evaluatePostWorkoutGarageNudgeIfUnpreempted(
            detector = mockDetector,
            isJubileeActive = { false },
            isQuotaSheetActive = { false }
        )
        assertEquals(event, result)
        coVerify(exactly = 1) { mockDetector.markNudgeShown(GarageLeague.GOLD) }
    }

    @Test
    fun settingsDataStore_recordSeedAndResetGarageNudgeFlags_roundTrip() = runTest {
        // TST-SET-117.2
        val testFile = tempFolder.newFile("garage_nudge_${System.nanoTime()}.preferences_pb")
        val rawDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { testFile }
        )
        val store = SettingsDataStore.createForTesting(mockk(relaxed = true), rawDataStore)

        // Legacy flag migration
        store.setHasShownGoldGarageNudge(true)
        store.seedLegacyGarageNudgeBaseline(GarageLeague.GOLD, timestampMs = 5_000L, workoutCount = 14)
        val migrated = store.goldGarageNudgeState.first()
        assertEquals(1, migrated.nudgeCount)
        assertEquals(5_000L, migrated.lastNudgeTimestampMs)
        assertEquals(14, migrated.lastNudgeWorkoutCount)
        assertTrue(migrated.legacyHasShown)

        // Recording another nudge increments count and updates timestamp + workout baseline
        store.recordGarageNudgeShown(GarageLeague.GOLD, timestampMs = 99_000L, workoutCount = 42)
        val recorded = store.goldGarageNudgeState.first()
        assertEquals(2, recorded.nudgeCount)
        assertEquals(99_000L, recorded.lastNudgeTimestampMs)
        assertEquals(42, recorded.lastNudgeWorkoutCount)

        // Debug reset clears both legacy and cooldown keys
        store.resetGarageNudgeFlags()
        val resetGold = store.goldGarageNudgeState.first()
        assertEquals(0, resetGold.nudgeCount)
        assertEquals(0L, resetGold.lastNudgeTimestampMs)
        assertEquals(0, resetGold.lastNudgeWorkoutCount)
        assertFalse(resetGold.legacyHasShown)
        assertFalse(store.hasShownGoldGarageNudge.first())
    }

    @Test
    fun queryGarageStats_aggregatesCounts() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>()
        val mockDevicesDb = mockk<DevicesDatabaseManager>()
        val mockSettingsDataStore = mockk<SettingsDataStore>()
        val mockProFeatureManager = mockk<ProFeatureManager>()

        // 3 bikes, 2 shoes
        val mockBikes = listOf(
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>()
        )
        val mockShoes = listOf(
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>()
        )
        every { mockEquipmentDb.getEquipmentItems(BSportType.BIKE, false) } returns mockBikes
        every { mockEquipmentDb.getEquipmentItems(BSportType.RUN, false) } returns mockShoes

        // 5 remote sensors, 1 power meter
        val mockSensors = listOf(
            DevicesDatabaseManager.SimpleSensorInfo(1L, "HRM", DeviceType.HRM),
            DevicesDatabaseManager.SimpleSensorInfo(2L, "Speed", DeviceType.BIKE_SPEED),
            DevicesDatabaseManager.SimpleSensorInfo(3L, "Cadence", DeviceType.BIKE_CADENCE),
            DevicesDatabaseManager.SimpleSensorInfo(4L, "Power", DeviceType.BIKE_POWER),
            DevicesDatabaseManager.SimpleSensorInfo(5L, "Environment", DeviceType.ENVIRONMENT)
        )
        every { mockDevicesDb.allRemoteSensors } returns mockSensors

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager
        )

        val stats = detector.queryGarageStats()
        assertEquals(3, stats.bikeCount)
        assertEquals(2, stats.shoeCount)
        assertEquals(5, stats.sensorCount)
        assertEquals(1, stats.powerMeterCount)
    }

    @Test
    fun evaluateNudge_detectsGoldQualification() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>()
        val mockDevicesDb = mockk<DevicesDatabaseManager>()
        val mockSettingsDataStore = mockk<SettingsDataStore>()
        val mockProFeatureManager = mockk<ProFeatureManager>()
        val mockWorkoutDb = mockk<WorkoutSummariesDatabaseManager>()

        val mockBikes = listOf(
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>()
        )
        every { mockWorkoutDb.finishedWorkoutCount } returns 10
        every { mockEquipmentDb.getEquipmentItems(BSportType.BIKE, false) } returns mockBikes
        every { mockEquipmentDb.getEquipmentItems(BSportType.RUN, false) } returns emptyList()
        every { mockDevicesDb.allRemoteSensors } returns emptyList()

        every { mockProFeatureManager.effectiveTier } returns MutableStateFlow(SupporterTier.FREE)
        every { mockSettingsDataStore.goldGarageNudgeState } returns flowOf(GarageNudgeCooldownState())
        every { mockSettingsDataStore.diamondGarageNudgeState } returns flowOf(GarageNudgeCooldownState())

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager,
            workoutSummariesDbManager = mockWorkoutDb,
            timeProvider = { 1_700_000_000_000L }
        )

        val nudge = detector.evaluateNudge()
        assertNotNull(nudge)
        assertEquals(GarageLeague.GOLD, nudge?.league)
        assertEquals(GarageNudgeEvaluator.PLAN_GOLD, nudge?.recommendedPlanId)
    }

    @Test
    fun markNudgeShown_persistsFlags() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        val mockSettingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockProFeatureManager = mockk<ProFeatureManager>(relaxed = true)
        val mockWorkoutDb = mockk<WorkoutSummariesDatabaseManager>()

        every { mockWorkoutDb.finishedWorkoutCount } returns 12
        coEvery { mockSettingsDataStore.recordGarageNudgeShown(any(), any(), any()) } returns Unit

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager,
            workoutSummariesDbManager = mockWorkoutDb,
            timeProvider = { 1_700_000_000_000L }
        )

        detector.markNudgeShown(GarageLeague.GOLD)
        coVerify { mockSettingsDataStore.recordGarageNudgeShown(GarageLeague.GOLD, 1_700_000_000_000L, 12) }

        detector.markNudgeShown(GarageLeague.DIAMOND)
        coVerify { mockSettingsDataStore.recordGarageNudgeShown(GarageLeague.DIAMOND, 1_700_000_000_000L, 12) }
    }
}
