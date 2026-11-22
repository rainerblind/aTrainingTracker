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
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WorkoutPostProcessingService] verifying API 34+ `shortService` FGS type,
 * `onTimeout()` isolation (never cancelling coordinator pipeline), and
 * `ForegroundServiceStartNotAllowedException` fallback (`REQ-TRK-091.3`, `TST-TRK-091.3`, `ATT-9154`).
 */
// Linked test specs: TST-TRK-091, TST-TRK-091.3
class WorkoutPostProcessingServiceTest {

    private class TestablePostProcessingService(
        private val sdkInt: Int,
        private val notifManager: WorkoutPostProcessingNotificationManager
    ) : WorkoutPostProcessingService() {
        var promotedId: Int? = null
        var promotedType: Int? = null
        var demoted = false
        var stoppedSelf = false
        var stoppedSelfStartId: Int? = null
        var promoteException: Throwable? = null
        val events = mutableListOf<String>()

        override fun getBuildVersionSdkInt(): Int = sdkInt
        override fun createNotificationManager(): WorkoutPostProcessingNotificationManager = notifManager
        override fun promoteForeground(notificationId: Int, notification: Notification, fgsType: Int) {
            events.add("promote")
            promoteException?.let { throw it }
            promotedId = notificationId
            promotedType = fgsType
        }
        override fun demoteForeground() {
            events.add("demote")
            demoted = true
        }
        override fun performStopSelf() {
            events.add("stopSelf")
            stoppedSelf = true
        }
        override fun performStopSelf(startId: Int) {
            events.add("stopSelf($startId)")
            stoppedSelf = true
            stoppedSelfStartId = startId
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        WorkoutPostProcessingCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        WorkoutPostProcessingCoordinator.resetForTesting()
        unmockkAll()
    }

    @Test
    fun testDetermineForegroundServiceType_api34ReturnsShortService_api33ReturnsZero_tstTrk091_3() {
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        val api34Service = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)
        val api33Service = TestablePostProcessingService(Build.VERSION_CODES.TIRAMISU, notifMgr)

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, api34Service.determineForegroundServiceType())
        assertEquals(0, api33Service.determineForegroundServiceType())
    }

    @Test
    fun testOnTimeout_stopsForegroundCleanlyWithoutCancellingActiveCoordinatorWork_tstTrk091_3() {
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        val service = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)

        // Mark a workout as actively post-processing in the coordinator
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        WorkoutPostProcessingCoordinator.enqueueHookForTesting = { _, _ -> false }
        WorkoutPostProcessingCoordinator.hostControllerOverride = object : WorkoutPostProcessingCoordinator.HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {}
            override fun onStageStarted(context: Context, stage: WorkoutPostProcessingNotificationManager.PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {}
        }
        val blocker = kotlinx.coroutines.CompletableDeferred<Unit>()
        WorkoutPostProcessingCoordinator.stepsOverride = object : WorkoutPostProcessingCoordinator.PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean {
                blocker.await()
                return true
            }
            override suspend fun runPolarVerityBackfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long) = null
            override suspend fun triggerDeferredExport(context: Context, workoutData: com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData) {}
        }
        WorkoutPostProcessingCoordinator.enqueue(
            ctx,
            WorkoutPostProcessingCoordinator.PostProcessingRequest(501L, "2026-11-20_200000", true, 1000L, 1L, false, 0L, 0L, false)
        )

        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)

        assertTrue("onTimeout must demote foreground cleanly", service.demoted)
        assertTrue("onTimeout must call stopSelf()", service.stoppedSelf)
        assertTrue(
            "onTimeout must NOT cancel or clear active WorkoutPostProcessingCoordinator work",
            WorkoutPostProcessingCoordinator.isPostProcessingActive(501L)
        )
        blocker.complete(Unit)
    }

    @Test
    fun testStartForPostProcessing_fgsStartDisallowed_fallsBackCleanlyWithoutThrowing_tstTrk091_3() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx

        val started = WorkoutPostProcessingService.startForPostProcessing(
            context = ctx,
            intentFactory = { mockk<Intent>(relaxed = true) }
        ) { _, _: Intent ->
            throw IllegalStateException("ForegroundServiceStartNotAllowedException simulated")
        }

        assertFalse("Disallowed FGS start must return false for in-process fallback", started)
    }

    @Test
    fun testOnTimeoutSingleArg_api34ShortServiceStopsForegroundWithoutCancellingCoordinatorWork_tstTrk091_3() {
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        val service = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        WorkoutPostProcessingCoordinator.hostControllerOverride = object : WorkoutPostProcessingCoordinator.HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {}
            override fun onStageStarted(context: Context, stage: WorkoutPostProcessingNotificationManager.PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {}
        }
        val blocker = CompletableDeferred<Unit>()
        WorkoutPostProcessingCoordinator.stepsOverride = object : WorkoutPostProcessingCoordinator.PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean {
                blocker.await()
                return true
            }
            override suspend fun runPolarVerityBackfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long) = null
            override suspend fun triggerDeferredExport(context: Context, workoutData: com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData) {}
        }
        WorkoutPostProcessingCoordinator.enqueue(
            ctx,
            WorkoutPostProcessingCoordinator.PostProcessingRequest(502L, "2026-11-20_200500", true, 1000L, 1L, false, 0L, 0L, false)
        )

        // Android 14 (API 34) & Android 15/16+ invoke the 1-arg onTimeout(startId) for shortService FGS
        service.onTimeout(7)

        assertTrue("1-arg onTimeout must demote foreground cleanly", service.demoted)
        assertTrue("1-arg onTimeout must call stopSelf()", service.stoppedSelf)
        assertTrue(
            "1-arg onTimeout must NOT cancel or clear active WorkoutPostProcessingCoordinator work",
            WorkoutPostProcessingCoordinator.isPostProcessingActive(502L)
        )
        blocker.complete(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOnStartCommand_rendersActiveCurrentProgressInsteadOfResettingToZeroOfOne_tstTrk091_3() = runTest {
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        val mockNotification = mockk<Notification>(relaxed = true)
        every { notifMgr.buildProgressNotification(any(), any(), any()) } returns mockNotification
        val service = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        val recomputeGate = CompletableDeferred<Unit>()
        WorkoutPostProcessingCoordinator.dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.hostControllerOverride = object : WorkoutPostProcessingCoordinator.HostController {
            override fun startForegroundHost(context: Context): Boolean = true
            override fun stopForegroundHost(context: Context) {}
            override fun onStageStarted(context: Context, stage: WorkoutPostProcessingNotificationManager.PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {}
        }
        WorkoutPostProcessingCoordinator.stepsOverride = object : WorkoutPostProcessingCoordinator.PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean = true
            override suspend fun runPolarVerityBackfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean {
                recomputeGate.await()
                return true
            }
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long) = null
            override suspend fun triggerDeferredExport(context: Context, workoutData: com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData) {}
        }

        WorkoutPostProcessingCoordinator.enqueue(
            ctx,
            WorkoutPostProcessingCoordinator.PostProcessingRequest(503L, "2026-11-20_201000", true, 1000L, 1L, true, 1000L, 2L, true)
        )
        testScheduler.runCurrent()

        // Pipeline is now blocked inside RECOMPUTE (step 2 of 4)
        service.onStartCommand(null, 0, 1)

        verify(exactly = 1) {
            notifMgr.buildProgressNotification(
                WorkoutPostProcessingNotificationManager.PostProcessingStage.RECOMPUTE,
                2,
                4
            )
        }
        assertEquals(WorkoutPostProcessingNotificationManager.NOTIFICATION_ID_POST_PROCESSING, service.promotedId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, service.promotedType)
        assertFalse("Active service must not demote while work is still active", service.demoted)
        assertFalse("Active service must not stopSelf while work is still active", service.stoppedSelf)

        recomputeGate.complete(Unit)
        advanceUntilIdle()
    }

    /**
     * TST-TRK-095.2 (ATT-9498): Verifies fast drain completing BEFORE onStartCommand() suppresses
     * appContext.stopService() while pendingFgsGeneration > 0, and subsequent onStartCommand()
     * calls promoteForeground() FIRST before demoteForeground() and stopSelf(startId).
     */
    @Test
    fun testFastDrainBeforeOnStartCommand_suppressesStopServiceThenPromotesAndStopsWithStartId_TST_TRK_095_2() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        every { notifMgr.buildProgressNotification(any(), any(), any()) } returns mockk(relaxed = true)
        val service = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)

        var startCalls = 0
        var stopServiceCalls = 0
        assertTrue(
            WorkoutPostProcessingService.startForPostProcessing(
                context = ctx,
                intentFactory = { mockk(relaxed = true) }
            ) { _, _ -> startCalls++ }
        )
        assertEquals(1, startCalls)

        // Drain finishes before AMS dispatches onStartCommand() -> stopIfRunning MUST NOT call stopService()
        WorkoutPostProcessingService.stopIfRunning(
            context = ctx,
            intentFactory = { mockk(relaxed = true) }
        ) { _, _ -> stopServiceCalls++ }
        assertEquals("stopService() MUST be suppressed while FGS start is pending", 0, stopServiceCalls)

        // AMS now delivers onStartCommand(startId=7) -> MUST promote first, then demote + stopSelf(7)
        service.onStartCommand(null, 0, 7)
        assertEquals(listOf("promote", "demote", "stopSelf(7)"), service.events)
        assertEquals(7, service.stoppedSelfStartId)
    }

    /**
     * TST-TRK-095.2 (ATT-9498): Verifies a second startForPostProcessing() before onStartCommand()
     * coalesces without redundant startForegroundService(), clears the pending stop request, and
     * allows post-promotion stopIfRunning() to invoke stopService() cleanly.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testReEnqueueBeforeOnStartCommand_clearsPendingStopAndAllowsPostPromotionStop_TST_TRK_095_2() = runTest {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        every { notifMgr.buildProgressNotification(any(), any(), any()) } returns mockk(relaxed = true)
        val service = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)

        var startCalls = 0
        var stopServiceCalls = 0
        assertTrue(
            WorkoutPostProcessingService.startForPostProcessing(ctx, { mockk(relaxed = true) }) { _, _ -> startCalls++ }
        )
        WorkoutPostProcessingService.stopIfRunning(ctx, { mockk(relaxed = true) }) { _, _ -> stopServiceCalls++ }
        assertEquals(0, stopServiceCalls)

        // Re-enqueue new active work before onStartCommand() runs
        val gate = CompletableDeferred<Unit>()
        WorkoutPostProcessingCoordinator.dispatcher = StandardTestDispatcher(testScheduler)
        WorkoutPostProcessingCoordinator.hostControllerOverride = object : WorkoutPostProcessingCoordinator.HostController {
            override fun startForegroundHost(context: Context): Boolean =
                WorkoutPostProcessingService.startForPostProcessing(context, { mockk(relaxed = true) }) { _, _ -> startCalls++ }
            override fun stopForegroundHost(context: Context) =
                WorkoutPostProcessingService.stopIfRunning(context, { mockk(relaxed = true) }) { _, _ -> stopServiceCalls++ }
            override fun onStageStarted(context: Context, stage: WorkoutPostProcessingNotificationManager.PostProcessingStage, currentStep: Int, totalSteps: Int) {}
            override fun onBatchCompleted(context: Context, hadWarning: Boolean) {}
        }
        WorkoutPostProcessingCoordinator.stepsOverride = object : WorkoutPostProcessingCoordinator.PostProcessingSteps {
            override suspend fun runPolarH10Backfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean = true
            override suspend fun runPolarVerityBackfill(context: Context, request: WorkoutPostProcessingCoordinator.PostProcessingRequest): Boolean = true
            override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean { gate.await(); return true }
            override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean = true
            override suspend fun reloadWorkout(context: Context, workoutId: Long) {}
            override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {}
            override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long) = null
            override suspend fun triggerDeferredExport(context: Context, workoutData: com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData) {}
        }

        WorkoutPostProcessingCoordinator.enqueue(
            ctx,
            WorkoutPostProcessingCoordinator.PostProcessingRequest(9498L, "2026-11-22_re", false, 0L, 0L, false, 0L, 0L, false)
        )
        testScheduler.runCurrent()
        assertEquals("Coalesced re-enqueue must reuse pending FGS start", 1, startCalls)

        // onStartCommand promotes and STAYS alive because stop was cleared and active work exists
        service.onStartCommand(null, 0, 2)
        assertEquals(listOf("promote"), service.events)
        assertFalse(service.stoppedSelf)

        // Once work finishes after promotion, stopIfRunning() safely calls stopService()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Post-promotion drain must invoke stopService() cleanly", 1, stopServiceCalls)
    }

    /**
     * TST-TRK-095.2 (ATT-9498): Verifies ACTION_STOP with pending FGS promotes before demote/stopSelf(startId),
     * and promoteForeground() failure clears pendingFgsGeneration so subsequent starts succeed (Guardrail #2).
     */
    @Test
    fun testActionStopPendingFgsAndPromoteFailureCleanup_TST_TRK_095_2() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        val notifMgr = mockk<WorkoutPostProcessingNotificationManager>(relaxed = true)
        every { notifMgr.buildProgressNotification(any(), any(), any()) } returns mockk(relaxed = true)

        // (A) ACTION_STOP with pending FGS must promote first
        var startCalls = 0
        assertTrue(
            WorkoutPostProcessingService.startForPostProcessing(ctx, { mockk(relaxed = true) }) { _, _ -> startCalls++ }
        )
        val stopIntent = mockk<Intent>(relaxed = true)
        every { stopIntent.action } returns WorkoutPostProcessingService.ACTION_STOP
        val serviceA = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr)
        serviceA.onStartCommand(stopIntent, 0, 11)
        assertEquals(listOf("promote", "demote", "stopSelf(11)"), serviceA.events)

        // (B) promoteForeground() throws -> clears pendingFgsGeneration so a new start is NOT wedged
        assertTrue(
            WorkoutPostProcessingService.startForPostProcessing(ctx, { mockk(relaxed = true) }) { _, _ -> startCalls++ }
        )
        assertEquals(2, startCalls)
        val failingService = TestablePostProcessingService(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, notifMgr).apply {
            promoteException = IllegalStateException("Simulated promotion failure")
        }
        failingService.onStartCommand(null, 0, 12)
        assertEquals(listOf("promote", "stopSelf(12)"), failingService.events)

        // Subsequent start MUST issue a fresh startForegroundService (startCalls 2 -> 3)
        assertTrue(
            WorkoutPostProcessingService.startForPostProcessing(ctx, { mockk(relaxed = true) }) { _, _ -> startCalls++ }
        )
        assertEquals("Pending FGS state must be cleared on promote failure", 3, startCalls)
    }
}
