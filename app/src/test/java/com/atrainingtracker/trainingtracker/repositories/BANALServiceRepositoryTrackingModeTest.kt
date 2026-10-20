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

package com.atrainingtracker.trainingtracker.repositories

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying sticky tracking mode initialization and broadcast synchronization
 * in BANALServiceRepository (REQ-UI-345, TST-UI-305, ATT-3165).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositoryTrackingModeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        resetSingleton()
    }

    @After
    fun tearDown() {
        resetSingleton()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
        setTrackingMode(TrackingMode.READY)
        setWasAutoPaused(false)
    }

    private fun resetSingleton() {
        try {
            val field: Field = BANALServiceRepository::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }

    private fun setTrackingMode(mode: TrackingMode) {
        val field: Field = TrainingApplication::class.java.getDeclaredField("cTrackingMode")
        field.isAccessible = true
        field.set(null, mode)
    }

    private fun setWasAutoPaused(wasAuto: Boolean) {
        val field: Field = TrainingApplication::class.java.getDeclaredField("mWasAutoPaused")
        field.isAccessible = true
        (field.get(null) as java.util.concurrent.atomic.AtomicBoolean).set(wasAuto)
    }

    @Test
    fun testInit_initializesTrackingModeFromTrainingApplication() {
        setTrackingMode(TrackingMode.TRACKING)

        val repository = BANALServiceRepository.getInstance(mockContext)

        assertEquals(
            "BANALServiceRepository must initialize _trackingMode with current TrainingApplication state",
            TrackingMode.TRACKING,
            repository.trackingMode.value
        )
    }

    @Test
    fun testInit_initializesWasAutoPausedFromTrainingApplication() {
        setWasAutoPaused(true)

        val repository = BANALServiceRepository.getInstance(mockContext)

        assertEquals(
            "BANALServiceRepository must initialize wasAutoPaused with current TrainingApplication state",
            true,
            repository.wasAutoPaused.value
        )
    }

    @Test
    fun testTrackingModeReceiver_updatesTrackingModeOnBroadcast() {
        setTrackingMode(TrackingMode.READY)
        val repository = BANALServiceRepository.getInstance(mockContext)
        assertEquals(TrackingMode.READY, repository.trackingMode.value)

        val receiverField = BANALServiceRepository::class.java.getDeclaredField("trackingModeReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(repository) as BroadcastReceiver

        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.getBooleanExtra(TrainingApplication.EXTRA_WAS_AUTO_PAUSED, any()) } returns false

        setTrackingMode(TrackingMode.PAUSED)
        receiver.onReceive(mockContext, mockIntent)

        assertEquals(
            "BANALServiceRepository must update _trackingMode when TRACKING_STATE_CHANGED broadcast is received",
            TrackingMode.PAUSED,
            repository.trackingMode.value
        )
    }

    @Test
    fun testTrackingModeReceiver_updatesWasAutoPausedFromIntentExtra() {
        val repository = BANALServiceRepository.getInstance(mockContext)
        assertEquals(false, repository.wasAutoPaused.value)

        val receiverField = BANALServiceRepository::class.java.getDeclaredField("trackingModeReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(repository) as BroadcastReceiver

        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.getBooleanExtra(TrainingApplication.EXTRA_WAS_AUTO_PAUSED, false) } returns true

        receiver.onReceive(mockContext, mockIntent)

        assertEquals(
            "BANALServiceRepository must update wasAutoPaused from EXTRA_WAS_AUTO_PAUSED",
            true,
            repository.wasAutoPaused.value
        )
    }

    @Test
    fun testTrackingModeReceiver_nullIntentFallbackToTrainingApplication() {
        setWasAutoPaused(true)
        val repository = BANALServiceRepository.getInstance(mockContext)

        val receiverField = BANALServiceRepository::class.java.getDeclaredField("trackingModeReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(repository) as BroadcastReceiver

        receiver.onReceive(mockContext, null)

        assertEquals(
            "BANALServiceRepository must fallback to TrainingApplication.wasAutoPaused() when intent is null",
            true,
            repository.wasAutoPaused.value
        )
    }
}
