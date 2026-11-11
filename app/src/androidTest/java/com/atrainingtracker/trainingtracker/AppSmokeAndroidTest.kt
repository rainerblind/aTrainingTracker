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

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test verifying application context bootstrap, package id,
 * Activity launch, and non-destructive tracking start/stop lifecycle under AndroidJUnit4
 * on an Android runtime / emulator (REQ-PRO-051.2, TST-PRO-051.3, ATT-6737).
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeAndroidTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull("Target context must not be null", appContext)

        val packageName = appContext.packageName
        assertTrue(
            "Package name must be either debug or release application id. Actual: $packageName",
            packageName == "com.atrainingtracker" || packageName == "com.atrainingtracker.debug"
        )
    }

    @Test
    fun testInstrumentationBootstrap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertNotNull("Instrumentation registry must provide valid instrumentation", instrumentation)
        assertNotNull("Instrumentation context must be non-null", instrumentation.context)
    }

    @Test
    fun testMainActivityLaunchAndTrackingLifecycle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        assertNotNull("Target context must not be null", appContext)

        val scenario = ActivityScenario.launch(MainActivityWithNavigation::class.java)
        assertNotNull("Activity scenario must launch successfully", scenario)

        scenario.use {
            scenario.onActivity { activity ->
                assertNotNull("MainActivityWithNavigation must not be null", activity)
                assertFalse("MainActivityWithNavigation must not be finishing", activity.isFinishing)
            }

            var syntheticWorkoutId = -1L
            try {
                // Exercise synthetic tracking start
                val startIntent = Intent(TrainingApplication.REQUEST_START_TRACKING)
                    .setPackage(appContext.packageName)
                appContext.sendBroadcast(startIntent)

                // Bounded wait for tracking state transition (up to 3 seconds)
                val startDeadline = SystemClock.uptimeMillis() + 3000L
                while (SystemClock.uptimeMillis() < startDeadline &&
                    !TrainingApplication.isTracking() && !TrackerService.isRunning()
                ) {
                    SystemClock.sleep(100L)
                }

                syntheticWorkoutId = TrainingApplication.getActiveWorkoutID()
                val isTrackingOrStarted = TrainingApplication.isTracking() || TrackerService.isRunning()
                assertTrue(
                    "Application must enter tracking state after start broadcast",
                    isTrackingOrStarted
                )

                // Exercise synthetic tracking stop / discard
                val stopIntent = Intent(TrainingApplication.REQUEST_STOP_TRACKING)
                    .setPackage(appContext.packageName)
                appContext.sendBroadcast(stopIntent)

                // Bounded wait for tracking stop transition (up to 3 seconds)
                val stopDeadline = SystemClock.uptimeMillis() + 3000L
                while (SystemClock.uptimeMillis() < stopDeadline &&
                    (TrainingApplication.isTracking() || TrackerService.isRunning())
                ) {
                    SystemClock.sleep(100L)
                }

                val isStopped = !TrainingApplication.isTracking()
                assertTrue(
                    "Application must return to idle / ready state after stop broadcast",
                    isStopped
                )
            } finally {
                // Non-destructive cleanup: strictly delete only the synthetic test session (Core Rule 61)
                if (syntheticWorkoutId > 0) {
                    val deletionHelper = WorkoutDeletionHelper(appContext)
                    deletionHelper.deleteWorkout(syntheticWorkoutId)
                }
            }
        }
    }
}
