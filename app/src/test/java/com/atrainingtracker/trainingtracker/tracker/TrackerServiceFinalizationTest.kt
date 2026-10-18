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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.tracker

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.util.ConcurrentModificationException
import java.util.concurrent.ExecutorService

/**
 * Unit tests verifying guaranteed workout finalization, fault isolation, zero-duration average speed
 * safety, and thread-safe accumulated sensor copies upon stop
 * (REQ-TRK-016, REQ-TRK-030, TST-TRK-008, TST-TRK-022, ATT-2972, ATT-3986).
 */
class TrackerServiceFinalizationTest {

    private open class TestableTrackerService : TrackerService() {
        var endWorkoutCalled = false
        var delegateToRealEndWorkout = false
        var throwInGetSportTypeId = false
        var throwInGuessCommuteAndTrainer = false
        var testPackageName = "com.atrainingtracker"
        var testSdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        var healthGranted = false

        override fun getBuildVersionSdkInt(): Int = testSdkInt
        override fun hasHealthPermission(): Boolean = healthGranted

        override fun endWorkout() {
            endWorkoutCalled = true
            if (delegateToRealEndWorkout) {
                super.endWorkout()
            }
        }

        override fun getSportTypeId(): Long {
            if (throwInGetSportTypeId) {
                throw RuntimeException("Simulated getSportTypeId failure")
            }
            return 1L
        }

        override fun guessCommuteAndTrainer() {
            if (throwInGuessCommuteAndTrainer) {
                throw RuntimeException("Simulated guessCommuteAndTrainer failure")
            }
            super.guessCommuteAndTrainer()
        }

        override fun getPackageName(): String = testPackageName

        override fun performSuperOnDestroy() {
            // No-op in unit tests
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            // No-op in unit tests
        }

        override fun unbindService(conn: ServiceConnection) {
            // No-op in unit tests
        }

        override fun sendBroadcast(intent: Intent?) {
            // No-op in unit tests
        }

        fun callAverageSpeedCalculateable(): Boolean = super.averageSpeedCalculateable()
        fun callGetAverageSpeed(): Double = super.getAverageSpeed()
        fun callFinalizeLiveSession() = super.finalizeLiveSession()
    }

    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        service = TestableTrackerService()

