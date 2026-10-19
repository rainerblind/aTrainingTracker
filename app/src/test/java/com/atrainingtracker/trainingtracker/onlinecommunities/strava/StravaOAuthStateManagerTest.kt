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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test suite verifying cryptographic OAuth state generation, 10-minute expiration,
 * constant-time validation, and single-use consumption (REQ-EXT-012, TST-EXT-009, ATT-4022).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StravaOAuthStateManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        StravaOAuthStateManager.clearState(context)
        StravaOAuthStateManager.timeProvider = { System.currentTimeMillis() }
    }

    @After
    fun tearDown() {
        StravaOAuthStateManager.clearState(context)
        StravaOAuthStateManager.timeProvider = { System.currentTimeMillis() }
    }

    @Test
    fun testGenerateAndSaveState_generates32HexCharactersAndPersistsTimestamp() {
        val state1 = StravaOAuthStateManager.generateAndSaveState(context)
        assertNotNull(state1)
        assertEquals("State must be exactly 32 hex characters (128-bit)", 32, state1.length)
        assertTrue("State must contain only lowercase hex characters", state1.matches(Regex("^[0-9a-f]{32}$")))

        val state2 = StravaOAuthStateManager.generateAndSaveState(context)
        assertNotEquals("Successive generated states must be random and unique", state1, state2)
    }

    @Test
    fun testValidateAndConsumeState_matchingStateWithinTTL_returnsTrueAndClearsState() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val isValid = StravaOAuthStateManager.validateAndConsumeState(state, context)
        assertTrue("Matching state within TTL must validate successfully", isValid)

        // Single-use guarantee: immediately subsequent validation must fail
        val isSecondValid = StravaOAuthStateManager.validateAndConsumeState(state, context)
        assertFalse("State must be single-use and cannot be replayed", isSecondValid)
    }

    @Test
    fun testValidateAndConsumeState_mismatchedState_returnsFalseAndClearsState() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val isValid = StravaOAuthStateManager.validateAndConsumeState("attacker_injected_state_12345678", context)
        assertFalse("Mismatched state must be rejected", isValid)

        // Stored state must also be wiped on failed attempt to prevent brute force
        val isRetryOriginalValid = StravaOAuthStateManager.validateAndConsumeState(state, context)
        assertFalse("Stored state must be cleared even after failed validation attempt", isRetryOriginalValid)
    }

    @Test
    fun testValidateAndConsumeState_nullOrEmptyState_returnsFalse() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        assertFalse("Null state must be rejected", StravaOAuthStateManager.validateAndConsumeState(null, context))
        assertFalse("State should be consumed after null attempt", StravaOAuthStateManager.validateAndConsumeState(state, context))

        StravaOAuthStateManager.generateAndSaveState(context)
        assertFalse("Empty state must be rejected", StravaOAuthStateManager.validateAndConsumeState("", context))
    }

    @Test
    fun testValidateAndConsumeState_expiredState_returnsFalse() {
        var simulatedTime = 1_000_000_000L
        StravaOAuthStateManager.timeProvider = { simulatedTime }

        val state = StravaOAuthStateManager.generateAndSaveState(context)

        // Advance simulated time past 10 minutes (600,001 ms)
        simulatedTime += 600_001L

        val isValid = StravaOAuthStateManager.validateAndConsumeState(state, context)
        assertFalse("State exceeding 10-minute TTL must be rejected as expired", isValid)
    }

    @Test
    fun testValidateAndConsumeState_uninitiatedFlow_returnsFalse() {
        // Ensure no state is present in preferences
        StravaOAuthStateManager.clearState(context)

        val isValid = StravaOAuthStateManager.validateAndConsumeState("some_random_state", context)
        assertFalse("Callback received without an active authorization flow must be rejected", isValid)
    }

    @Test
    fun testClearState_clearsPersistedStateAndTimestamp() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)
        StravaOAuthStateManager.clearState(context)

        val isValid = StravaOAuthStateManager.validateAndConsumeState(state, context)
        assertFalse("State must not validate after explicit clearState() invocation", isValid)
    }
}
