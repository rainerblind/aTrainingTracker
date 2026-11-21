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
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings
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
 * Unit tests for [DeviceSearchPeriodicRetryCoordinator] verifying extended re-search backoff
 * scheduling, disable toggle enforcement, and clean cancellation on connect/removal/shutdown
 * (`ATT-8965`, `REQ-CON-052.2`, `TST-CON-052.2`).
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
        scheduled.clear()
        cancelled.clear()
    }

    private fun newCoordinator(): DeviceSearchPeriodicRetryCoordinator =
        DeviceSearchPeriodicRetryCoordinator(
            context = context,
            initialScheduleDelayed = { delayMs, runnable -> scheduled.add(delayMs to runnable) },
            initialCancelScheduled = { runnable -> cancelled.add(runnable) }
        )

    @Test
    fun testScheduleRetry_progressesThroughExtendedStepsAndResetsOnSuccess() {
        val coordinator = newCoordinator()
        var retryInvocations = 0

        val delays = mutableListOf<Long>()
        repeat(5) {
            assertTrue(coordinator.scheduleRetryIfEnabled(42L) { retryInvocations++ })
            val (delayMs, runnable) = scheduled.last()
            delays.add(delayMs)
            runnable.run()
        }

        assertEquals(listOf(32_000L, 64_000L, 128_000L, 300_000L, 300_000L), delays)
        assertEquals(5, retryInvocations)

        // Next schedule starts pending timer, then search succeeds -> cancels and resets backoff
        assertTrue(coordinator.scheduleRetryIfEnabled(42L) { retryInvocations++ })
        assertTrue(coordinator.hasPendingRetryForTesting(42L))
        val pendingRunnable = scheduled.last().second

        coordinator.onDeviceSearchSucceeded(42L)
        assertFalse(coordinator.hasPendingRetryForTesting(42L))
        assertNull(coordinator.getLastDelayMsForTesting(42L))
        assertTrue(cancelled.contains(pendingRunnable))

        // Subsequent exhaustion starts cleanly at 32s again
        assertTrue(coordinator.scheduleRetryIfEnabled(42L) { retryInvocations++ })
        assertEquals(32_000L, scheduled.last().first)
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

        // Even if a stalled runnable fires after shutdown, callback MUST NOT execute
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

        // User disables periodic re-search while timer is queued
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

        // Stale firstRunnable runs asynchronously -> MUST NOT remove replacementRunnable or fire retry
        firstRunnable.run()
        assertEquals(0, retries)
        assertTrue(coordinator.hasPendingRetryForTesting(55L))

        replacementRunnable.run()
        assertEquals(1, retries)
        assertFalse(coordinator.hasPendingRetryForTesting(55L))
    }

    /**
     * TST-SET-125.3: When !success && current.shouldKeepAliveOnSearchTimeout() is true, coordinator
     * checks keep-alive BEFORE tries <= 1, removes current from deviceTries, skips re-enqueue for
     * tries 2..N, schedules no periodic retry, and preserves multi-try re-enqueue when keep-alive is false.
     */
    @Test
    fun testHandleSearchStopped_keepAliveDevice_removesDeviceTriesAndSkipsTries2ToN_TST_SET_125_3() {
        val coordinator = newCoordinator()
        val keepAliveDevice = io.mockk.mockk<MyRemoteDevice>(relaxed = true)
        io.mockk.every { keepAliveDevice.shouldKeepAliveOnSearchTimeout() } returns true

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

        // Contrast: non-keep-alive device (READY startup) with tries = 3 still decrements & re-enqueues
        val readyDevice = io.mockk.mockk<MyRemoteDevice>(relaxed = true)
        io.mockk.every { readyDevice.shouldKeepAliveOnSearchTimeout() } returns false
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
