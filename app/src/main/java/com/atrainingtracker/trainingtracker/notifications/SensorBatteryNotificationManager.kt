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

package com.atrainingtracker.trainingtracker.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation

/**
 * Manages system notifications for peripheral sensor battery alerts following workout completion (ATT-2192).
 *
 * Dispatches non-blocking notifications on [TrainingApplication.NOTIFICATION_CHANNEL__SENSOR_BATTERY]
 * targeting "Meine Sensoren" ([R.id.drawer_my_sensors]).
 */
class SensorBatteryNotificationManager @VisibleForTesting constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    @VisibleForTesting
    internal var notificationFactory: ((NotificationCompat.Builder) -> Notification)? = null,
    @VisibleForTesting
    internal var pendingIntentFactory: ((Context) -> PendingIntent?)? = null
) {

    constructor(context: Context) : this(
        context,
        NotificationManagerCompat.from(context)
    )

    companion object {
        const val NOTIFICATION_ID_SENSOR_BATTERY = 4300
    }

    /**
     * Posts a system notification warning the user about critical peripheral sensor battery levels.
     *
     * @param criticalDevices List of devices actively used in the workout with battery <= 20%.
     */
    fun showLowBatteryNotification(criticalDevices: List<DevicesDatabaseManager.NameAndBatteryPercentage>) {
        if (criticalDevices.isEmpty()) return

        try {
            if (!notificationManager.areNotificationsEnabled()) return
        } catch (ignored: Exception) {
            // Defensive handling across mocked contexts
        }

        val title = if (criticalDevices.size == 1) {
            context.getString(R.string.sensor_battery_notification_title_1)
        } else {
            context.getString(R.string.sensor_battery_notification_title_many)
        }

        val content = if (criticalDevices.size == 1) {
            val device = criticalDevices[0]
            "${device.name}: ${device.batteryPercentage}%"
        } else {
            criticalDevices.joinToString(", ") { "${it.name} (${it.batteryPercentage}%)" }
        }

        val pendingIntent = pendingIntentFactory?.invoke(context) ?: try {
            val intent = Intent(context, MainActivityWithNavigation::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.SENSORS.name)
                putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_my_sensors)
            }
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_SENSOR_BATTERY,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (ignored: Throwable) {
            null
        }

        val builder = NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__SENSOR_BATTERY)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        val notification = notificationFactory?.invoke(builder) ?: builder.build()
        notificationManager.notify(NOTIFICATION_ID_SENSOR_BATTERY, notification)
    }
}