        // Mock mDbExecutor to execute tasks inline in synchronous unit tests
        val executorMock = mockk<ExecutorService>(relaxed = true)
        every { executorMock.execute(any()) } answers { firstArg<Runnable>().run() }
        every { executorMock.awaitTermination(any(), any()) } returns true
        setField("mDbExecutor", executorMock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setField(fieldName: String, value: Any?) {
        val field: Field = TrackerService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun setWorkoutId(workoutId: Long) {
        setField("mWorkoutID", workoutId)
    }

    private fun setTrackingInterrupted(interrupted: Boolean) {
        setField("mTrackingInterrupted", interrupted)
    }

    @Test
    fun testOnDestroy_WhenTrackingNotInterrupted_CallsEndWorkout() {
        setWorkoutId(1234L)
        setTrackingInterrupted(false)

        service.onDestroy()

        assertTrue("endWorkout must be called when tracking stops normally", service.endWorkoutCalled)
    }

    @Test
    fun testOnDestroy_WhenTrackingInterrupted_DoesNotCallEndWorkoutToPreserveUnfinishedWorkout() {
        setWorkoutId(1234L)
        setTrackingInterrupted(true)

        service.onDestroy()

        assertFalse("endWorkout must NOT be called if tracking was interrupted so unfinished state is preserved for resume", service.endWorkoutCalled)
    }

    @Test
    fun testDetermineForegroundServiceType_WithoutHealthPermission_OmitsHealthType() {
        service.healthGranted = false

        val fgsType = service.determineForegroundServiceType()

        val expected = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        assertEquals(expected, fgsType)
        assertFalse("Health FGS type must NOT be requested without health permission to prevent SecurityException",
            (fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH) != 0)
        assertFalse("Connected device FGS type must NOT be present (REQ-TRK-022)",
            (fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) != 0)
    }

    /**
     * TST-TRK-022.1: Verifies that when mTimeActive_s <= 0, averageSpeedCalculateable() returns false
     * and getAverageSpeed() returns 0.0 (never Double.NaN or Double.POSITIVE_INFINITY).
     */
    @Test
    fun testAverageSpeed_ZeroActiveTime_ReturnsZeroAndNeverNaNOrInfinity() {
        // Case 1: 0 s active time, 0.0 m distance (previously 0.0 / 0 = NaN)
        setField("mTimeActive_s", 0)
        setField("mDistanceTotal_m", 0.0)
        assertFalse("averageSpeedCalculateable must be false at 0s", service.callAverageSpeedCalculateable())
        val speedZeroDist = service.callGetAverageSpeed()
        assertTrue("getAverageSpeed must be finite at 0s / 0m", speedZeroDist.isFinite())
        assertEquals(0.0, speedZeroDist, 0.0)

        // Case 2: 0 s active time, 15.0 m distance (previously 15.0 / 0 = +Infinity)
        setField("mTimeActive_s", 0)
        setField("mDistanceTotal_m", 15.0)
        assertFalse("averageSpeedCalculateable must be false at 0s even with positive distance", service.callAverageSpeedCalculateable())
        val speedPosDist = service.callGetAverageSpeed()
        assertTrue("getAverageSpeed must be finite at 0s / 15m", speedPosDist.isFinite())
        assertEquals(0.0, speedPosDist, 0.0)

        // Case 3: Positive active time and positive distance -> valid speed
        setField("mTimeActive_s", 10)
        setField("mDistanceTotal_m", 50.0)
        assertTrue("averageSpeedCalculateable must be true for 10s / 50m", service.callAverageSpeedCalculateable())
        assertEquals(5.0, service.callGetAverageSpeed(), 1e-6)
    }

    /**
     * TST-TRK-022.1 & TST-TRK-022.2: Verifies that when finalizeLiveSession() runs with mTimeActive_s == 0,
     * mBanalService == null, and downstream steps (guessCommuteAndTrainer, discoveryManager) throw,
     * Step 1 persists the map polyline and elevation/distance streams first and no NaN/Infinity is emitted.
     */
    @Test
    fun testFinalizeLiveSession_ZeroActiveTimeAndDownstreamFailures_StillPersistsMapAndStreamsFirst() {
        val workoutId = 42L
        setWorkoutId(workoutId)
        setField("mTimeActive_s", 0)
        setField("mDistanceTotal_m", 0.0)
        setField("mBanalService", null)

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        setField("mWorkoutRepository", repositoryMock)

        val summariesManagerMock = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns summariesManagerMock

        val discoveryManagerMock = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns discoveryManagerMock
        val capturedSpeedSlot = slot<Double>()
        every {
            discoveryManagerMock.resolveIdentity(any<Set<Long>>(), any<BSportType>(), capture(capturedSpeedSlot))
        } throws RuntimeException("Simulated discovery failure after speed capture")

        val liveSessionMock = mockk<LiveWorkoutSession>(relaxed = true)
        val trackPoints = listOf(LatLng(48.137, 11.576), LatLng(48.138, 11.577))
        val altitudes = listOf(520.0, 525.0)
        val distances = listOf(0.0, 120.0)
        every { liveSessionMock.rawLatLngs } returns trackPoints
        every { liveSessionMock.sampledLatLngs } returns trackPoints
        every { liveSessionMock.sampledAltitudes } returns altitudes
        every { liveSessionMock.sampledDistances } returns distances
        every { liveSessionMock.startLatLng } returns trackPoints.first()
        every { liveSessionMock.lastLatLng } returns trackPoints.last()
        val speedStats = LiveWorkoutSession.RunningStats()
        every { liveSessionMock.sensorStats } returns mapOf(SensorType.SPEED_mps to speedStats)
        setField("mLiveSession", liveSessionMock)

        service.throwInGuessCommuteAndTrainer = true

        // Act: must not throw despite mBanalService == null, 0s active time, and exceptions in steps 4 & 5
        service.callFinalizeLiveSession()

        // Assert 1: Map and streams were persisted in both DB and WorkoutRepository
        verify(exactly = 1) {
            summariesManagerMock.updateMapAndStreams(
                eq(workoutId),
                match { it.isNotEmpty() },
                match { it.isNotEmpty() },
                match { it.isNotEmpty() }
            )
        }
        verify(exactly = 1) { repositoryMock.setMapPolyline(eq(workoutId), match { it.isNotEmpty() }) }
        verify(exactly = 1) { repositoryMock.setElevationStreams(eq(workoutId), match { it.isNotEmpty() }, match { it.isNotEmpty() }) }

        // Assert 2: Speed passed to resolveIdentity at 0s active time is 0.0 (finite, never NaN/Infinity)
        assertTrue(capturedSpeedSlot.isCaptured)
        assertTrue("Speed passed to resolveIdentity must be finite", capturedSpeedSlot.captured.isFinite())
        assertEquals(0.0, capturedSpeedSlot.captured, 0.0)

        // Assert 3: Non-finite empty RunningStats (min=+Infinity, max=-Infinity) are never persisted to WorkoutExtrema
        verify(exactly = 0) {
            summariesManagerMock.updateExtremaValue(
                eq(workoutId),
                eq(SensorType.SPEED_mps),
                eq(ExtremaType.MIN),
                any(),
                any()
            )
        }
    }

    /**
     * TST-TRK-022.2: Verifies that in endWorkout(), WorkoutSummaries.FINISHED = 1 is committed first in its
     * own dedicated SQLite transaction before saveAccumulatedSensorTypes() and getSportTypeId(), so even when
     * saveAccumulatedSensorTypes() throws ConcurrentModificationException and getSportTypeId() throws RuntimeException,
     * FINISHED = 1 is still committed and finalizeLiveSession() still persists map polyline and streams.
     */
    @Test
    fun testEndWorkout_WhenSaveAccumulatedSensorTypesAndSportTypeThrow_StillCommitsFinishedAndMapStreams() {
        val workoutId = 99L
        setWorkoutId(workoutId)
        setField("mTimeActive_s", 0)
        setField("mDistanceTotal_m", 0.0)

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().size() } returns 1

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<Long>()) } answers { self as Intent }

