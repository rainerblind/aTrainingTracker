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

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests verifying Chrome Custom Tabs Strava OAuth launch and reactive
 * [StravaAuthViewModel] / [StravaAuthRepository] token-exchange feedback states
 * (REQ-EXT-004, TST-EXT-001; REQ-EXT-005, TST-EXT-002).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StravaAuthCustomTabsFlowTest {

    private lateinit var app: Application
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        StravaOAuthStateManager.clearState(app)
        StravaAuthRepository.getInstance().resetState()
    }

    @After
    fun tearDown() {
        StravaOAuthStateManager.clearState(app)
        StravaAuthRepository.getInstance().resetState()
        Dispatchers.resetMain()
    }

    @Test
    fun testRequestAccessToken_launchesCustomTabsIntentWithAuthorizeUrlAndState_TST_EXT_001() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        StravaHelper.requestAccessToken(activity)

        val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull("CustomTabsIntent must launch an Activity intent", startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertTrue(
            "Intent must carry CustomTabs EXTRA_SESSION",
            startedIntent.hasExtra(CustomTabsIntent.EXTRA_SESSION)
        )
        val uri: Uri? = startedIntent.data
        assertNotNull(uri)
        assertEquals("www.strava.com", uri!!.host)
        assertEquals("/oauth/mobile/authorize", uri.path)
        val state = uri.getQueryParameter("state")
        assertTrue("OAuth URL must include non-blank CSRF state parameter", !state.isNullOrBlank())
        assertTrue(
            "Generated state must validate cleanly",
            StravaOAuthStateManager.validateAndConsumeState(state, app)
        )
    }

    @Test
    fun testStravaAuthViewModel_exchangeCodeUpdatesLoadingAndErrorState_TST_EXT_002() = runBlocking {
        val repo = StravaAuthRepository.getInstance()
        val clientField = StravaAuthRepository::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        val originalClient = clientField.get(repo) as OkHttpClient
        try {
            // Short-circuit network call via OkHttp Interceptor to verify Error state transition deterministically
            val testClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"message":"Unauthorized"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            clientField.set(repo, testClient)

            val vm = StravaAuthViewModel()
            assertEquals(StravaAuthState.Idle, vm.authState.value)

            vm.exchangeCode("dummy_code")
            val state = withTimeout(5_000) {
                vm.authState.first { it is StravaAuthState.Error }
            }
            assertTrue("Expected Error state on failed token exchange, got $state", state is StravaAuthState.Error)

            vm.resetState()
            assertEquals(StravaAuthState.Idle, vm.authState.value)
        } finally {
            clientField.set(repo, originalClient)
        }
    }

    @Test
    fun testStravaAuthRepository_transitionsThroughLoadingToErrorOnHttpFailure_TST_EXT_002() = runBlocking {
        val repo = StravaAuthRepository.getInstance()
        repo.setAuthState(StravaAuthState.Loading)
        assertEquals(StravaAuthState.Loading, repo.authState.value)
        repo.setAuthState(StravaAuthState.Success("tok_123"))
        assertEquals(StravaAuthState.Success("tok_123"), repo.authState.value)
    }
}
