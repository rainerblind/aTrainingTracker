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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Security and contract test suite verifying Strava OAuth redirect URI scheme scoping
 * by applicationId to prevent release/debug app chooser intent collisions and wildcard path collisions with legacy builds (REQ-EXT-012, TST-EXT-012, ATT-4668, ATT-4706).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StravaOAuthSecurityTest {

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

    /**
     * TST-EXT-012.1: Authorization URL contains redirect_uri scoped with BuildConfig.APPLICATION_ID scheme.
     */
    @Test
    fun testAuthorizationUrl_scopedByApplicationId() {
        val testState = "sec_state_1234567890abcdef123456"
        val authUrl = StravaHelper.getAuthorizationUrl(testState)

        val expectedRedirectUri = "strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"
        val encodedRedirectUri = "strava.${BuildConfig.APPLICATION_ID}%3A%2F%2Frainerblind.github.io"

        assertTrue(
            "Authorization URL redirect_uri parameter must contain applicationId scheme ($expectedRedirectUri)",
            authUrl.contains("redirect_uri=$encodedRedirectUri") || authUrl.contains("redirect_uri=$expectedRedirectUri")
        )
    }

    /**
     * TST-EXT-012.2: Callback activity accepts matching variant scheme, validates state, and initiates token exchange.
     */
    @Test
    fun testCallbackActivity_matchingVariantScheme_consumesStateAndProcessesCallback() {
        val legitimateState = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io?state=$legitimateState&code=auth_code_789")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must finish cleanly", activity.isFinishing)
        assertFalse("Legitimate state must be consumed on matching variant callback",
            StravaOAuthStateManager.validateAndConsumeState(legitimateState, context))
        val authState = StravaAuthRepository.getInstance().authState.value
        assertFalse("Auth state must not be state validation Error on valid callback",
            authState is StravaAuthState.Error && authState.message.contains("state validation failed"))
    }

    /**
     * TST-EXT-012.3: Callback activity ignores callback targeting a different variant scheme.
     */
    @Test
    fun testCallbackActivity_mismatchedVariantScheme_ignoredWithoutConsumingState() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava.com.atrainingtracker.other://rainerblind.github.io?state=$state&code=foreign_auth_code")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must finish cleanly", activity.isFinishing)
        assertTrue("Auth state must remain Idle for mismatched variant scheme",
            StravaAuthRepository.getInstance().authState.value is StravaAuthState.Idle)
        assertTrue("State must remain untouched and consumable by legitimate variant",
            StravaOAuthStateManager.validateAndConsumeState(state, context))
    }

    /**
     * TST-EXT-012.4: Callback activity ignores legacy un-scoped callback URI.
     */
    @Test
    fun testCallbackActivity_legacyUnscopedUri_ignoredWithoutConsumingState() {
        val state = StravaOAuthStateManager.generateAndSaveState(context)

        val uri = Uri.parse("strava://rainerblind.github.io?state=$state&code=legacy_auth_code")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val controller = Robolectric.buildActivity(StravaOAuthCallbackActivity::class.java, intent)
        val activity = controller.create().get()

        assertTrue("Activity must finish cleanly", activity.isFinishing)
        assertTrue("Auth state must remain Idle for legacy un-scoped URI",
            StravaAuthRepository.getInstance().authState.value is StravaAuthState.Idle)
        assertTrue("State must remain untouched and consumable",
            StravaOAuthStateManager.validateAndConsumeState(state, context))
    }

    /**
     * TST-EXT-012.5: AndroidManifest.xml configures android:scheme="strava.${applicationId}" without android:path for StravaOAuthCallbackActivity.
     */
    @Test
    fun testManifest_stravaOAuthCallbackActivity_declaresApplicationIdSchemeScoping() {
        val manifestFile = findManifestFile()
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val activities = doc.getElementsByTagName("activity")
        var callbackActivity: Element? = null

        for (i in 0 until activities.length) {
            val element = activities.item(i) as Element
            val name = element.getAttribute("android:name")
            if (name.contains("StravaOAuthCallbackActivity")) {
                callbackActivity = element
                break
            }
        }

        assertNotNull("StravaOAuthCallbackActivity must be declared in AndroidManifest.xml", callbackActivity)

        val intentFilters = callbackActivity!!.getElementsByTagName("intent-filter")
        assertTrue("StravaOAuthCallbackActivity must declare intent-filter", intentFilters.length > 0)

        var foundScopedData = false
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val dataElements = filter.getElementsByTagName("data")

            var hasScheme = false
            var hasHost = false
            var hasPath = false

            for (j in 0 until dataElements.length) {
                val data = dataElements.item(j) as Element
                if (data.getAttribute("android:scheme") == "strava.\${applicationId}") hasScheme = true
                if (data.getAttribute("android:host") == "rainerblind.github.io") hasHost = true
                if (data.hasAttribute("android:path")) hasPath = true
            }

            if (hasScheme && hasHost && !hasPath) {
                foundScopedData = true
                break
            }
        }

        assertTrue(
            "StravaOAuthCallbackActivity intent filter must declare scheme='strava.\${applicationId}', host='rainerblind.github.io', and no path (REQ-EXT-012.6a, TST-EXT-012.5)",
            foundScopedData
        )
    }

    private fun findManifestFile(): File {
        val candidates = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("../src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find AndroidManifest.xml from ${File(".").absolutePath}")
    }
}
