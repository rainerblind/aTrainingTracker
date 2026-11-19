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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit verification for [PolarRetrySyncCoordinator] (`TST-SET-121.1`, `TST-SET-121.3`, `REQ-SET-119.1`).
 */
class PolarRetrySyncCoordinatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        PolarRetrySyncCoordinator.resetForTesting()
        PolarRetrySyncCoordinator.dispatcher = Dispatchers.Unconfined
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
    }

    @After
    fun tearDown() {
        PolarRetrySyncCoordinator.resetForTesting()
    }

    @Test
    fun testStartSync_deduplicatesConcurrentRequestsAndEmitsCompletion() = runBlocking {
        val gate = CompletableDeferred<Boolean>()
        val invocations = AtomicInteger(0)
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ ->
            invocations.incrementAndGet()
            gate.await()
        }

        val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) {
                PolarRetrySyncCoordinator.completedWorkoutEvents.first()
            }
        }

        val firstJob = PolarRetrySyncCoordinator.startSync(
            context = context,
            workoutId = 42L,
            polarExerciseId = "verity:/U/0/HR.BPB",
            failedMessage = "Sync failed"
        )
        val duplicateJob = PolarRetrySyncCoordinator.startSync(
            context = context,
            workoutId = 42L,
            polarExerciseId = "verity:/U/0/HR.BPB",
            failedMessage = "Sync failed"
        )

        assertNotNull(firstJob)
        assertNull(duplicateJob)
        assertEquals(1, invocations.get())
        assertTrue(PolarRetrySyncCoordinator.stateFor(42L).isSyncing)
        assertNull(PolarRetrySyncCoordinator.stateFor(42L).errorMessage)

        gate.complete(true)
        withTimeout(5_000L) { firstJob!!.join() }

        assertFalse(PolarRetrySyncCoordinator.stateFor(42L).isSyncing)
        assertNull(PolarRetrySyncCoordinator.stateFor(42L).errorMessage)
        assertEquals(42L, completionDeferred.await())
    }

    @Test
    fun testStartSync_failureSetsErrorAndClearsOnSuccessfulRetry() = runBlocking {
        var shouldSucceed = false
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ -> shouldSucceed }

        val failedJob = PolarRetrySyncCoordinator.startSync(
            context = context,
            workoutId = 77L,
            polarExerciseId = "verity:",
            failedMessage = "Could not connect"
        )
        withTimeout(5_000L) { failedJob!!.join() }

        val failedState = PolarRetrySyncCoordinator.stateFor(77L)
        assertFalse(failedState.isSyncing)
        assertEquals("Could not connect", failedState.errorMessage)

        shouldSucceed = true
        val retryJob = PolarRetrySyncCoordinator.startSync(
            context = context,
            workoutId = 77L,
            polarExerciseId = "verity:",
            failedMessage = "Could not connect"
        )
        withTimeout(5_000L) { retryJob!!.join() }

        val recoveredState = PolarRetrySyncCoordinator.stateFor(77L)
        assertFalse(recoveredState.isSyncing)
        assertNull(recoveredState.errorMessage)
    }

    @Test
    fun testStartSync_cancellationResetsSyncingWithoutFailureBanner() = runBlocking {
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ ->
            throw CancellationException("Cancelled externally")
        }

        val job = PolarRetrySyncCoordinator.startSync(
            context = context,
            workoutId = 88L,
            polarExerciseId = "/exercises/1",
            failedMessage = "Failed"
        )
        withTimeout(5_000L) { job!!.join() }

        val state = PolarRetrySyncCoordinator.stateFor(88L)
        assertFalse(state.isSyncing)
        assertNull(state.errorMessage)
    }
}
