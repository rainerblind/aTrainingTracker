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

package com.atrainingtracker.trainingtracker

import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.atrainingtracker.trainingtracker.TrackingMode
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying Pause and Resume Neutrality for TrainingApplication (REQ-TRK-002, TST-TRK-002, ATT-896).
 * Ensures that pausing and resuming does not split laps or emit REQUEST_NEW_LAP broadcasts.
 */
class TrainingApplicationLapTest {

    private class TestableTrainingApplication : TrainingApplication() {
        val broadcastedIntents = mutableListOf<Intent>()

        override fun sendBroadcast(intent: Intent) {
            broadcastedIntents.add(intent)
        }

        override fun getPackageName(): String = "com.atrainingtracker.trainingtracker"

        override fun notifyTrackingStateChanged() {
            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns TRACKING_STATE_CHANGED
            sendBroadcast(intent)
        }

        fun invokePauseTracking() {
            pauseTracking()
        }

        fun invokeResumeFromPaused() {
            resumeFromPaused()
        }
    }

    private lateinit var app: TestableTrainingApplication
    private lateinit var mockPrefs: SharedPreferences

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getBoolean(any(), any()) } returns false

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)

        app = TestableTrainingApplication()
    }

    @After
    fun tearDown() {
        unmockkAll()
        TrainingApplication.cTrackingMode = TrackingMode.READY
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testPauseTracking_transitionsToPausedWithoutBroadcastingRequestNewLap() {
        TrainingApplication.cTrackingMode = TrackingMode.TRACKING
        assertTrue("TrackingMode must initially be TRACKING", TrainingApplication.isTracking())

        app.invokePauseTracking()

        assertEquals("TrackingMode must transition to PAUSED", TrackingMode.PAUSED, TrainingApplication.getTrackingMode())
        assertTrue("isPaused() must return true", TrainingApplication.isPaused())

        // Verify that NO broadcast with action REQUEST_NEW_LAP was sent
        val newLapIntents = app.broadcastedIntents.filter { it.action == TrainingApplication.REQUEST_NEW_LAP }
        assertTrue("pauseTracking must NOT broadcast REQUEST_NEW_LAP (REQ-TRK-002)", newLapIntents.isEmpty())

        // Verify state change broadcast was sent
        val stateChangedIntents = app.broadcastedIntents.filter { it.action == TrainingApplication.TRACKING_STATE_CHANGED }
        assertEquals(1, stateChangedIntents.size)
    }

    @Test
    fun testResumeFromPaused_transitionsToTrackingWithoutBroadcastingRequestNewLap() {
        TrainingApplication.cTrackingMode = TrackingMode.PAUSED
        assertTrue("TrackingMode must initially be PAUSED", TrainingApplication.isPaused())

        app.invokeResumeFromPaused()

        assertEquals("TrackingMode must transition to TRACKING", TrackingMode.TRACKING, TrainingApplication.cTrackingMode)
        assertTrue("isTracking() must return true", TrainingApplication.isTracking())
        assertFalse("isPaused() must return false", TrainingApplication.isPaused())

        // Verify that NO broadcast with action REQUEST_NEW_LAP was sent
        val newLapIntents = app.broadcastedIntents.filter { it.action == TrainingApplication.REQUEST_NEW_LAP }
        assertTrue("resumeFromPaused must NOT broadcast REQUEST_NEW_LAP (REQ-TRK-002)", newLapIntents.isEmpty())

        // Verify state change broadcast was sent
        val stateChangedIntents = app.broadcastedIntents.filter { it.action == TrainingApplication.TRACKING_STATE_CHANGED }
        assertEquals(1, stateChangedIntents.size)
    }
}
