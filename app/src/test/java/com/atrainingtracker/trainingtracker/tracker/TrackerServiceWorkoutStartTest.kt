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

package com.atrainingtracker.trainingtracker.tracker

import android.app.Notification
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Unit tests verifying single-shot workout start recording in TrackerService.
 *
 * Traceability:
 * - REQ-DAT-015: Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting.
 * - TST-DAT-010.6: Normal workout start single-shot recording.
 * - TST-DAT-010.7: Resumed workout start neutrality.
 */
class TrackerServiceWorkoutStartTest {

    private class TestableTrackerService : TrackerService() {
        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun createNewWorkout(): Long = 42L
        override fun showTrackingInterruptedNotification() {}
        override fun performStopSelf() {}
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
    }

    private lateinit var mockKnownLocationsDb: KnownLocationsDatabaseManager
    private lateinit var mockBanalComm: BANALServiceComm
    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(KnownLocationsDatabaseManager::class)
        mockKnownLocationsDb = mockk(relaxed = true)
        every { KnownLocationsDatabaseManager.getInstance(any<Context>()) } returns mockKnownLocationsDb

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false

        mockBanalComm = mockk(relaxed = true)
        every { mockBanalComm.getBestSensorData(any()) } returns null
        every { mockBanalComm.allSensorData } returns emptyList()

        service = TestableTrackerService()

        // Inject direct synchronous executor for mDbExecutor to test synchronously
        val directExecutor = ExecutorServiceDirect()
        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(service, directExecutor)

        // Inject BANALComm
        val banalField: Field = TrackerService::class.java.getDeclaredField("mBanalService")
        banalField.isAccessible = true
        banalField.set(service, mockBanalComm)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class ExecutorServiceDirect : java.util.concurrent.AbstractExecutorService() {
        private var shutdown = false
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = true
        override fun execute(command: Runnable) { command.run() }
    }

    private fun setStartType(startType: TrackerService.StartType) {
        val startTypeField: Field = TrackerService::class.java.getDeclaredField("mStartType")
        startTypeField.isAccessible = true
        startTypeField.set(service, startType)

        val recordedField: Field = TrackerService::class.java.getDeclaredField("mWorkoutStartLocationRecorded")
        recordedField.isAccessible = true
        recordedField.set(service, startType != TrackerService.StartType.START_NORMAL)
    }

    private fun invokeSampleAndWriteToDb(trackerService: TrackerService) {
        val method: Method = TrackerService::class.java.getDeclaredMethod("sampleAndWriteToDb")
        method.isAccessible = true
        method.invoke(trackerService)
    }

    private fun setupGpsAndAltitudeData(lat: Double, lng: Double, alt: Double?) {
        val latData = SensorData<Number>(SensorType.LATITUDE, lat, "", "gps")
        val lonData = SensorData<Number>(SensorType.LONGITUDE, lng, "", "gps")

        every { mockBanalComm.getBestSensorData(SensorType.LATITUDE) } returns latData
        every { mockBanalComm.getBestSensorData(SensorType.LONGITUDE) } returns lonData
        if (alt != null) {
            val altData = SensorData<Number>(SensorType.ALTITUDE, alt, "", "baro")
            every { mockBanalComm.getBestSensorData(SensorType.ALTITUDE) } returns altData
        } else {
            every { mockBanalComm.getBestSensorData(SensorType.ALTITUDE) } returns null
        }
    }

    /**
     * TST-DAT-010.6: On START_NORMAL, the first valid GPS coordinate triggers recordWorkoutStart
     * exactly once, and subsequent samples do NOT trigger repeated calls.
     */
    @Test
    fun testStartNormal_recordsWorkoutStartOnceOnFirstValidGpsFix() {
        setStartType(TrackerService.StartType.START_NORMAL)

        setupGpsAndAltitudeData(48.137, 11.576, 520.0)

        // 1st sample: valid GPS acquired -> recordWorkoutStart should be invoked once
        invokeSampleAndWriteToDb(service)

        verify(exactly = 1) {
            mockKnownLocationsDb.recordWorkoutStart(
                match { it.latitude == 48.137 && it.longitude == 11.576 },
                520.0
            )
        }

        // 2nd sample: next second in workout -> should NOT record another workout start
        invokeSampleAndWriteToDb(service)

        // Still exactly 1
        verify(exactly = 1) {
            mockKnownLocationsDb.recordWorkoutStart(any(), any())
        }
    }

    /**
     * Verifies that when GPS fix is null, recordWorkoutStart is not called and the flag remains false.
     */
    @Test
    fun testStartNormal_nullGpsFix_doesNotRecordWorkoutStart() {
        setStartType(TrackerService.StartType.START_NORMAL)

        every { mockBanalComm.getBestSensorData(SensorType.LATITUDE) } returns null
        every { mockBanalComm.getBestSensorData(SensorType.LONGITUDE) } returns null
        every { mockBanalComm.getBestSensorData(SensorType.ALTITUDE) } returns null

        invokeSampleAndWriteToDb(service)

        verify(exactly = 0) {
            mockKnownLocationsDb.recordWorkoutStart(any(), any())
            mockKnownLocationsDb.recordWorkoutStart(any())
        }
    }

    /**
     * TST-DAT-010.7: On RESUME_BY_USER or RESUME_SERVICE_RECREATION,
     * recordWorkoutStart is NEVER invoked.
     */
    @Test
    fun testResumeByUser_neverRecordsWorkoutStart() {
        setStartType(TrackerService.StartType.RESUME_BY_USER)

        setupGpsAndAltitudeData(48.137, 11.576, 520.0)

        invokeSampleAndWriteToDb(service)
        invokeSampleAndWriteToDb(service)

        verify(exactly = 0) {
            mockKnownLocationsDb.recordWorkoutStart(any(), any())
            mockKnownLocationsDb.recordWorkoutStart(any())
        }
    }

    @Test
    fun testResumeServiceRecreation_neverRecordsWorkoutStart() {
        setStartType(TrackerService.StartType.RESUME_SERVICE_RECREATION)

        setupGpsAndAltitudeData(48.137, 11.576, 520.0)

        invokeSampleAndWriteToDb(service)

        verify(exactly = 0) {
            mockKnownLocationsDb.recordWorkoutStart(any(), any())
            mockKnownLocationsDb.recordWorkoutStart(any())
        }
    }
}
