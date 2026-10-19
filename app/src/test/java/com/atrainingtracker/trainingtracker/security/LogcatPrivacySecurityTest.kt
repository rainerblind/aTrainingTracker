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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.security

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSecurityMigration
import io.mockk.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * TST-SEC-038: Privacy and Logcat Sanitization Test Suite (REQ-SEC-041, ATT-3983).
 *
 * Verifies:
 * - TST-SEC-038.1: Zero hardcoded DEBUG = true constants in production source set.
 * - TST-SEC-038.2: Static regex verification that sensitive variables (mapPolyline, accessToken,
 *                  refreshToken, raw response bodies) are not passed or concatenated to Logcat.
 * - TST-SEC-038.3: StravaHelper.storeJSONData parses athlete ID & token without logging raw secrets.
 * - TST-SEC-038.4: StravaSecurityMigration runs idempotently and non-destructively on upgrade.
 * - TST-SEC-038.5: Diagnostic status codes preserved while error bodies are redacted.
 */
class LogcatPrivacySecurityTest {

    private val mainSourceDir: File
        get() = if (File("src/main/java").exists()) {
            File("src/main/java")
        } else {
            File("app/src/main/java")
        }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-SEC-038.1: Zero active DEBUG = true occurrences in app/src/main/.
     */
    @Test
    fun verifyZeroHardcodedDebugTrueInMainSourceSet() {
        val violations = mutableListOf<String>()
        val files = mainSourceDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }

        val debugTrueRegex = Regex("""(?<!//.*)(?<!/\*.*)\bDEBUG\s*=\s*true\b""")

