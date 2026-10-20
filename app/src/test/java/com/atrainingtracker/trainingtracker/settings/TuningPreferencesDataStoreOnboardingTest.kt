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

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for first-install permission onboarding DataStore preference model,
 * default constant, preference key registration, reactive flow, and atomic update
 * (REQ-PRI-007 / TST-PRI-006 / ATT-4455).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TuningPreferencesDataStoreOnboardingTest {

    @Test
    fun defaultConstants_matchSpecification() {
        assertFalse(
            "First-install permission onboarding default must be false per REQ-PRI-007",
            TuningPreferencesDefaults.DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING
        )
    }

    @Test
    fun key_matchesSpecificationAndIsRegisteredInAllKeys() {
        assertEquals(
            "tuning_has_completed_permission_onboarding",
            TuningPreferencesDataStore.KEY_HAS_COMPLETED_PERMISSION_ONBOARDING.name
        )
        assertTrue(
            "KEY_HAS_COMPLETED_PERMISSION_ONBOARDING must be registered in ALL_KEYS for factory reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(
                TuningPreferencesDataStore.KEY_HAS_COMPLETED_PERMISSION_ONBOARDING
            )
        )
    }

    @Test
    fun dataStoreFlow_defaultsToFalse_andUpdatesReactively() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = TuningPreferencesDataStore.getInstance(context)

        // Reset to clean state
        dataStore.setHasCompletedPermissionOnboarding(false)
        assertFalse(
            "Initial onboarding completion state must be false",
            dataStore.hasCompletedPermissionOnboardingFlow.first()
        )

        // Set to true
        dataStore.setHasCompletedPermissionOnboarding(true)
        assertTrue(
            "Onboarding completion state must reflect true after persistence",
            dataStore.hasCompletedPermissionOnboardingFlow.first()
        )

        // Reset back to false
        dataStore.setHasCompletedPermissionOnboarding(false)
        assertFalse(
            "Onboarding completion state must reflect false after reset",
            dataStore.hasCompletedPermissionOnboardingFlow.first()
        )
    }
}
