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

import android.content.Context
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.devices.SimpleSensorData
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests for 1-Hz live-tracking memory and allocation optimizations
 * (REQ-TRK-042.1, TST-TRK-042.1, ATT-4863).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositoryMemoryTest {

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
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        resetSingleton()
        setTrackingMode(TrackingMode.READY)
    }

    @After
    fun tearDown() {
        resetSingleton()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
        setTrackingMode(TrackingMode.READY)
    }

    private fun resetSingleton() {
        try {
            val field: Field = BANALServiceRepository::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
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
    fun testDeviceTelemetry_valueEqualityEliminatesStateFlowChurn() {
        val sensorData = SimpleSensorData("145", SensorType.HR)
        val telemetry1 = DeviceTelemetry(
            deviceId = 42L,
            mainValue = sensorData,
            allValues = listOf(sensorData),
            batteryPercentage = 85
        )
        val telemetry2 = DeviceTelemetry(
            deviceId = 42L,
            mainValue = sensorData,
            allValues = listOf(sensorData),
            batteryPercentage = 85
        )

        // Verifies absence of nanosecond version timestamp: structural equality holds
        assertEquals(telemetry1, telemetry2)
        assertEquals(telemetry1.hashCode(), telemetry2.hashCode())

        val telemetryDifferent = telemetry1.copy(batteryPercentage = 84)
        assertNotEquals(telemetry1, telemetryDifferent)
    }

    @Test
    fun testPathPoints_stationaryTicksDoNotAppendDuplicatePoints() {
        setTrackingMode(TrackingMode.TRACKING)
        val repository = BANALServiceRepository.getInstance(mockContext)

        val mockBinder = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { mockBinder.getBestSensorData(any()) } returns null

        val latData = FilteredSensorData(SensorType.LATITUDE, 48.137, "48.137", "GPS", FilterType.INSTANTANEOUS, 0.0)
        val lonData = FilteredSensorData(SensorType.LONGITUDE, 11.575, "11.575", "GPS", FilterType.INSTANTANEOUS, 0.0)
        val distData = FilteredSensorData(SensorType.DISTANCE_m, 1250.0, "1250", "GPS", FilterType.INSTANTANEOUS, 0.0)
        val altData = FilteredSensorData(SensorType.ALTITUDE, 520.0, "520", "GPS", FilterType.INSTANTANEOUS, 0.0)

        every { mockBinder.getBestSensorData(SensorType.LATITUDE) } returns latData
        every { mockBinder.getBestSensorData(SensorType.LONGITUDE) } returns lonData
        every { mockBinder.getBestSensorData(SensorType.DISTANCE_m) } returns distData
        every { mockBinder.getBestSensorData(SensorType.ALTITUDE) } returns altData

        setServiceBinder(repository, mockBinder)
        testDispatcher.scheduler.runCurrent()

        // First tick: initial location and distance recorded, 1 point added
        assertEquals(1, repository.currentPathPoints.value.size)
        assertEquals(48.137, repository.currentPathPoints.value[0].latLng.latitude, 1e-5)

        // Advance 5 simulated seconds with stationary coordinates and constant distance
        for (i in 1..5) {
            testDispatcher.scheduler.advanceTimeBy(1000)
            testDispatcher.scheduler.runCurrent()
        }

        // Must still be exactly 1 point, NOT 6 points (prevents O(N^2) memory explosion)
        assertEquals(1, repository.currentPathPoints.value.size)

        // Now advance distance (user moves)
        val advancedDistData = FilteredSensorData(SensorType.DISTANCE_m, 1260.0, "1260", "GPS", FilterType.INSTANTANEOUS, 0.0)
        every { mockBinder.getBestSensorData(SensorType.DISTANCE_m) } returns advancedDistData
        testDispatcher.scheduler.advanceTimeBy(1000)
        testDispatcher.scheduler.runCurrent()

        // Point appended because distance advanced
        assertEquals(2, repository.currentPathPoints.value.size)
        assertEquals(1260.0, repository.currentPathPoints.value[1].distance, 1e-3)
    }

    @Test
    fun testPathPoints_doesNotAppendWhenTrackingModeIsNotTracking() {
        setTrackingMode(TrackingMode.READY)
        val repository = BANALServiceRepository.getInstance(mockContext)

        val mockBinder = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { mockBinder.getBestSensorData(any()) } returns null

        val latData = FilteredSensorData(SensorType.LATITUDE, 48.137, "48.137", "GPS", FilterType.INSTANTANEOUS, 0.0)
        val lonData = FilteredSensorData(SensorType.LONGITUDE, 11.575, "11.575", "GPS", FilterType.INSTANTANEOUS, 0.0)
        val distData = FilteredSensorData(SensorType.DISTANCE_m, 1250.0, "1250", "GPS", FilterType.INSTANTANEOUS, 0.0)

        every { mockBinder.getBestSensorData(SensorType.LATITUDE) } returns latData
        every { mockBinder.getBestSensorData(SensorType.LONGITUDE) } returns lonData
        every { mockBinder.getBestSensorData(SensorType.DISTANCE_m) } returns distData

        setServiceBinder(repository, mockBinder)
        testDispatcher.scheduler.runCurrent()

        // When mode is READY, no points should be appended
        assertEquals(0, repository.currentPathPoints.value.size)

        setTrackingMode(TrackingMode.PAUSED)
        testDispatcher.scheduler.advanceTimeBy(1000)
        testDispatcher.scheduler.runCurrent()

        // When mode is PAUSED, no points should be appended
        assertEquals(0, repository.currentPathPoints.value.size)
    }
}
