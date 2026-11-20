/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit test verifying that live track and path point accumulation in [BANALServiceRepository]
 * strictly isolates active tracking from paused periods (REQ-PRO-004, TST-UI-039).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositoryPauseIsolationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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

        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val out = arg<FloatArray>(4)
            out[0] = 50.0f
        }

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        BANALServiceRepository.resetForTesting(null)
        setTrackingMode(TrackingMode.TRACKING)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        BANALServiceRepository.resetForTesting(null)
        setTrackingMode(TrackingMode.READY)
        unmockkAll()
    }

    private fun setTrackingMode(mode: TrackingMode) {
        val field: Field = TrainingApplication::class.java.getDeclaredField("cTrackingMode")
        field.isAccessible = true
        field.set(null, mode)
    }

    private fun setServiceBinder(repository: BANALServiceRepository, binder: BANALService.BANALServiceComm?) {
        val binderField = BANALServiceRepository::class.java.getDeclaredField("_serviceBinder")
        binderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = binderField.get(repository) as MutableStateFlow<BANALService.BANALServiceComm?>
        flow.value = binder
    }

    @Test
    fun trackAccumulation_ignoresLocationUpdatesWhilePaused_TST_UI_039() = runTest(testDispatcher) {
        // TST-UI-039
        var currentLat = 48.1000
        var currentDist = 0.0

        val mockBinder = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { mockBinder.getBestSensorData(any()) } returns null
        every { mockBinder.getBestSensorData(SensorType.LATITUDE) } answers {
            FilteredSensorData(SensorType.LATITUDE, currentLat, "$currentLat", "GPS", FilterType.INSTANTANEOUS, 0.0)
        }
        every { mockBinder.getBestSensorData(SensorType.LONGITUDE) } returns
            FilteredSensorData(SensorType.LONGITUDE, 11.5000, "11.5000", "GPS", FilterType.INSTANTANEOUS, 0.0)
        every { mockBinder.getBestSensorData(SensorType.DISTANCE_m) } answers {
            FilteredSensorData(SensorType.DISTANCE_m, currentDist, "$currentDist", "GPS", FilterType.INSTANTANEOUS, 0.0)
        }
        every { mockBinder.getBestSensorData(SensorType.SPEED_mps) } returns
            FilteredSensorData(SensorType.SPEED_mps, 3.0, "3.0", "GPS", FilterType.INSTANTANEOUS, 0.0)
        every { mockBinder.getBestSensorData(SensorType.ALTITUDE) } returns
            FilteredSensorData(SensorType.ALTITUDE, 510.0, "510.0", "GPS", FilterType.INSTANTANEOUS, 0.0)

        val repo = BANALServiceRepository.getInstance(mockContext)
        setServiceBinder(repo, mockBinder)

        // Active sample 1 recorded during TRACKING
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, repo.currentTrack.value.size)
        assertEquals(1, repo.currentPathPoints.value.size)

        // Transition to PAUSED and move 50m
        setTrackingMode(TrackingMode.PAUSED)
        currentLat = 48.1005
        currentDist = 50.0
        advanceTimeBy(1100)
        runCurrent()

        assertEquals("Paused movement must not append track coordinates", 1, repo.currentTrack.value.size)
        assertEquals("Paused movement must not append elevation profile points", 1, repo.currentPathPoints.value.size)

        val scopeField = BANALServiceRepository::class.java.getDeclaredField("repositoryScope")
        scopeField.isAccessible = true
        (scopeField.get(repo) as? CoroutineScope)?.coroutineContext?.get(Job)?.cancel()
    }
}
