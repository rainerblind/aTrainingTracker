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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRetrySyncCoordinator
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Behavioral Compose UI tests for Polar H10 / Polar Verity Sense pending retry sync banner
 * mounted inside [EditWorkoutScreen] (`REQ-SET-127`, `TST-SET-129`, `REQ-PRO-132`, `ATT-9174`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditWorkoutPolarRetrySyncUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockEquipmentRepository: EquipmentRepository
    private lateinit var mockSportTypesRepository: SportTypesRepository
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine

    private lateinit var initialWorkoutLoadedLiveData: MutableLiveData<WorkoutData>
    private lateinit var allWorkoutsFlow: MutableStateFlow<List<WorkoutData>>

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        PolarRetrySyncCoordinator.resetForTesting()
        PolarRetrySyncCoordinator.dispatcher = Dispatchers.Unconfined
        DeviceDataRepository.resetForTesting(null)
        DevicesDatabaseManager.resetForTesting()

        initialWorkoutLoadedLiveData = MutableLiveData()
        allWorkoutsFlow = MutableStateFlow(emptyList())

        mockWorkoutRepository = mockk(relaxed = true)
        mockEquipmentRepository = mockk(relaxed = true)
        mockSportTypesRepository = mockk(relaxed = true)
        mockDiscoveryManager = mockk(relaxed = true)
        mockClusterEngine = mockk(relaxed = true)

        every { mockWorkoutRepository.initialWorkoutLoaded } returns initialWorkoutLoadedLiveData
        every { mockWorkoutRepository.allWorkouts } returns allWorkoutsFlow
        every { mockEquipmentRepository.equipmentList } returns emptyList()
        every { mockSportTypesRepository.sportTypesList } returns listOf(
            SimpleSportTypeInfo(1L, "Running", BSportType.RUN)
        )
        every { mockSportTypesRepository.getBSportType(1L) } returns BSportType.RUN
        every { mockSportTypesRepository.getStravaName(1L) } returns "Run"
        every { mockDiscoveryManager.getSpeedBasedSportTypeNames(any(), any()) } returns setOf("Running")
        every { mockDiscoveryManager.getEquipmentNamesForSport(any()) } returns emptySet()
        every { mockDiscoveryManager.getLinkedSportTypeIds(any<Long>()) } returns emptySet()
        every { mockDiscoveryManager.getCandidateBSportTypes(any(), any()) } returns setOf(BSportType.RUN)

        WorkoutRepository.resetForTesting(mockWorkoutRepository)
        EquipmentRepository.resetForTesting(mockEquipmentRepository)
        SportTypesRepository.resetForTesting(mockSportTypesRepository)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscoveryManager)
        WorkoutClusterEngine.resetForTesting(mockClusterEngine)
    }

    @After
    fun tearDown() {
        PolarRetrySyncCoordinator.resetForTesting()
        WorkoutRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        DeviceDataRepository.resetForTesting(null)
        DevicesDatabaseManager.resetForTesting()
        unmockkAll()
    }

    private fun seedPolarSensor(name: String, mac: String, paired: Boolean) {
        val db = DevicesDatabaseManager.getInstance(application)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            mac,
            name,
            "Polar Electro Oy",
            88,
            false
        )
        val values = ContentValues().apply {
            put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, if (paired) 1 else 0)
        }
        db.database.update(
            DevicesDatabaseManager.DevicesDbHelper.DEVICES,
            values,
            "${DevicesDatabaseManager.DevicesDbHelper.C_ID} = ?",
            arrayOf(deviceId.toString())
        )
        runBlocking { DeviceDataRepository.getInstance(application).loadAllDevices() }
    }

    private fun createWorkout(
        id: Long = 2417L,
        workoutName: String = "Morning Run",
        polarSyncPending: Boolean = false,
        polarExerciseId: String? = null,
        polarDeviceId: String? = null,
        totalDistance: Double = 5200.0
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "2026-11-21_run_$id",
            workoutName = workoutName,
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1763700000L,
            formattedDate = "21.11.2026",
            formattedTime = "08:30",
            localDateTime = LocalDateTime.of(2026, 11, 21, 8, 30),
            equipmentName = null,
            equipmentId = -1L,
            commute = false,
            trainer = false,
            race = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 1,
            totalDistance = totalDistance,
            maxDisplacement = 2100.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1820L,
            avgSpeedMps = 2.89,
            ascentMeters = 45L,
            descentMeters = 45L,
            minAltitude = 210.0,
            maxAltitude = 255.0,
            description = "",
            goal = "",
            method = "",
            stravaSportName = "Run",
            polarSyncPending = polarSyncPending,
            polarExerciseId = polarExerciseId,
            polarDeviceId = polarDeviceId
        )
    }

    private fun createViewModel(initialWorkout: WorkoutData): EditWorkoutViewModel {
        coEvery { mockWorkoutRepository.loadWorkout(initialWorkout.id) } coAnswers {
            initialWorkoutLoadedLiveData.postValue(initialWorkout)
        }
        return EditWorkoutViewModel(application, initialWorkout.id)
    }

    @Test
    fun editWorkout_whenPolarSyncPendingFalse_hidesBanner() {
        val workout = createWorkout(id = 2418L, polarSyncPending = false)
        val viewModel = createViewModel(workout)

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                EditWorkoutScreen(viewModel = viewModel, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertDoesNotExist()
        composeTestRule.onNodeWithText(application.getString(R.string.polar_verity_retry_sync_title))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(application.getString(R.string.polar_h10_retry_sync_title))
            .assertDoesNotExist()
    }

    @Test
    fun editWorkout_whenPolarSyncPendingTrue_rendersVerityBannerTriggersCoordinatorAndRefreshesOnCompletion() {
        seedPolarSensor(name = "Polar Sense 24825632", mac = "A0:9E:1A:82:56:32", paired = true)

        val syncGate = CompletableDeferred<Boolean>()
        val executionCount = AtomicInteger(0)
        var capturedWorkoutId: Long? = null
        var capturedExerciseId: String? = null
        PolarRetrySyncCoordinator.syncExecutorOverride = { _, wId, exId ->
            executionCount.incrementAndGet()
            capturedWorkoutId = wId
            capturedExerciseId = exId
            syncGate.await()
        }

        val initial = createWorkout(
            id = 2417L,
            workoutName = "Morning Run",
            polarSyncPending = true,
            polarExerciseId = "verity:/U/0/HR.BPB",
            polarDeviceId = "24825632",
            totalDistance = 5200.0
        )
        val viewModel = createViewModel(initial)

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                EditWorkoutScreen(viewModel = viewModel, onBack = {})
            }
        }

        val title = application.getString(R.string.polar_verity_retry_sync_title)
        val syncNowLabel = application.getString(R.string.polar_h10_retry_sync_action)
        val dismissLabel = application.getString(R.string.polar_h10_retry_sync_dismiss)
        val syncingLabel = application.getString(R.string.polar_verity_retry_syncing)

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsDisplayed().assertIsEnabled()

        // User edits name before syncing — edit must survive completion refresh
        viewModel.updateWorkoutName("Evening Tempo Run")

        // Tap Sync Now -> Coordinator starts sync in non-composition scope
        composeTestRule.onNodeWithText(syncNowLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, executionCount.get())
        assertEquals(2417L, capturedWorkoutId)
        assertEquals("verity:/U/0/HR.BPB", capturedExerciseId)
        composeTestRule.onNodeWithText(syncingLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(syncNowLabel).assertIsNotEnabled()
        composeTestRule.onNodeWithText(dismissLabel).assertIsNotEnabled()

        // Complete sync -> ViewModel refreshes workout via repository; repo emits updated stats & polarSyncPending=false
        syncGate.complete(true)
        composeTestRule.waitForIdle()

        verify(atLeast = 1) { mockWorkoutRepository.refreshWorkout(2417L) }

        allWorkoutsFlow.value = listOf(
            initial.copy(
                workoutName = "Morning Run",
                polarSyncPending = false,
                totalDistance = 5450.0,
                ascentMeters = 72L
            )
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }

        val refreshed = viewModel.workoutData.value!!
        assertFalse(refreshed.polarSyncPending)
        assertEquals(5450.0, refreshed.totalDistance, 0.001)
        assertEquals(72L, refreshed.ascentMeters)
        assertEquals("Evening Tempo Run", refreshed.workoutName)
    }

    @Test
    fun editWorkout_whenStopTimeFastFailMarksPolarPendingAfterOpen_reactivelyMountsBanner() {
        seedPolarSensor(name = "Polar Sense 24825632", mac = "A0:9E:1A:82:56:32", paired = true)

        val initial = createWorkout(
            id = 2419L,
            workoutName = "Track Session",
            polarSyncPending = false
        )
        val viewModel = createViewModel(initial)

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                EditWorkoutScreen(viewModel = viewModel, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertDoesNotExist()

        // User starts editing while stop-time post-processing fast-fails and sets polarSyncPending=true
        viewModel.updateWorkoutName("Track 8x400m")
        allWorkoutsFlow.value = listOf(
            initial.copy(
                polarSyncPending = true,
                polarExerciseId = "verity:/U/0/HR.BPB",
                polarDeviceId = "24825632"
            )
        )

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(application.getString(R.string.polar_verity_retry_sync_title))
            .assertIsDisplayed()
        assertEquals("Track 8x400m", viewModel.workoutData.value?.workoutName)
    }

    @Test
    fun editWorkout_dismissPolarBanner_hidesBannerClearsCoordinatorAndPersistsDismiss() {
        seedPolarSensor(name = "Polar Sense 24825632", mac = "A0:9E:1A:82:56:32", paired = true)

        val initial = createWorkout(
            id = 2420L,
            polarSyncPending = true,
            polarExerciseId = "verity:/U/0/HR.BPB",
            polarDeviceId = "24825632"
        )
        val viewModel = createViewModel(initial)

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                EditWorkoutScreen(viewModel = viewModel, onBack = {})
            }
        }

        val dismissLabel = application.getString(R.string.polar_h10_retry_sync_dismiss)

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText(dismissLabel).assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertDoesNotExist()
        verify(exactly = 1) { mockWorkoutRepository.dismissPolarSyncPending(2420L) }
        assertFalse(viewModel.workoutData.value?.polarSyncPending ?: true)

        // Stale repository emission with polarSyncPending=true must not resurrect dismissed banner
        allWorkoutsFlow.value = listOf(initial.copy(polarSyncPending = true))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertDoesNotExist()
    }

    @Test
    fun editWorkout_unpairedPolarSensor_showsUnpairedHintDisablesSyncNowAndKeepsDismissEnabled() {
        seedPolarSensor(name = "Polar Sense 24825632", mac = "A0:9E:1A:82:56:32", paired = false)

        val initial = createWorkout(
            id = 2421L,
            polarSyncPending = true,
            polarExerciseId = "verity:/U/0/HR.BPB",
            polarDeviceId = "24825632"
        )
        val viewModel = createViewModel(initial)

        composeTestRule.setContent {
            ATrainingTrackerTheme(setWindowColors = false) {
                EditWorkoutScreen(viewModel = viewModel, onBack = {})
            }
        }

        val syncNowLabel = application.getString(R.string.polar_h10_retry_sync_action)
        val dismissLabel = application.getString(R.string.polar_h10_retry_sync_dismiss)
        val unpairedHint = application.getString(R.string.polar_verity_retry_sync_unpaired_hint)

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText(unpairedHint).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(syncNowLabel).assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText(dismissLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun editWorkout_captureLightDarkPolarRetryBannerPng_rule85() {
        seedPolarSensor(name = "Polar Sense 24825632", mac = "A0:9E:1A:82:56:32", paired = true)

        val initial = createWorkout(
            id = 2417L,
            workoutName = "Morning Run",
            polarSyncPending = true,
            polarExerciseId = "verity:/U/0/HR.BPB",
            polarDeviceId = "24825632"
        )
        val viewModel = createViewModel(initial)
        var darkTheme by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = darkTheme, setWindowColors = false) {
                EditWorkoutScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.waitForIdle()

        val lightBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()

        darkTheme = true
        composeTestRule.waitForIdle()
        val darkBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()

        val gapPx = 24
        val width = lightBmp.width + gapPx + darkBmp.width
        val height = max(lightBmp.height, darkBmp.height)
        val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        canvas.drawBitmap(lightBmp, 0f, 0f, null)
        canvas.drawBitmap(darkBmp, (lightBmp.width + gapPx).toFloat(), 0f, null)

        val outFile = File("/tmp/att9174_verify/ATT-9174_edit_workout_polar_retry_banner_light_dark.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            combined.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
