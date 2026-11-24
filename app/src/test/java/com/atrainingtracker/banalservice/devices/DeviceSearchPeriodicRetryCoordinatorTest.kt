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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [DeviceSearchPeriodicRetryCoordinator] verifying MIA-only periodic re-search
 * gating, clean give-up for never-connected sensors, workout-end session reset, identity guard,
 * and `15s/30s/1m/2m` backoff progression (`ATT-8965`, `ATT-9178`, `REQ-CON-052.2`, `TST-CON-052.2`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceSearchPeriodicRetryCoordinatorTest {

    private lateinit var context: Context
    private val scheduled = mutableListOf<Pair<Long, Runnable>>()
    private val cancelled = mutableListOf<Runnable>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        scheduled.clear()
        cancelled.clear()
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
    }

    private fun newCoordinator(): DeviceSearchPeriodicRetryCoordinator =
        DeviceSearchPeriodicRetryCoordinator(
            context = context,
            initialScheduleDelayed = { delayMs, runnable -> scheduled.add(delayMs to runnable) },
            initialCancelScheduled = { runnable -> cancelled.add(runnable) }
        )

    @Test
    fun testScheduleRetry_progressesThroughConfiguredPresetsAndResetsOnSuccess() {
        // Default 1m (60s): 32s -> 60s -> 60s
        val coordinator = newCoordinator()
        var retryInvocations = 0
        val defaultDelays = mutableListOf<Long>()
        repeat(3) {
            assertTrue(coordinator.scheduleRetryIfEnabled(42L) { retryInvocations++ })
            val (delayMs, runnable) = scheduled.last()
            defaultDelays.add(delayMs)
            runnable.run()
        }
        assertEquals(listOf(32_000L, 60_000L, 60_000L), defaultDelays)
        assertEquals(3, retryInvocations)

        // Reset on success then configure 2m (120s): 32s -> 64s -> 120s -> 120s
        assertTrue(coordinator.scheduleRetryIfEnabled(42L) { retryInvocations++ })
        val pendingRunnable = scheduled.last().second
        coordinator.onDeviceSearchSucceeded(42L)
        assertFalse(coordinator.hasPendingRetryForTesting(42L))
        assertNull(coordinator.getLastDelayMsForTesting(42L))
        assertTrue(cancelled.contains(pendingRunnable))

        SensorSearchSettings.setPeriodicResearchFinalPeriodSec(context, 120)
        val twoMinDelays = mutableListOf<Long>()
        repeat(4) {
            assertTrue(coordinator.scheduleRetryIfEnabled(42L) { retryInvocations++ })
            val (delayMs, runnable) = scheduled.last()
            twoMinDelays.add(delayMs)
            runnable.run()
        }
        assertEquals(listOf(32_000L, 64_000L, 120_000L, 120_000L), twoMinDelays)
    }

    @Test
    fun testScheduleRetry_whenDisabled_cancelsAndReturnsFalse() {
        val coordinator = newCoordinator()
        assertTrue(coordinator.scheduleRetryIfEnabled(7L) {})
        val firstRunnable = scheduled.last().second

        SensorSearchSettings.setPeriodicResearchEnabled(context, false)
        assertFalse(coordinator.scheduleRetryIfEnabled(7L) {})
        assertTrue(cancelled.contains(firstRunnable))
        assertFalse(coordinator.hasPendingRetryForTesting(7L))
    }

    @Test
    fun testShutDown_cancelsAllPendingAndSuppressesLateCallbacks() {
        val coordinator = newCoordinator()
        var invoked = 0
        assertTrue(coordinator.scheduleRetryIfEnabled(1L) { invoked++ })
        assertTrue(coordinator.scheduleRetryIfEnabled(2L) { invoked++ })
        val r1 = scheduled[0].second
        val r2 = scheduled[1].second

        coordinator.shutDown()
        assertTrue(cancelled.containsAll(listOf(r1, r2)))
        assertFalse(coordinator.scheduleRetryIfEnabled(3L) { invoked++ })

        r1.run()
        r2.run()
        assertEquals(0, invoked)
    }

    @Test
    fun testDisableWhilePending_clearsDelayAndInvokesGiveUpCallback() {
        val coordinator = newCoordinator()
        var retries = 0
        var gaveUp = 0

        assertTrue(coordinator.scheduleRetryIfEnabled(99L, onRetryDue = { retries++ }, onDisabledGiveUp = { gaveUp++ }))
        assertEquals(32_000L, coordinator.getLastDelayMsForTesting(99L))
        val pending = scheduled.last().second

        SensorSearchSettings.setPeriodicResearchEnabled(context, false)
        pending.run()

        assertEquals(0, retries)
        assertEquals(1, gaveUp)
        assertNull(coordinator.getLastDelayMsForTesting(99L))
        assertFalse(coordinator.hasPendingRetryForTesting(99L))
    }

    @Test
    fun testStaleRunnableIdentityCheck_doesNotRemoveReplacementOrInvokeRetry() {
        val coordinator = newCoordinator()
        var retries = 0

        assertTrue(coordinator.scheduleRetryIfEnabled(55L) { retries++ })
        val firstRunnable = scheduled[0].second

        coordinator.cancelDevice(55L)
        assertTrue(coordinator.scheduleRetryIfEnabled(55L) { retries++ })
        val replacementRunnable = scheduled[1].second

        firstRunnable.run()
        assertEquals(0, retries)
        assertTrue(coordinator.hasPendingRetryForTesting(55L))

        replacementRunnable.run()
        assertEquals(1, retries)
        assertFalse(coordinator.hasPendingRetryForTesting(55L))
    }

    @Test
    fun testHandleSearchStopped_neverConnectedDeviceGivesUpWithZeroTimers_whileMiaDeviceSchedulesRetry() {
        val coordinator = newCoordinator()
        val neverConnected = mockk<MyRemoteDevice>(relaxed = true)
        every { neverConnected.shouldKeepAliveOnSearchTimeout() } returns false
        every { neverConnected.hasConnectedInSession() } returns false

        val miaDevice = mockk<MyRemoteDevice>(relaxed = true)
        every { miaDevice.shouldKeepAliveOnSearchTimeout() } returns false
        every { miaDevice.hasConnectedInSession() } returns true
        every { miaDevice.isReceivingData } returns false

        val remoteDevices = mutableMapOf<Long, MyRemoteDevice>(201L to neverConnected, 202L to miaDevice)
        val deviceTries = mutableMapOf<MyRemoteDevice, Int>(neverConnected to 1, miaDevice to 1)
        val searchQueue = java.util.ArrayDeque<MyRemoteDevice>()
        var detachNever = 0
        var detachMia = 0

        coordinator.handleSearchStoppedForCurrentDevice(
            current = neverConnected,
            deviceId = 201L,
            success = false,
            remoteDevices = remoteDevices,
            deviceTries = deviceTries,
            searchQueue = searchQueue,
            currentlySearchingSupplier = { null },
            detachAndGiveUpDevice = { detachNever++ },
            searchNextRunnable = {}
        )
        assertEquals("Never-connected sensor must detach and give up cleanly", 1, detachNever)
        assertFalse("Never-connected sensor must NOT schedule periodic retry", coordinator.hasPendingRetryForTesting(201L))
        assertFalse("deviceTries must be cleared on give-up", deviceTries.containsKey(neverConnected))
        assertTrue(scheduled.isEmpty())

        coordinator.handleSearchStoppedForCurrentDevice(
            current = miaDevice,
            deviceId = 202L,
            success = false,
            remoteDevices = remoteDevices,
            deviceTries = deviceTries,
            searchQueue = searchQueue,
            currentlySearchingSupplier = { null },
            detachAndGiveUpDevice = { detachMia++ },
            searchNextRunnable = {}
        )
        assertEquals("MIA sensor must NOT detach while periodic retry is enabled", 0, detachMia)
        assertTrue("MIA sensor must schedule periodic retry", coordinator.hasPendingRetryForTesting(202L))
        assertEquals(32_000L, scheduled.single().first)
    }

    @Test
    fun testEnqueueDeviceForPeriodicResearch_enforcesIdentityGuardAndClearsDelayWhenSessionReset() {
        val coordinator = newCoordinator()
        var miaConnectedInSession = true
        val original = mockk<MyRemoteDevice>(relaxed = true)
        every { original.shouldKeepAliveOnSearchTimeout() } returns false
        every { original.hasConnectedInSession() } answers { miaConnectedInSession }
        every { original.isReceivingData } returns false

        val remoteDevices = mutableMapOf<Long, MyRemoteDevice>(301L to original)
        val deviceTries = mutableMapOf<MyRemoteDevice, Int>(original to 1)
        val searchQueue = java.util.ArrayDeque<MyRemoteDevice>()
        var detachCount = 0

        coordinator.handleSearchStoppedForCurrentDevice(
            current = original,
            deviceId = 301L,
            success = false,
            remoteDevices = remoteDevices,
            deviceTries = deviceTries,
            searchQueue = searchQueue,
            currentlySearchingSupplier = { null },
            detachAndGiveUpDevice = { detachCount++ },
            searchNextRunnable = {}
        )
        assertEquals(32_000L, coordinator.getLastDelayMsForTesting(301L))

        // Case 1: device was replaced in remoteDevices before timer fired -> identity guard ignores cleanly
        val replacement = mockk<MyRemoteDevice>(relaxed = true)
        every { replacement.hasConnectedInSession() } returns false
        remoteDevices[301L] = replacement
        scheduled.last().second.run()
        assertEquals("Replacement instance must NOT be detached by stale timer", 0, detachCount)
        assertTrue(searchQueue.isEmpty())

        // Case 2: same device instance had session connection state reset -> clears delay & detaches cleanly
        remoteDevices[301L] = original
        miaConnectedInSession = true
        coordinator.handleSearchStoppedForCurrentDevice(
            current = original,
            deviceId = 301L,
            success = false,
            remoteDevices = remoteDevices,
            deviceTries = deviceTries,
            searchQueue = searchQueue,
            currentlySearchingSupplier = { null },
            detachAndGiveUpDevice = { detachCount++ },
            searchNextRunnable = {}
        )
        miaConnectedInSession = false
        scheduled.last().second.run()
        assertEquals("Reset MIA device must detach when timer fires", 1, detachCount)
        assertNull("Stale delay state must be cleared on reset give-up", coordinator.getLastDelayMsForTesting(301L))
        assertTrue(searchQueue.isEmpty())
    }

    @Test
    fun testOnTrackingModeChanged_workoutEndCancelsPendingAndResetsOnlyDisconnectedDevices() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val coordinator = newCoordinator()

        val disconnectedMia = mockk<MyRemoteDevice>(relaxed = true)
        every { disconnectedMia.isReceivingData } returns false
        val connectedLive = mockk<MyRemoteDevice>(relaxed = true)
        every { connectedLive.isReceivingData } returns true
        val remoteDevices = mapOf(1L to disconnectedMia, 2L to connectedLive)

        assertTrue(coordinator.scheduleRetryIfEnabled(1L) {})
        assertTrue(coordinator.hasPendingRetryForTesting(1L))

        // Workout ends (TRACKING -> READY)
        coordinator.onTrackingModeChanged(TrackingMode.READY, remoteDevices)
        assertFalse(coordinator.hasPendingRetryForTesting(1L))
        verify(exactly = 1) { disconnectedMia.resetSessionConnectionState() }
        verify(exactly = 0) { connectedLive.resetSessionConnectionState() }

        // Subsequent READY -> READY does NOT reset pre-workout disconnected MIA sensors
        coordinator.onTrackingModeChanged(TrackingMode.READY, remoteDevices)
        verify(exactly = 1) { disconnectedMia.resetSessionConnectionState() }
    }

    /**
     * TST-SET-125.3: When !success && current.shouldKeepAliveOnSearchTimeout() is true, coordinator
     * checks keep-alive BEFORE tries <= 1, removes current from deviceTries, skips re-enqueue for
     * tries 2..N, schedules no periodic retry, and preserves multi-try re-enqueue when keep-alive is false.
     */
    @Test
    fun testHandleSearchStopped_keepAliveDevice_removesDeviceTriesAndSkipsTries2ToN_TST_SET_125_3() {
        val coordinator = newCoordinator()
        val keepAliveDevice = mockk<MyRemoteDevice>(relaxed = true)
        every { keepAliveDevice.shouldKeepAliveOnSearchTimeout() } returns true

        val remoteDevices = mutableMapOf<Long, MyRemoteDevice>(101L to keepAliveDevice)
        val deviceTries = mutableMapOf<MyRemoteDevice, Int>(keepAliveDevice to 3)
        val searchQueue = java.util.ArrayDeque<MyRemoteDevice>()
        var detachCount = 0

        coordinator.handleSearchStoppedForCurrentDevice(
            current = keepAliveDevice,
            deviceId = 101L,
            success = false,
            remoteDevices = remoteDevices,
            deviceTries = deviceTries,
            searchQueue = searchQueue,
            currentlySearchingSupplier = { null },
            detachAndGiveUpDevice = { detachCount++ },
            searchNextRunnable = {}
        )

        assertFalse("Keep-alive device must be removed from deviceTries", deviceTries.containsKey(keepAliveDevice))
        assertTrue("Keep-alive device must NOT be re-enqueued for tries 2..N", searchQueue.isEmpty())
        assertFalse("Keep-alive device must NOT schedule periodic retry", coordinator.hasPendingRetryForTesting(101L))
        assertEquals("Keep-alive device must NOT be detached", 0, detachCount)

        val readyDevice = mockk<MyRemoteDevice>(relaxed = true)
        every { readyDevice.shouldKeepAliveOnSearchTimeout() } returns false
        remoteDevices[102L] = readyDevice
        deviceTries[readyDevice] = 3

        coordinator.handleSearchStoppedForCurrentDevice(
            current = readyDevice,
            deviceId = 102L,
            success = false,
            remoteDevices = remoteDevices,
            deviceTries = deviceTries,
            searchQueue = searchQueue,
            currentlySearchingSupplier = { null },
            detachAndGiveUpDevice = { detachCount++ },
            searchNextRunnable = {}
        )

        assertEquals(2, deviceTries[readyDevice])
        assertEquals(1, searchQueue.size)
        assertTrue(searchQueue.contains(readyDevice))
        assertEquals(0, detachCount)
    }
}
