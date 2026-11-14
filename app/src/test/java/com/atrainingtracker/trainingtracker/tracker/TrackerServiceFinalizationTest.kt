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
}


