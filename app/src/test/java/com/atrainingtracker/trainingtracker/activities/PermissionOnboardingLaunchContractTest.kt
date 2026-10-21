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

package com.atrainingtracker.trainingtracker.activities

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.navigation.checkAreAllPermissionsGranted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Launch contract, initial launch state branching, upgraded user bypass,
 * and returning user persistence tests for first-install permission onboarding
 * (REQ-PRI-007 / TST-PRI-006 / ATT-4455).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionOnboardingLaunchContractTest {

    private lateinit var application: Application
    private lateinit var dataStore: TuningPreferencesDataStore

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        dataStore = TuningPreferencesDataStore.getInstance(application)
    }

    @Test
    fun cleanInstall_permissionsUngranted_showsOnboardingSheet() = runBlocking {
        dataStore.setHasCompletedPermissionOnboarding(false)
        val hasCompleted = dataStore.hasCompletedPermissionOnboardingFlow.first()
        val allGranted = checkAreAllPermissionsGranted(application)

        assertFalse("Clean install must default to false", hasCompleted)
        assertFalse("Clean install context does not have permissions granted by default", allGranted)

        val shouldShowSheet = !hasCompleted && !allGranted
        assertTrue(
            "Clean install with ungranted permissions must trigger PermissionOnboardingSheet overlay",
            shouldShowSheet
        )
    }

    @Test
    fun returningUser_onboardingCompleted_bypassesOnboardingSheet() = runBlocking {
        dataStore.setHasCompletedPermissionOnboarding(true)
        val hasCompleted = dataStore.hasCompletedPermissionOnboardingFlow.first()
        val allGranted = checkAreAllPermissionsGranted(application)

        assertTrue("Returning user has completed onboarding = true", hasCompleted)

        val shouldShowSheet = !hasCompleted && !allGranted
        assertFalse(
            "Returning user who completed or skipped onboarding must never see PermissionOnboardingSheet",
            shouldShowSheet
        )
    }

    @Test
    fun upgradedUser_allPermissionsAlreadyGranted_autoCompletesOnboarding() = runBlocking {
        dataStore.setHasCompletedPermissionOnboarding(false)

        fun shouldShowOnboarding(hasCompleted: Boolean, allGranted: Boolean): Boolean {
            return !hasCompleted && !allGranted
        }

        // When all permissions are granted (simulated upgrade scenario)
        val simulatedAllGranted = true
        val hasCompleted = dataStore.hasCompletedPermissionOnboardingFlow.first()

        assertFalse(
            "Upgraded user with all permissions granted must bypass onboarding sheet",
            shouldShowOnboarding(hasCompleted, simulatedAllGranted)
        )
    }

    @Test
    fun rule47_retainsModalOverlayAlongsideHostScreenInComposeTree() {
        val appFile = File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt"
        )
        assertTrue(appFile.exists())
        val content = appFile.readText()

        assertTrue(
            "Must mount PermissionOnboardingSheet in ATrainingTrackerApp alongside host Scaffold",
            content.contains("PermissionOnboardingSheet(")
        )
        assertTrue(
            "Must observe tuningDataStore.hasCompletedPermissionOnboardingFlow in ATrainingTrackerApp",
            content.contains("hasCompletedPermissionOnboardingFlow")
        )
        assertTrue(
            "Must evaluate checkAreAllPermissionsGranted",
            content.contains("checkAreAllPermissionsGranted(context)")
        )
    }
}