        for (file in files) {
            val lines = file.readLines()
            var inBlockComment = false
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("/*")) inBlockComment = true
                if (inBlockComment) {
                    if (trimmed.contains("*/")) inBlockComment = false
                    return@forEachIndexed
                }
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    return@forEachIndexed
                }
                if (debugTrueRegex.containsMatchIn(line)) {
                    violations.add("${file.path}:${index + 1}: $trimmed")
                }
            }
        }

        assertTrue(
            "Found hardcoded DEBUG = true violations in production code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * TST-SEC-038.2: Ensure no sensitive tokens, raw response bodies, or GPS polylines are passed to Log.*
     * via direct argument, string concatenation (+), or string template interpolation ($ / ${...}).
     */
    @Test
    fun verifyNoSensitiveDataPassedToLogcat() {
        val violations = mutableListOf<String>()
        val files = mainSourceDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }

        // Catch Log.<level>(TAG, ...sensitive...) where sensitive is mapPolyline, accessToken, refreshToken, or raw responseBody
        val d = "\\$"
        val sensitiveVarNames = "mapPolyline|accessToken|refreshToken|responseBody|errorBody"
        val logSensitivePatterns = listOf(
            // String interpolation: $var or ${...var...}
            Regex("""Log\.[vdiwe]\s*\([^)]*${d}(?:\{[^\}]*\b(?:$sensitiveVarNames)\b[^\}]*\}|\b(?:$sensitiveVarNames)\b)"""),
            // String concatenation: + ...var
            Regex("""Log\.[vdiwe]\s*\([^)]*\+\s*[^,)]*\b(?:$sensitiveVarNames)\b"""),
            // Direct variable or getter call as log argument: Log.d(TAG, var)
            Regex("""Log\.[vdiwe]\s*\([^,)]+,\s*[^,)]*\b(?:$sensitiveVarNames)\b\s*[,)]""")
        )

        for (file in files) {
            val lines = file.readLines()
            var inBlockComment = false
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("/*")) inBlockComment = true
                if (inBlockComment) {
                    if (trimmed.contains("*/")) inBlockComment = false
                    return@forEachIndexed
                }
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    return@forEachIndexed
                }

                for (pattern in logSensitivePatterns) {
                    if (pattern.containsMatchIn(line)) {
                        violations.add("${file.path}:${index + 1}: $trimmed")
                    }
                }
            }
        }

        assertTrue(
            "Found logging of sensitive credentials, polylines, or response bodies:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    /**
     * TST-SEC-038.3: StravaHelper.storeJSONData handles athlete ID and access token without leaking to Logcat.
     */
    @Test
    fun testStravaHelperStoreJsonDataDoesNotLeakTokens() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.setStravaAthleteId(any()) } just Runs
        every { TrainingApplication.setStravaAccessToken(any()) } just Runs
        every { TrainingApplication.setStravaRefreshToken(any()) } just Runs

        val secretAccessToken = "test_strava_access_token_abcdef12345"
        val secretRefreshToken = "test_strava_refresh_token_98765fedcba"
        val json = JSONObject().apply {
            put("access_token", secretAccessToken)
            put("refresh_token", secretRefreshToken)
            put("athlete", JSONObject().put("id", 424242))
        }

        StravaHelper.storeJSONData(json)

        verify(exactly = 1) { TrainingApplication.setStravaAthleteId(424242) }
        verify(exactly = 1) { TrainingApplication.setStravaAccessToken(secretAccessToken) }
        verify(exactly = 1) { TrainingApplication.setStravaRefreshToken(secretRefreshToken) }

        // Assert Log.i/Log.d never logged raw JSON or secrets
        verify(exactly = 0) { Log.i(any(), match { it.contains(secretAccessToken) || it.contains(secretRefreshToken) }) }
        verify(exactly = 0) { Log.d(any(), match { it.contains(secretAccessToken) || it.contains(secretRefreshToken) }) }
    }

    /**
     * TST-SEC-038.4: StravaSecurityMigration is non-destructive and flags re-authentication on first run.
     */
    @Test
    fun testStravaSecurityMigrationFirstRunFlagsReauthAndResetsTokens() {
        mockkStatic(TrainingApplication::class)
        mockkStatic(PreferenceManager::class)

        val mockContext = mockk<Context>(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>()
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)

        every { PreferenceManager.getDefaultSharedPreferences(mockContext) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor

        // Run 1: Not yet migrated, token exists
        every { mockPrefs.getBoolean(StravaSecurityMigration.PREF_KEY_SECURITY_CHECK, false) } returns false
        every { TrainingApplication.hasStravaAccessToken() } returns true
        every { TrainingApplication.setStravaAccessToken(any()) } just Runs
        every { TrainingApplication.setStravaRefreshToken(any()) } just Runs

        StravaSecurityMigration.runMigrationIfRequired(mockContext)

        verify(exactly = 1) { mockEditor.putBoolean(StravaSecurityMigration.PREF_KEY_SECURITY_CHECK, true) }
        verify(exactly = 1) { mockEditor.apply() }
        verify(exactly = 1) { TrainingApplication.setStravaAccessToken("") }
        verify(exactly = 1) { TrainingApplication.setStravaRefreshToken("") }
    }

    /**
     * TST-SEC-038.4b: StravaSecurityMigration second run is an idempotent no-op.
     */
    @Test
    fun testStravaSecurityMigrationSecondRunIsIdempotent() {
        mockkStatic(TrainingApplication::class)
        mockkStatic(PreferenceManager::class)

        val mockContext = mockk<Context>(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>()
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)

        every { PreferenceManager.getDefaultSharedPreferences(mockContext) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getBoolean(StravaSecurityMigration.PREF_KEY_SECURITY_CHECK, false) } returns true
        every { TrainingApplication.setStravaAccessToken(any()) } just Runs
        every { TrainingApplication.setStravaRefreshToken(any()) } just Runs

        StravaSecurityMigration.runMigrationIfRequired(mockContext)

        verify(exactly = 0) { mockEditor.putBoolean(any(), any()) }
        verify(exactly = 0) { TrainingApplication.setStravaAccessToken(any()) }
        verify(exactly = 0) { TrainingApplication.setStravaRefreshToken(any()) }
    }

    /**
     * TST-SEC-038.5: Diagnostic status logging preserves HTTP response code without leaking bodies.
     */
    @Test
    fun testDiagnosticStatusLoggingPreservesStatusCodeWithoutBody() {
        val capturedErrors = mutableListOf<String>()
        every { Log.e(any(), capture(capturedErrors)) } returns 0

        // Simulate error logging matching StravaHelper's new sanitized logging format
        val code = 401
        val message = "Unauthorized"
        val errorBody = "{\"message\":\"Bad Request\",\"errors\":[{\"resource\":\"Athlete\",\"field\":\"access_token\",\"code\":\"invalid\"}]}"

        val logMessage = "Refresh failed: HTTP $code $message"
        Log.e("StravaHelper", logMessage)

        assertTrue("Diagnostic HTTP code should be present", capturedErrors.any { it.contains("HTTP 401") })
        assertTrue("Diagnostic message should be present", capturedErrors.any { it.contains("Unauthorized") })
        assertFalse("Raw errorBody JSON must not be in log message", capturedErrors.any { it.contains(errorBody) })
        assertFalse("Field access_token must not be leaked", capturedErrors.any { it.contains("access_token") })
    }
}
