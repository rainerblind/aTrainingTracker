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

package com.atrainingtracker.trainingtracker.repositories

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldConfig
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.WPrimeBatteryMath
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModel
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioral verification suite for `ATT-8126` (`REQ-UI-2800.1`, `TST-UI-2800.1`):
 * - Repository scopes run off `Dispatchers.Main` (`Default` / `IO`)
 * - Structural `FilteredSensorData` `equals`/`hashCode` (including `Double.NaN` parity, ignoring `mTimestamp`)
 * - Steady-value `StateFlow` conflation while keeping `showGraph` sparklines & `W_PRIME_BAL` freshness alive
 * - `connectionLock` unbind-before-connect leak closure & `bindService` false/throw reset
 * - `trackLock` safety in `applyRetroactiveAltitudeCorrection`
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositoryHotpathTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        BANALServiceRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        BANALServiceRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testRepositoryScopes_doNotUseDispatchersMain_tstUi2800_1() {
        val banalRepo = BANALServiceRepository.getInstance(mockContext)
        assertEquals(Dispatchers.Default, banalRepo.repositoryDispatcher)
        assertNotEquals(Dispatchers.Main, banalRepo.repositoryDispatcher)

        val mockApp = mockk<Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp
        val workoutRepo = WorkoutRepository.getInstance(mockApp)
        assertEquals(Dispatchers.Default, workoutRepo.coroutineContext[ContinuationInterceptor])

        val equipRepo = EquipmentRepository.getInstance(mockContext)
        assertEquals(Dispatchers.IO, equipRepo.coroutineContext[ContinuationInterceptor])

        val sportRepo = SportTypesRepository.getInstance(mockContext)
        assertEquals(Dispatchers.IO, sportRepo.coroutineContext[ContinuationInterceptor])
    }

    @Test
    fun testFilteredSensorData_structuralEqualsIgnoresTimestampAndHandlesNaN_tstUi2800_1() {
        val a = FilteredSensorData(SensorType.POWER, 220.0, "220", "Stages", FilterType.INSTANTANEOUS, 1.0, 42L)
        Thread.sleep(2)
        val b = FilteredSensorData(SensorType.POWER, 220.0, "220", "Stages", FilterType.INSTANTANEOUS, 1.0, 42L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val nan1 = FilteredSensorData(SensorType.SLOPE, Double.NaN, "--", null, FilterType.EXPONENTIAL_SMOOTHING, Double.NaN)
        val nan2 = FilteredSensorData(SensorType.SLOPE, Double.NaN, "--", null, FilterType.EXPONENTIAL_SMOOTHING, Double.NaN)
        assertEquals("NaN value and NaN filterConstant must conflate", nan1, nan2)
        assertEquals(nan1.hashCode(), nan2.hashCode())

        val diffValue = FilteredSensorData(SensorType.POWER, 221.0, "221", "Stages", FilterType.INSTANTANEOUS, 1.0, 42L)
        val diffDeviceId = FilteredSensorData(SensorType.POWER, 220.0, "220", "Stages", FilterType.INSTANTANEOUS, 1.0, 99L)
        assertNotEquals(a, diffValue)
        assertNotEquals(a, diffDeviceId)
    }

    @Test
    fun testPollingLoop_conflatesSteadySnapshotsWhileAdvancingSparklinesAndWPrimeHeartbeat_tstUi2800_1() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        BANALServiceRepository.setDispatcherForTesting(testDispatcher)

        val repo = BANALServiceRepository.getInstance(mockContext)
        repo.registerSparklineBuffer(
            sensorFieldId = 10L,
            horizonSeconds = 60,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceName = "Trainer",
            sourceDeviceId = 7L
        )

        val mockBinder = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { mockBinder.activityType } returns ActivityType.BIKE_POWER
        every { mockBinder.bSportType } returns BSportType.BIKE
        every { mockBinder.allFilteredSensorData } answers {
            listOf(
                FilteredSensorData(SensorType.POWER, 200.0, "200", "Trainer", FilterType.INSTANTANEOUS, 1.0, 7L),
                FilteredSensorData(SensorType.W_PRIME_BAL, 20.0, "20.0", null, FilterType.INSTANTANEOUS, 1.0, -1L)
            )
        }

        val emissions = mutableListOf<List<FilteredSensorData<*>>>()
        val collectJob = launch(testDispatcher) {
            repo.allFilteredSensorData.collect { emissions.add(it) }
        }

        val binderField = BANALServiceRepository::class.java.getDeclaredField("_serviceBinder")
        binderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (binderField.get(repo) as MutableStateFlow<BANALService.BANALServiceComm?>).value = mockBinder

        runCurrent()
        repeat(4) {
            advanceTimeBy(1000L)
            runCurrent()
        }

        // Initial emptyList() + strictly ONE non-empty emission despite 5 identical 1-Hz polls
        assertEquals(2, emissions.size)
        // Sparkline buffer and tick advanced all 5 ticks!
        assertEquals(5, repo.getSparklineSamples(10L).size)
        assertEquals(5L, repo.sparklineTick.value)
        // W'bal heartbeat advanced all 5 ticks!
        assertEquals(5L, repo.wPrimeTelemetryHeartbeat.value)

        collectJob.cancel()
        @Suppress("UNCHECKED_CAST")
        (binderField.get(repo) as MutableStateFlow<BANALService.BANALServiceComm?>).value = null
        runCurrent()
    }

    @Test
    fun testTrackingViewModel_steadySparklineProgressesAndWPrimeNeverFalseStale_tstUi2800_1() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        BANALServiceRepository.setDispatcherForTesting(testDispatcher)

        val repo = BANALServiceRepository.getInstance(mockContext)
        val mockApp = mockk<Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp
        every { mockApp.getString(any()) } returns "Label"
        every { mockApp.getColor(any()) } returns 0

        val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
        val routesRepo = mockk<RoutesRepository>(relaxed = true)
        val liveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
        val workoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { liveSegmentsRepo.liveSegments } returns MutableStateFlow(emptyList())
        every { trackingViewsRepo.getTrackingViewInfoFlow(1L) } returns flowOf(
            TrackingViewInfo(
                tabViewId = 1L,
                name = "Ride",
                showMap = false,
                showLapButton = true,
                showLiveSegments = false,
                showElevationProfile = false
            )
        )
        every { trackingViewsRepo.getSectionDividersFlow(1L) } returns flowOf(emptyList())
        coEvery { trackingViewsRepo.getActivityTypeForView(1L) } returns ActivityType.BIKE_POWER

        val powerConfig = SensorFieldConfig(
            sensorFieldId = 101L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = 7L,
            sourceDeviceName = "Trainer",
            showGraph = true,
            graphHorizonSeconds = 60
        )
        val wPrimeConfig = SensorFieldConfig(
            sensorFieldId = 102L,
            rowNr = 2,
            colNr = 1,
            viewSize = ViewSize.NORMAL,
            sensorType = SensorType.W_PRIME_BAL,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 1.0,
            sourceDeviceId = -1L,
            sourceDeviceName = null,
            showBar = true
        )
        every { trackingViewsRepo.getSensorFieldConfigsForView(1L) } returns flowOf(listOf(powerConfig, wPrimeConfig))

        val vm = TrackingViewModel(
            application = mockApp,
            viewId = 1L,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = repo,
            routesRepository = routesRepo,
            liveSegmentsRepository = liveSegmentsRepo,
            workoutRepository = workoutRepo
        )
        runCurrent()

        var emitWPrime = true
        val mockBinder = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { mockBinder.activityType } returns ActivityType.BIKE_POWER
        every { mockBinder.bSportType } returns BSportType.BIKE
        every { mockBinder.allFilteredSensorData } answers {
            buildList {
                add(FilteredSensorData(SensorType.POWER, 200.0, "200", "Trainer", FilterType.INSTANTANEOUS, 1.0, 7L))
                if (emitWPrime) {
                    add(FilteredSensorData(SensorType.W_PRIME_BAL, 20.0, "20.0", null, FilterType.INSTANTANEOUS, 1.0, -1L))
                }
            }
        }

        val binderField = BANALServiceRepository::class.java.getDeclaredField("_serviceBinder")
        binderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (binderField.get(repo) as MutableStateFlow<BANALService.BANALServiceComm?>).value = mockBinder
        runCurrent()

        // Advance well past WPrimeBatteryMath.STALE_TIMEOUT_MS with steady identical values
        val ticksBeyondTimeout = (WPrimeBatteryMath.STALE_TIMEOUT_MS / 1000L).toInt() + 3
        repeat(ticksBeyondTimeout) {
            advanceTimeBy(1000L)
            runCurrent()
        }

        val fieldsSteady = vm.uiState.value.fields
        val powerField = fieldsSteady.first { it.sensorFieldId == 101L }
        val wPrimeField = fieldsSteady.first { it.sensorFieldId == 102L }
        assertTrue(
            "Steady-value sparkline must keep advancing under StateFlow conflation (samples=${powerField.sparklineSamples.size})",
            powerField.sparklineSamples.size >= ticksBeyondTimeout
        )
        assertFalse(
            "Steady numeric W_PRIME_BAL must NOT falsely become stale while heartbeat ticks",
            wPrimeField.wPrimeBatteryState?.isStale ?: true
        )

        // Now drop W_PRIME_BAL and confirm it DOES transition to stale after STALE_TIMEOUT_MS
        emitWPrime = false
        repeat((WPrimeBatteryMath.STALE_TIMEOUT_MS / 1000L).toInt() + 2) {
            advanceTimeBy(1000L)
            runCurrent()
        }
        val wPrimeAfterDrop = vm.uiState.value.fields.first { it.sensorFieldId == 102L }
        assertTrue(
            "W_PRIME_BAL must become stale after telemetry stops",
            wPrimeAfterDrop.wPrimeBatteryState?.isStale ?: false
        )

        @Suppress("UNCHECKED_CAST")
        (binderField.get(repo) as MutableStateFlow<BANALService.BANALServiceComm?>).value = null
        runCurrent()
    }

    @Test
    fun testUnbindBeforeConnectAndBindFailure_closesServiceConnectionLeak_tstUi2800_1() {
        val connSlot = slot<ServiceConnection>()
        every { mockContext.bindService(any<Intent>(), capture(connSlot), any<Int>()) } returns true

        val repo = BANALServiceRepository.getInstance(mockContext)
        repo.bindToBANALService()
        // Unbind BEFORE onServiceConnected fires!
        repo.unbindFromBANALService()

        verify(exactly = 1) { mockContext.unbindService(connSlot.captured) }

        // Even if Android delivers a late onServiceConnected, it must be rejected cleanly
        val lateBinder = mockk<BANALService.BANALServiceComm>(relaxed = true)
        connSlot.captured.onServiceConnected(ComponentName("pkg", "cls"), lateBinder)

        val binderField = BANALServiceRepository::class.java.getDeclaredField("_serviceBinder")
        binderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        assertNull((binderField.get(repo) as MutableStateFlow<BANALService.BANALServiceComm?>).value)
        verify(exactly = 2) { mockContext.unbindService(connSlot.captured) }

        // Verify bindService returning false clears isBinding so a subsequent bind succeeds
        every { mockContext.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } returns false
        repo.bindToBANALService()
        val isBindingField = BANALServiceRepository::class.java.getDeclaredField("isBinding")
        isBindingField.isAccessible = true
        assertFalse("isBinding must be cleared when bindService returns false", isBindingField.getBoolean(repo))
    }

    @Test
    fun testApplyRetroactiveAltitudeCorrection_updatesPathPointsSafely_tstUi2800_1() {
        val repo = BANALServiceRepository.getInstance(mockContext)
        val pathField = BANALServiceRepository::class.java.getDeclaredField("_currentPathPoints")
        pathField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val pathFlow = pathField.get(repo) as MutableStateFlow<List<PathPoint>>
        pathFlow.value = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0, timeSec = 0L),
            PathPoint(distance = 100.0, latLng = LatLng(48.001, 11.0), altitude = 510.0, timeSec = 10L)
        )

        repo.applyRetroactiveAltitudeCorrection(deltaOffset = 15.0)
        assertEquals(515.0, repo.currentPathPoints.value[0].altitude!!, 0.001)
        assertEquals(525.0, repo.currentPathPoints.value[1].altitude!!, 0.001)
    }
}