        val banalServiceMock = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        every { banalServiceMock.accumulatedSensorTypeSet } returns setOf(SensorType.HR, SensorType.SPEED_mps)
        setField("mBanalService", banalServiceMock)

        val dbMock = mockk<SQLiteDatabase>(relaxed = true)
        val updatedContentValues = mutableListOf<ContentValues>()
        every {
            dbMock.update(eq(WorkoutSummaries.TABLE), any(), eq("${WorkoutSummaries.C_ID}=$workoutId"), isNull())
        } answers {
            updatedContentValues.add(secondArg())
            1
        }

        val summariesManagerMock = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { summariesManagerMock.database } returns dbMock
        every {
            summariesManagerMock.saveAccumulatedSensorTypes(eq(workoutId), any())
        } throws ConcurrentModificationException("Simulated concurrent sensor registration during stop")

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns summariesManagerMock

        val discoveryManagerMock = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns discoveryManagerMock

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        setField("mWorkoutRepository", repositoryMock)

        val liveSessionMock = mockk<LiveWorkoutSession>(relaxed = true)
        val trackPoints = listOf(LatLng(48.10, 11.50), LatLng(48.11, 11.51))
        every { liveSessionMock.rawLatLngs } returns trackPoints
        every { liveSessionMock.sampledLatLngs } returns trackPoints
        every { liveSessionMock.sampledAltitudes } returns listOf(500.0, 505.0)
        every { liveSessionMock.sampledDistances } returns listOf(0.0, 100.0)
        every { liveSessionMock.sensorStats } returns emptyMap()
        setField("mLiveSession", liveSessionMock)

        mockkConstructor(com.atrainingtracker.trainingtracker.exporter.ExportManager::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.exporter.ExportManager>().workoutFinished(any()) } returns Unit

