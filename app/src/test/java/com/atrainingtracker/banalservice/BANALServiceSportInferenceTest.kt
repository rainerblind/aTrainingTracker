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

package com.atrainingtracker.banalservice

import android.content.Intent
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.odometry.MovingSpeedSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests verifying `BANALService` 4-tier `BSportType` precedence,
 * `getAuthoritativeBSportType()` anti-circular isolation, unified `getSportTypeId*`,
 * explicit `WALK`/`OTHER` sovereignty, and `RESET_ACCUMULATORS_INTENT` / `onDestroy()` hygiene
 * (`ATT-8891`, `REQ-FIL-030.3`, `TST-FIL-030.3`).
 */
// Linked test specs: TST-FIL-030
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BANALServiceSportInferenceTest {

    @After
    fun tearDown() {
        BANALService.setServiceInstanceForTesting(null)
        BANALService.setServiceContextForTesting(null)
        BANALService.setSensorManagerForTesting(null)
        setStaticDeviceManager(null)
        unmockkAll()
    }

    @Test
    fun fourTierPrecedence_userAndHardwareAlwaysOverrideKinematicInference() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(Intent()) as BANALService.BANALServiceComm
        val summary = MovingSpeedSummary(5.2, 5.0, 6.4, 60, 120.0)

        try {
            // Tier 4: Initially UNKNOWN
            assertEquals(BSportType.UNKNOWN, service.bSportType)
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)
            assertEquals(SportTypeDatabaseManager.getSportTypeId(BSportType.UNKNOWN), service.sportTypeId)

            // Tier 3: Kinematic BIKE activates when unselected & no external sensors
            service.setInferredSportTelemetry(BSportType.BIKE, summary)
            assertEquals(BSportType.BIKE, service.bSportType)
            assertEquals(BSportType.BIKE, BANALService.getActiveBSportType())
            assertEquals(BSportType.BIKE, binder.bSportType)
            assertEquals(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE), service.sportTypeId)
            assertEquals(summary, binder.movingSpeedSummary)
            // Anti-circular isolation: authoritative stays UNKNOWN!
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)
            assertEquals(BSportType.UNKNOWN, BANALService.getAuthoritativeActiveBSportType())

            // Tier 2: Paired external hardware sensor (RUN_SPEED) overrides Tier 3 BIKE
            val dmMock = mockk<DeviceManager>(relaxed = true)
            every { dmMock.sportType } returns BSportType.RUN
            setStaticDeviceManager(dmMock)
            assertEquals(BSportType.RUN, service.bSportType)
            assertEquals(BSportType.RUN, service.authoritativeBSportType)
            assertEquals(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN), service.sportTypeId)

            // Tier 1: Explicit user selection OTHER (BSportType.UNKNOWN, sportId=1) overrides Tier 2 & Tier 3!
            binder.setUserSelectedSportTypeId(1L)
            assertEquals(BSportType.UNKNOWN, service.bSportType)
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)
            assertEquals(BSportType.UNKNOWN, binder.userSelectedBSportType)
            assertEquals(1L, service.sportTypeId)
            assertEquals(1L, service.getSportTypeId(6.5))

            // Explicit user selection WALK (sportId=4, BSportType.RUN) also holds Tier 1 precedence
            binder.setUserSelectedSportTypeId(4L)
            assertEquals(BSportType.RUN, service.bSportType)
            assertEquals(4L, service.sportTypeId)
            assertEquals(4L, service.getSportTypeId(6.5))
        } finally {
            setStaticDeviceManager(null)
            controller.destroy()
        }
    }

    @Test
    fun resetAccumulatorsIntent_clearsTier3AndMovingSummaryWhilePreservingUserSelection() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(Intent()) as BANALService.BANALServiceComm
        val summary = MovingSpeedSummary(5.4, 5.1, 6.6, 90, 200.0)

        try {
            // Set user-selected OTHER (sportId=1, BSportType.UNKNOWN) AND inferred BIKE telemetry
            binder.setUserSelectedSportTypeId(1L)
            service.setInferredSportTelemetry(BSportType.BIKE, summary)
            assertNotNull(service.movingSpeedSummary)

            // Simulate RESET_ACCUMULATORS_INTENT broadcast at workout start
            service.mResetAccumulatorsReceiver.onReceive(service, Intent(BANALService.RESET_ACCUMULATORS_INTENT))

            // Tier 3 & moving summary cleared, while explicit user selection OTHER is preserved!
            assertNull(service.movingSpeedSummary)
            assertEquals(BSportType.UNKNOWN, binder.userSelectedBSportType)
            assertEquals(1L, service.sportTypeId)

            // Verify onDestroy() clears both Tier 1 and Tier 3 state
            controller.destroy()
            assertNull(service.userSelectedBSportType)
            assertNull(service.movingSpeedSummary)
            assertEquals(BSportType.UNKNOWN, service.bSportType)
        } catch (t: Throwable) {
            controller.destroy()
            throw t
        }
    }

    // TST-FIL-046.1: Tier-4 mResolvedBSportType precedence, anti-circular isolation, reset & fallback
    @Test
    fun tier4ResolvedBSportType_precedenceIsolationResetAndFallback_tstFil046_1() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(Intent()) as BANALService.BANALServiceComm
        val summary = MovingSpeedSummary(5.2, 5.0, 6.4, 60, 120.0)

        try {
            // 1. Tier 4 RUN activates effectiveBSportType while keeping getBSportType() & authoritative UNKNOWN
            binder.setResolvedBSportType(BSportType.RUN)
            assertEquals(BSportType.RUN, service.effectiveBSportType)
            assertEquals(BSportType.RUN, BANALService.getEffectiveActiveBSportType())
            assertEquals(BSportType.UNKNOWN, service.bSportType)
            assertEquals(BSportType.UNKNOWN, service.authoritativeBSportType)

            // 2. Tier 3 kinematic BIKE overrides Tier 4 RUN immediately
            service.setInferredSportTelemetry(BSportType.BIKE, summary)
            assertEquals(BSportType.BIKE, service.effectiveBSportType)
            assertEquals(BSportType.BIKE, BANALService.getEffectiveActiveBSportType())

            // 3. Clearing Tier 3 restores Tier 4 until reset; Tier 2 hardware overrides Tier 3/4
            service.setInferredSportTelemetry(BSportType.UNKNOWN, null)
            assertEquals(BSportType.RUN, service.effectiveBSportType)
            val dmMock = mockk<DeviceManager>(relaxed = true)
            every { dmMock.sportType } returns BSportType.BIKE
            setStaticDeviceManager(dmMock)
            assertEquals(BSportType.BIKE, service.effectiveBSportType)

            // 4. Tier 1 explicit user OTHER (sportId=1, UNKNOWN) strictly overrides Tier 2..4
            binder.setUserSelectedSportTypeId(1L)
            assertEquals(BSportType.UNKNOWN, service.effectiveBSportType)
            assertEquals(BSportType.UNKNOWN, BANALService.getEffectiveActiveBSportType())

            // 5. RESET_ACCUMULATORS_INTENT clears Tier 4 mResolvedBSportType while preserving Tier 1
            setStaticDeviceManager(null)
            service.mResetAccumulatorsReceiver.onReceive(service, Intent(BANALService.RESET_ACCUMULATORS_INTENT))
            assertEquals(BSportType.UNKNOWN, service.effectiveBSportType)
            assertEquals(1L, service.sportTypeId)
        } finally {
            setStaticDeviceManager(null)
            controller.destroy()
        }

        // 6. Watchpoint 2: activeInstance() == null falls back to cDeviceManager.getSportType() / UNKNOWN
        assertEquals(BSportType.UNKNOWN, BANALService.getEffectiveActiveBSportType())
        val fallbackDm = mockk<DeviceManager>(relaxed = true)
        every { fallbackDm.sportType } returns BSportType.RUN
        setStaticDeviceManager(fallbackDm)
        try {
            assertEquals(BSportType.RUN, BANALService.getEffectiveActiveBSportType())
        } finally {
            setStaticDeviceManager(null)
        }
    }

    private fun setStaticDeviceManager(dm: DeviceManager?) {
        val field = BANALService::class.java.getDeclaredField("cDeviceManager")
        field.isAccessible = true
        field.set(null, dm)
    }
}
