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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication

/**
 * Manages the unified progress notification displayed while asynchronous post-workout processing
 * (Polar offline backfill, HRV/ERTS recomputation, and export finalization) runs in the background
 * (`REQ-TRK-091.3`, `ATT-9154`).
 */
class WorkoutPostProcessingNotificationManager @VisibleForTesting constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    @get:VisibleForTesting
    internal var notificationFactory: ((NotificationCompat.Builder) -> Notification)? = null
) {

    constructor(context: Context) : this(
        context.applicationContext,
        NotificationManagerCompat.from(context.applicationContext)
    )

    /**
     * Ordered processing stages surfaced in the progress notification.
     */
    enum class PostProcessingStage(@StringRes val messageResId: Int) {
        POLAR_H10(R.string.notification_post_processing_stage_polar_h10),
        POLAR_VERITY(R.string.notification_post_processing_stage_polar_verity),
        RECOMPUTE(R.string.notification_post_processing_stage_recompute),
        EXPORT(R.string.notification_post_processing_stage_export)
    }

    companion object {
        private const val TAG = "WorkoutPostProcNotif"
        const val NOTIFICATION_ID_POST_PROCESSING = 8047
        const val NOTIFICATION_ID_POST_PROCESSING_WARNING = 8048
    }

    /**
     * Builds an ongoing progress notification for the given [stage] and step progress.
     */
    fun buildProgressNotification(
        stage: PostProcessingStage,
        currentStep: Int,
        totalSteps: Int
    ): Notification {
        val safeTotal = totalSteps.coerceAtLeast(1)
        val safeCurrent = currentStep.coerceIn(0, safeTotal)
        val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
            .setSmallIcon(R.drawable.ic_stat_notify_logo)
            .setContentTitle(context.getString(R.string.notification_post_processing_title))
            .setContentText(context.getString(stage.messageResId))
            .setProgress(safeTotal, safeCurrent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return notificationFactory?.invoke(builder) ?: builder.build()
    }

    /**
     * Builds and (when permitted) posts the ongoing progress notification for [stage].
     */
    @SuppressLint("MissingPermission")
    fun showStageProgress(
        stage: PostProcessingStage,
        currentStep: Int,
        totalSteps: Int
    ): Notification? {
        return try {
            val notification = buildProgressNotification(stage, currentStep, totalSteps)
            if (hasNotificationPermission()) {
                notificationManager.notify(NOTIFICATION_ID_POST_PROCESSING, notification)
            }
            notification
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to update post-processing progress notification: ${t.message}")
            null
        }
    }

    /**
     * Cancels the ongoing progress notification (`8047`) and posts a non-ongoing, auto-cancelling
     * warning notification on dedicated ID [NOTIFICATION_ID_POST_PROCESSING_WARNING] (`8048`) so
     * stopping the `shortService` FGS never wipes the warning notification (`REQ-TRK-091.3`).
     */
    @SuppressLint("MissingPermission")
    fun showWarningNotification() {
        cancelNotification()
        try {
            if (!hasNotificationPermission()) return
            val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.ic_stat_notify_logo)
                .setContentTitle(context.getString(R.string.notification_post_processing_title))
                .setContentText(context.getString(R.string.notification_post_processing_failed))
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            val notification = notificationFactory?.invoke(builder) ?: builder.build()
            notificationManager.notify(NOTIFICATION_ID_POST_PROCESSING_WARNING, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to post post-processing warning notification: ${t.message}")
        }
    }

    /**
     * Cancels and removes the post-processing notification cleanly on normal completion.
     */
    fun cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_POST_PROCESSING)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to cancel post-processing notification: ${t.message}")
        }
    }

    @VisibleForTesting
    internal fun hasNotificationPermission(): Boolean {
        if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return notificationManager.areNotificationsEnabled()
    }
}
