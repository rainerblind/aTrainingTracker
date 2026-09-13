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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication

/**
 * Manages foreground system notifications during bulk workout deletion operations.
 */
class WorkoutDeletionNotificationManager @VisibleForTesting constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    @VisibleForTesting
    internal var notificationFactory: ((NotificationCompat.Builder) -> Notification)? = null
) {

    constructor(context: Context) : this(
        context,
        NotificationManagerCompat.from(context)
    )

    companion object {
        const val NOTIFICATION_ID_DELETION = 4296
    }

    /**
     * Posts or updates an ongoing progress notification while deleting workout records.
     */
    fun showProgressNotification(current: Int, total: Int, workoutName: String) {
        if (!hasNotificationPermission()) return

        val title = context.getString(R.string.deleteOldWorkouts)
        val content = context.getString(R.string.deleting_workout, workoutName) + " ($current/$total)"

        val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notification = notificationFactory?.invoke(builder) ?: builder.build()
        notificationManager.notify(NOTIFICATION_ID_DELETION, notification)
    }

    /**
     * Posts or updates an indeterminate progress notification while resynchronizing caches.
     */
    fun showResyncNotification() {
        if (!hasNotificationPermission()) return

        val title = context.getString(R.string.deleteOldWorkouts)
        val content = context.getString(R.string.please_wait)

        val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notification = notificationFactory?.invoke(builder) ?: builder.build()
        notificationManager.notify(NOTIFICATION_ID_DELETION, notification)
    }

    /**
     * Cancels and clears the ongoing bulk deletion notification.
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID_DELETION)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return notificationManager.areNotificationsEnabled()
    }
}