        mockkStatic(androidx.localbroadcastmanager.content.LocalBroadcastManager::class)
        val lbmMock = mockk<androidx.localbroadcastmanager.content.LocalBroadcastManager>(relaxed = true)
        every { androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(any()) } returns lbmMock

        service.delegateToRealEndWorkout = true
        service.throwInGetSportTypeId = true

        // Act
        service.endWorkout()

        // Assert 1: FINISHED = 1 transaction committed BEFORE saveAccumulatedSensorTypes threw
        verifyOrder {
            dbMock.beginTransaction()
            dbMock.update(eq(WorkoutSummaries.TABLE), any(), eq("${WorkoutSummaries.C_ID}=$workoutId"), isNull())
            dbMock.setTransactionSuccessful()
            dbMock.endTransaction()
            summariesManagerMock.saveAccumulatedSensorTypes(eq(workoutId), any())
        }

        // Assert 2: Map and streams were still persisted in finalizeLiveSession()
        verify(exactly = 1) {
            summariesManagerMock.updateMapAndStreams(
                eq(workoutId),
                match { it.isNotEmpty() },
                match { it.isNotEmpty() },
                match { it.isNotEmpty() }
            )
        }
        verify(exactly = 1) { repositoryMock.setMapPolyline(eq(workoutId), match { it.isNotEmpty() }) }
        verify(exactly = 1) { repositoryMock.setElevationStreams(eq(workoutId), match { it.isNotEmpty() }, match { it.isNotEmpty() }) }
    }

    /**
     * TST-TRK-022.3: Verifies that MySensorManager.getAccumulatedSensorTypeSet() returns a defensive
     * copy that does not expose internal state and does not throw ConcurrentModificationException when
     * new sensors are registered while iterating over the returned set.
     */
    @Test
    fun testMySensorManager_GetAccumulatedSensorTypeSet_ReturnsDefensiveCopyAndResistsConcurrentRegistration() {
        mockkStatic(androidx.core.content.ContextCompat::class)
        every {
            androidx.core.content.ContextCompat.registerReceiver(any(), any(), any(), any())
        } returns null
        mockkStatic(com.atrainingtracker.banalservice.database.DevicesDatabaseManager::class)
        every {
            com.atrainingtracker.banalservice.database.DevicesDatabaseManager.getInstance(any())
        } returns mockk(relaxed = true)

        val contextMock = mockk<Context>(relaxed = true)
        val sensorManager = MySensorManager(contextMock)

        val mockDevice = mockk<MyDevice>(relaxed = true)
        every { mockDevice.deviceType } returns DeviceType.HRM
        val hrSensor = MySensor<Number>(mockDevice, SensorType.HR)
        sensorManager.registerSensor(hrSensor)

        val snapshot1 = sensorManager.accumulatedSensorTypeSet
        assertTrue("Initial snapshot must contain HR", snapshot1.contains(SensorType.HR))

        // Mutating the returned snapshot must not mutate MySensorManager's internal set
        snapshot1.clear()
        val snapshot2 = sensorManager.accumulatedSensorTypeSet
        assertNotSame("Each call must return a distinct defensive copy", snapshot1, snapshot2)
        assertTrue("Internal set must still contain HR after external mutation of snapshot", snapshot2.contains(SensorType.HR))

        // Registering new sensors while iterating over snapshot2 must not throw ConcurrentModificationException
        val iteratedTypes = mutableListOf<SensorType>()
        for (sensorType in snapshot2) {
            iteratedTypes.add(sensorType)
            val powerSensor = MySensor<Number>(mockDevice, SensorType.POWER)
            sensorManager.registerSensor(powerSensor)
        }

        assertTrue("Iteration over snapshot2 must complete cleanly", iteratedTypes.contains(SensorType.HR))
        val snapshot3 = sensorManager.accumulatedSensorTypeSet
        assertTrue("Subsequent snapshot must contain newly registered POWER sensor", snapshot3.contains(SensorType.POWER))
    }

    /**
     * TST-TRK-025.1: Verifies that onDestroy() shuts down mScheduler immediately, does not block
     * the calling (main) thread while a slow database task (300 ms) is running on mDbExecutor,
     * and executes endWorkout() on mDbExecutor in strict FIFO order after the pending DB task completes
     * (REQ-TRK-033.1, REQ-TRK-033.2).
     */
    @Test
    fun testOnDestroy_NonBlockingMainThreadAndFifoFinalizationOnRealDbExecutor() {
        val realService = object : TestableTrackerService() {
            var endWorkoutThreadName: String? = null
            val executionOrder = java.util.Collections.synchronizedList(mutableListOf<String>())

            override fun endWorkout() {
                endWorkoutThreadName = Thread.currentThread().name
                executionOrder.add("endWorkout")
                super.endWorkout()
            }
        }

        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 9001L)

        val interruptedField = TrackerService::class.java.getDeclaredField("mTrackingInterrupted").apply { isAccessible = true }
        interruptedField.set(realService, false)

        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        val mockBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        banalField.set(realService, mockBanal)

        val dbExecutorField = TrackerService::class.java.getDeclaredField("mDbExecutor").apply { isAccessible = true }
        val realDbExecutor = dbExecutorField.get(realService) as ExecutorService

        val taskStartedLatch = java.util.concurrent.CountDownLatch(1)
        realDbExecutor.execute {
            taskStartedLatch.countDown()
            Thread.sleep(300)
            realService.executionOrder.add("sample-write-done")
        }
        assertTrue("Slow DB task must start before onDestroy()", taskStartedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))

        // Act: Call onDestroy() on the test (main) thread while the 300ms DB task is in flight
        val callerThreadName = Thread.currentThread().name
        val startNs = System.nanoTime()
        realService.onDestroy()
        val elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        // Assert 1: Main thread returned without waiting for the 300ms DB task
        assertTrue("onDestroy() must not block the calling thread (< 150ms), took ${elapsedMs}ms", elapsedMs < 150)

        // Assert 2: Both mScheduler and mDbExecutor are shut down immediately upon return from onDestroy()
        assertTrue("mScheduler must be shut down in onDestroy() (REQ-TRK-033.1)", realService.isSchedulerShutdownForTesting)
        assertTrue("mDbExecutor must be shut down in onDestroy() (REQ-TRK-033.2)", realService.isDbExecutorShutdownForTesting)

        // Assert 3: Await asynchronous finalization completion and verify strict FIFO ordering
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(
            "Pending sample write must complete strictly before endWorkout() on single-thread FIFO mDbExecutor",
            listOf("sample-write-done", "endWorkout"),
            realService.executionOrder
        )
        assertTrue("endWorkout must have been invoked", realService.endWorkoutCalled)
        assertFalse("endWorkout must execute on background mDbExecutor thread, not caller thread",
            callerThreadName == realService.endWorkoutThreadName)
        org.junit.Assert.assertNull("mBanalService must be nulled in finalizationTask finally block", banalField.get(realService))
    }

    /**
     * TST-TRK-025.2: Verifies that when mTrackingInterrupted is true on a real mDbExecutor,
     * onDestroy() still shuts down mScheduler and mDbExecutor, skips endWorkout(), clears mBanalService,
     * and completes finalizationFuture (REQ-TRK-033.1, REQ-TRK-033.2).
     */
    @Test
    fun testOnDestroy_RealDbExecutorWhenInterrupted_SkipsEndWorkoutAndCompletesTeardown() {
        val realService = TestableTrackerService()
        val interruptedField = TrackerService::class.java.getDeclaredField("mTrackingInterrupted").apply { isAccessible = true }
        interruptedField.set(realService, true)

        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        banalField.set(realService, mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true))

        realService.onDestroy()

        assertTrue("mScheduler must be shut down in onDestroy()", realService.isSchedulerShutdownForTesting)
        assertTrue("mDbExecutor must be shut down in onDestroy()", realService.isDbExecutorShutdownForTesting)

        realService.finalizationFuture.get(2, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse("endWorkout must NOT be called when mTrackingInterrupted is true", realService.endWorkoutCalled)
        org.junit.Assert.assertNull("mBanalService must be nulled in finally block", banalField.get(realService))
    }
}


