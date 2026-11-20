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
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.tracker.WorkoutPostProcessingNotificationManager.PostProcessingStage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM unit tests for [WorkoutPostProcessingNotificationManager] verifying progress stages,
 * API 33+ `POST_NOTIFICATIONS` gating, completion dismissal, and warning notifications
 * (`REQ-TRK-091.3`, `TST-TRK-091.3`, `ATT-9154`).
 */
// Linked test specs: TST-TRK-091, TST-TRK-091.3
class WorkoutPostProcessingNotificationManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockNotificationManager: NotificationManagerCompat
    private lateinit var dummyNotification: Notification

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        dummyNotification = mockk(relaxed = true)

        every { mockContext.getString(R.string.notification_post_processing_title) } returns "Finalizing workout"
        every { mockContext.getString(R.string.notification_post_processing_stage_polar_h10) } returns "Syncing Polar H10 sensor data…"
        every { mockContext.getString(R.string.notification_post_processing_stage_polar_verity) } returns "Syncing Polar Verity Sense sensor data…"
        every { mockContext.getString(R.string.notification_post_processing_stage_recompute) } returns "Recomputing HRV and track metrics…"
        every { mockContext.getString(R.string.notification_post_processing_stage_export) } returns "Finalizing workout exports…"
        every { mockContext.getString(R.string.notification_post_processing_failed) } returns "Background workout processing finished with warnings"

        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testShowStageProgress_api33PermissionGranted_postsProgressNotification_tstTrk091_3() {
        every {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.POST_NOTIFICATIONS)
        } returns PackageManager.PERMISSION_GRANTED

        var capturedBuilder: NotificationCompat.Builder? = null
        val manager = WorkoutPostProcessingNotificationManager(
            context = mockContext,
            notificationManager = mockNotificationManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU },
            notificationFactory = { builder ->
                capturedBuilder = builder
                dummyNotification
            }
        )

        val built = manager.showStageProgress(PostProcessingStage.POLAR_H10, 0, 4)

        assertNotNull(built)
        assertNotNull(capturedBuilder)
        verify(exactly = 1) {
            mockNotificationManager.notify(
                WorkoutPostProcessingNotificationManager.NOTIFICATION_ID_POST_PROCESSING,
                dummyNotification
            )
        }
        verify(exactly = 1) { mockContext.getString(R.string.notification_post_processing_stage_polar_h10) }
    }

    @Test
    fun testShowStageProgress_api33PermissionDenied_suppressesNotifyAndWarning_tstTrk091_3() {
        every {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.POST_NOTIFICATIONS)
        } returns PackageManager.PERMISSION_DENIED

        val manager = WorkoutPostProcessingNotificationManager(
            context = mockContext,
            notificationManager = mockNotificationManager,
            sdkIntProvider = { Build.VERSION_CODES.UPSIDE_DOWN_CAKE },
            notificationFactory = { dummyNotification }
        )

        manager.showStageProgress(PostProcessingStage.RECOMPUTE, 1, 2)
        manager.showWarningNotification()

        verify(exactly = 0) { mockNotificationManager.notify(any(), any()) }
        verify(exactly = 1) { mockNotificationManager.cancel(8047) }
    }

    @Test
    fun testShowWarningAndCancelNotification_usesDedicatedWarningId8048AndCancels8047_tstTrk091_3() {
        every { mockNotificationManager.areNotificationsEnabled() } returns true

        val manager = WorkoutPostProcessingNotificationManager(
            context = mockContext,
            notificationManager = mockNotificationManager,
            sdkIntProvider = { Build.VERSION_CODES.S },
            notificationFactory = { dummyNotification }
        )

        manager.showWarningNotification()
        manager.cancelNotification()

        assertEquals(8047, WorkoutPostProcessingNotificationManager.NOTIFICATION_ID_POST_PROCESSING)
        assertEquals(8048, WorkoutPostProcessingNotificationManager.NOTIFICATION_ID_POST_PROCESSING_WARNING)
        verify(exactly = 1) { mockContext.getString(R.string.notification_post_processing_failed) }
        verify(exactly = 1) { mockNotificationManager.notify(8048, dummyNotification) }
        verify(exactly = 0) { mockNotificationManager.notify(8047, any()) }
        verify(exactly = 2) { mockNotificationManager.cancel(8047) }
    }
}
