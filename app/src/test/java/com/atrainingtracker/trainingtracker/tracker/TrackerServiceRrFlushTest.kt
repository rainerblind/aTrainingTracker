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

package com.atrainingtracker.trainingtracker.tracker

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit tests verifying thread safety, FIFO execution, and barrier completion
 * of live RR interval persistence during tracking and finalization.
 *
 * Traceability:
 * - Requirement: REQ-PER-034.1, REQ-PER-034.2, REQ-PER-034.3
 * - Test Specification: TST-PER-013
 * - Ticket: ATT-6716
 */
class TrackerServiceRrFlushTest {

    private class TestableTrackerService : TrackerService() {
        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun performStopSelf() {}
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun sendBroadcast(intent: Intent?) {}
        override fun getPackageName(): String = "com.atrainingtracker"
        override fun performSuperOnDestroy() {}
    }

    private lateinit var trackerService: TestableTrackerService
    private lateinit var mockRepository: WorkoutRrRepository
    private val insertedBatches = CopyOnWriteArrayList<List<WorkoutRrInterval>>()

    @Before
    fun setUp() {
        mockRepository = mockk(relaxed = true)
        insertedBatches.clear()

        every { mockRepository.insertIntervalsBlocking(any()) } answers {
            val list = firstArg<List<WorkoutRrInterval>>()
            insertedBatches.add(ArrayList(list))
            list.size
        }

        trackerService = TestableTrackerService()
        trackerService.mWorkoutRrRepository = mockRepository
        setWorkoutId(trackerService, 42L)
    }

    @After
    fun tearDown() {
        trackerService.onDestroy()
    }

    private fun setWorkoutId(service: TrackerService, workoutId: Long) {
        val field = TrackerService::class.java.getDeclaredField("mWorkoutID")
        field.isAccessible = true
        field.setLong(service, workoutId)
    }

    private fun createInterval(idx: Int, workoutId: Long = 42L): WorkoutRrInterval {
        return WorkoutRrInterval(
            id = null,
            workoutId = workoutId,
            beatIndex = idx,
            beatTimestampMs = 1000L + idx * 800L,
            rrMs = 800f,
            artifactFlag = 0,
            gapBeats = 0
        )
    }

    @Test
    fun testFlushLiveRrQueueSync_blocksUntilInFlightAsyncFlushCompletes() {
        val asyncStartLatch = CountDownLatch(1)
        val asyncBlockLatch = CountDownLatch(1)
        val asyncCompleted = AtomicBoolean(false)

        every { mockRepository.insertIntervalsBlocking(any()) } answers {
            val list = firstArg<List<WorkoutRrInterval>>()
            if (list.any { it.beatIndex == 1 }) {
                asyncStartLatch.countDown()
                asyncBlockLatch.await(5, TimeUnit.SECONDS)
                asyncCompleted.set(true)
            }
            insertedBatches.add(ArrayList(list))
            list.size
        }

        // Add 3 beats for async flush
        trackerService.liveRrQueueForTesting.add(createInterval(1))
        trackerService.liveRrQueueForTesting.add(createInterval(2))
        trackerService.liveRrQueueForTesting.add(createInterval(3))

        // Trigger async flush
        trackerService.flushLiveRrQueueAsync()

        // Wait until async task has begun execution
        assertTrue("Async task should start", asyncStartLatch.await(2, TimeUnit.SECONDS))
        assertFalse("Async task should be blocked", asyncCompleted.get())

        // In a separate thread, trigger flushLiveRrQueueSync
        val syncCompleted = AtomicBoolean(false)
        val syncThread = Thread {
            trackerService.flushLiveRrQueueSync(42L)
            syncCompleted.set(true)
        }
        syncThread.start()

        // Give sync thread time to reach lock/await
        Thread.sleep(50)
        assertFalse("Sync flush must NOT return while async flush is in-flight", syncCompleted.get())

        // Unblock async task
        asyncBlockLatch.countDown()

        // Now sync thread must complete
        syncThread.join(3000)
        assertFalse("Sync thread should not hang", syncThread.isAlive)
        assertTrue("Sync flush should be completed", syncCompleted.get())
        assertTrue("Async flush should be completed", asyncCompleted.get())

        val totalInserted = insertedBatches.sumOf { it.size }
        assertEquals(3, totalInserted)
    }

    @Test
    fun testFlushLiveRrQueueSync_interleavesRemainingQueueItemsSequentially() {
        // Enqueue batch 1
        trackerService.liveRrQueueForTesting.add(createInterval(1))
        trackerService.liveRrQueueForTesting.add(createInterval(2))

        // Trigger async flush
        trackerService.flushLiveRrQueueAsync()

        // Immediately add batch 2 while async flush is pending / running
        trackerService.liveRrQueueForTesting.add(createInterval(3))
        trackerService.liveRrQueueForTesting.add(createInterval(4))

        // Run sync flush
        trackerService.flushLiveRrQueueSync(42L)

        // Queue must now be drained completely
        assertTrue(trackerService.liveRrQueueForTesting.isEmpty())

        // All 4 beats must have been inserted
        val allInsertedBeats = insertedBatches.flatten()
        assertEquals(4, allInsertedBeats.size)
        assertEquals(listOf(1, 2, 3, 4), allInsertedBeats.map { it.beatIndex })
    }

    @Test
    fun testFlushLiveRrQueueSync_handlesEmptyQueueWithoutPendingFlushes() {
        trackerService.flushLiveRrQueueSync(42L)

        assertTrue(insertedBatches.isEmpty())
        assertTrue(trackerService.liveRrQueueForTesting.isEmpty())
    }

    @Test
    fun testFlushLiveRrQueueSync_flushesSynchronouslyWhenNoPriorAsyncFlush() {
        trackerService.liveRrQueueForTesting.add(createInterval(10))
        trackerService.liveRrQueueForTesting.add(createInterval(11))

        trackerService.flushLiveRrQueueSync(42L)

        assertTrue(trackerService.liveRrQueueForTesting.isEmpty())
        assertEquals(1, insertedBatches.size)
        assertEquals(2, insertedBatches[0].size)
        assertEquals(10, insertedBatches[0][0].beatIndex)
        assertEquals(11, insertedBatches[0][1].beatIndex)
    }
}
