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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit and contract tests for singleton LiveSparklineBuffer persistence and lifecycle management in
 * BANALServiceRepository (REQ-TRK-059.6, TST-TRK-059.6, ATT-6710).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositorySparklineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var repository: BANALServiceRepository

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

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        resetSingleton()
        repository = BANALServiceRepository.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        resetSingleton()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun resetSingleton() {
        try {
            val field: Field = BANALServiceRepository::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testRegisterSparklineBuffer_reusesExistingBufferAcrossViewModelRecreation() {
        val fieldId = 101L
        val buf1 = repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 300,
            sensorType = SensorType.HR,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 0.0,
            sourceDeviceName = "Garmin HRM"
        )
        buf1.addSample(145.0f)
        buf1.addSample(148.0f)

        // Simulating second ViewModel or unbind/bind recreation: same fieldId and configuration
        val buf2 = repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 300,
            sensorType = SensorType.HR,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 0.0,
            sourceDeviceName = "Garmin HRM"
        )

        assertSame("Existing buffer must be reused to preserve samples", buf1, buf2)
        assertEquals(listOf(145.0f, 148.0f), repository.getSparklineSamples(fieldId))
    }

    @Test
    fun testUpdateSparklineBuffers_appendsIncomingSamplesMatchingConfigHash() {
        val fieldId = 202L
        repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 60,
            sensorType = SensorType.POWER,
            filterType = FilterType.MOVING_AVERAGE_TIME,
            filterConstant = 3.0,
            sourceDeviceName = null
        )

        val powerData = FilteredSensorData<Double>(
            SensorType.POWER,
            250.0,
            "250",
            null,
            FilterType.MOVING_AVERAGE_TIME,
            3.0
        )
        val hrData = FilteredSensorData<Double>(
            SensorType.HR,
            160.0,
            "160",
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )

        repository.updateSparklineBuffers(listOf(powerData, hrData))

        val samples = repository.getSparklineSamples(fieldId)
        assertEquals(1, samples.size)
        assertEquals(250.0f, samples[0])
    }

    @Test
    fun testClearSparklineBuffers_andClearBreadcrumbs_resetsBuffersToEmpty() {
        val fieldId = 303L
        val buffer = repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 300,
            sensorType = SensorType.SPEED_mps,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 0.0,
            sourceDeviceName = null
        )
        buffer.addSample(8.5f)
        assertEquals(1, repository.getSparklineSamples(fieldId).size)

        repository.clearBreadcrumbs()

        assertEquals(0, repository.getSparklineSamples(fieldId).size)
    }

    @Test
    fun testRemoveSparklineBuffer_removesRegistration() {
        val fieldId = 404L
        repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 300,
            sensorType = SensorType.CADENCE,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 0.0,
            sourceDeviceName = null
        )
        repository.removeSparklineBuffer(fieldId)

        assertEquals(emptyList<Float>(), repository.getSparklineSamples(fieldId))
    }

    @Test
    fun testResetAccumulatorsBroadcast_clearsSparklineBuffers() {
        val fieldId = 505L
        val buffer = repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 300,
            sensorType = SensorType.HR,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 0.0,
            sourceDeviceName = null
        )
        buffer.addSample(150f)
        assertEquals(1, repository.getSparklineSamples(fieldId).size)

        val receiverField = BANALServiceRepository::class.java.getDeclaredField("resetAccumulatorsReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(repository) as BroadcastReceiver
        receiver.onReceive(mockContext, Intent(BANALService.RESET_ACCUMULATORS_INTENT))

        assertEquals("RESET_ACCUMULATORS_INTENT must clear sparkline buffers", 0, repository.getSparklineSamples(fieldId).size)
    }

    @Test
    fun testTrackingModeTransition_readyToTracking_clearsSparklineBuffers() {
        val fieldId = 606L
        val buffer = repository.registerSparklineBuffer(
            sensorFieldId = fieldId,
            horizonSeconds = 300,
            sensorType = SensorType.POWER,
            filterType = FilterType.INSTANTANEOUS,
            filterConstant = 0.0,
            sourceDeviceName = null
        )
        buffer.addSample(220f)
        assertEquals(1, repository.getSparklineSamples(fieldId).size)

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.wasAutoPaused() } returns false
        every { TrainingApplication.getTrackingMode() } returns TrackingMode.TRACKING

        val receiverField = BANALServiceRepository::class.java.getDeclaredField("trackingModeReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(repository) as BroadcastReceiver
        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.getBooleanExtra(any(), any()) } returns false
        receiver.onReceive(mockContext, mockIntent)

        assertEquals("Transition READY -> TRACKING must clear sparkline buffers", 0, repository.getSparklineSamples(fieldId).size)
    }
}
