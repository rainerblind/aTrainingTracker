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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRetrySyncCoordinator
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Behavioral Compose UI verification for [PolarPendingRetrySyncSection] and [PolarH10RetrySyncBanner]
 * (`TST-SET-121.1`, `REQ-SET-119.1`, `REQ-SET-125`, `TST-SET-127`, `ATT-9509`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        DeviceDataRepository.resetForTesting(null)
        DevicesDatabaseManager.resetForTesting()
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
                        polarDeviceId = "24825632",
                        isSensorPairedOverride = true,
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
                    polarDeviceId = "24825632",
                    isSensorPairedOverride = true,
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

    @Test
    fun unpairedVeritySensor_disablesSyncNowKeepsDismissEnabledAndShowsHintOverStaleError() {
        // Verifies REQ-SET-125 / TST-SET-127
        var syncClicks = 0
        var dismissClicks = 0
        val staleError = "Stale generic connection error"

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                PolarH10RetrySyncBanner(
                    onSyncNow = { syncClicks++ },
                    onDismiss = { dismissClicks++ },
                    isVeritySense = true,
                    isSensorPaired = false,
                    errorMessage = staleError
                )
            }
        }

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val dismissLabel = appContext.getString(R.string.polar_h10_retry_sync_dismiss)
        val unpairedHint = appContext.getString(R.string.polar_verity_retry_sync_unpaired_hint)

        composeTestRule.onNodeWithText(unpairedHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(staleError).assertDoesNotExist()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText(dismissLabel).assertIsDisplayed().assertIsEnabled().performClick()

        assertEquals(0, syncClicks)
        assertEquals(1, dismissClicks)
    }

    @Test
    fun unpairedH10Sensor_disablesSyncNowAndShowsH10UnpairedHint() {
        // Verifies REQ-SET-125 / TST-SET-127
        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                PolarH10RetrySyncBanner(
                    onSyncNow = {},
                    onDismiss = {},
                    isVeritySense = false,
                    isSensorPaired = false
                )
            }
        }

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val dismissLabel = appContext.getString(R.string.polar_h10_retry_sync_dismiss)
        val h10UnpairedHint = appContext.getString(R.string.polar_h10_retry_sync_unpaired_hint)

        composeTestRule.onNodeWithText(h10UnpairedHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsNotEnabled()
        composeTestRule.onNodeWithText(dismissLabel).assertIsEnabled()
    }

    @Test
    fun initialAsyncPairingCheck_avoidsFrame0FalseUnpairedFlicker_thenDisablesWhenUnpaired() {
        // Verifies REQ-SET-125 / TST-SET-127
        val initialQueryGate = CompletableDeferred<Boolean>()

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                PolarPendingRetrySyncSection(
                    workoutId = 2198L,
                    polarExerciseId = "verity:/U/0/HR.BPB",
                    polarDeviceId = "24825632",
                    checkSensorPaired = { _, _ -> initialQueryGate.await() },
                    onDismiss = {}
                )
            }
        }

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val unpairedHint = appContext.getString(R.string.polar_verity_retry_sync_unpaired_hint)

        // Frame 0 while async DB query is still in flight: no false unpaired hint or disabled flicker
        composeTestRule.onNodeWithText(unpairedHint).assertDoesNotExist()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsEnabled()

        // Complete initial async query with unpaired = false
        initialQueryGate.complete(false)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(unpairedHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsNotEnabled()
    }

    @Test
    fun rePairingSensorOnResume_clearsHintReEnablesSyncNowAndLaunchesSync() {
        // Verifies REQ-SET-125 / TST-SET-127
        val executionCount = AtomicInteger(0)
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, _, _ ->
            executionCount.incrementAndGet()
            true
        }
        var pairedState = false
        val fakeOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
            override val lifecycle: Lifecycle get() = registry
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides fakeOwner) {
                ATrainingTrackerTheme(setWindowColors = false) {
                    PolarPendingRetrySyncSection(
                        workoutId = 2199L,
                        polarExerciseId = "verity:/U/0/HR.BPB",
                        polarDeviceId = "24825632",
                        checkSensorPaired = { _, _ -> pairedState },
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val unpairedHint = appContext.getString(R.string.polar_verity_retry_sync_unpaired_hint)

        composeTestRule.onNodeWithText(unpairedHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsNotEnabled()

        // Simulate user navigating to Devices, re-pairing the Polar sensor, and returning (ON_RESUME)
        pairedState = true
        composeTestRule.runOnUiThread {
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(unpairedHint).assertDoesNotExist()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, executionCount.get())
    }

    @Test
    fun rePairingViaDeviceDataRepositoryAllDevices_reactivelyReEnablesSyncNow() {
        // Verifies REQ-SET-125 / TST-SET-127
        DeviceDataRepository.resetForTesting(null)
        DevicesDatabaseManager.resetForTesting()
        val db = DevicesDatabaseManager.getInstance(appContext)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "A0:9E:1A:82:56:99",
            "Polar Sense 24825632",
            "Polar Electro Oy",
            85,
            false
        )
        val repo = DeviceDataRepository.getInstance(appContext)
        runBlocking { repo.loadAllDevices() }

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                PolarPendingRetrySyncSection(
                    workoutId = 2200L,
                    polarExerciseId = "verity:/U/0/HR.BPB",
                    polarDeviceId = "24825632",
                    onDismiss = {}
                )
            }
        }

        val syncNowLabel = appContext.getString(R.string.polar_h10_retry_sync_action)
        val unpairedHint = appContext.getString(R.string.polar_verity_retry_sync_unpaired_hint)

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText(unpairedHint).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(syncNowLabel).assertIsNotEnabled()

        // Re-pair in DB and emit via DeviceDataRepository.allDevices
        val values = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, 1)
        }
        db.database.update(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            values,
            "${DevicesDatabaseManager.DevicesDbHelper.C_ID} = ?",
            arrayOf(deviceId.toString())
        )
        runBlocking { repo.loadAllDevices() }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText(unpairedHint).assertDoesNotExist()
                composeTestRule.onNodeWithText(syncNowLabel).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun captureLightDarkUnpairedAndPairedBannerPng_rule85() {
        // Verifies REQ-SET-125 / TST-SET-127
        var darkTheme by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = darkTheme, setWindowColors = false) {
                Surface(modifier = Modifier.fillMaxWidth().testTag("polar_retry_banner_capture")) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PolarH10RetrySyncBanner(
                            onSyncNow = {},
                            onDismiss = {},
                            isVeritySense = true,
                            isSensorPaired = false
                        )
                        PolarH10RetrySyncBanner(
                            onSyncNow = {},
                            onDismiss = {},
                            isVeritySense = false,
                            isSensorPaired = false
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val lightBmp = composeTestRule.onNodeWithTag("polar_retry_banner_capture")
            .captureToImage().asAndroidBitmap()

        darkTheme = true
        composeTestRule.waitForIdle()
        val darkBmp = composeTestRule.onNodeWithTag("polar_retry_banner_capture")
            .captureToImage().asAndroidBitmap()

        val gapPx = 24
        val width = max(lightBmp.width, darkBmp.width)
        val height = lightBmp.height + gapPx + darkBmp.height
        val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(android.graphics.Color.GRAY)
        canvas.drawBitmap(lightBmp, (width - lightBmp.width) / 2f, 0f, null)
        canvas.drawBitmap(darkBmp, (width - darkBmp.width) / 2f, (lightBmp.height + gapPx).toFloat(), null)

        val outFile = File("/tmp/att9509_verify/ATT-9509_unpaired_retry_banner_light_dark.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            combined.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
