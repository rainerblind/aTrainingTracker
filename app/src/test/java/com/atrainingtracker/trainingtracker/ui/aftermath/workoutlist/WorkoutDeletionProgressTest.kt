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

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit tests verifying bulk workout deletion progress feedback,
 * state machine transitions, notification dispatch, and fail-safe reset (REQ-UI-128, TST-UI-081, ATT-705).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDeletionProgressTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockNotificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val mockNotification = mockk<Notification>(relaxed = true)
    private lateinit var deletionNotificationManager: WorkoutDeletionNotificationManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { mockContext.getString(any()) } returns "Test String"
        every { mockContext.getString(any(), any()) } returns "Deleting: Test Workout"
        every { mockContext.packageName } returns "com.atrainingtracker"
        every { mockNotificationManager.areNotificationsEnabled() } returns true

        deletionNotificationManager = WorkoutDeletionNotificationManager(
            mockContext,
            mockNotificationManager
        ).apply {
            notificationFactory = { mockNotification }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testDeletionProgress_progressCalculation() {
        val progress1 = DeletionProgress.Deleting(1, 4, "W1", 101L)
        assertEquals(0.25f, progress1.progress, 0.001f)

        val progress2 = DeletionProgress.Deleting(4, 4, "W4", 104L)
        assertEquals(1.0f, progress2.progress, 0.001f)

        val progressZero = DeletionProgress.Deleting(0, 0, "W0", 100L)
        assertEquals(0.0f, progressZero.progress, 0.001f)
    }

    @Test
    fun testNotificationManager_showProgressNotification_notifiesWithCorrectId() {
        deletionNotificationManager.showProgressNotification(2, 5, "Morning Ride")

        verify {
            mockNotificationManager.notify(
                WorkoutDeletionNotificationManager.NOTIFICATION_ID_DELETION,
                any()
            )
        }
    }

    @Test
    fun testNotificationManager_showResyncNotification_notifiesWithCorrectId() {
        deletionNotificationManager.showResyncNotification()

        verify {
            mockNotificationManager.notify(
                WorkoutDeletionNotificationManager.NOTIFICATION_ID_DELETION,
                any()
            )
        }
    }

    @Test
    fun testNotificationManager_cancelNotification_cancelsCorrectId() {
        deletionNotificationManager.cancelNotification()

        verify {
            mockNotificationManager.cancel(WorkoutDeletionNotificationManager.NOTIFICATION_ID_DELETION)
        }
    }

    @Test
    fun testNotificationManager_disabledNotifications_doesNotNotify() {
        every { mockNotificationManager.areNotificationsEnabled() } returns false

        val disabledManager = WorkoutDeletionNotificationManager(mockContext, mockNotificationManager)
        disabledManager.showProgressNotification(1, 2, "Run")

        verify(exactly = 0) { mockNotificationManager.notify(any(), any()) }
    }
}
