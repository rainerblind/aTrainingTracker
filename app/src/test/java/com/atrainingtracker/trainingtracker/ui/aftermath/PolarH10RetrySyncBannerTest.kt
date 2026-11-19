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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRetrySyncCoordinator
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behavioral Compose UI verification for [PolarPendingRetrySyncSection] (`TST-SET-121.1`, `REQ-SET-119.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolarH10RetrySyncBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        PolarRetrySyncCoordinator.resetForTesting()
        PolarRetrySyncCoordinator.dispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        PolarRetrySyncCoordinator.resetForTesting()
    }

    @Test
    fun syncNow_survivesBannerLeavingAndReenteringCompositionWithoutForgottenScopeException() {
        val syncGate = CompletableDeferred<Boolean>()
        val executionCount = AtomicInteger(0)
        var capturedError: Throwable? = null
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ ->
            executionCount.incrementAndGet()
            try {
                syncGate.await()
            } catch (t: Throwable) {
                capturedError = t
                throw t
            }
        }

        var showBanner by mutableStateOf(true)
        var dismissCount = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                if (showBanner) {
                    PolarPendingRetrySyncSection(
                        workoutId = 2195L,
                        polarExerciseId = "verity:/U/0/HR.BPB",
                        onDismiss = { dismissCount++ }
                    )
                }
            }
        }

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val syncingLabel = appContext.getString(R.string.polar_verity_retry_syncing)
        val dismissLabel = appContext.getString(R.string.polar_h10_retry_sync_dismiss)

        composeTestRule.onNodeWithText(syncNowLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, executionCount.get())
        composeTestRule.onNodeWithText(syncingLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsNotEnabled()
        composeTestRule.onNodeWithText(dismissLabel).assertIsNotEnabled()

        // Dispose banner from composition mid-flight and remount it
        showBanner = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(syncingLabel).assertDoesNotExist()

        showBanner = true
        composeTestRule.waitForIdle()

        // Must survive without ForgottenCoroutineScopeException and still show in-flight syncing state
        assertTrue(capturedError == null)
        assertTrue(PolarRetrySyncCoordinator.stateFor(2195L).isSyncing)
        composeTestRule.onNodeWithText(syncingLabel).assertIsDisplayed()

        // Complete sync and verify completion state
        syncGate.complete(true)
        composeTestRule.waitForIdle()

        assertFalse(PolarRetrySyncCoordinator.stateFor(2195L).isSyncing)
        assertEquals(0, dismissCount)
    }

    @Test
    fun syncNow_failureDisplaysLocalizedErrorMessage() {
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ -> false }

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                PolarPendingRetrySyncSection(
                    workoutId = 2196L,
                    polarExerciseId = "verity:/U/0/HR.BPB",
                    onDismiss = {}
                )
            }
        }

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val failedLabel = appContext.getString(R.string.polar_verity_retry_sync_failed)

        composeTestRule.onNodeWithText(syncNowLabel).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(failedLabel).assertIsDisplayed()
    }

    @Test
    fun syncNow_completionEmitsWorkoutIdForTelemetryRefresh() = runBlocking {
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ -> true }
        val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000L) {
                PolarRetrySyncCoordinator.completedWorkoutEvents.first()
            }
        }
        val job = PolarRetrySyncCoordinator.startSync(
            context = appContext,
            workoutId = 2197L,
            polarExerciseId = "verity:",
            failedMessage = "failed"
        )
        withTimeout(5_000L) { job!!.join() }
        assertEquals(2197L, completionDeferred.await())
    }
}
