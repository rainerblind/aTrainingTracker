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

package com.atrainingtracker.trainingtracker.ui.onboarding.permission

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Architectural contract, enum sequence, resource mapping, and modularity tests
 * for [PermissionOnboardingSheet] (REQ-PRI-007 / TST-PRI-006 / ATT-4455).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionOnboardingSheetContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    @Test
    fun permissionOnboardingSheet_enforcesStrictLineCountUnder400() {
        val file = File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/onboarding/permission/PermissionOnboardingSheet.kt"
        )
        assertTrue("PermissionOnboardingSheet.kt must exist at specified path", file.exists())
        val lineCount = file.readLines().size
        assertTrue(
            "PermissionOnboardingSheet.kt must remain strictly < 400 lines (current: $lineCount)",
            lineCount < 400
        )
    }

    @Test
    fun onboardingStepEnum_hasExpectedSequenceAndCount() {
        val steps = OnboardingStep.values()
        assertEquals("Must contain exactly 4 steps", 4, steps.size)
        assertEquals(OnboardingStep.LOCATION, steps[0])
        assertEquals(OnboardingStep.BLUETOOTH, steps[1])
        assertEquals(OnboardingStep.NOTIFICATION, steps[2])
        assertEquals(OnboardingStep.BATTERY_OPTIMIZATION, steps[3])
    }

    @Test
    fun onboardingStep_resourceProperties_areValidAndNonZero() {
        for (step in OnboardingStep.values()) {
            assertTrue("iconRes must be valid non-zero id for $step", step.iconRes != 0)
            assertTrue("titleRes must be valid non-zero id for $step", step.titleRes != 0)
            assertTrue("descRes must be valid non-zero id for $step", step.descRes != 0)
        }

        assertEquals(R.drawable.ic_location, OnboardingStep.LOCATION.iconRes)
        assertEquals(R.drawable.ic_my_paired_devices, OnboardingStep.BLUETOOTH.iconRes)
        assertEquals(R.drawable.ic_lap_timer, OnboardingStep.NOTIFICATION.iconRes)
        assertEquals(R.drawable.ic_battery_full, OnboardingStep.BATTERY_OPTIMIZATION.iconRes)

        assertEquals(R.string.permission_onboarding_location_title, OnboardingStep.LOCATION.titleRes)
        assertEquals(R.string.permission_onboarding_bluetooth_title, OnboardingStep.BLUETOOTH.titleRes)
        assertEquals(R.string.permission_onboarding_notification_title, OnboardingStep.NOTIFICATION.titleRes)
        assertEquals(R.string.permission_onboarding_battery_title, OnboardingStep.BATTERY_OPTIMIZATION.titleRes)
    }

    @Test
    fun isStepPermissionGranted_returnsBooleanWithoutExceptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (step in OnboardingStep.values()) {
            val granted = isStepPermissionGranted(context, step)
            assertNotNull("isStepPermissionGranted should return a valid boolean for $step", granted)
        }
    }
}
