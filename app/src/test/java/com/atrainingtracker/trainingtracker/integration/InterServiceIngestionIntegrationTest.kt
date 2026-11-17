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

package com.atrainingtracker.trainingtracker.integration

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSession
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Software Integration Verification Test: Inter-Service Ingestion (ASPICE SWE.5,
 * INT-SWE-03, IF-SWE-03, REQ-PRO-110.3, TST-INT-011, ATT-8155).
 *
 * Verifies:
 * 1. Android Local Binder IPC interaction between TrackerService and real BANALService.BANALServiceComm.
 * 2. 1-Hz batch telemetry polling (`sampleAndWriteToDb`) persisting real SQLite samples and updating LiveWorkoutSession.
 * 3. Graceful null-safety resilience when BANALService disconnects or `sampleAndWriteToDb(null)` is invoked.
 * 4. Multi-source sensor priority arbitration via real MySensorManager and BANALServiceComm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterServiceIngestionIntegrationTest {

    private class TestableTrackerService : TrackerService() {
        var stopped = false
        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun showTrackingInterruptedNotification() {}
        override fun performStopSelf() {
            stopped = true
        }
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun getSportTypeId(): Long = 1L
        public override fun attachBaseContext(base: Context) {
            super.attachBaseContext(base)
        }
    }

    private class TestableBANALService : BANALService() {
        public override fun attachBaseContext(base: Context) {
            super.attachBaseContext(base)
        }
    }

    private class StubSensorDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceType: DeviceType
    ) : MyDevice(context, sensorManager, deviceType) {
        val hrSensor = MySensor<Int>(this, SensorType.HR)
        val powerSensor = MySensor<Int>(this, SensorType.POWER)
        val speedSensor = MySensor<Double>(this, SensorType.SPEED_mps)
        val distanceSensor = MySensor<Double>(this, SensorType.DISTANCE_m)
        val activeTimeSensor = MySensor<Int>(this, SensorType.TIME_ACTIVE)
        val totalTimeSensor = MySensor<Int>(this, SensorType.TIME_TOTAL)

        init {
            addSensor(hrSensor)
            addSensor(powerSensor)
            addSensor(speedSensor)
            addSensor(distanceSensor)
            addSensor(activeTimeSensor)
            addSensor(totalTimeSensor)
        }

        override fun addSensors() {}
        override fun newLap() {}
        fun registerForTest() = super.registerSensors()
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): List<Runnable> { shutdown = true; return emptyList() }
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
        override fun execute(command: Runnable) { command.run() }
    }

    private lateinit var context: Context
    private lateinit var service: TestableTrackerService
    private lateinit var banalService: TestableBANALService
    private lateinit var realSensorManager: MySensorManager
    private lateinit var realBanalComm: BANALServiceComm

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("WorkoutSummaries.db")
        context.deleteDatabase("WorkoutSamples.db")
        context.deleteDatabase("Laps.db")

        val appField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appField.isAccessible = true
        appField.set(null, context)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, PreferenceManager.getDefaultSharedPreferences(context))

        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setAutoPaused(false, context)
        TrainingApplication.setManualPauseLock(false, context)
        WorkoutRepository.resetForTesting(null)

        // Real BANALService + MySensorManager + real BANALServiceComm Local Binder
        realSensorManager = MySensorManager(context)
        banalService = TestableBANALService()
        banalService.attachBaseContext(context)
        BANALService.setServiceContextForTesting(context)
        BANALService.setSensorManagerForTesting(realSensorManager)
        realBanalComm = banalService.onBind(Intent(context, BANALService::class.java)) as BANALServiceComm

        service = TestableTrackerService()
        service.attachBaseContext(context)
        service.onCreate()

        val directExecutor = DirectExecutorService()
        val executorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        executorField.isAccessible = true
        executorField.set(service, directExecutor)

        val mockScheduler = mockk<java.util.concurrent.ScheduledExecutorService>(relaxed = true)
        val schedulerField: Field = TrackerService::class.java.getDeclaredField("mScheduler")
        schedulerField.isAccessible = true
        schedulerField.set(service, mockScheduler)

        val mockApp = mockk<TrainingApplication>(relaxed = true)
        val appRefField: Field = TrackerService::class.java.getDeclaredField("mTrainingApplication")
        appRefField.isAccessible = true
        appRefField.set(service, mockApp)

        val mockSportRepo = mockk<com.atrainingtracker.trainingtracker.repositories.SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportRepo.getUIName(any()) } returns "Cycling"
        val sportRepoField: Field = TrackerService::class.java.getDeclaredField("mSportTypesRepository")
        sportRepoField.isAccessible = true
        sportRepoField.set(service, mockSportRepo)

        val mockLocationsRepo = mockk<com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository>(relaxed = true)
        val locationsRepoField: Field = TrackerService::class.java.getDeclaredField("mKnownLocationsRepository")
        locationsRepoField.isAccessible = true
        locationsRepoField.set(service, mockLocationsRepo)

        val banalField: Field = TrackerService::class.java.getDeclaredField("mBanalService")
        banalField.isAccessible = true
        banalField.set(service, realBanalComm)
    }

    @After
    fun tearDown() {
        try {
            service.onDestroy()
        } catch (_: Throwable) {
        }
        BANALService.setSensorManagerForTesting(null)
        BANALService.setServiceContextForTesting(null)
        realSensorManager.shutDown()
        unmockkAll()
        WorkoutRepository.resetForTesting(null)
        context.deleteDatabase("WorkoutSummaries.db")
        context.deleteDatabase("WorkoutSamples.db")
        context.deleteDatabase("Laps.db")
    }

    /**
     * TST-INT-011.1: Verifies 1-Hz batch telemetry ingestion from real BANALServiceComm Binder
     * updates LiveWorkoutSession statistics and persists a real SQLite sample and summary row.
     */
    @Test
    fun testTrackerService_batchPolling_persistsRealSqliteAndUpdatesLiveSession() {
        val device = StubSensorDevice(context, realSensorManager, DeviceType.BIKE_POWER)
        device.registerForTest()
        device.activeTimeSensor.newValue(5)
        device.totalTimeSensor.newValue(5)
        device.distanceSensor.newValue(120.0)
        device.speedSensor.newValue(8.5)
        device.hrSensor.newValue(142)
        device.powerSensor.newValue(210)

        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)

        val sampleMethod: Method = TrackerService::class.java.getDeclaredMethod(
            "sampleAndWriteToDb",
            BANALServiceComm::class.java
        )
        sampleMethod.isAccessible = true
        sampleMethod.invoke(service, realBanalComm)

        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        val workoutId = workoutIdField.getLong(service)
        assertTrue("Workout ID must be allocated", workoutId > 0L)

        val samplesTableField = TrackerService::class.java.getDeclaredField("mSamplesTableName").apply { isAccessible = true }
        val samplesTable = samplesTableField.get(service) as String

        val liveSessionField = TrackerService::class.java.getDeclaredField("mLiveSession").apply { isAccessible = true }
        val liveSession = liveSessionField.get(service) as LiveWorkoutSession
        val hrStats = liveSession.sensorStats[SensorType.HR]
        val powerStats = liveSession.sensorStats[SensorType.POWER]
        assertNotNull("LiveWorkoutSession must record HR stats from real BANALServiceComm", hrStats)
        assertNotNull("LiveWorkoutSession must record POWER stats from real BANALServiceComm", powerStats)
        assertEquals(142.0, hrStats!!.max, 0.01)
        assertEquals(210.0, powerStats!!.max, 0.01)

        // Verify real SQLite sample row written via real getAllSensorData() / getBestSensorData()
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database!!
        samplesDb.query(samplesTable, null, null, null, null, null, null).use { cursor ->
            assertEquals("Samples table must contain 1 row after batch tick", 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(142, cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name)))
            assertEquals(210, cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.POWER.name)))
        }

        // Verify summary table reflects distance and total time
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database!!
        summariesDb.query(
            WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummaries.TIME_TOTAL_s, WorkoutSummaries.DISTANCE_TOTAL_m),
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5, cursor.getInt(0))
            assertEquals(120.0, cursor.getDouble(1), 0.01)
        }
    }

    /**
     * TST-INT-011.2: Verifies unbind null-safety when BANALService disconnects or `sampleAndWriteToDb(null)` runs.
     */
    @Test
    fun testTrackerService_serviceDisconnected_nullSafety() {
        val startIntent = Intent(context, TrackerService::class.java).apply {
            putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name)
        }
        service.onStartCommand(startIntent, 0, 1)

        val connMethod = TrackerService::class.java.getDeclaredMethod("getBanalConnectionForTesting").apply {
            isAccessible = true
        }
        val connection = connMethod.invoke(service) as ServiceConnection
        connection.onServiceDisconnected(ComponentName(context, BANALService::class.java))

        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        assertNull("mBanalService must be cleared to null on disconnect", banalField.get(service))

        val sampleMethod: Method = TrackerService::class.java.getDeclaredMethod(
            "sampleAndWriteToDb",
            BANALServiceComm::class.java
        )
        sampleMethod.isAccessible = true
        sampleMethod.invoke(service, null)

        val samplesTableField = TrackerService::class.java.getDeclaredField("mSamplesTableName").apply { isAccessible = true }
        val samplesTable = samplesTableField.get(service) as String
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database!!
        samplesDb.query(samplesTable, null, null, null, null, null, null).use { cursor ->
            assertEquals("Disconnected poll must not write corrupt sample rows", 0, cursor.count)
        }
    }

    /**
     * TST-INT-011.3: Verifies multi-source sensor priority arbitration via real MySensorManager
     * and real BANALServiceComm.getBestSensorData / getAllSensorData / getAvailableSensorTypeSet.
     */
    @Test
    fun testBanalServiceComm_realSensorPriorityArbitration() {
        // BIKE_SPEED has higher priority in MySensorManager.SPEED_PRIORITY than SPEED_AND_LOCATION_GPS
        val gpsDevice = StubSensorDevice(context, realSensorManager, DeviceType.SPEED_AND_LOCATION_GPS)
        val wheelDevice = StubSensorDevice(context, realSensorManager, DeviceType.BIKE_SPEED)

        gpsDevice.registerForTest()
        wheelDevice.registerForTest()

        gpsDevice.speedSensor.newValue(5.5)
        wheelDevice.speedSensor.newValue(9.2)
        wheelDevice.hrSensor.newValue(154)

        val bestSpeed = realBanalComm.getBestSensorData(SensorType.SPEED_mps)
        assertNotNull("Best speed sensor data must resolve via real BANALServiceComm", bestSpeed)
        assertEquals(
            "Dedicated BIKE_SPEED must take priority over GPS speed in MySensorManager",
            9.2,
            (bestSpeed.value as Number).toDouble(),
            0.01
        )

        val available = realBanalComm.availableSensorTypeSet
        assertTrue(available.contains(SensorType.SPEED_mps))
        assertTrue(available.contains(SensorType.HR))

        val allSensors = realBanalComm.allSensorData
        assertTrue("getAllSensorData must include entries from registered devices", allSensors.size >= 2)
    }
}
