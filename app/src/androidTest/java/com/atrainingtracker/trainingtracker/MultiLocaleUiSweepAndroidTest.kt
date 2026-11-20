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
import com.atrainingtracker.R
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
 * Deterministic multi-screen navigation and tracking click-through instrumented verification
 * executed per OS/AVD and per active locale during the Multi-OS & 9-Language Emulator Sweep
 * (REQ-PRO-135.5, TST-PRO-135.5, ATT-8222).
 */
// Linked test specs: TST-PRO-135, TST-PRO-135.5
@RunWith(AndroidJUnit4::class)
class MultiLocaleUiSweepAndroidTest {

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
    fun sweepCoreScreensAndTrackingLifecycle_withoutCrashing_TST_PRO_135_5() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        assertNotNull("Target context must be non-null", appContext)

        // Verify localized core labels resolve cleanly in the active system/app locale
        assertTrue(appContext.getString(R.string.Start).isNotBlank())
        assertTrue(appContext.getString(R.string.Pause).isNotBlank())
        assertTrue(appContext.getString(R.string.Stop).isNotBlank())

        val drawerScreens = listOf(
            R.id.drawer_start_tracking,
            R.id.drawer_workouts,
            R.id.drawer_periods,
            R.id.drawer_my_sensors,
            R.id.drawer_sport_types,
            R.id.drawer_start_locations,
            R.id.drawer_units,
            R.id.drawer_display_settings,
            R.id.drawer_export,
            R.id.drawer_start_tracking
        )

        ActivityScenario.launch(MainActivityWithNavigation::class.java).use { scenario ->
            for (drawerId in drawerScreens) {
                scenario.onActivity { activity ->
                    assertFalse("Activity must stay alive before navigating", activity.isFinishing)
                    activity.navigateToDrawerItem(drawerId)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(250L)
                scenario.onActivity { activity ->
                    assertFalse("Activity must remain healthy after navigating to $drawerId", activity.isFinishing)
                }
            }

            // Exercise non-destructive Start -> Pause -> Stop tracking lifecycle
            var syntheticWorkoutId = -1L
            try {
                appContext.sendBroadcast(
                    Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(appContext.packageName)
                )
                val startDeadline = SystemClock.uptimeMillis() + 3000L
                while (SystemClock.uptimeMillis() < startDeadline &&
                    !TrainingApplication.isTracking() && !TrackerService.isRunning()
                ) {
                    SystemClock.sleep(100L)
                }
                syntheticWorkoutId = TrainingApplication.getActiveWorkoutID()
                assertTrue(
                    "Tracking must start during sweep",
                    TrainingApplication.isTracking() || TrackerService.isRunning()
                )

                appContext.sendBroadcast(
                    Intent(TrainingApplication.REQUEST_PAUSE_TRACKING).setPackage(appContext.packageName)
                )
                SystemClock.sleep(200L)

                appContext.sendBroadcast(
                    Intent(TrainingApplication.REQUEST_STOP_TRACKING).setPackage(appContext.packageName)
                )
                val stopDeadline = SystemClock.uptimeMillis() + 3000L
                while (SystemClock.uptimeMillis() < stopDeadline &&
                    (TrainingApplication.isTracking() || TrackerService.isRunning())
                ) {
                    SystemClock.sleep(100L)
                }
                assertFalse("Tracking must stop cleanly", TrainingApplication.isTracking())
            } finally {
                if (syntheticWorkoutId > 0L) {
                    WorkoutDeletionHelper(appContext).deleteWorkout(syntheticWorkoutId)
                }
            }
        }
    }
}
