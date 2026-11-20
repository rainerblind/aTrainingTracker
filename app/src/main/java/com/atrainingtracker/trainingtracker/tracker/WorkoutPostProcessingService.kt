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

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Lightweight short-lived Foreground Service (`android:foregroundServiceType="shortService"`)
 * keeping post-workout Polar backfill, HRV/ERTS recomputation, and export finalization alive
 * when the user pockets the phone or switches apps after tapping Stop (`REQ-TRK-091.3`, `ATT-9154`).
 *
 * Requires zero new Android manifest permissions (strictly preserves `REQ-TRK-022` omission of
 * `FOREGROUND_SERVICE_CONNECTED_DEVICE`).
 */
open class WorkoutPostProcessingService : Service() {

    @VisibleForTesting
    internal open fun getBuildVersionSdkInt(): Int = Build.VERSION.SDK_INT

    @VisibleForTesting
    internal open fun createNotificationManager(): WorkoutPostProcessingNotificationManager {
        return WorkoutPostProcessingNotificationManager(this)
    }

    @VisibleForTesting
    internal open fun promoteForeground(notificationId: Int, notification: Notification, fgsType: Int) {
        ServiceCompat.startForeground(this, notificationId, notification, fgsType)
    }

    @VisibleForTesting
    internal open fun demoteForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    @VisibleForTesting
    internal open fun performStopSelf() {
        stopSelf()
    }

    @VisibleForTesting
    internal fun determineForegroundServiceType(): Int {
        return if (getBuildVersionSdkInt() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            demoteForeground()
            performStopSelf()
            return START_NOT_STICKY
        }

        val notifManager = createNotificationManager()
        val progress = WorkoutPostProcessingCoordinator.getCurrentProgress()
        val initialStage = progress?.stage
            ?: WorkoutPostProcessingCoordinator.getCurrentStage()
            ?: WorkoutPostProcessingNotificationManager.PostProcessingStage.RECOMPUTE
        val initialCurrentStep = progress?.currentStep ?: 0
        val initialTotalSteps = progress?.totalSteps ?: 1
        val initialNotification = notifManager.buildProgressNotification(
            initialStage,
            initialCurrentStep,
            initialTotalSteps
        )
        try {
            promoteForeground(
                WorkoutPostProcessingNotificationManager.NOTIFICATION_ID_POST_PROCESSING,
                initialNotification,
                determineForegroundServiceType()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to promote WorkoutPostProcessingService to foreground: ${t.message}")
            performStopSelf()
            return START_NOT_STICKY
        }

        if (!WorkoutPostProcessingCoordinator.hasActiveWork()) {
            demoteForeground()
            performStopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * Android 14 (API 34) & Android 15/16+ `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` timeout
     * callback (1-argument overload invoked by the system for `shortService` FGS): cleanly stops
     * the foreground service without cancelling [WorkoutPostProcessingCoordinator]'s
     * `SupervisorJob + Dispatchers.IO` pipeline (`REQ-TRK-091.3`).
     */
    override fun onTimeout(startId: Int) {
        handleTimeout(startId, null)
    }

    /**
     * API 35+ typed foreground service timeout callback (2-argument overload): delegates to the
     * same clean FGS demotion path without cancelling [WorkoutPostProcessingCoordinator] (`REQ-TRK-091.3`).
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout(startId, fgsType)
    }

    private fun handleTimeout(startId: Int, fgsType: Int?) {
        Log.w(TAG, "shortService timeout reached (startId=$startId, fgsType=${fgsType ?: "shortService"}); demoting foreground cleanly")
        try {
            demoteForeground()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping foreground in onTimeout: ${t.message}")
        }
        performStopSelf()
    }

    companion object {
        private const val TAG = "WorkoutPostProcService"
        const val ACTION_START = "com.atrainingtracker.trainingtracker.tracker.ACTION_START_POST_PROCESSING"
        const val ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.ACTION_STOP_POST_PROCESSING"

        /**
         * Attempts to start [WorkoutPostProcessingService] as a foreground service, gracefully
         * returning `false` on `ForegroundServiceStartNotAllowedException` / `IllegalStateException`
         * so [WorkoutPostProcessingCoordinator] falls back to in-process execution (`REQ-TRK-091.3`).
         */
        @JvmStatic
        fun startForPostProcessing(
            context: Context,
            intentFactory: (Context) -> Intent = { ctx ->
                Intent(ctx, WorkoutPostProcessingService::class.java).apply {
                    action = ACTION_START
                }
            },
            starter: (Context, Intent) -> Unit = { ctx, intent ->
                ContextCompat.startForegroundService(ctx, intent)
            }
        ): Boolean {
            val appContext = context.applicationContext ?: context
            return try {
                val intent = intentFactory(appContext)
                starter(appContext, intent)
                true
            } catch (e: Throwable) {
                val isFgsStartNotAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                if (isFgsStartNotAllowed || e is IllegalStateException || e is SecurityException) {
                    Log.w(TAG, "Foreground service start disallowed; falling back to in-process post-processing: ${e.message}")
                    false
                } else {
                    Log.w(TAG, "Unexpected error starting WorkoutPostProcessingService; falling back in-process: ${e.message}")
                    false
                }
            }
        }

        /**
         * Stops [WorkoutPostProcessingService] once the post-processing queue has drained.
         */
        @JvmStatic
        fun stopIfRunning(context: Context) {
            val appContext = context.applicationContext ?: context
            try {
                appContext.stopService(Intent(appContext, WorkoutPostProcessingService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "Non-fatal error stopping WorkoutPostProcessingService: ${t.message}")
            }
        }
    }
}
