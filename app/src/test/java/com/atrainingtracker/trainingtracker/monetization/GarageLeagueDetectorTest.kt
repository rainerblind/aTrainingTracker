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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test suite verifying N+1 garage league detection, threshold hierarchy,
 * tier monotonicity, and non-spam gating (TST-SET-088.1, TST-SET-088.2).
 */
class GarageLeagueDetectorTest {

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

        // Free, Pioneer, Bronze, Silver -> eligible for Gold nudge
        val freeNudge = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = goldStats,
            currentTier = SupporterTier.FREE,
            hasShownGold = false,
            hasShownDiamond = false
        )
        assertNotNull(freeNudge)
        assertEquals(GarageLeague.GOLD, freeNudge?.league)
        assertEquals(GarageNudgeEvaluator.PLAN_GOLD, freeNudge?.recommendedPlanId)

        val silverNudge = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = goldStats,
            currentTier = SupporterTier.SILVER,
            hasShownGold = false,
            hasShownDiamond = false
        )
        assertNotNull(silverNudge)

        // Gold, Diamond -> suppressed by monotonicity (already at or above Gold)
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.GOLD,
                hasShownGold = false,
                hasShownDiamond = false
            )
        )
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.DIAMOND,
                hasShownGold = false,
                hasShownDiamond = false
            )
        )
    }

    @Test
    fun shouldTriggerNudge_diamondMonotonicity() {
        val diamondStats = GarageStats(bikeCount = 4, shoeCount = 1, sensorCount = 3, powerMeterCount = 0)

        // Below Diamond -> eligible for Diamond nudge
        val goldUserNudge = GarageNudgeEvaluator.shouldTriggerNudge(
            stats = diamondStats,
            currentTier = SupporterTier.GOLD,
            hasShownGold = true,
            hasShownDiamond = false
        )
        assertNotNull(goldUserNudge)
        assertEquals(GarageLeague.DIAMOND, goldUserNudge?.league)
        assertEquals(GarageNudgeEvaluator.PLAN_DIAMOND, goldUserNudge?.recommendedPlanId)

        // Diamond -> suppressed by monotonicity
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = diamondStats,
                currentTier = SupporterTier.DIAMOND,
                hasShownGold = true,
                hasShownDiamond = false
            )
        )
    }

    @Test
    fun shouldTriggerNudge_nonSpamPersistence() {
        val goldStats = GarageStats(bikeCount = 3, shoeCount = 1, sensorCount = 2, powerMeterCount = 0)
        val diamondStats = GarageStats(bikeCount = 4, shoeCount = 1, sensorCount = 3, powerMeterCount = 0)

        // Once Gold flag is set, Gold nudge is suppressed
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = goldStats,
                currentTier = SupporterTier.FREE,
                hasShownGold = true,
                hasShownDiamond = false
            )
        )

        // Once Diamond flag is set, Diamond nudge is suppressed
        assertNull(
            GarageNudgeEvaluator.shouldTriggerNudge(
                stats = diamondStats,
                currentTier = SupporterTier.GOLD,
                hasShownGold = true,
                hasShownDiamond = true
            )
        )
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

        val mockBikes = listOf(
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>(),
            mockk<EquipmentDbHelper.EquipmentData>()
        )
        every { mockEquipmentDb.getEquipmentItems(BSportType.BIKE, false) } returns mockBikes
        every { mockEquipmentDb.getEquipmentItems(BSportType.RUN, false) } returns emptyList()
        every { mockDevicesDb.allRemoteSensors } returns emptyList()

        every { mockProFeatureManager.effectiveTier } returns MutableStateFlow(SupporterTier.FREE)
        every { mockSettingsDataStore.hasShownGoldGarageNudge } returns flowOf(false)
        every { mockSettingsDataStore.hasShownDiamondGarageNudge } returns flowOf(false)

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager
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

        coEvery { mockSettingsDataStore.setHasShownGoldGarageNudge(true) } returns Unit
        coEvery { mockSettingsDataStore.setHasShownDiamondGarageNudge(true) } returns Unit

        val detector = GarageLeagueDetector(
            context = mockContext,
            equipmentDbHelper = mockEquipmentDb,
            devicesDbHelper = mockDevicesDb,
            settingsDataStore = mockSettingsDataStore,
            proFeatureManager = mockProFeatureManager
        )

        detector.markNudgeShown(GarageLeague.GOLD)
        coVerify { mockSettingsDataStore.setHasShownGoldGarageNudge(true) }

        detector.markNudgeShown(GarageLeague.DIAMOND)
        coVerify { mockSettingsDataStore.setHasShownDiamondGarageNudge(true) }
    }
}
