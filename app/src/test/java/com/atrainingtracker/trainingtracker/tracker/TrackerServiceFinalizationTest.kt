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

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
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
import org.junit.Assert.assertSame
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

        var performStopForegroundCalled = false
        var performStopSelfCalled = false
        var unbindServiceCalled = false
        var banalServicePresentDuringEndWorkout: Boolean? = null
        var endWorkoutLatchHook: (() -> Unit)? = null
        var onEndWorkoutAction: (() -> Unit)? = null
        var onUnbindServiceAction: (() -> Unit)? = null

        var capturedSnapshot: TrackerService.BanalFinalizationSnapshot? = null
        var capturedFinalizationWorkoutId: Long? = null
        var capturedFinalizationBaseFileName: String? = null
        var capturedFinalizationLiveSession: LiveWorkoutSession? = null
        var mockCreatedWorkoutId: Long? = null
        var savedLapNr: Long? = null
        var savedLapTime: Int? = null
        var savedLapDistance: Double? = null
        var savedLapSpeed: Double? = null

        override fun getBuildVersionSdkInt(): Int = testSdkInt
        override fun hasHealthPermission(): Boolean = healthGranted

        private var inEndWorkoutWithArgs = false

        override fun endWorkout() {
            endWorkoutCalled = true
            banalServicePresentDuringEndWorkout = (mBanalService != null)
            onEndWorkoutAction?.invoke()
            endWorkoutLatchHook?.invoke()
            if (delegateToRealEndWorkout && !inEndWorkoutWithArgs) {
                super.endWorkout()
            }
        }

        override fun endWorkout(
            snapshot: TrackerService.BanalFinalizationSnapshot?,
            workoutId: Long,
            baseFileName: String?,
            liveSession: LiveWorkoutSession?
        ) {
            capturedSnapshot = snapshot
            capturedFinalizationWorkoutId = workoutId
            capturedFinalizationBaseFileName = baseFileName
            capturedFinalizationLiveSession = liveSession
            inEndWorkoutWithArgs = true
            try {
                endWorkout()
            } finally {
                inEndWorkoutWithArgs = false
            }
            if (delegateToRealEndWorkout) {
                super.endWorkout(snapshot, workoutId, baseFileName, liveSession)
            }
        }

        val finalizationSnapshotForTesting: TrackerService.BanalFinalizationSnapshot?
            get() = capturedSnapshot

        override fun createNewWorkout(): Long {
            return mockCreatedWorkoutId ?: super.createNewWorkout()
        }

        override fun performStartForeground(id: Int, notification: android.app.Notification?, foregroundServiceType: Int) {
            // No-op in unit tests
        }

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? = null

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = true

        override fun registerTransientReceivers() {
            // No-op in unit tests
        }

        override fun unregisterTransientReceivers() {
            // No-op in unit tests
        }

        override fun onSearchingFinished() {
            // No-op in unit tests
        }

        override fun saveLap(lapNr: Long, lapTime: Int, lapDistance: Double, averageSpeed: Double) {
            savedLapNr = lapNr
            savedLapTime = lapTime
            savedLapDistance = lapDistance
            savedLapSpeed = averageSpeed
        }

        override fun saveLap(workoutId: Long, lapNr: Long, lapTime: Int, lapDistance: Double, averageSpeed: Double) {
            saveLap(lapNr, lapTime, lapDistance, averageSpeed)
        }

        override fun performStopForeground() {
            performStopForegroundCalled = true
        }

        override fun performStopSelf() {
            performStopSelfCalled = true
        }

        override fun runOnMainThread(action: Runnable) {
            action.run()
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
            unbindServiceCalled = true
            onUnbindServiceAction?.invoke()
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

        // Isolate plain-JVM tests from launching real async Dispatchers.IO post-processing coroutines (Rule 66)
        WorkoutPostProcessingCoordinator.resetForTesting()
        WorkoutPostProcessingCoordinator.enqueueHookForTesting = { _, _ -> true }
        TrackerService.resetFgsStartStateForTesting()

        service = TestableTrackerService()

        // Mock mDbExecutor to execute tasks inline in synchronous unit tests
        val executorMock = mockk<ExecutorService>(relaxed = true)
        every { executorMock.execute(any()) } answers { firstArg<Runnable>().run() }
        every { executorMock.awaitTermination(any(), any()) } returns true
        setField("mDbExecutor", executorMock)
    }

    @After
    fun tearDown() {
        TrackerService.resetFgsStartStateForTesting()
        WorkoutPostProcessingCoordinator.resetForTesting()
        com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.resetForTesting(null)
        com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(null)
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

        // Assert 1: Map and streams were persisted via WorkoutRepository
        verify(exactly = 1) {
            repositoryMock.updateMapAndStreams(
                eq(workoutId),
                match { it.isNotEmpty() },
                match { it.isNotEmpty() },
                match { it.isNotEmpty() }
            )
        }

        // Assert 2: Speed passed to resolveIdentity at 0s active time is 0.0 (finite, never NaN/Infinity)
        assertTrue(capturedSpeedSlot.isCaptured)
        assertTrue("Speed passed to resolveIdentity must be finite", capturedSpeedSlot.captured.isFinite())
        assertEquals(0.0, capturedSpeedSlot.captured, 0.0)

        // Assert 3: Non-finite empty RunningStats (min=+Infinity, max=-Infinity) are never persisted to WorkoutExtrema
        verify(exactly = 0) {
            repositoryMock.updateExtremaValue(
                eq(workoutId),
                eq(SensorType.SPEED_mps),
                eq(ExtremaType.MIN),
                any(),
                any()
            )
        }
    }

    /**
     * TST-TRK-072.10d: Verifies that in finalizeLiveSession(), if the workout is a guided HRV test
     * (method == "GUIDED_STEP" or "GUIDED_RAMP"), cluster and location auto-naming do NOT overwrite
     * the localized protocol title via repository.updateWorkoutName(), while cluster ID and hardware identity
     * are still assigned.
     */
    @Test
    fun testFinalizeLiveSession_GuidedHrvTest_PreservesProtocolNameAndSuppressesClusterOverwrite_TST_TRK_072_10d() {
        val workoutId = 7745L
        setWorkoutId(workoutId)
        setField("mTimeActive_s", 1800)
        setField("mDistanceTotal_m", 15000.0)
        setField("mBanalService", null)

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        every { repositoryMock.getWorkoutMethod(workoutId) } returns "GUIDED_STEP"
        every { repositoryMock.getExtremaPosition(any(), any(), any()) } returns LatLng(48.139, 11.578)
        setField("mWorkoutRepository", repositoryMock)

        val summariesManagerMock = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns summariesManagerMock

        val discoveryManagerMock = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns discoveryManagerMock
        val identity = EquipmentAndSportTypeDiscoveryManager.InferredIdentity(
            sportId = 1L,
            bSportType = BSportType.BIKE,
            equipmentId = 0L,
            stravaSportName = null,
            uploadToStrava = 1,
            isHighConfidence = true
        )
        every { discoveryManagerMock.resolveIdentity(any<Set<Long>>(), any<BSportType>(), any<Double>()) } returns identity

        val liveSessionMock = mockk<LiveWorkoutSession>(relaxed = true)
        val trackPoints = listOf(LatLng(48.137, 11.576), LatLng(48.138, 11.577))
        every { liveSessionMock.rawLatLngs } returns trackPoints
        every { liveSessionMock.sampledLatLngs } returns trackPoints
        every { liveSessionMock.startLatLng } returns trackPoints.first()
        every { liveSessionMock.lastLatLng } returns trackPoints.last()
        setField("mLiveSession", liveSessionMock)

        val engineMock = mockk<com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine>(relaxed = true)
        val clusterSuggestion = WorkoutCluster(
            id = 55L,
            name = "Hausrunde",
            probableSportId = 1L,
            startLat = 48.137,
            startLng = 11.576,
            endLat = 48.138,
            endLng = 11.577,
            maxDispLat = 48.139,
            maxDispLng = 11.578,
            refDistance = 1000.0,
            hitCount = 4,
            hasCounter = true
        )
        com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.resetForTesting(engineMock)
        mockkObject(com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.Companion)
        every { com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.getInstance(any()) } returns engineMock
        every {
            engineMock.suggestCluster(
                start = any(),
                end = any(),
                apex = any(),
                distance = any(),
                workoutName = or(isNull(), any()),
                candidateSportTypes = any(),
                minAltPos = or(isNull(), any()),
                maxAltPos = or(isNull(), any()),
                cachedClusters = or(isNull(), any()),
                scoreThreshold = any(),
                workoutPolyline = or(isNull(), any())
            )
        } returns clusterSuggestion

        service.throwInGuessCommuteAndTrainer = true

        // Act
        service.callFinalizeLiveSession()

        // Assert: updateWorkoutName was NEVER called because isGuidedHrvTest is true
        verify(exactly = 0) {
            repositoryMock.updateWorkoutName(eq(workoutId), any())
        }
        // Cluster ID was still associated
        verify(exactly = 1) {
            repositoryMock.updateWorkoutClusterId(eq(workoutId), eq(55L))
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

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        every {
            repositoryMock.saveAccumulatedSensorTypes(eq(workoutId), any())
        } throws ConcurrentModificationException("Simulated concurrent sensor registration during stop")
        setField("mWorkoutRepository", repositoryMock)

        val summariesManagerMock = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { summariesManagerMock.database } returns dbMock
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns summariesManagerMock

        val discoveryManagerMock = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns discoveryManagerMock

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
            repositoryMock.markWorkoutFinishedBlocking(workoutId)
            repositoryMock.saveAccumulatedSensorTypes(eq(workoutId), any())
        }

        // Assert 2: Map and streams were still persisted in finalizeLiveSession()
        verify(exactly = 1) {
            repositoryMock.updateMapAndStreams(
                eq(workoutId),
                match { it.isNotEmpty() },
                match { it.isNotEmpty() },
                match { it.isNotEmpty() }
            )
        }
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
            Thread.sleep(600)
            realService.executionOrder.add("sample-write-done")
        }
        assertTrue("Slow DB task must start before onDestroy()", taskStartedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))

        // Act: Call onDestroy() on the test (main) thread while the 300ms DB task is in flight
        val callerThreadName = Thread.currentThread().name
        val startNs = System.nanoTime()
        realService.onDestroy()
        val elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        // Assert 1: Main thread returned without waiting for the DB task
        assertTrue("onDestroy() must not block the calling thread (< 500ms), took ${elapsedMs}ms", elapsedMs < 500)

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

    private fun createStopIntent(): Intent {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns TrackerService.ACTION_STOP
        return intent
    }

    /**
     * TST-TRK-026.1: Verifies that when ACTION_STOP is received, performStopForeground() and
     * performStopSelf() are NOT called while endWorkout() is executing on mDbExecutor, and are
     * called on the main thread after the finalization task finishes (REQ-TRK-034.2, REQ-TRK-034.3).
     */
    @Test
    fun testActionStop_MaintainsForegroundStatusDuringEndWorkoutAndStopsSelfAfterCompletion() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 1001L)
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        baseFileNameField.set(realService, "2026-10-18_120000")

        val endWorkoutStartedLatch = java.util.concurrent.CountDownLatch(1)
        val endWorkoutBlockLatch = java.util.concurrent.CountDownLatch(1)

        realService.endWorkoutLatchHook = {
            endWorkoutStartedLatch.countDown()
            endWorkoutBlockLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }

        val stopIntent = createStopIntent()
        val result = realService.onStartCommand(stopIntent, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)

        // Wait until endWorkout() is currently executing on mDbExecutor
        assertTrue("endWorkout must start executing", endWorkoutStartedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))

        // Assert: While endWorkout() is running, stopForeground and stopSelf must NOT have been called
        assertFalse("stopForeground must NOT be called while endWorkout is running", realService.performStopForegroundCalled)
        assertFalse("stopSelf must NOT be called while endWorkout is running", realService.performStopSelfCalled)
        assertTrue("isStopping must be true", realService.isStoppingForTesting)

        // Release endWorkout to complete
        endWorkoutBlockLatch.countDown()

        // Wait for finalizationFuture
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)

        // Assert: After completion, stopForeground and stopSelf were invoked
        assertTrue("endWorkout must be called", realService.endWorkoutCalled)
        assertTrue("performStopForeground must be called after finalization completes", realService.performStopForegroundCalled)
        assertTrue("performStopSelf must be called after finalization completes", realService.performStopSelfCalled)
    }

    /**
     * TST-TRK-026.2: Verifies that mBanalService remains non-null during endWorkout() execution,
     * and safeUnbindBanalService() is called only after endWorkout() finishes (REQ-TRK-034.3).
     */
    @Test
    fun testActionStop_KeepsBanalServiceBoundUntilEndWorkoutCompletes() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 2002L)
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        baseFileNameField.set(realService, "2026-10-18_120000")

        val mockBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        banalField.set(realService, mockBanal)
        realService.setBanalBoundForTesting(true)

        val unbindOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
        realService.onEndWorkoutAction = { unbindOrder.add("endWorkout") }
        realService.onUnbindServiceAction = { unbindOrder.add("unbindService") }

        val stopIntent = createStopIntent()
        realService.onStartCommand(stopIntent, 0, 1)

        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)

        assertTrue("mBanalService must be non-null during endWorkout", realService.banalServicePresentDuringEndWorkout == true)
        assertEquals("endWorkout must complete before unbindService", listOf("endWorkout", "unbindService"), unbindOrder)
        assertTrue("unbindService must have been called", realService.unbindServiceCalled)
        org.junit.Assert.assertNull("mBanalService must be cleared to null after completion", banalField.get(realService))
    }

    /**
     * TST-TRK-026.3: Verifies that delivering a duplicate ACTION_STOP while stopping is already in progress
     * safely returns START_NOT_STICKY and does not submit duplicate finalization tasks (REQ-TRK-034.2).
     */
    @Test
    fun testActionStop_WhenDuplicateStopReceived_IgnoresSecondStopSafely() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 3003L)
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        baseFileNameField.set(realService, "2026-10-18_120000")

        val endWorkoutStartedLatch = java.util.concurrent.CountDownLatch(1)
        val endWorkoutBlockLatch = java.util.concurrent.CountDownLatch(1)
        var endWorkoutCount = 0

        realService.endWorkoutLatchHook = {
            endWorkoutCount++
            endWorkoutStartedLatch.countDown()
            endWorkoutBlockLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }

        // First stop
        val res1 = realService.onStartCommand(createStopIntent(), 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, res1)
        assertTrue(endWorkoutStartedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))

        // Second duplicate stop while still running
        val res2 = realService.onStartCommand(createStopIntent(), 0, 2)
        assertEquals(android.app.Service.START_NOT_STICKY, res2)

        endWorkoutBlockLatch.countDown()
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals("endWorkout must only be executed once", 1, endWorkoutCount)
    }

    /**
     * TST-TRK-026.3: Verifies that if ACTION_STOP is received when not tracking, the service
     * calls performStopSelf() immediately and does not run endWorkout().
     */
    @Test
    fun testActionStop_WhenNotTracking_CallsStopSelfImmediately() {
        val realService = TestableTrackerService()

        val res = realService.onStartCommand(createStopIntent(), 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, res)
        assertTrue("performStopSelf must be called", realService.performStopSelfCalled)
        assertFalse("endWorkout must NOT be called", realService.endWorkoutCalled)
    }

    /**
     * TST-TRK-026.4: Verifies TrackerService.isRunning() and setInstanceForTesting lifecycle (REQ-TRK-034.1).
     */
    @Test
    fun testTrackerService_IsRunningLifecycle() {
        TrackerService.setInstanceForTesting(null)
        assertFalse("isRunning must be false when instance is null", TrackerService.isRunning())

        val mockService = TestableTrackerService()
        TrackerService.setInstanceForTesting(mockService)
        assertTrue("isRunning must be true when instance is set", TrackerService.isRunning())
        TrackerService.setInstanceForTesting(null)
        assertFalse("isRunning must be false after reset", TrackerService.isRunning())
    }

    /**
     * TST-TRK-027.1: Verifies that BanalFinalizationSnapshot captures all telemetry fields
     * defensively and immutably from BANALServiceComm (REQ-TRK-035.1).
     */
    @Test
    fun testBanalFinalizationSnapshot_capturesAllTelemetryCorrectly() {
        val mockBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        val mockLapNr = mockk<com.atrainingtracker.banalservice.sensor.SensorData<Int>>()
        every { mockLapNr.value } returns 3
        every { mockBanal.getBestSensorData(SensorType.LAP_NR) } returns mockLapNr

        val mockLapTime = mockk<com.atrainingtracker.banalservice.sensor.SensorData<Int>>()
        every { mockLapTime.value } returns 120
        every { mockBanal.getBestSensorData(SensorType.TIME_LAP) } returns mockLapTime

        val mockLapDist = mockk<com.atrainingtracker.banalservice.sensor.SensorData<Double>>()
        every { mockLapDist.value } returns 850.5
        every { mockBanal.getBestSensorData(SensorType.DISTANCE_m_LAP) } returns mockLapDist

        every { mockBanal.databaseIdsOfActiveRemoteDevices } returns listOf(101L, 102L)
        every { mockBanal.accumulatedSensorTypeSet } returns setOf(SensorType.HR, SensorType.SPEED_mps, SensorType.CADENCE)
        every { mockBanal.accumulatedGCDataString } returns "HR=150,SPD=25"
        every { mockBanal.bSportType } returns BSportType.BIKE
        every { mockBanal.userSelectedBSportType } returns BSportType.BIKE

        val mockContext = mockk<Context>(relaxed = true)

        val snapshot = TrackerService.BanalFinalizationSnapshot.capture(
            mockBanal,
            mockContext,
            7.5,
            true,
            5000.0,
            667
        )

        assertEquals(3, snapshot.lapNr)
        assertEquals(120, snapshot.lapTime_s)
        assertEquals(850.5, snapshot.lapDistance_m, 1e-6)
        assertEquals(setOf(101L, 102L), snapshot.activeRemoteDeviceIds)
        assertEquals(setOf(SensorType.HR, SensorType.SPEED_mps, SensorType.CADENCE), snapshot.accumulatedSensorTypes)
        assertEquals("HR=150,SPD=25", snapshot.accumulatedGCDataString)
        assertEquals(BSportType.BIKE, snapshot.bSportType)
        assertEquals(BSportType.BIKE, snapshot.userSelectedSportType)
        assertEquals(7.5, snapshot.avgSpeed, 1e-6)
        assertTrue(snapshot.avgSpeedCalculable)
        assertEquals(5000.0, snapshot.totalDistance_m, 1e-6)
        assertEquals(667, snapshot.timeActive_s)
    }

    /**
     * TST-TRK-027.2: Verifies that in-flight ticks and sample writes are aborted when mIsStopping is true (REQ-TRK-035.2).
     */
    @Test
    fun testInFlightTick_whenIsStoppingTrue_abortsWithoutSampleWrite() {
        val mockBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        setField("mBanalService", mockBanal)
        setField("mIsStopping", true)

        // Run tracker tick
        val trackerField = TrackerService::class.java.getDeclaredField("tracker").apply { isAccessible = true }
        val trackerRunnable = trackerField.get(service) as Runnable
        trackerRunnable.run()

        // Call sampleAndWriteToDb directly as well
        service.sampleAndWriteToDb(mockBanal)

        // Verify no sensor data was queried
        verify(exactly = 0) { mockBanal.getBestSensorData(any()) }
        verify(exactly = 0) { mockBanal.databaseIdsOfActiveRemoteDevices }
    }

    /**
     * TST-TRK-027.3: Verifies that endWorkout(snapshot) successfully persists the final lap,
     * active devices, and accumulated sensors even if BANALService is already destroyed/null (REQ-TRK-035.3).
     */
    @Test
    fun testEndWorkout_withSnapshot_persistsFinalLapAndAccumulatedSensorsEvenIfBanalServiceDestroyed() {
        service.delegateToRealEndWorkout = true
        val workoutId = 999L
        setWorkoutId(workoutId)
        setField("mBaseFileName", "test_file_name")

        // Crucial: mBanalService is null to simulate BANALService already destroyed
        setField("mBanalService", null)

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().size() } returns 1

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<Long>()) } answers { self as Intent }

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        setField("mWorkoutRepository", repositoryMock)

        val snapshot = TrackerService.BanalFinalizationSnapshot(
            4,
            300,
            2000.0,
            setOf(200L),
            setOf(SensorType.POWER),
            "GC_SAMPLE",
            BSportType.BIKE,
            BSportType.BIKE,
            1L
        )

        service.endWorkout(snapshot)

        // Verify lap saved with snapshot metrics
        assertEquals(4L, service.savedLapNr)
        assertEquals(300, service.savedLapTime)
        assertEquals(2000.0, service.savedLapDistance ?: 0.0, 1e-6)
        assertEquals(2000.0 / 300.0, service.savedLapSpeed ?: 0.0, 1e-6)

        // Verify accumulated sensors saved from snapshot via repository
        verify(exactly = 1) {
            repositoryMock.saveAccumulatedSensorTypes(workoutId, setOf(SensorType.POWER))
        }

        // Verify FINISHED = 1 executed via repository
        verify(atLeast = 1) {
            repositoryMock.markWorkoutFinishedBlocking(workoutId)
        }
    }

    /**
     * TST-TRK-027.4: Verifies that onDestroy() defers safeUnbindBanalService() until after async
     * finalization on mDbExecutor completes, keeping BANALService alive during endWorkout() (REQ-TRK-035.4).
     */
    @Test
    fun testOnDestroy_defersUnbindBanalUntilAfterFinalization() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 888L)
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        baseFileNameField.set(realService, "2026-10-18_test")

        val mockBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        banalField.set(realService, mockBanal)
        realService.setBanalBoundForTesting(true)

        val executionOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
        realService.onEndWorkoutAction = {
            executionOrder.add("endWorkout")
            // Verify BANAL snapshot was captured
            assertTrue(realService.finalizationSnapshotForTesting != null)
        }
        realService.onUnbindServiceAction = {
            executionOrder.add("unbindService")
        }

        com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.start(com.atrainingtracker.banalservice.devices.radar.RadarScenario.SINGLE_CAR)
        assertTrue(com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.isSimulating)

        realService.onDestroy()

        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)

        assertTrue("RadarTrafficSimulator must remain running across onDestroy (e.g. on workout pause)", com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.isSimulating)
        com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.stop()
        assertTrue("endWorkout must be called during onDestroy", realService.endWorkoutCalled)
        assertEquals("endWorkout must execute before unbindService", listOf("endWorkout", "unbindService"), executionOrder)
        assertTrue("unbindService must be called", realService.unbindServiceCalled)
    }

    @Test
    fun testHandleActionStop_stopsRadarTrafficSimulator() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 999L)
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        baseFileNameField.set(realService, "2026-10-18_stop_test")

        com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.start(com.atrainingtracker.banalservice.devices.radar.RadarScenario.SINGLE_CAR)
        assertTrue(com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.isSimulating)

        realService.handleActionStop()

        assertFalse("handleActionStop must stop RadarTrafficSimulator", com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator.isSimulating)
    }

    /**
     * TST-TRK-076.1 / TST-TRK-076.4: Verifies that handleActionStop() does NOT shut down
     * mDbExecutor, mScheduler, or mRrFlushExecutor, and that complete executor shutdown occurs
     * strictly in onDestroy() (REQ-TRK-076.1).
     */
    @Test
    fun testHandleActionStop_doesNotShutdownExecutors_executorsOnlyShutdownInOnDestroy() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 1001L)
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        baseFileNameField.set(realService, "2026-11-14_1001")

        // Act 1: Stop workout
        realService.handleActionStop()

        // Assert 1: Immediately upon stop, executors must remain active (not shut down)
        assertFalse("mDbExecutor must NOT be shut down in handleActionStop", realService.isDbExecutorShutdownForTesting)
        assertFalse("mScheduler must NOT be shut down in handleActionStop", realService.isSchedulerShutdownForTesting)
        assertFalse("mRrFlushExecutor must NOT be shut down in handleActionStop", realService.isRrFlushExecutorShutdownForTesting)
        assertTrue("isStopping must be true while finalization is running", realService.isStoppingForTesting)

        // Wait for async finalization to complete
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)

        // Assert 2: Even after finalization completes, executors remain active until onDestroy
        assertFalse("mDbExecutor must remain open after finalization until onDestroy", realService.isDbExecutorShutdownForTesting)
        assertFalse("mScheduler must remain open after finalization until onDestroy", realService.isSchedulerShutdownForTesting)
        assertFalse("mRrFlushExecutor must remain open after finalization until onDestroy", realService.isRrFlushExecutorShutdownForTesting)

        // Act 2: Destroy service
        realService.onDestroy()

        // Assert 3: Executors are cleanly shut down strictly in onDestroy()
        assertTrue("mDbExecutor must be shut down in onDestroy", realService.isDbExecutorShutdownForTesting)
        assertTrue("mScheduler must be shut down in onDestroy", realService.isSchedulerShutdownForTesting)
        assertTrue("mRrFlushExecutor must be shut down in onDestroy", realService.isRrFlushExecutorShutdownForTesting)
    }

    /**
     * TST-TRK-076.1 / TST-TRK-076.2: Verifies that starting a new workout while async finalization
     * is in progress on mDbExecutor does not throw RejectedExecutionException, increments mSessionGeneration,
     * resets mIsStopping, and ensures session 1 finalization executes with its isolated snapshot parameters.
     */
    @Test
    fun testOnStartCommand_duringAsyncFinalization_noRejectedExecution_and_isolatedParameters() {
        val realService = TestableTrackerService()
        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(realService, repositoryMock)

        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        val liveSessionField = TrackerService::class.java.getDeclaredField("mLiveSession").apply { isAccessible = true }

        val session1WorkoutId = 1001L
        val session1BaseFileName = "2026-11-14_session1"
        val session1LiveSession = LiveWorkoutSession(0, TrackerService.IMPORTANT_SENSOR_TYPES)

        workoutIdField.set(realService, session1WorkoutId)
        baseFileNameField.set(realService, session1BaseFileName)
        liveSessionField.set(realService, session1LiveSession)

        val session1ExecutingLatch = java.util.concurrent.CountDownLatch(1)
        val session2StartLatch = java.util.concurrent.CountDownLatch(1)

        realService.endWorkoutLatchHook = {
            session1ExecutingLatch.countDown()
            // Hold session 1 in endWorkout() until session 2 is submitted via onStartCommand
            session2StartLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }

        val initialGeneration = realService.sessionGenerationForTesting

        // Trigger stop for session 1
        realService.handleActionStop()

        // Wait until session 1's endWorkout() is active on mDbExecutor
        assertTrue("Session 1 endWorkout must be active", session1ExecutingLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("realService must be in stopping state", realService.isStoppingForTesting)

        // Session 2 start intent
        val startIntent = mockk<Intent>(relaxed = true)
        every { startIntent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name
        realService.mockCreatedWorkoutId = 2002L

        // Act: Start session 2 re-entrantly during session 1 finalization
        var rejectedExecutionOccurred = false
        try {
            realService.onStartCommand(startIntent, 0, 1)
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            rejectedExecutionOccurred = true
        } finally {
            session2StartLatch.countDown()
        }

        assertFalse("onStartCommand must NEVER throw RejectedExecutionException during async stop finalization", rejectedExecutionOccurred)

        // Verify session generation incremented and stopping flag cleared
        assertTrue("mSessionGeneration must increment on restart", realService.sessionGenerationForTesting > initialGeneration)
        assertFalse("mIsStopping must be reset to false on restart", realService.isStoppingForTesting)

        // Wait for session 1 finalization to complete
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)
        realService.cancelTrackerHandle()

        // Assert: Session 1 finalization received its isolated snapshot parameters
        assertEquals("Session 1 endWorkout must use isolated workoutId", session1WorkoutId, realService.capturedFinalizationWorkoutId)
        assertEquals("Session 1 endWorkout must use isolated baseFileName", session1BaseFileName, realService.capturedFinalizationBaseFileName)
        assertSame("Session 1 endWorkout must use isolated liveSession", session1LiveSession, realService.capturedFinalizationLiveSession)
    }

    /**
     * TST-TRK-076.3: Verifies that session teardown (performStopSelf, performStopForeground,
     * safeUnbindBanalService) is suppressed when a new session starts while finalization is in progress.
     */
    @Test
    fun testTeardownSuppressed_whenRestartedDuringFinalization() {
        val realService = TestableTrackerService()
        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(realService, repositoryMock)

        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 3001L)

        val banalMock = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        banalField.set(realService, banalMock)
        realService.setBanalBoundForTesting(true)

        val finalizationActiveLatch = java.util.concurrent.CountDownLatch(1)
        val restartLatch = java.util.concurrent.CountDownLatch(1)

        realService.endWorkoutLatchHook = {
            finalizationActiveLatch.countDown()
            restartLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }

        realService.handleActionStop()
        assertTrue(finalizationActiveLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))

        // Trigger restart before finalization task completes
        val startIntent = mockk<Intent>(relaxed = true)
        every { startIntent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name
        realService.mockCreatedWorkoutId = 3002L
        realService.onStartCommand(startIntent, 0, 1)

        restartLatch.countDown()
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)
        realService.cancelTrackerHandle()

        // Teardown should have been suppressed because generation was incremented and mIsStopping was reset to false
        assertFalse("performStopSelf must be suppressed when new workout starts during finalization", realService.performStopSelfCalled)
        assertFalse("performStopForeground must be suppressed when new workout starts during finalization", realService.performStopForegroundCalled)
        assertFalse("safeUnbindBanalService must be suppressed when new workout starts during finalization", realService.unbindServiceCalled)
    }

    /**
     * TST-TRK-076.4: Verifies that when stop finalization finishes and no restart has occurred,
     * teardown (performStopForeground, performStopSelf, unbindService) proceeds normally.
     */
    @Test
    fun testCleanTerminalTeardown_whenNotRestarted() {
        val realService = TestableTrackerService()
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(realService, 4001L)
        val banalMock = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        banalField.set(realService, banalMock)
        realService.setBanalBoundForTesting(true)

        realService.handleActionStop()
        realService.finalizationFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)

        assertTrue("performStopForeground must be called when terminal stop completes", realService.performStopForegroundCalled)
        assertTrue("performStopSelf must be called when terminal stop completes", realService.performStopSelfCalled)
        assertTrue("unbindService must be called when terminal stop completes", realService.unbindServiceCalled)
    }

    /**
     * TST-TRK-076.5: Verifies that finalizeLiveSession consumes snapshot speed, distance, and duration
     * instead of mutating TrackerService instance fields when a concurrent session restart occurs.
     */
    @Test
    fun testFinalizeLiveSession_usesSnapshotMetrics_isolatedFromServiceFieldMutations() {
        val service = TestableTrackerService()
        val repoMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(service, repoMock)

        // Mutate service fields as if session 2 has started
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        workoutIdField.set(service, 9999L)
        val distField = TrackerService::class.java.getDeclaredField("mDistanceTotal_m").apply { isAccessible = true }
        distField.set(service, 100.0)
        val timeField = TrackerService::class.java.getDeclaredField("mTimeActive_s").apply { isAccessible = true }
        timeField.set(service, 10)

        // Snapshot captures Session 1 telemetry
        val session1WorkoutId = 1234L
        val snapshot = TrackerService.BanalFinalizationSnapshot(
            1, 600, 5000.0,
            emptySet(), emptySet(), null,
            BSportType.RUN, null, 1L,
            8.33, true, 5000.0, 600
        )

        val liveSession = mockk<LiveWorkoutSession>(relaxed = true)
        val speedStats = LiveWorkoutSession.RunningStats()
        speedStats.count = 20
        speedStats.min = 5.0
        speedStats.max = 12.0
        speedStats.sum = 160.0
        every { liveSession.sensorStats } returns mapOf(SensorType.SPEED_mps to speedStats)

        service.finalizeLiveSession(snapshot, session1WorkoutId, "2026-10-18_session1", liveSession)

        // Verify updateExtremaValue for SPEED_mps AVG used snapshot.avgSpeed (8.33), NOT service getAverageSpeed() (10.0)
        io.mockk.verify {
            repoMock.updateExtremaValue(
                session1WorkoutId,
                SensorType.SPEED_mps,
                ExtremaType.AVG,
                8.33,
                null,
                20
            )
        }
    }

    @Test
    fun testFinalizeLiveSession_triggersPersistConvergedCircumferences() {
        val service = TestableTrackerService()
        val repoMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(service, repoMock)

        val banalRepoMock = mockk<com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository>(relaxed = true)
        com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(banalRepoMock)
        try {
            val liveSession = mockk<LiveWorkoutSession>(relaxed = true)
            val snapshot = TrackerService.BanalFinalizationSnapshot(
                1, 600, 5000.0,
                emptySet(), emptySet(), null,
                BSportType.BIKE, null, 1L,
                8.33, true, 5000.0, 600
            )

            service.finalizeLiveSession(snapshot, 1234L, "2026-10-18_session1", liveSession)

            io.mockk.verify {
                banalRepoMock.persistConvergedCircumferences(null, any())
            }
        } finally {
            com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(null)
        }
    }

    /**
     * TST-TRK-084.1: Verifies that endWorkout() persists strictly liveSession.observedSensorTypes
     * rather than unfiltered BANALService.getAccumulatedSensorTypeSet() when liveSession is active,
     * excluding unattached virtual HRV/Power/Dynamics sensors (REQ-TRK-084.1).
     */
    @Test
    fun testEndWorkout_withActiveLiveSession_persistsOnlyObservedSensorTypes() {
        service.delegateToRealEndWorkout = true
        val workoutId = 4321L
        setWorkoutId(workoutId)
        setField("mBaseFileName", "2026-11-17_test")
        setField("mTimeActive_s", 120)
        setField("mDistanceTotal_m", 600.0)

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().size() } returns 1

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any(), any<Long>()) } answers { self as Intent }

        val repositoryMock = mockk<WorkoutRepository>(relaxed = true)
        setField("mWorkoutRepository", repositoryMock)

        val liveSession = LiveWorkoutSession(workoutId, TrackerService.IMPORTANT_SENSOR_TYPES)
        liveSession.recordObservedSensorType(SensorType.TIME_TOTAL)
        liveSession.recordObservedSensorType(SensorType.SPEED_mps)
        liveSession.recordObservedSensorType(SensorType.LATITUDE)
        liveSession.recordObservedSensorType(SensorType.LONGITUDE)
        setField("mLiveSession", liveSession)

        // Snapshot still carries the 40 registered virtual sensors from BANALService
        val registeredSet = setOf(
            SensorType.TIME_TOTAL, SensorType.SPEED_mps, SensorType.LATITUDE, SensorType.LONGITUDE,
            SensorType.HR, SensorType.POWER,
            SensorType.HRV_DFA_A1, SensorType.HRV_RMSSD, SensorType.HRV_ARTIFACT_RATE,
            SensorType.LIVE_HR_DRIFT, SensorType.POWER_5S, SensorType.NORMALIZED_POWER,
            SensorType.INTENSITY_FACTOR, SensorType.TSS, SensorType.WORK_KJ,
            SensorType.W_PRIME_BAL, SensorType.POWER_TO_WEIGHT
        )
        val snapshot = TrackerService.BanalFinalizationSnapshot(
            1, 120, 600.0,
            emptySet(), registeredSet, "TDS----AG--",
            BSportType.RUN, BSportType.RUN, 1L,
            5.0, true, 600.0, 120
        )

        service.endWorkout(snapshot)

        val savedSensorsSlot = slot<Set<SensorType>>()
        verifyOrder {
            repositoryMock.markWorkoutFinishedBlocking(workoutId)
            repositoryMock.saveAccumulatedSensorTypes(workoutId, capture(savedSensorsSlot))
        }
        assertEquals(
            setOf(SensorType.TIME_TOTAL, SensorType.SPEED_mps, SensorType.LATITUDE, SensorType.LONGITUDE),
            savedSensorsSlot.captured
        )
    }

    /**
     * TST-TRK-084.2: Verifies that finalizeLiveSession() idempotently upserts non-null
     * computed metrics (LINE_DISTANCE_m, NORMALIZED_POWER, INTENSITY_FACTOR, TSS, WORK_KJ,
     * POWER_TO_WEIGHT) into AccumulatedSensors after FINISHED = 1 (REQ-TRK-084.2).
     */
    @Test
    fun testFinalizeLiveSession_upsertsComputedPowerAndLineDistanceMetrics() {
        val service = TestableTrackerService()
        val repoMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(service, repoMock)

        mockkStatic(com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper::class)
        every {
            com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(any(), any())
        } returns 250
        every {
            com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt(any(), any())
        } returns 70.0f

        val baseFileName = "2026-11-17_power_test"
        val powerSamples = List(60) { 200.0 }
        every { repoMock.getPowerSpeedHrSamples(baseFileName) } returns
            com.atrainingtracker.trainingtracker.ui.aftermath.PowerSpeedHrSamples(
                powerSamples = powerSamples,
                speedSamples = emptyList(),
                hrSamples = emptyList()
            )

        val engineMock = mockk<com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine>(relaxed = true)
        every { engineMock.findApexFromPoints(any(), any(), any()) } returns LatLng(48.11, 11.51)
        com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.resetForTesting(engineMock)
        mockkObject(com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.Companion)
        every { com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.getInstance(any()) } returns engineMock

        val banalRepoMock = mockk<com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository>(relaxed = true)
        com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(banalRepoMock)

        try {
            val liveSession = LiveWorkoutSession(555L, TrackerService.IMPORTANT_SENSOR_TYPES)
            liveSession.addSample(SensorType.SPEED_mps, 8.0, LatLng(48.10, 11.50))
            liveSession.rehydrateStreams(
                listOf(LatLng(48.10, 11.50), LatLng(48.11, 11.51)),
                listOf(500.0, 505.0),
                listOf(0.0, 1500.0)
            )

            val snapshot = TrackerService.BanalFinalizationSnapshot(
                1, 60, 1500.0,
                emptySet(), emptySet(), null,
                BSportType.BIKE, BSportType.BIKE, 1L,
                8.0, true, 1500.0, 60
            )

            service.finalizeLiveSession(snapshot, 555L, baseFileName, liveSession)

            val upsertedSlot = slot<Set<SensorType>>()
            verify(exactly = 1) {
                repoMock.addAccumulatedSensorTypes(555L, capture(upsertedSlot))
            }
            assertTrue(upsertedSlot.captured.contains(SensorType.LINE_DISTANCE_m))
            assertTrue(upsertedSlot.captured.contains(SensorType.NORMALIZED_POWER))
            assertTrue(upsertedSlot.captured.contains(SensorType.INTENSITY_FACTOR))
            assertTrue(upsertedSlot.captured.contains(SensorType.TSS))
            assertTrue(upsertedSlot.captured.contains(SensorType.WORK_KJ))
            assertTrue(upsertedSlot.captured.contains(SensorType.POWER_TO_WEIGHT))
        } finally {
            com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.resetForTesting(null)
            com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(null)
        }
    }

    /**
     * TST-FIL-030.4: Verifies multi-stop errand bike finalization passes cruising moving speed to
     * `EquipmentAndSportTypeDiscoveryManager.resolveIdentity()` and `getCandidateBSportTypes()`
     * (including `LiveWorkoutSession` 1-Hz GPS-only fallback) while strictly preserving session
     * `mDistanceTotal_m / mTimeActive_s` (`1.35 m/s`) for `SensorType.SPEED_mps` `ExtremaType.AVG`
     * (`SCRUM-108` / `REQ-TRK-030.3`), and honors explicit `WALK` pre-selection in `capture()`.
     */
    @Test
    fun testFinalizeLiveSession_errandBikeUsesMovingSpeedForIdentityAndPreservesSessionSpeedExtrema_tstFil030_4() {
        val repoMock = mockk<WorkoutRepository>(relaxed = true)
        setField("mWorkoutRepository", repoMock)

        val discoveryMock = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns discoveryMock

        val identitySpeedSlot = slot<Double>()
        val candidateSpeedSlot = slot<Double>()
        val bikeIdentity = EquipmentAndSportTypeDiscoveryManager.InferredIdentity(
            sportId = 3L,
            bSportType = BSportType.BIKE,
            equipmentId = 42L,
            stravaSportName = "Ride",
            uploadToStrava = 1,
            isHighConfidence = false
        )
        every {
            discoveryMock.resolveIdentity(any<Set<Long>>(), eq(BSportType.BIKE), capture(identitySpeedSlot))
        } returns bikeIdentity
        every {
            discoveryMock.getCandidateBSportTypes(eq(BSportType.BIKE), capture(candidateSpeedSlot))
        } returns setOf(BSportType.BIKE)

        val engineMock = mockk<com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine>(relaxed = true)
        every { engineMock.findApexFromPoints(any(), any(), any()) } returns LatLng(48.11, 11.51)
        every {
            engineMock.suggestCluster(
                any(), any(), any(), any(), any(),
                any<Set<BSportType>>(), any(), any(), any(), any(), any()
            )
        } returns null
        com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.resetForTesting(engineMock)
        mockkObject(com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.Companion)
        every { com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.getInstance(any()) } returns engineMock
        every { repoMock.getExtremaPosition(any(), any(), any()) } returns LatLng(48.11, 11.51)

        val banalRepoMock = mockk<com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository>(relaxed = true)
        com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(banalRepoMock)

        try {
            // LiveWorkoutSession 1-Hz fallback: 10 ticks at 3.88 m/s, 10 ticks at 6.34 m/s, 40 ticks at 0.0 m/s
            val liveSession = LiveWorkoutSession(777L, TrackerService.IMPORTANT_SENSOR_TYPES)
            repeat(10) { liveSession.addSample(SensorType.SPEED_mps, 3.88, LatLng(48.10, 11.50)) }
            repeat(10) { liveSession.addSample(SensorType.SPEED_mps, 6.34, LatLng(48.11, 11.51)) }
            repeat(40) { liveSession.addSample(SensorType.SPEED_mps, 0.0, LatLng(48.11, 11.51)) }

            // Snapshot with null movingSpeedSummary -> exercises LiveWorkoutSession 1-Hz fallback!
            val snapshot = TrackerService.BanalFinalizationSnapshot(
                1, 1800, 2430.0,
                emptySet(), emptySet(), "TDS----AG--",
                BSportType.BIKE, null, 3L,
                1.35, true, 2430.0, 1800
            )
            service.finalizeLiveSession(snapshot, 777L, "errand_bike", liveSession)

            // Identity & cluster candidates receive cruising moving speed (> 5.0 m/s), NOT 1.35 m/s!
            assertTrue(identitySpeedSlot.isCaptured)
            assertTrue("Expected cruising moving speed > 5.0 m/s, got ${identitySpeedSlot.captured}", identitySpeedSlot.captured > 5.0)
            assertTrue(candidateSpeedSlot.isCaptured)
            assertTrue("Expected cluster candidate speed > 5.0 m/s, got ${candidateSpeedSlot.captured}", candidateSpeedSlot.captured > 5.0)

            // SPEED_mps ExtremaType.AVG MUST still persist authoritative session avgSpeed = 1.35 m/s!
            verify(exactly = 1) {
                repoMock.updateExtremaValue(777L, SensorType.SPEED_mps, ExtremaType.AVG, 1.35, null, 60)
            }

            // Also verify BanalFinalizationSnapshot.capture preserves explicit WALK (sportId=4, BSportType.UNKNOWN)
            val walkBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
            every { walkBanal.bSportType } returns BSportType.UNKNOWN
            every { walkBanal.userSelectedBSportType } returns BSportType.UNKNOWN
            every { walkBanal.sportTypeId } returns 4L
            every { walkBanal.movingSpeedSummary } returns com.atrainingtracker.banalservice.odometry.MovingSpeedSummary(4.1, 3.9, 6.3, 100, 120.0)
            val walkSnap = TrackerService.BanalFinalizationSnapshot.capture(walkBanal, mockk(relaxed = true), 1.35, true, 2430.0, 1800)
            assertEquals(4L, walkSnap.sportTypeId)
            assertEquals(BSportType.UNKNOWN, walkSnap.userSelectedSportType)
            assertEquals(100, walkSnap.movingSpeedSummary?.movingSampleCount)
        } finally {
            com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine.resetForTesting(null)
            com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository.resetForTesting(null)
        }
    }

    @Test
    fun testEndWorkout_decouplesPostProcessingAndBroadcastsTrackingFinishedImmediately_tstTrk091_1() {
        // REQ-TRK-091.1 / TST-TRK-091.1
        WorkoutPostProcessingCoordinator.resetForTesting()
        var capturedRequest: WorkoutPostProcessingCoordinator.PostProcessingRequest? = null
        WorkoutPostProcessingCoordinator.enqueueHookForTesting = { _, req ->
            capturedRequest = req
            true
        }

        mockkStatic(com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController::class)
        mockkStatic(com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController::class)
        mockkStatic(com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper::class)
        every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(any()) } returns false
        every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getRawSensorRetentionPolicy(any()) } returns com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy.KEEP_ALL
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.isRecordingArmed() } returns true
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.getSessionStartTimeMs() } returns 1111L
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.getEffectiveAnchorStartTimeMs() } returns 1111L
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.getArmedSessionToken() } returns 42L
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController.isRecordingArmed() } returns true
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController.getSessionStartTimeMs() } returns 2222L
        every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController.getArmedSessionToken() } returns 84L

        try {
            mockkConstructor(Intent::class)
            every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
            every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
            every { anyConstructed<Intent>().action } returns TrackerService.TRACKING_FINISHED_INTENT

            val mockApp = mockk<Application>(relaxed = true)
            every { mockApp.applicationContext } returns mockApp
            every { mockApp.packageName } returns "com.atrainingtracker"
            val sentBroadcasts = mutableListOf<Intent>()
            val spyingService = object : TestableTrackerService() {
                override fun getApplicationContext(): Context = mockApp
                override fun sendBroadcast(intent: Intent?) {
                    if (intent != null) sentBroadcasts.add(intent)
                }
            }
            spyingService.delegateToRealEndWorkout = true

            val repoMock = mockk<WorkoutRepository>(relaxed = true)
            WorkoutRepository.resetForTesting(repoMock)

            spyingService.endWorkout(null, 2414L, "2026-11-20_184953", null)

            val request = requireNotNull(capturedRequest) { "Expected PostProcessingRequest to be enqueued" }
            assertEquals(2414L, request.workoutId)
            assertEquals("2026-11-20_184953", request.baseFileName)
            assertTrue(request.h10Armed)
            assertEquals(42L, request.h10ArmedToken)
            assertTrue(request.verityArmed)
            assertEquals(84L, request.verityArmedToken)
            assertTrue(
                "TRACKING_FINISHED_INTENT must be broadcast immediately on Stop",
                sentBroadcasts.isNotEmpty()
            )
            verify(exactly = 0) {
                com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController
                    .stopAndBackfillWorkoutBlocking(any(), any(), any())
            }
            verify(exactly = 0) {
                com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController
                    .stopAndBackfillWorkoutBlocking(any(), any(), any())
            }
        } finally {
            WorkoutPostProcessingCoordinator.resetForTesting()
            WorkoutRepository.resetForTesting(null)
        }
    }

    /**
     * TST-TRK-092.1 / REQ-TRK-034.4 / REQ-TRK-033.2 / REQ-TRK-076.1 (ATT-6178):
     * Verifies both external onDestroy() races during active ACTION_STOP finalization using a
     * queued Main-Thread dispatcher (simulating Android's Main Looper):
     * (A) endWorkout() is still executing on mDbExecutor -> onDestroy() returns in < 100 ms,
     *     shuts down executors, and does NOT prematurely unbind BANALService, stop foreground,
     *     or release WakeLock.
     * (B) endWorkout() already finished and queued its Main-Thread cleanup Runnable ahead of
     *     onDestroy() -> onDestroy() still returns in < 100 ms without self-deadlocking for 2.0 s.
     */
    @Test
    fun testOnDestroy_duringActiveActionStopFinalization_returnsNonBlockingWithoutPrematureUnbind_tstTrk092_1() {
        // --- Race A: external onDestroy() while endWorkout() is still blocked on mDbExecutor ---
        val queuedMainTasksA = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val mainTaskQueuedLatchA = java.util.concurrent.CountDownLatch(1)
        val serviceA = object : TestableTrackerService() {
            override fun runOnMainThread(action: Runnable) {
                queuedMainTasksA.add(action)
                mainTaskQueuedLatchA.countDown()
            }
        }
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        val wakeLockField = TrackerService::class.java.getDeclaredField("wakeLock").apply { isAccessible = true }

        workoutIdField.set(serviceA, 61781L)
        baseFileNameField.set(serviceA, "2026-11-22_att6178_raceA")
        banalField.set(serviceA, mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true))
        serviceA.setBanalBoundForTesting(true)
        val mockWakeLockA = mockk<android.os.PowerManager.WakeLock>(relaxed = true)
        every { mockWakeLockA.isHeld } returns true
        wakeLockField.set(serviceA, mockWakeLockA)

        val endWorkoutEnteredA = java.util.concurrent.CountDownLatch(1)
        val releaseEndWorkoutA = java.util.concurrent.CountDownLatch(1)
        serviceA.endWorkoutLatchHook = {
            endWorkoutEnteredA.countDown()
            releaseEndWorkoutA.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }

        serviceA.handleActionStop()
        assertTrue("endWorkout must start on mDbExecutor", endWorkoutEnteredA.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse("finalizationFuture must still be incomplete", serviceA.finalizationFuture.isDone)

        val startNsA = System.nanoTime()
        serviceA.onDestroy()
        val elapsedMsA = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNsA)

        try {
            assertTrue("Race A: onDestroy() must return non-blocking (< 100 ms), took ${elapsedMsA} ms", elapsedMsA < 100)
            assertTrue("mScheduler must be shut down in onDestroy() (REQ-TRK-076.1)", serviceA.isSchedulerShutdownForTesting)
            assertTrue("mRrFlushExecutor must be shut down in onDestroy() (REQ-TRK-076.1)", serviceA.isRrFlushExecutorShutdownForTesting)
            assertTrue("mDbExecutor must be shut down in onDestroy() (REQ-TRK-076.1)", serviceA.isDbExecutorShutdownForTesting)
            assertFalse("BANALService must NOT be unbound prematurely while endWorkout is active (REQ-TRK-033.2)", serviceA.unbindServiceCalled)
            assertFalse("performStopForeground must NOT be called prematurely while endWorkout is active", serviceA.performStopForegroundCalled)
            verify(exactly = 0) { mockWakeLockA.release() }
        } finally {
            releaseEndWorkoutA.countDown()
        }

        assertTrue("Cleanup runnable must be posted to Main Thread after endWorkout", mainTaskQueuedLatchA.await(2, java.util.concurrent.TimeUnit.SECONDS))
        while (true) {
            val r = queuedMainTasksA.poll() ?: break
            r.run()
        }
        assertTrue("finalizationFuture must complete after Main-Thread runnable drains", serviceA.finalizationFuture.isDone)
        assertTrue("BANALService must be unbound after endWorkout completes", serviceA.unbindServiceCalled)

        // --- Race B: endWorkout() already finished & queued Main-Thread cleanup BEFORE onDestroy() runs ---
        val queuedMainTasksB = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val mainTaskQueuedLatchB = java.util.concurrent.CountDownLatch(1)
        val serviceB = object : TestableTrackerService() {
            override fun runOnMainThread(action: Runnable) {
                queuedMainTasksB.add(action)
                mainTaskQueuedLatchB.countDown()
            }
        }
        workoutIdField.set(serviceB, 61782L)
        baseFileNameField.set(serviceB, "2026-11-22_att6178_raceB")
        banalField.set(serviceB, mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true))
        serviceB.setBanalBoundForTesting(true)

        serviceB.handleActionStop()
        assertTrue("Race B: endWorkout must finish and enqueue Main-Thread runnable", mainTaskQueuedLatchB.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse("Race B: finalizationFuture is still incomplete until Main Looper drains runnable", serviceB.finalizationFuture.isDone)

        val startNsB = System.nanoTime()
        serviceB.onDestroy()
        val elapsedMsB = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNsB)

        assertTrue("Race B: onDestroy() must NOT self-deadlock on queued Main-Thread completion (< 100 ms), took ${elapsedMsB} ms", elapsedMsB < 100)
        assertFalse("Race B: onDestroy() must leave unbind to the queued Main-Thread runnable", serviceB.unbindServiceCalled)
        while (true) {
            val r = queuedMainTasksB.poll() ?: break
            r.run()
        }
        assertTrue("Race B: finalizationFuture must complete once Main-Thread runnable drains", serviceB.finalizationFuture.isDone)
        assertTrue("Race B: queued Main-Thread runnable must unbind BANALService", serviceB.unbindServiceCalled)
    }

    /**
     * TST-TRK-092.2 / REQ-TRK-034.3 / REQ-TRK-034.4 (ATT-6178):
     * Verifies that after external onDestroy() returns during active ACTION_STOP finalization,
     * the post-endWorkout Main-Thread cleanup Runnable runs strictly after endWorkout(), is
     * individually fault-isolated when removeRrBeatListener / unbindService / performStopForeground /
     * performStopSelf throw on an already-destroyed Service, releases WakeLock idempotently once,
     * clears mBanalService, and completes finalizationFuture without leaking exceptions.
     */
    @Test
    fun testPostEndWorkoutCleanupAfterOnDestroy_runsIdempotentlyWithoutExceptionOnDestroyedService_tstTrk092_2() {
        val queuedMainTasks = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val mainTaskQueuedLatch = java.util.concurrent.CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        var unbindCalls = 0
        var stopForegroundCalls = 0
        var stopSelfCalls = 0

        val destroyedService = object : TestableTrackerService() {
            override fun runOnMainThread(action: Runnable) {
                queuedMainTasks.add(action)
                mainTaskQueuedLatch.countDown()
            }

            override fun unbindService(conn: ServiceConnection) {
                unbindCalls++
                events.add("unbindService")
                super.unbindService(conn)
                throw IllegalArgumentException("Service not registered (already destroyed)")
            }

            override fun performStopForeground() {
                stopForegroundCalls++
                events.add("stopForeground")
                super.performStopForeground()
                throw IllegalStateException("Service already destroyed in stopForeground")
            }

            override fun performStopSelf() {
                stopSelfCalls++
                events.add("stopSelf")
                super.performStopSelf()
                throw IllegalStateException("Service already destroyed in stopSelf")
            }
        }

        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        val wakeLockField = TrackerService::class.java.getDeclaredField("wakeLock").apply { isAccessible = true }

        workoutIdField.set(destroyedService, 61783L)
        baseFileNameField.set(destroyedService, "2026-11-22_att6178_idempotent")
        val mockBanal = mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true)
        // Gate 3 Guardrail #2: removeRrBeatListener throws -> must NOT skip unbindService
        every { mockBanal.removeRrBeatListener(any()) } throws RuntimeException("Dead binder during removeRrBeatListener")
        banalField.set(destroyedService, mockBanal)
        destroyedService.setBanalBoundForTesting(true)

        var wakeLockHeld = true
        val mockWakeLock = mockk<android.os.PowerManager.WakeLock>(relaxed = true)
        every { mockWakeLock.isHeld } answers { wakeLockHeld }
        every { mockWakeLock.release() } answers { wakeLockHeld = false }
        wakeLockField.set(destroyedService, mockWakeLock)

        val endWorkoutEntered = java.util.concurrent.CountDownLatch(1)
        val releaseEndWorkout = java.util.concurrent.CountDownLatch(1)
        destroyedService.endWorkoutLatchHook = {
            events.add("endWorkout")
            endWorkoutEntered.countDown()
            releaseEndWorkout.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }

        destroyedService.handleActionStop()
        assertTrue(endWorkoutEntered.await(2, java.util.concurrent.TimeUnit.SECONDS))

        // External onDestroy() arrives while endWorkout() is active
        destroyedService.onDestroy()
        assertEquals("Only endWorkout may have run before release", listOf("endWorkout"), events)

        releaseEndWorkout.countDown()
        assertTrue(mainTaskQueuedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))

        val cleanupRunnable = requireNotNull(queuedMainTasks.poll())
        // Must not throw despite removeRrBeatListener, unbindService, stopForeground, and stopSelf all throwing
        cleanupRunnable.run()

        assertEquals(listOf("endWorkout", "unbindService", "stopForeground", "stopSelf"), events)
        assertEquals("unbindService must run even when removeRrBeatListener throws", 1, unbindCalls)
        assertEquals(1, stopForegroundCalls)
        assertEquals(1, stopSelfCalls)
        verify(exactly = 1) { mockWakeLock.release() }
        org.junit.Assert.assertNull("mBanalService must be nulled after cleanup", banalField.get(destroyedService))
        org.junit.Assert.assertNull("wakeLock must be nulled after cleanup", wakeLockField.get(destroyedService))
        assertTrue("finalizationFuture must complete even when teardown calls throw", destroyedService.finalizationFuture.isDone)

        // Idempotent second execution of cleanup runnable must be a safe no-op for unbind & wakeLock
        cleanupRunnable.run()
        assertEquals("Second unbind must be skipped once mBanalBound=false and mBanalService=null", 1, unbindCalls)
        verify(exactly = 1) { mockWakeLock.release() }
    }

    /**
     * TST-TRK-092.3 / REQ-TRK-034.4 / REQ-TRK-033.2 (ATT-6178):
     * Verifies:
     * (A) Exception thrown inside endWorkout() after external onDestroy() still completes cleanup & future.
     * (B) onDestroy() when mIsStopping == true and mFinalizationFuture is already done still releases
     *     any remaining BANAL binding and WakeLock defensively without blocking.
     * (C) Gate 3 Guardrail #1: flushLiveRrQueueSync() catches RejectedExecutionException on a concurrent
     *     mRrFlushExecutor.shutdown() race and flushes RR intervals inline without losing data.
     */
    @Test
    fun testOnDestroy_duringActionStopWithEndWorkoutFailureOrCompletedFuture_completesCleanupSafely_tstTrk092_3() {
        val workoutIdField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        val baseFileNameField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        val banalField = TrackerService::class.java.getDeclaredField("mBanalService").apply { isAccessible = true }
        val wakeLockField = TrackerService::class.java.getDeclaredField("wakeLock").apply { isAccessible = true }
        val isStoppingField = TrackerService::class.java.getDeclaredField("mIsStopping").apply { isAccessible = true }

        // (A) endWorkout throws RuntimeException after external onDestroy()
        val queuedMainTasks = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val queuedLatch = java.util.concurrent.CountDownLatch(1)
        val failingService = object : TestableTrackerService() {
            override fun runOnMainThread(action: Runnable) {
                queuedMainTasks.add(action)
                queuedLatch.countDown()
            }
        }
        workoutIdField.set(failingService, 61784L)
        baseFileNameField.set(failingService, "2026-11-22_att6178_fail")
        banalField.set(failingService, mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true))
        failingService.setBanalBoundForTesting(true)
        val wlA = mockk<android.os.PowerManager.WakeLock>(relaxed = true)
        every { wlA.isHeld } returns true
        wakeLockField.set(failingService, wlA)

        val enteredLatch = java.util.concurrent.CountDownLatch(1)
        val releaseLatch = java.util.concurrent.CountDownLatch(1)
        failingService.endWorkoutLatchHook = {
            enteredLatch.countDown()
            releaseLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            throw RuntimeException("Simulated endWorkout SQLite failure")
        }

        failingService.handleActionStop()
        assertTrue(enteredLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))
        failingService.onDestroy()
        releaseLatch.countDown()
        assertTrue(queuedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))
        requireNotNull(queuedMainTasks.poll()).run()

        assertTrue("finalizationFuture must complete after endWorkout failure", failingService.finalizationFuture.isDone)
        assertTrue("BANALService must still be unbound after endWorkout failure", failingService.unbindServiceCalled)
        verify(exactly = 1) { wlA.release() }
        org.junit.Assert.assertNull(banalField.get(failingService))

        // (B) onDestroy() with mIsStopping == true and already-completed mFinalizationFuture
        val completedService = TestableTrackerService()
        isStoppingField.set(completedService, true)
        banalField.set(completedService, mockk<com.atrainingtracker.banalservice.BANALService.BANALServiceComm>(relaxed = true))
        completedService.setBanalBoundForTesting(true)
        val wlB = mockk<android.os.PowerManager.WakeLock>(relaxed = true)
        every { wlB.isHeld } returns true
        wakeLockField.set(completedService, wlB)

        completedService.onDestroy()
        assertTrue("Completed-future onDestroy must still unbind BANALService defensively", completedService.unbindServiceCalled)
        verify(exactly = 1) { wlB.release() }
        org.junit.Assert.assertNull(banalField.get(completedService))

        // (C) Gate 3 Guardrail #1: flushLiveRrQueueSync TOCTOU RejectedExecutionException -> inline fallback
        val rrService = TestableTrackerService()
        val rrRepoMock = mockk<com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository>(relaxed = true)
        val rrRepoField = TrackerService::class.java.getDeclaredField("mWorkoutRrRepository").apply { isAccessible = true }
        rrRepoField.set(rrService, rrRepoMock)

        val racingExecutor = mockk<ExecutorService>(relaxed = true)
        every { racingExecutor.isShutdown } returns false
        every { racingExecutor.submit(any<Runnable>()) } throws java.util.concurrent.RejectedExecutionException("Concurrent shutdown race")
        val rrExecField = TrackerService::class.java.getDeclaredField("mRrFlushExecutor").apply { isAccessible = true }
        rrExecField.set(rrService, racingExecutor)

        rrService.liveRrQueueForTesting.add(
            com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval(
                id = null,
                workoutId = 61785L,
                beatIndex = 0,
                beatTimestampMs = 1000L,
                rrMs = 820f,
                artifactFlag = 0,
                gapBeats = 0
            )
        )
        rrService.flushLiveRrQueueSync(61785L)
        verify(exactly = 1) { rrRepoMock.insertIntervalsBlocking(match { it.size == 1 && it[0].rrMs == 820f }) }
    }

    // REQ-TRK-093.1 / REQ-TRK-093.2 / REQ-TRK-093.3 / TST-TRK-093.3 (ATT-8124)
    @Test
    fun testEndWorkout_consistentSingleH10ArmedSnapshot_defersHrPhysiologyAndPassesAnchorAndEndTime_TST_TRK_093_3() {
        WorkoutPostProcessingCoordinator.resetForTesting()
        val capturedRequests = mutableListOf<WorkoutPostProcessingCoordinator.PostProcessingRequest>()
        WorkoutPostProcessingCoordinator.enqueueHookForTesting = { _, req ->
            capturedRequests.add(req)
            true
        }

        mockkStatic(com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController::class)
        mockkStatic(com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController::class)
        mockkStatic(com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator::class)
        mockkStatic(com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator::class)
        mockkStatic(com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper::class)
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        mockkStatic(com.atrainingtracker.trainingtracker.database.MilestoneAutoNamingManager::class)
        mockkObject(com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Companion)

        try {
            mockkConstructor(Intent::class)
            every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
            every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
            every { anyConstructed<Intent>().action } returns TrackerService.TRACKING_FINISHED_INTENT

            every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController.isRecordingArmed() } returns false
            every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.isPostWorkoutErtsSmoothingEnabled(any()) } returns false
            every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getRawSensorRetentionPolicy(any()) } returns com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy.KEEP_ALL
            every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(any(), any()) } returns 250
            every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt(any(), any()) } returns 75.0f
            every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)
            every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns mockk(relaxed = true)
            every {
                com.atrainingtracker.trainingtracker.database.MilestoneAutoNamingManager.evaluateAndApplyMilestone(any(), any(), any(), any(), any())
            } returns null
            every { com.atrainingtracker.trainingtracker.settings.SettingsDataStore.getInstance(any()) } returns mockk(relaxed = true)

            val repoMock = mockk<WorkoutRepository>(relaxed = true)
            every { repoMock.getPowerSpeedHrSamples(any()) } returns com.atrainingtracker.trainingtracker.ui.aftermath.PowerSpeedHrSamples(
                powerSamples = listOf(220.0, 230.0),
                speedSamples = listOf(8.5, 8.6),
                hrSamples = listOf(145.0, 148.0)
            )
            WorkoutRepository.resetForTesting(repoMock)

            every {
                com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(any(), any(), any(), any())
            } returns com.atrainingtracker.trainingtracker.physiology.AerobicEfficiencyResult(1.55, 2.0, 1.57, 1.53, true, false)
            every {
                com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator.calculate(any(), any(), any(), any())
            } returns null
            every {
                com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computePowerTrainingMetrics(any(), any(), any(), any())
            } returns com.atrainingtracker.trainingtracker.physiology.PowerTrainingMetricsResult(225.0, 225.0, 230.0, 1.0, 0.9, 10.0, 5.0, 3.0, 3.1, 3.0)

            val mockApp = mockk<Application>(relaxed = true)
            every { mockApp.applicationContext } returns mockApp
            every { mockApp.packageName } returns "com.atrainingtracker"

            val liveSession = mockk<LiveWorkoutSession>(relaxed = true)

            // (A) H10 armed: isRecordingArmed() queried ONCE, EF/Decoupling/Dynamics deferred, NP/IF/TSS computed,
            //     and PostProcessingRequest carries getEffectiveAnchorStartTimeMs() + workoutEndMs > 0
            every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.isRecordingArmed() } returns true
            every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.getEffectiveAnchorStartTimeMs() } returns 1_700_000_008_000L
            every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.getArmedSessionToken() } returns 93L

            val armedService = object : TestableTrackerService() {
                override fun getApplicationContext(): Context = mockApp
                override fun sendBroadcast(intent: Intent?) {}
            }
            armedService.delegateToRealEndWorkout = true
            val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
            repoField.set(armedService, repoMock)

            val beforeMs = System.currentTimeMillis()
            armedService.endWorkout(null, 81241L, "2026-11-22_armed", liveSession)
            val afterMs = System.currentTimeMillis()

            verify(exactly = 1) { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.isRecordingArmed() }
            verify(exactly = 0) {
                com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(any(), any(), any(), any())
            }
            verify(exactly = 0) {
                com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator.calculate(any(), any(), any(), any())
            }
            verify(exactly = 1) {
                com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computePowerTrainingMetrics(any(), any(), any(), any())
            }
            assertEquals(1, capturedRequests.size)
            val armedReq = capturedRequests[0]
            assertTrue(armedReq.h10Armed)
            assertEquals(1_700_000_008_000L, armedReq.h10StartMs)
            assertEquals(93L, armedReq.h10ArmedToken)
            assertTrue("workoutEndMs must be captured in [beforeMs, afterMs]", armedReq.workoutEndMs in beforeMs..afterMs)

            // (B) H10 NOT armed: EF/Decoupling/Dynamics MUST run synchronously inside finalizeLiveSession()
            every { com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController.isRecordingArmed() } returns false
            armedService.endWorkout(null, 81242L, "2026-11-22_unarmed", liveSession)
            verify(exactly = 1) {
                com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(any(), any(), any(), any())
            }
            verify(exactly = 1) {
                com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator.calculate(any(), any(), 81242L, 1.0)
            }
        } finally {
            WorkoutPostProcessingCoordinator.resetForTesting()
            WorkoutRepository.resetForTesting(null)
        }
    }

    /**
     * TST-TRK-095.1 (ATT-9498): Verifies deferred pre-start stop executes performStartForeground()
     * FIRST, then stops foreground and stopSelf() without creating an orphan DB workout.
     */
    @Test
    fun testOnStartCommand_deferredPreStartStop_promotesForegroundThenStopsWithoutCreatingWorkout_TST_TRK_095_1() {
        val events = mutableListOf<String>()
        val mockApp = mockk<com.atrainingtracker.trainingtracker.TrainingApplication>(relaxed = true)
        val mockNotif = mockk<android.app.Notification>(relaxed = true)
        every { mockApp.searchingAndTrackingNotification } returns mockNotif

        val trackedService = object : TestableTrackerService() {
            override fun performStartForeground(id: Int, notification: android.app.Notification?, foregroundServiceType: Int) {
                events.add("startForeground")
            }
            override fun performStopForeground() {
                events.add("stopForeground")
                super.performStopForeground()
            }
            override fun performStopSelf() {
                events.add("stopSelf")
                super.performStopSelf()
            }
            override fun createNewWorkout(): Long {
                events.add("createNewWorkout")
                return 999L
            }
        }
        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication").apply { isAccessible = true }
        appField.set(trackedService, mockApp)

        TrackerService.markForegroundStartRequested()
        assertTrue(TrackerService.requestStopIfForegroundStartPending())

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns null
        every { intent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name

        val result = trackedService.onStartCommand(intent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
        assertEquals(listOf("startForeground", "stopForeground", "stopSelf"), events)
        assertTrue(trackedService.isStoppingForTesting)
        assertTrue(trackedService.isTrackingInterrupted)
        assertFalse(
            "sFgsStartPending must be cleared after promotion",
            TrackerService.requestStopIfForegroundStartPending()
        )
        trackedService.onDestroy()
    }

    /**
     * TST-TRK-095.1 (ATT-9498): Verifies onStartCommand(START_NORMAL) calls performStartForeground()
     * BEFORE submitting createNewWorkout() to mDbExecutor, and rapid ACTION_STOP resolves the
     * FIFO-created mWorkoutID in endWorkout() so FINISHED=1 is committed without orphan workouts.
     */
    @Test
    fun testOnStartCommand_promotesBeforeDbSubmit_andRapidActionStopResolvesFifoWorkoutId_TST_TRK_095_1() {
        val events = mutableListOf<String>()
        val queuedDbTasks = mutableListOf<Runnable>()
        val mockApp = mockk<com.atrainingtracker.trainingtracker.TrainingApplication>(relaxed = true)
        val mockNotif = mockk<android.app.Notification>(relaxed = true)
        every { mockApp.searchingAndTrackingNotification } returns mockNotif

        val trackedService = object : TestableTrackerService() {
            override fun performStartForeground(id: Int, notification: android.app.Notification?, foregroundServiceType: Int) {
                events.add("startForeground")
            }
            override fun createNewWorkout(): Long {
                events.add("createNewWorkout")
                return 9498L
            }
        }
        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication").apply { isAccessible = true }
        appField.set(trackedService, mockApp)
        val repoMock = mockk<WorkoutRepository>(relaxed = true)
        val repoField = TrackerService::class.java.getDeclaredField("mWorkoutRepository").apply { isAccessible = true }
        repoField.set(trackedService, repoMock)

        val fifoMock = mockk<ExecutorService>(relaxed = true)
        every { fifoMock.submit(any<Runnable>()) } answers {
            events.add("dbSubmit")
            queuedDbTasks.add(firstArg())
            java.util.concurrent.CompletableFuture.completedFuture(null)
        }
        every { fifoMock.execute(any()) } answers {
            events.add("dbExecute")
            queuedDbTasks.add(firstArg())
        }
        val execField = TrackerService::class.java.getDeclaredField("mDbExecutor").apply { isAccessible = true }
        execField.set(trackedService, fifoMock)

        TrackerService.markForegroundStartRequested()
        val startIntent = mockk<Intent>(relaxed = true)
        every { startIntent.action } returns null
        every { startIntent.getStringExtra(TrackerService.START_TYPE) } returns TrackerService.StartType.START_NORMAL.name

        trackedService.onStartCommand(startIntent, 0, 1)

        assertTrue("startForeground must run before dbSubmit", events.indexOf("startForeground") < events.indexOf("dbSubmit"))

        // Rapid ACTION_STOP arrives BEFORE queued createNewWorkout() has executed on mDbExecutor!
        val stopIntent = mockk<Intent>(relaxed = true)
        every { stopIntent.action } returns TrackerService.ACTION_STOP
        trackedService.onStartCommand(stopIntent, 0, 2)

        assertEquals("Both init and finalization tasks must be queued FIFO", 2, queuedDbTasks.size)
        // Execute FIFO: Task 1 creates workout 9498L, Task 2 runs endWorkout
        queuedDbTasks[0].run()
        queuedDbTasks[1].run()

        assertTrue("endWorkout must execute", trackedService.endWorkoutCalled)
        assertEquals(
            "FIFO finalization must resolve the newly created mWorkoutID (9498L) instead of stale -1",
            9498L,
            trackedService.capturedFinalizationWorkoutId
        )
        trackedService.onDestroy()
    }

    /**
     * TST-TRK-095.1 (ATT-9498): Verifies handleActionStop() with no active workout still satisfies
     * a pending unpromoted FGS start before calling performStopSelf(), and stale Workout 1 teardown
     * is suppressed when Workout 2 has already called markForegroundStartRequested() (Guardrail #3).
     */
    @Test
    fun testActionStopPendingFgsGuard_andStaleTeardownSuppressedWhenNewFgsStartPending_TST_TRK_095_1() {
        val events = mutableListOf<String>()
        val mockApp = mockk<com.atrainingtracker.trainingtracker.TrainingApplication>(relaxed = true)
        val mockNotif = mockk<android.app.Notification>(relaxed = true)
        every { mockApp.searchingAndTrackingNotification } returns mockNotif

        val trackedService = object : TestableTrackerService() {
            override fun performStartForeground(id: Int, notification: android.app.Notification?, foregroundServiceType: Int) {
                events.add("startForeground")
            }
            override fun performStopForeground() {
                events.add("stopForeground")
                super.performStopForeground()
            }
            override fun performStopSelf() {
                events.add("stopSelf")
                super.performStopSelf()
            }
        }
        val appField = TrackerService::class.java.getDeclaredField("mTrainingApplication").apply { isAccessible = true }
        appField.set(trackedService, mockApp)

        // (A) ACTION_STOP received when mWorkoutID<=0 && mBaseFileName==null while FGS start is pending
        TrackerService.markForegroundStartRequested()
        trackedService.handleActionStop()
        assertEquals(listOf("startForeground", "stopForeground", "stopSelf"), events)

        // (B) Stale Workout 1 posted main-thread teardown suppressed when Workout 2 sets sFgsStartPending
        val postedMainRunnables = mutableListOf<Runnable>()
        val teardownService = object : TestableTrackerService() {
            override fun runOnMainThread(action: Runnable) {
                postedMainRunnables.add(action)
            }
        }
        val idField = TrackerService::class.java.getDeclaredField("mWorkoutID").apply { isAccessible = true }
        idField.set(teardownService, 55L)
        val fnField = TrackerService::class.java.getDeclaredField("mBaseFileName").apply { isAccessible = true }
        fnField.set(teardownService, "2026-11-22_w1")
        val execField = TrackerService::class.java.getDeclaredField("mDbExecutor").apply { isAccessible = true }
        val inlineExec = mockk<ExecutorService>(relaxed = true)
        every { inlineExec.execute(any()) } answers { firstArg<Runnable>().run() }
        execField.set(teardownService, inlineExec)

        teardownService.handleActionStop()
        assertEquals(1, postedMainRunnables.size)

        // Workout 2 triggers startForegroundService() BEFORE Workout 1's posted teardown runs!
        TrackerService.markForegroundStartRequested()
        postedMainRunnables[0].run()

        assertFalse(
            "Stale Workout 1 teardown MUST NOT call performStopSelf() while Workout 2 FGS start is pending",
            teardownService.performStopSelfCalled
        )
        assertFalse(
            "Stale Workout 1 teardown MUST NOT call performStopForeground() while Workout 2 FGS start is pending",
            teardownService.performStopForegroundCalled
        )
        teardownService.onDestroy()
        trackedService.onDestroy()
    }
}


