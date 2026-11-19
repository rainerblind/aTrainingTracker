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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.odometry.MovingSpeedSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying moving-speed sport/equipment discovery, `>= 10` moving-sample sparse floor,
 * `UNKNOWN`/`RUN` `movingMeanMps` protection against `p90` spikes, `BIKE` errand ride cruising speed,
 * and explicit `WALK`/`OTHER` snapshot locking (`ATT-8891`, `REQ-FIL-030.4`, `TST-FIL-030.4`).
 */
// Linked test specs: TST-FIL-030
class EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest {

    @After
    fun tearDown() {
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun resolveEffectiveSpeedForIdentity_enforcesSparseSampleFloor() {
        val sparse = MovingSpeedSummary(movingMeanMps = 6.0, p50Mps = 5.8, p90Mps = 6.9, movingSampleCount = 9)
        assertEquals(
            1.2,
            EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.BIKE, 1.2, sparse),
            1e-6
        )
        assertEquals(
            0.0,
            EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.UNKNOWN, 0.0, null),
            1e-6
        )
    }

    @Test
    fun resolveEffectiveSpeedForIdentity_usesMovingMeanForUnknownAndRun_neverP90() {
        // Fast 4.3-5.6 m/s interval runner whose p90 is 5.6 m/s but movingMean is 3.6 m/s
        val fastRun = MovingSpeedSummary(movingMeanMps = 3.6, p50Mps = 3.5, p90Mps = 5.6, movingSampleCount = 180)
        assertEquals(
            3.6,
            EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.UNKNOWN, 3.2, fastRun),
            1e-6
        )
        assertEquals(
            3.6,
            EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.RUN, 3.2, fastRun),
            1e-6
        )
    }

    @Test
    fun resolveEffectiveSpeedForIdentity_errandBikeRidesResolveToCyclingCruisingSpeed() {
        // Metzger, Bäcker, und Knittel: session avg 1.35 m/s, p50 3.88 m/s, p90 6.34 m/s
        val metzger = MovingSpeedSummary(movingMeanMps = 4.10, p50Mps = 3.88, p90Mps = 6.34, movingSampleCount = 900, coastingSeconds = 416.0)
        val metzgerSpeed = EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.BIKE, 1.35, metzger)
        assertEquals(5.11, metzgerSpeed, 1e-2)

        // Norma #9: session avg 2.34 m/s, p50 5.60 m/s, p90 6.66 m/s
        val norma = MovingSpeedSummary(movingMeanMps = 5.50, p50Mps = 5.60, p90Mps = 6.66, movingSampleCount = 600, coastingSeconds = 343.0)
        val normaSpeed = EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.BIKE, 2.34, norma)
        assertEquals(6.13, normaSpeed, 1e-2)

        // Zum Bäcker #57: session avg 3.19 m/s, p50 5.04 m/s, p90 6.65 m/s
        val baecker = MovingSpeedSummary(movingMeanMps = 5.10, p50Mps = 5.04, p90Mps = 6.65, movingSampleCount = 400, coastingSeconds = 46.0)
        val baeckerSpeed = EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(BSportType.BIKE, 3.19, baecker)
        assertEquals(5.845, baeckerSpeed, 1e-2)
    }

    @Test
    fun resolveIdentityOverload_errandBikeRideMatchesEnduranceBikeAndGear() {
        val ctx = mockk<Context>(relaxed = true)
        val sportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
        val activeDevices = mockk<ActiveDevicesDbHelper>(relaxed = true)
        val equipDb = mockk<EquipmentDbHelper>(relaxed = true)
        val linkMgr = mockk<SportTypeEquipmentLinkManager>(relaxed = true)
        val summaries = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)

        val enduranceSportId = 30L
        val bikeEquipmentId = 77L
        every { sportDb.getSportTypesIdList(BSportType.BIKE, match { it >= 5.0 }) } returns listOf(enduranceSportId)
        every { sportDb.getSportTypesIdList(BSportType.BIKE, match { it < 2.0 }) } returns emptyList()
        every { sportDb.getBSportType(enduranceSportId) } returns BSportType.BIKE
        every { sportDb.getStravaName(enduranceSportId) } returns "Ride"
        every { linkMgr.getEquipmentIdsForSport(enduranceSportId) } returns listOf(bikeEquipmentId)
        every { equipDb.isEquipmentRetired(bikeEquipmentId) } returns false

        val manager = EquipmentAndSportTypeDiscoveryManager(
            context = ctx,
            sportTypeManager = sportDb,
            activeDevicesHelper = activeDevices,
            equipmentDbHelper = equipDb,
            sportTypeEquipmentLinkHelper = linkMgr,
            workoutSummariesManager = summaries
        )

        val metzger = MovingSpeedSummary(4.10, 3.88, 6.34, 900, 416.0)
        val identity = manager.resolveIdentity(emptySet(), BSportType.BIKE, 1.35, metzger)
        assertEquals(enduranceSportId, identity.sportId)
        assertEquals(BSportType.BIKE, identity.bSportType)
        assertEquals(bikeEquipmentId, identity.equipmentId)
        assertEquals(setOf(BSportType.BIKE), manager.getCandidateBSportTypes(BSportType.BIKE, 1.35, metzger))
    }

    @Test
    fun resolveSnapshotSportTypeId_honorsExplicitWalkAndHandlesRelaxedMock() {
        val ctx = mockk<Context>(relaxed = true)
        val discoveryMock = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(discoveryMock)

        // Case 1: Explicit WALK (userSelectedSport = BSportType.UNKNOWN, sportTypeId = 4L)
        val walkBanal = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { walkBanal.sportTypeId } returns 4L
        val metzger = MovingSpeedSummary(4.10, 3.88, 6.34, 900, 416.0)
        val walkId = EquipmentAndSportTypeDiscoveryManager.resolveSnapshotSportTypeId(
            ctx, walkBanal, emptySet(), BSportType.UNKNOWN, BSportType.UNKNOWN, 1.35, true, metzger
        )
        assertEquals(4L, walkId)
        verify(exactly = 0) { discoveryMock.resolveSportType(any(), any(), any()) }

        // Case 2: Relaxed mock returns 0L for sportTypeId -> safely falls through to discoveryManager
        val relaxedBanal = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { relaxedBanal.sportTypeId } returns 0L
        every { discoveryMock.resolveSportType(emptySet(), BSportType.BIKE, match { it > 5.0 }) } returns 3L
        val bikeId = EquipmentAndSportTypeDiscoveryManager.resolveSnapshotSportTypeId(
            ctx, relaxedBanal, emptySet(), BSportType.BIKE, BSportType.BIKE, 1.35, true, metzger
        )
        assertEquals(3L, bikeId)
    }
}
