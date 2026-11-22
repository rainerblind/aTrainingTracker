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

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController
import com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingNotificationManager.PostProcessingStage
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Coordinates decoupled asynchronous stop-workout post-processing (Polar H10/Verity Sense offline
 * backfill, HRV/ERTS recomputation, reactive repository refresh, and ordered export finalization)
 * without blocking `TrackerService.endWorkout()` -> `EditWorkoutScreen` navigation (`REQ-TRK-091`).
 */
object WorkoutPostProcessingCoordinator {

    private const val TAG = "WorkoutPostProcCoord"

    /**
     * Immutable per-workout snapshot captured synchronously in `TrackerService.endWorkout()`
     * before `TRACKING_FINISHED_INTENT` is broadcast (`REQ-TRK-091.1`).
     */
    data class PostProcessingRequest @JvmOverloads constructor(
        val workoutId: Long,
        val baseFileName: String?,
        val h10Armed: Boolean,
        val h10StartMs: Long,
        val h10ArmedToken: Long,
        val verityArmed: Boolean,
        val verityStartMs: Long,
        val verityArmedToken: Long,
        val ertsEnabled: Boolean,
        val workoutEndMs: Long = 0L
    )

    /**
     * Injectable execution boundary for deterministic pure-JVM unit testing (`TST-TRK-091`, `TST-TRK-093`).
     */
    interface PostProcessingSteps {
        suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean
        suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean
        suspend fun runPhysiologyRecompute(context: Context, request: PostProcessingRequest): Boolean = false
        suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean
        suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean
        suspend fun reloadWorkout(context: Context, workoutId: Long)
        suspend fun markExportWorkoutFinished(context: Context, baseFileName: String)
        suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData?
        suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData)
    }

    /**
     * Injectable foreground service and notification controller for deterministic testing.
     */
    interface HostController {
        fun startForegroundHost(context: Context): Boolean
        fun stopForegroundHost(context: Context)
        fun onStageStarted(context: Context, stage: PostProcessingStage, currentStep: Int, totalSteps: Int)
        fun onBatchCompleted(context: Context, hadWarning: Boolean)
    }

    private object DefaultPostProcessingSteps : PostProcessingSteps {
        override suspend fun runPolarH10Backfill(context: Context, request: PostProcessingRequest): Boolean {
            val baseName = request.baseFileName ?: return false
            return PolarH10OfflineController.stopAndBackfillWorkout(
                context = context,
                workoutId = request.workoutId,
                baseFileName = baseName,
                startTimeMs = request.h10StartMs,
                showOwnNotification = false,
                expectedArmedToken = request.h10ArmedToken,
                endTimeMs = request.workoutEndMs,
                runPostBackfillRecompute = false
            )
        }

        override suspend fun runPolarVerityBackfill(context: Context, request: PostProcessingRequest): Boolean {
            val baseName = request.baseFileName ?: return false
            return PolarVeritySenseOfflineController.stopAndBackfillWorkout(
                context = context,
                workoutId = request.workoutId,
                baseFileName = baseName,
                startTimeMs = request.verityStartMs,
                showOwnNotification = false,
                expectedArmedToken = request.verityArmedToken
            )
        }

        override suspend fun runPhysiologyRecompute(context: Context, request: PostProcessingRequest): Boolean {
            return PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(
                context,
                request.workoutId,
                request.baseFileName
            )
        }

        override suspend fun runHrvRecompute(context: Context, workoutId: Long): Boolean {
            return HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        }

        override suspend fun runErtsRecompute(context: Context, workoutId: Long): Boolean {
            return ErtsPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
        }

        override suspend fun reloadWorkout(context: Context, workoutId: Long) {
            WorkoutRepository.getInstance(context).reloadWorkoutData(workoutId)
        }

        override suspend fun markExportWorkoutFinished(context: Context, baseFileName: String) {
            ExportManager(context).workoutFinished(baseFileName)
        }

        override suspend fun loadFreshWorkoutForExport(context: Context, workoutId: Long): WorkoutData? {
            return WorkoutRepository.getInstance(context).loadFreshWorkoutForExport(workoutId)
        }

        override suspend fun triggerDeferredExport(context: Context, workoutData: WorkoutData) {
            ExportManager(context).exportWorkout(workoutData)
        }
    }

    private object DefaultHostController : HostController {
        override fun startForegroundHost(context: Context): Boolean {
            return WorkoutPostProcessingService.startForPostProcessing(context)
        }

        override fun stopForegroundHost(context: Context) {
            WorkoutPostProcessingService.stopIfRunning(context)
        }

        override fun onStageStarted(
            context: Context,
            stage: PostProcessingStage,
            currentStep: Int,
            totalSteps: Int
        ) {
            try {
                WorkoutPostProcessingNotificationManager(context).showStageProgress(stage, currentStep, totalSteps)
            } catch (t: Throwable) {
                safeLogWarn("Non-fatal notification update error for stage $stage: ${t.message}")
            }
        }

        override fun onBatchCompleted(context: Context, hadWarning: Boolean) {
            try {
                val manager = WorkoutPostProcessingNotificationManager(context)
                if (hadWarning) {
                    manager.showWarningNotification()
                } else {
                    manager.cancelNotification()
                }
            } catch (t: Throwable) {
                safeLogWarn("Non-fatal notification completion error: ${t.message}")
            }
        }
    }

    /**
     * Immutable active progress snapshot for `WorkoutPostProcessingService.onStartCommand()`
     * so FGS promotion renders real step progress instead of resetting to `0/1` (`REQ-TRK-091.3`).
     */
    data class StageProgress(
        val stage: PostProcessingStage,
        val currentStep: Int,
        val totalSteps: Int
    )

    /**
     * Atomic deferred-export decision consumed under [stateLock] when a workout completes
     * post-processing, preserving transient per-workout `syncToHealthConnect` toggles that are
     * not stored in SQLite (`REQ-TRK-091.2`).
     */
    internal data class DeferredExportDecision(
        val pending: Boolean,
        val syncToHealthConnect: Boolean?
    )

    private data class QueuedJob(
        val appContext: Context,
        val request: PostProcessingRequest
    )

    // Strict lock ordering: hostLock -> stateLock (NEVER stateLock -> hostLock, and NEVER held across I/O stages).
    private val hostLock = Any()
    private val stateLock = Any()
    private val activeWorkouts = mutableSetOf<Long>()
    private val deferredExportWorkouts = mutableMapOf<Long, Boolean?>()
    private val pendingQueue = ArrayDeque<QueuedJob>()
    private var isDraining = false

    @Volatile
    private var currentStage: PostProcessingStage? = null

    @Volatile
    private var currentProgress: StageProgress? = null

    @VisibleForTesting
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    internal var stepsOverride: PostProcessingSteps? = null

    @VisibleForTesting
    internal var hostControllerOverride: HostController? = null

    @VisibleForTesting
    internal var enqueueHookForTesting: ((Context, PostProcessingRequest) -> Boolean)? = null

    private val supervisorJob = SupervisorJob()

    private fun activeSteps(): PostProcessingSteps = stepsOverride ?: DefaultPostProcessingSteps

    private fun activeHost(): HostController = hostControllerOverride ?: DefaultHostController

    @JvmStatic
    fun getCurrentStage(): PostProcessingStage? = currentStage

    @JvmStatic
    fun getCurrentProgress(): StageProgress? = currentProgress

    @JvmStatic
    fun isPostProcessingActive(workoutId: Long): Boolean = synchronized(stateLock) {
        workoutId in activeWorkouts
    }

    @JvmStatic
    fun hasActiveWork(): Boolean = synchronized(stateLock) {
        hasActiveWorkLocked()
    }

    private fun hasActiveWorkLocked(): Boolean {
        return activeWorkouts.isNotEmpty() || pendingQueue.isNotEmpty() || isDraining
    }

    /**
     * Atomically records a deferred export request (including the transient per-workout
     * [syncToHealthConnect] toggle) iff [workoutId] is currently undergoing post-processing
     * (`REQ-TRK-091.2`).
     *
     * @return `true` when export was deferred to post-processing completion, `false` when the
     * caller may execute `ExportManager.exportWorkout()` immediately.
     */
    @JvmStatic
    @JvmOverloads
    fun deferExportIfPostProcessing(
        workoutId: Long,
        syncToHealthConnect: Boolean? = null
    ): Boolean = synchronized(stateLock) {
        if (workoutId in activeWorkouts) {
            deferredExportWorkouts[workoutId] = syncToHealthConnect
            true
        } else {
            false
        }
    }

    /**
     * Atomically clears [workoutId] from `activeWorkouts` and consumes any pending deferred-export
     * decision (and transient `syncToHealthConnect` override) under [stateLock] (`REQ-TRK-091.2`).
     */
    @VisibleForTesting
    internal fun completePostProcessing(workoutId: Long): DeferredExportDecision = synchronized(stateLock) {
        activeWorkouts.remove(workoutId)
        val pending = deferredExportWorkouts.containsKey(workoutId)
        val syncToHealthConnect = deferredExportWorkouts.remove(workoutId)
        DeferredExportDecision(pending = pending, syncToHealthConnect = syncToHealthConnect)
    }

    /**
     * Enqueues a workout for asynchronous foreground-protected post-processing and returns
     * immediately without blocking `TrackerService.endWorkout()` (`REQ-TRK-091.1`).
     */
    @JvmStatic
    fun enqueue(context: Context, request: PostProcessingRequest) {
        if (request.workoutId <= 0L && request.baseFileName.isNullOrBlank()) {
            return
        }
        val appContext = context.applicationContext ?: context
        enqueueHookForTesting?.let { hook ->
            if (hook(appContext, request)) return
        }

        val shouldStartDrain: Boolean
        synchronized(hostLock) {
            synchronized(stateLock) {
                if (request.workoutId > 0L) {
                    activeWorkouts.add(request.workoutId)
                }
                pendingQueue.addLast(QueuedJob(appContext, request))
                shouldStartDrain = !isDraining
                if (shouldStartDrain) {
                    isDraining = true
                }
                if (currentStage == null) {
                    val initialStage = initialStageFor(request)
                    currentStage = initialStage
                    currentProgress = StageProgress(initialStage, 0, totalStepsFor(request))
                }
            }
            activeHost().startForegroundHost(appContext)
        }

        if (shouldStartDrain) {
            CoroutineScope(supervisorJob + dispatcher).launch {
                drainQueue(appContext, initialHadWarning = false)
            }
        }
    }

    private fun initialStageFor(request: PostProcessingRequest): PostProcessingStage = when {
        request.h10Armed -> PostProcessingStage.POLAR_H10
        request.verityArmed -> PostProcessingStage.POLAR_VERITY
        else -> PostProcessingStage.RECOMPUTE
    }

    private fun totalStepsFor(request: PostProcessingRequest): Int =
        (if (request.h10Armed) 1 else 0) + (if (request.verityArmed) 1 else 0) + 2

    private fun updateProgress(stage: PostProcessingStage, currentStep: Int, totalSteps: Int) {
        currentStage = stage
        currentProgress = StageProgress(stage, currentStep, totalSteps)
    }

    private suspend fun drainQueue(initialContext: Context, initialHadWarning: Boolean = false) {
        var lastContext = initialContext
        var batchHadWarning = initialHadWarning
        try {
            while (true) {
                val nextJob = synchronized(stateLock) {
                    if (pendingQueue.isEmpty()) null else pendingQueue.removeFirst()
                } ?: break
                lastContext = nextJob.appContext
                if (processRequest(nextJob.appContext, nextJob.request)) {
                    batchHadWarning = true
                }
            }
        } finally {
            var shouldRestartDrain = false
            synchronized(hostLock) {
                val stillIdle = synchronized(stateLock) {
                    if (pendingQueue.isEmpty()) {
                        isDraining = false
                        currentStage = null
                        currentProgress = null
                        !hasActiveWorkLocked()
                    } else {
                        shouldRestartDrain = true
                        false
                    }
                }
                if (stillIdle) {
                    try {
                        activeHost().onBatchCompleted(lastContext, batchHadWarning)
                    } finally {
                        activeHost().stopForegroundHost(lastContext)
                    }
                }
            }
            if (shouldRestartDrain) {
                CoroutineScope(supervisorJob + dispatcher).launch {
                    drainQueue(lastContext, initialHadWarning = batchHadWarning)
                }
            }
        }
    }

    private suspend fun processRequest(
        appContext: Context,
        request: PostProcessingRequest
    ): Boolean {
        val steps = activeSteps()
        val host = activeHost()
        val totalSteps = totalStepsFor(request)
        var completedSteps = 0
        var hadWarning = false
        var finalizedUnderLock = false

        try {
            if (request.workoutId > 0L) {
                if (request.h10Armed) {
                    updateProgress(PostProcessingStage.POLAR_H10, completedSteps, totalSteps)
                    host.onStageStarted(appContext, PostProcessingStage.POLAR_H10, completedSteps, totalSteps)
                    hadWarning = runStageIsolated("Polar H10 backfill", request.workoutId) {
                        steps.runPolarH10Backfill(appContext, request)
                    } || hadWarning
                    completedSteps++
                }

                var verityBackfilled = false
                if (request.verityArmed) {
                    updateProgress(PostProcessingStage.POLAR_VERITY, completedSteps, totalSteps)
                    host.onStageStarted(appContext, PostProcessingStage.POLAR_VERITY, completedSteps, totalSteps)
                    hadWarning = runStageIsolated("Polar Verity Sense backfill", request.workoutId) {
                        verityBackfilled = steps.runPolarVerityBackfill(appContext, request)
                    } || hadWarning
                    completedSteps++
                }

                updateProgress(PostProcessingStage.RECOMPUTE, completedSteps, totalSteps)
                host.onStageStarted(appContext, PostProcessingStage.RECOMPUTE, completedSteps, totalSteps)
                if (request.h10Armed || verityBackfilled) {
                    hadWarning = runStageIsolated("Post-backfill physiology recompute", request.workoutId) {
                        steps.runPhysiologyRecompute(appContext, request)
                    } || hadWarning
                }
                hadWarning = runStageIsolated("HRV recompute", request.workoutId) {
                    steps.runHrvRecompute(appContext, request.workoutId)
                } || hadWarning
                if (request.ertsEnabled) {
                    hadWarning = runStageIsolated("ERTS recompute", request.workoutId) {
                        steps.runErtsRecompute(appContext, request.workoutId)
                    } || hadWarning
                }
                hadWarning = runStageIsolated("WorkoutRepository reload", request.workoutId) {
                    steps.reloadWorkout(appContext, request.workoutId)
                } || hadWarning
                completedSteps++
            }

            updateProgress(PostProcessingStage.EXPORT, completedSteps, totalSteps)
            host.onStageStarted(appContext, PostProcessingStage.EXPORT, completedSteps, totalSteps)
            val baseFileName = request.baseFileName
            if (!baseFileName.isNullOrBlank()) {
                hadWarning = runStageIsolated("ExportManager.workoutFinished", request.workoutId) {
                    steps.markExportWorkoutFinished(appContext, baseFileName)
                } || hadWarning
            }

            if (request.workoutId > 0L) {
                val decision = completePostProcessing(request.workoutId)
                finalizedUnderLock = true
                if (decision.pending) {
                    hadWarning = runStageIsolated("Deferred ExportManager.exportWorkout", request.workoutId) {
                        val freshWorkout = steps.loadFreshWorkoutForExport(appContext, request.workoutId)
                        if (freshWorkout != null) {
                            val exportPayload = freshWorkout.copy(syncToHealthConnect = decision.syncToHealthConnect)
                            steps.triggerDeferredExport(appContext, exportPayload)
                        }
                    } || hadWarning
                }
            }
        } finally {
            if (!finalizedUnderLock && request.workoutId > 0L) {
                completePostProcessing(request.workoutId)
            }
        }
        return hadWarning
    }

    private fun safeLogWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // Unmocked android.util.Log in plain JVM unit test teardown
        }
    }

    private suspend inline fun runStageIsolated(
        stageName: String,
        workoutId: Long,
        block: () -> Unit
    ): Boolean {
        return try {
            block()
            false
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            try {
                Log.e(TAG, "Error during $stageName for workout $workoutId: ${t.message}", t)
            } catch (_: RuntimeException) {
                // Unmocked android.util.Log in plain JVM unit test teardown
            }
            true
        }
    }

    @VisibleForTesting
    internal fun resetForTesting() {
        synchronized(hostLock) {
            synchronized(stateLock) {
                activeWorkouts.clear()
                deferredExportWorkouts.clear()
                pendingQueue.clear()
                isDraining = false
            }
        }
        currentStage = null
        currentProgress = null
        dispatcher = Dispatchers.IO
        stepsOverride = null
        hostControllerOverride = null
        enqueueHookForTesting = null
        WorkoutPostProcessingService.resetLifecycleStateForTesting()
    }
}
