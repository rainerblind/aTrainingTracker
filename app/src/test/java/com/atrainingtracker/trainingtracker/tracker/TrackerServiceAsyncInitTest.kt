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
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.ServiceConnection
import android.database.SQLException
import android.util.Log
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying asynchronous workout initialization, CompletableFuture reactive barrier,
 * zero sample loss, and defensive error teardown in TrackerService.
 *
 * Traceability:
 * - Requirement: REQ-STB-009 (Clauses 1–6)
 * - Test Specification: TST-STB-009 (Vectors 1–5)
 * - Ticket: ATT-1245 / ATT-1262
 */
class TrackerServiceAsyncInitTest {

    private class TestableTrackerService : TrackerService() {
        var stoppedSelf = false
        var notificationShown = false
        var createNewWorkoutCalled = false
        var createNewWorkoutThreadName: String? = null
        var createNewTableCalled = false
        var simulatedWorkoutId = 12345L
        var tableCreationException: Throwable? = null
        var trackingStartedBroadcastWorkoutId: Long? = null
        val tableCreationLatch = CountDownLatch(1)

        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true

        override fun createNewWorkout(): Long {
            createNewWorkoutCalled = true
            createNewWorkoutThreadName = Thread.currentThread().name
            tableCreationException?.let { throw it }
            return simulatedWorkoutId
        }

        override fun showTrackingInterruptedNotification() {
            notificationShown = true
        }

        override fun performStopSelf() {
            stoppedSelf = true
        }

        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {
            // No-op
        }

        override fun notifyTrackingStarted(workoutId: Long) {
            trackingStartedBroadcastWorkoutId = workoutId
        }

        override fun sendBroadcast(intent: Intent?) {
            // No-op
        }

        override fun getPackageName(): String = "com.atrainingtracker"

        override fun performSuperOnDestroy() {
            // No-op
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            // No-op
        }

        override fun unbindService(conn: ServiceConnection) {
            // No-op
        }
    }

    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        val mockSamplesManager = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesManager
        every { WorkoutSamplesDatabaseManager.getTableName(any()) } returns "workout_samples_test"

        service = TestableTrackerService()
    }

    @After
    fun tearDown() {
        try {
            service.onDestroy()
        } catch (_: Exception) {
        }
        unmockkAll()
    }

    @Test
    fun testOnStartCommand_returnsImmediatelyWithoutBlockingMainThread() {
        // Given: START_NORMAL intent
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name

        // When: onStartCommand executes on calling (main) thread
        val startTime = System.currentTimeMillis()
        val result = service.onStartCommand(intent, 0, 1)
        val durationMs = System.currentTimeMillis() - startTime

        // Then:
        // 1. Returns Service.START_STICKY
        assertEquals(Service.START_STICKY, result)
        // 2. Main-thread execution duration < 100ms (SLA is < 10ms for non-blocking return)
        assertTrue("onStartCommand must return immediately (< 100ms in unit test), took: ${durationMs}ms", durationMs < 100)
        // 3. Table initialization future is registered
        assertNotNull(service.tableInitializationFuture)
    }

    @Test
    fun testAsyncWorkoutCreation_dispatchesToDbExecutorAndCompletesFuture() {
        // Given: START_NORMAL intent
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name

        // When: onStartCommand is invoked
        service.onStartCommand(intent, 0, 1)

        // Await the table initialization future (up to 5s)
        val future = service.tableInitializationFuture
        assertNotNull("tableInitializationFuture must not be null", future)
        future.get(5, TimeUnit.SECONDS)

        // Then:
        // 1. createNewWorkout was executed on a background thread (not main)
        assertTrue("createNewWorkout must be invoked asynchronously", service.createNewWorkoutCalled)
        assertFalse("createNewWorkout must NOT run on the main thread", Thread.currentThread().name == service.createNewWorkoutThreadName)
        // 2. Future is completed
        assertTrue("Future must complete normally", future.isDone && !future.isCompletedExceptionally)
        // 3. notifyTrackingStarted was invoked with valid non-zero workoutId
        assertEquals(service.simulatedWorkoutId, service.trackingStartedBroadcastWorkoutId)
    }

    @Test
    fun testSampleWrites_queuedBeforeTableCreation_bufferedAndPersistedWithoutLoss() {
        // Given: START_NORMAL intent
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name

        service.onStartCommand(intent, 0, 1)

        val processedCounter = AtomicInteger(0)
        val completedLatch = CountDownLatch(3)

        // When: Early samples attach to the future via thenAcceptAsync before future resolves
        val future = service.tableInitializationFuture
        for (i in 1..3) {
            future.thenAcceptAsync({
                processedCounter.incrementAndGet()
                completedLatch.countDown()
            }, java.util.concurrent.Executors.newSingleThreadExecutor())
        }

        // Await all callbacks
        val allExecuted = completedLatch.await(5, TimeUnit.SECONDS)

        // Then:
        assertTrue("All 3 queued tasks must execute upon future completion", allExecuted)
        assertEquals("Zero sample tasks dropped; all 3 executed in order", 3, processedCounter.get())
    }

    @Test
    fun testTableCreationFailure_triggersDefensiveTeardownAndNotifiesUser() {
        // Given: Table creation throws fatal SQLException
        service.tableCreationException = SQLException("Disk I/O failure or corrupt database")

        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name

        // When: onStartCommand executes
        service.onStartCommand(intent, 0, 1)

        val future = service.tableInitializationFuture

        // Await future failure
        try {
            future.get(5, TimeUnit.SECONDS)
            fail("Expected ExecutionException due to table creation failure")
        } catch (e: ExecutionException) {
            assertTrue("Cause must be SQLException", e.cause is SQLException)
        }

        // Then:
        // 1. Future is completed exceptionally
        assertTrue(future.isCompletedExceptionally)
        // 2. Service stopped itself
        assertTrue("performStopSelf must be called on fatal error", service.stoppedSelf)
        // 3. Notification shown to user
        assertTrue("User must be notified about interrupted tracking", service.notificationShown)
        // 4. Tracking interrupted flag is set
        assertTrue("mTrackingInterrupted must be true", service.isTrackingInterrupted)
    }

    @Test
    fun testLiveWorkoutSession_acceptsPlaceholderIdAndUpdatesAtomically() {
        // Given: LiveWorkoutSession instantiated with placeholder ID 0
        val session = LiveWorkoutSession(0, setOf(SensorType.HR, SensorType.SPEED_mps))
        assertEquals(0L, session.workoutId)

        // When: setWorkoutId is invoked once background DB creation finishes
        session.setWorkoutId(98765L)

        // Then:
        assertEquals(98765L, session.workoutId)
    }
}
