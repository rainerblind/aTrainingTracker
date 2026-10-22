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
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.BuildConfig
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test suite verifying CSRF protection, state parameter validation, error handling,
 * and structured coroutine execution in StravaOAuthCallbackActivity (REQ-EXT-012, TST-EXT-009, ATT-4022).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StravaOAuthCallbackActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        StravaOAuthStateManager.clearState(context)
        StravaAuthRepository.getInstance().resetState()
    }

    @After
    fun tearDown() {
        StravaOAuthStateManager.clearState(context)
        StravaAuthRepository.getInstance().resetState()
    }

    @Test
    fun testHandleIntent_missingStateParameter_rejectsAndSetsErrorState() {
        val uri = Uri.parse("strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}?code=injected_auth_code_123")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)
        val authState = StravaAuthRepository.getInstance().authState.value
        assertTrue("Auth state must be Error when state parameter is missing", authState is StravaAuthState.Error)
    }

    @Test
    fun testHandleIntent_mismatchedStateParameter_rejectsAndSetsErrorState() {
        val legitimateState = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}?state=attacker_state_xyz&code=injected_auth_code_123")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)
        val authState = StravaAuthRepository.getInstance().authState.value
        assertTrue("Auth state must be Error when state parameter mismatches", authState is StravaAuthState.Error)

        // Verify legitimate state was consumed and can no longer be used
        assertFalse(StravaOAuthStateManager.validateAndConsumeState(legitimateState, context))
    }

    @Test
    fun testHandleIntent_validStateParameter_dispatchesExchangeAndConsumesState() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}?state=$state&code=valid_exchange_code")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)

        // The state must have been consumed immediately
        assertFalse("State must be consumed upon valid callback processing",
            StravaOAuthStateManager.validateAndConsumeState(state, context))

        val authState = StravaAuthRepository.getInstance().authState.value
        // Auth state should be Loading or progressed past Idle, NOT rejected with state validation Error
        assertFalse("Auth state must not be state validation Error on valid state",
            authState is StravaAuthState.Error && (authState as StravaAuthState.Error).message.contains("state validation failed"))
    }

    @Test
    fun testHandleIntent_withErrorQueryParam_clearsStateAndSetsError() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}?error=access_denied")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)

        val authState = StravaAuthRepository.getInstance().authState.value
        assertTrue("Auth state must reflect cancellation error", authState is StravaAuthState.Error)
        assertTrue("Error message should mention access_denied",
            (authState as StravaAuthState.Error).message.contains("access_denied"))

        // State must be cleared
        assertFalse(StravaOAuthStateManager.validateAndConsumeState(state, context))
    }

    @Test
    fun testHandleIntent_unrecognizedSchemeOrHost_finishesCleanly() {
        val uri = Uri.parse("https://random.site.com/callback?code=foo")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)
        assertTrue("Auth state must remain Idle", StravaAuthRepository.getInstance().authState.value is StravaAuthState.Idle)
    }

    /**
     * TST-EXT-012.3: Callback targeting a different application ID path is ignored without state consumption.
     */
    @Test
    fun testHandleIntent_mismatchedApplicationIdPath_ignoresAndFinishesCleanly() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava://rainerblind.github.io/com.other.variant?state=$state&code=valid_exchange_code")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)
        assertTrue("Auth state must remain Idle for mismatched variant",
            StravaAuthRepository.getInstance().authState.value is StravaAuthState.Idle)
        assertTrue("State must NOT be consumed by mismatched variant callback",
            StravaOAuthStateManager.validateAndConsumeState(state, context))
    }

    /**
     * TST-EXT-012.4: Legacy un-scoped redirect URI is ignored without state consumption.
     */
    @Test
    fun testHandleIntent_legacyUnscopedUri_ignoresAndFinishesCleanly() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava://rainerblind.github.io?state=$state&code=valid_exchange_code")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must call finish()", activity.isFinishing)
        assertTrue("Auth state must remain Idle for legacy un-scoped URI",
            StravaAuthRepository.getInstance().authState.value is StravaAuthState.Idle)
        assertTrue("State must NOT be consumed by un-scoped callback",
            StravaOAuthStateManager.validateAndConsumeState(state, context))
    }
}
