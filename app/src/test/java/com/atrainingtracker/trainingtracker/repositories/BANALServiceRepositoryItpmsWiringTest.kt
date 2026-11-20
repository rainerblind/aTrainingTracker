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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.repositories

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.google.android.gms.maps.model.LatLng
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic behavioral unit tests for iTPMS persisted baseline wiring, live reset propagation,
 * zero-baseline overwrite, null/non-finite GNSS gating, and GPS-free dual-wheel isolation
 * in [BANALServiceRepository] and [DeviceDataRepository] (REQ-SEN-034, TST-SEN-034, ATT-8421).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositoryItpmsWiringTest {

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
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(R.string.itpms_low_pressure_reminder_title) } returns "Low Tire Pressure"
        every { mockContext.getString(R.string.itpms_low_pressure_reminder_message, any()) } answers {
            "Low pressure ${secondArg<Array<Any>>().firstOrNull()}"
        }
        every { mockContext.getString(R.string.itpms_slow_puncture_alert_title) } returns "Slow Puncture"
        every { mockContext.getString(R.string.itpms_slow_puncture_alert_message, any()) } answers {
            "Slow puncture ${secondArg<Array<Any>>().firstOrNull()}"
        }
        every { mockContext.getString(R.string.itpms_dual_puncture_rear_title) } returns "Rear Wheel Pressure Loss!"
        every { mockContext.getString(R.string.itpms_dual_puncture_rear_message, any()) } answers {
            "Rear puncture ${secondArg<Array<Any>>().firstOrNull()}"
        }
        every { mockContext.getString(R.string.itpms_dual_puncture_front_title) } returns "Front Wheel Pressure Loss!"
        every { mockContext.getString(R.string.itpms_dual_puncture_front_message, any()) } answers {
            "Front puncture ${secondArg<Array<Any>>().firstOrNull()}"
        }

        WorkoutNavigationEvents.consumeTpmsAlert()
        BANALServiceRepository.resetForTesting(null)
        DeviceDataRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)

        repository = BANALServiceRepository.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutNavigationEvents.consumeTpmsAlert()
        BANALServiceRepository.resetForTesting(null)
        DeviceDataRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createWheelDevice(
        deviceId: Long,
        calibrationMeters: Double,
        distanceProvider: () -> Double
    ): MyRemoteDevice {
        val device = mockk<MyRemoteDevice>(relaxed = true)
        val distanceSensor = mockk<MySensor<Double>>(relaxed = true)
        every { distanceSensor.value } answers { distanceProvider() }
        every { device.deviceId } returns deviceId
        every { device.deviceType } returns DeviceType.BIKE_SPEED
        every { device.calibrationFactor } returns calibrationMeters
        every { device.getSensor(SensorType.DISTANCE_m) } returns distanceSensor
        return device
    }

    // REQ-SEN-034.1 / TST-SEN-034.1
    @Test
    fun testPersistedBaselineLoaded_triggersLowPressureAlertAndDeduplicatesColdStartIo_tstSen034_1() = runTest(testDispatcher) {
        val deviceId = 42L
        val loadCount = AtomicInteger(0)
        repository.configureItpmsForTesting(
            loader = { id ->
                assertEquals(deviceId, id)
                loadCount.incrementAndGet()
                2.130
            },
            ioDispatcher = testDispatcher
        )

        var wheelDist = 0.0
        val calibration = 2.130
        val effectiveCirc = 2.115 // -0.70% drop below 2.130 m baseline (>= 0.6% threshold)
        val wheelDevice = createWheelDevice(deviceId, calibration) { wheelDist }

        val metersPerDegLat = 111194.92664455873
        val stepMeters = 10.0
        var lat = 48.0
        var ts = 1_000_000L

        // Feed 3 rapid 1-Hz ticks before IO completes: pendingBaselineLoads must deduplicate to 1 IO load
        repeat(3) {
            repository.processItpmsTelemetryTick(
                wheelDevices = listOf(wheelDevice),
                location = LatLng(lat, 11.0),
                accuracyMeters = 3.0,
                bearingDeg = 0.0,
                speedMps = stepMeters,
                altitudeMeters = 500.0,
                yawRateRadPerSec = 0.0,
                timestampMs = ts
            )
            lat += stepMeters / metersPerDegLat
            wheelDist += (stepMeters / effectiveCirc) * calibration
            ts += 1000L
        }

        testScheduler.advanceUntilIdle()
        assertEquals("Cold-start cache miss must deduplicate concurrent IO loads", 1, loadCount.get())
        val estimator = repository.perDeviceItpmsEstimators[deviceId]
        assertNotNull(estimator)
        assertEquals(2.130, estimator!!.baselineCircumferenceMeters, 1e-6)

        // Continue straight-line gated riding until > 2.0 km start lock
        repeat(210) {
            repository.processItpmsTelemetryTick(
                wheelDevices = listOf(wheelDevice),
                location = LatLng(lat, 11.0),
                accuracyMeters = 3.0,
                bearingDeg = 0.0,
                speedMps = stepMeters,
                altitudeMeters = 500.0,
                yawRateRadPerSec = 0.0,
                timestampMs = ts
            )
            lat += stepMeters / metersPerDegLat
            wheelDist += (stepMeters / effectiveCirc) * calibration
            ts += 1000L
        }

        assertTrue("Estimator must lock C_start after >= 2.0 km", estimator.isStartLocked)
        val alert = WorkoutNavigationEvents.tpmsAlert.replayCache.lastOrNull()
        assertNotNull("Persisted baseline 2.130 m vs 2.115 m C_start must fire LowPressure alert", alert)
        assertEquals(WorkoutNavigationEvents.TpmsAlertSeverity.WARNING, alert!!.severity)
        assertEquals("Low Tire Pressure", alert.title)
        assertTrue("Drop percentage should be ~0.7%", alert.dropPercentage >= 0.6)
    }

    // REQ-SEN-034.1 / TST-SEN-034.1
    @Test
    fun testLiveResetSignalsZeroOverwriteAndErrorPreservation_tstSen034_1() = runTest(testDispatcher) {
        val deviceId = 77L
        val currentDbBaseline = AtomicReference<Result<Double>>(Result.success(2.135))
        repository.configureItpmsForTesting(
            loader = { currentDbBaseline.get().getOrThrow() },
            ioDispatcher = testDispatcher
        )

        var wheelDist = 0.0
        val wheelDevice = createWheelDevice(deviceId, 2.135) { wheelDist }
        repository.processItpmsTelemetryTick(
            wheelDevices = listOf(wheelDevice),
            location = LatLng(48.0, 11.0),
            accuracyMeters = 3.0,
            bearingDeg = 0.0,
            speedMps = 8.0,
            altitudeMeters = 500.0,
            yawRateRadPerSec = 0.0,
            timestampMs = 1_000L
        )
        testScheduler.advanceUntilIdle()

        val estimator = repository.perDeviceItpmsEstimators[deviceId]!!
        assertEquals(2.135, estimator.baselineCircumferenceMeters, 1e-6)

        // 1. Reactive SharedFlow notification updates live cached estimator
        currentDbBaseline.set(Result.success(2.142))
        DeviceDataRepository.notifyBaselineCircumferenceChanged(deviceId)
        testScheduler.advanceUntilIdle()
        assertEquals(2.142, estimator.baselineCircumferenceMeters, 1e-6)

        // 2. CALIBRATION_FACTOR_CHANGED broadcast with specific DEVICE_ID updates live estimator idempotently
        currentDbBaseline.set(Result.success(2.128))
        val specificIntent = mockk<Intent>(relaxed = true)
        every { specificIntent.getLongExtra(BANALService.DEVICE_ID, -1L) } returns deviceId
        repository.handleCalibrationFactorChangedBroadcast(specificIntent)
        testScheduler.advanceUntilIdle()
        assertEquals(2.128, estimator.baselineCircumferenceMeters, 1e-6)

        // 3. Zero and non-finite DB baselines MUST explicitly overwrite stale non-zero baseline to 0.0
        currentDbBaseline.set(Result.success(0.0))
        val fallbackIntent = mockk<Intent>(relaxed = true)
        every { fallbackIntent.getLongExtra(BANALService.DEVICE_ID, -1L) } returns -1L
        repository.handleCalibrationFactorChangedBroadcast(fallbackIntent)
        testScheduler.advanceUntilIdle()
        assertEquals(0.0, estimator.baselineCircumferenceMeters, 1e-6)

        currentDbBaseline.set(Result.success(2.150))
        repository.clearBreadcrumbs()
        testScheduler.advanceUntilIdle()
        assertEquals(2.150, estimator.baselineCircumferenceMeters, 1e-6)

        currentDbBaseline.set(Result.success(Double.NaN))
        DeviceDataRepository.notifyBaselineCircumferenceChanged(deviceId)
        testScheduler.advanceUntilIdle()
        assertEquals(0.0, estimator.baselineCircumferenceMeters, 1e-6)

        // 4. Transient DB loader Throwable must NOT overwrite a valid cached baseline
        currentDbBaseline.set(Result.success(2.140))
        repository.refreshItpmsBaselineForDevice(deviceId)
        testScheduler.advanceUntilIdle()
        assertEquals(2.140, estimator.baselineCircumferenceMeters, 1e-6)

        currentDbBaseline.set(Result.failure(IllegalStateException("Transient DB lock")))
        repository.clearBreadcrumbs()
        testScheduler.advanceUntilIdle()
        assertEquals("Loader failure must preserve existing baseline", 2.140, estimator.baselineCircumferenceMeters, 1e-6)
    }

    // REQ-SEN-034.2 / TST-SEN-034.2
    @Test
    fun testNullOrNonFiniteGnssAccuracyOrBearing_skipsIndirectTpmsWithoutFabricatingDefaults_tstSen034_2() = runTest(testDispatcher) {
        val deviceId = 91L
        repository.configureItpmsForTesting(
            loader = { 2.130 },
            ioDispatcher = testDispatcher
        )

        var wheelDist = 0.0
        val wheelDevice = createWheelDevice(deviceId, 2.130) { wheelDist }
        val metersPerDegLat = 111194.92664455873
        var lat = 48.0
        var ts = 10_000L

        val invalidPairs = listOf(
            null to 0.0,
            Double.NaN to 0.0,
            -1.0 to 0.0,
            Double.POSITIVE_INFINITY to 0.0,
            3.0 to null,
            3.0 to Double.NaN,
            3.0 to Double.NEGATIVE_INFINITY
        )

        // Repeat well past 2.0 km equivalent travel with null/invalid accuracy or bearing
        repeat(250) { idx ->
            val (acc, bearing) = invalidPairs[idx % invalidPairs.size]
            repository.processItpmsTelemetryTick(
                wheelDevices = listOf(wheelDevice),
                location = LatLng(lat, 11.0),
                accuracyMeters = acc,
                bearingDeg = bearing,
                speedMps = 10.0,
                altitudeMeters = 500.0,
                yawRateRadPerSec = 0.0,
                timestampMs = ts
            )
            lat += 10.0 / metersPerDegLat
            wheelDist += 10.2
            ts += 1000L
        }

        testScheduler.advanceUntilIdle()
        assertFalse(
            "Null or non-finite GNSS accuracy/bearing must skip GNSS IndirectTpmsEstimator entirely",
            repository.perDeviceItpmsEstimators.containsKey(deviceId)
        )
        assertNull(WorkoutNavigationEvents.tpmsAlert.replayCache.lastOrNull())
    }

    // REQ-SEN-034.2 / TST-SEN-034.2
    @Test
    fun testNullLocationAccuracyBearing_dualWheelTpmsStillDetectsRearPunctureGpsFree_tstSen034_2() = runTest(testDispatcher) {
        val frontId = 201L
        val rearId = 202L
        repository.configureItpmsForTesting(
            loader = { 2.105 },
            ioDispatcher = testDispatcher
        )
        repository.setCachedWheelPosition(frontId, WheelPosition.FRONT, equipmentId = 10L)
        repository.setCachedWheelPosition(rearId, WheelPosition.REAR, equipmentId = 10L)

        var frontDist = 0.0
        var rearDist = 0.0
        val frontDevice = createWheelDevice(frontId, 2.105) { frontDist }
        val rearDevice = createWheelDevice(rearId, 2.105) { rearDist }

        var ts = 50_000L
        // Phase 1: 40 ticks at 10 m/s = 400 m (> 300 m lock) with NO GNSS fix, accuracy, or bearing
        repeat(40) {
            repository.processItpmsTelemetryTick(
                wheelDevices = listOf(frontDevice, rearDevice),
                location = null,
                accuracyMeters = null,
                bearingDeg = null,
                speedMps = 10.0,
                altitudeMeters = null,
                yawRateRadPerSec = 0.0,
                timestampMs = ts
            )
            frontDist += 10.0
            rearDist += 10.0
            ts += 1000L
        }

        assertTrue("Dual-wheel ratio must lock even with null GNSS", repository.dualWheelTpmsEstimator.isStartLocked)
        assertTrue(
            "Section 1 GNSS estimators must remain untouched when location/acc/bearing are null",
            repository.perDeviceItpmsEstimators.isEmpty()
        )

        // Phase 2: Rear tire deflates by 1.5% -> rear revolutions increase by ~1.52% over 300 m
        repeat(35) {
            repository.processItpmsTelemetryTick(
                wheelDevices = listOf(frontDevice, rearDevice),
                location = null,
                accuracyMeters = null,
                bearingDeg = null,
                speedMps = 10.0,
                altitudeMeters = null,
                yawRateRadPerSec = 0.0,
                timestampMs = ts
            )
            frontDist += 10.0
            rearDist += 10.16
            ts += 1000L
        }

        val alert = WorkoutNavigationEvents.tpmsAlert.replayCache.lastOrNull()
        assertNotNull("GPS-free DualWheelTpmsEstimator must detect rear puncture when GNSS is absent", alert)
        assertEquals(WorkoutNavigationEvents.TpmsAlertSeverity.CRITICAL, alert!!.severity)
        assertEquals("Rear Wheel Pressure Loss!", alert.title)
        assertTrue("GNSS IndirectTpmsEstimator must still be skipped", repository.perDeviceItpmsEstimators.isEmpty())
    }

    // REQ-SEN-034.3 / TST-SEN-034.3
    @Test
    fun testBaselineCircumferenceChangedFlow_isBufferedAndDeliversWithoutDropping_tstSen034_3() = runTest(testDispatcher) {
        val observedIds = mutableListOf<Long>()
        val targetId = 505L
        repository.configureItpmsForTesting(
            loader = { id ->
                observedIds.add(id)
                2.111
            },
            ioDispatcher = testDispatcher
        )

        // Emit burst of notifications on buffered SharedFlow (extraBufferCapacity = 64)
        repeat(10) {
            DeviceDataRepository.notifyBaselineCircumferenceChanged(targetId)
        }
        testScheduler.advanceUntilIdle()

        assertEquals(10, observedIds.size)
        assertTrue(observedIds.all { it == targetId })
    }

    // REQ-SEN-034.1 / TST-SEN-034.1
    @Test
    fun testDeviceDataRepositoryResetBranches_emitBaselineChangedToBanalServiceRepository_tstSen034_1() = runTest(testDispatcher) {
        val deviceId = 313L
        val mockDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        val mockSqlDb = mockk<android.database.sqlite.SQLiteDatabase>(relaxed = true)
        every { mockDbManager.database } returns mockSqlDb
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDbManager

        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        mockkStatic(EquipmentDbHelper::class)
        every { EquipmentDbHelper.getInstance(any()) } returns mockEqDb

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns mockDiscovery

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } just Runs
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        val currentBaseline = AtomicReference(2.100)
        repository.configureItpmsForTesting(
            loader = { currentBaseline.get() },
            ioDispatcher = testDispatcher
        )
        val wheelDevice = createWheelDevice(deviceId, 2.100) { 0.0 }
        repository.processItpmsTelemetryTick(
            wheelDevices = listOf(wheelDevice),
            location = LatLng(48.0, 11.0),
            accuracyMeters = 3.0,
            bearingDeg = 0.0,
            speedMps = 8.0,
            altitudeMeters = 500.0,
            yawRateRadPerSec = 0.0,
            timestampMs = 1_000L
        )
        testScheduler.advanceUntilIdle()
        val estimator = repository.perDeviceItpmsEstimators[deviceId]!!
        assertEquals(2.100, estimator.baselineCircumferenceMeters, 1e-6)

        val deviceRepo = DeviceDataRepository(mockContext, testDispatcher, testDispatcher)
        try {
            // Branch 1: resetTirePressureBaseline with explicit circumferenceMm > 0
            currentBaseline.set(2.136)
            deviceRepo.resetTirePressureBaseline(deviceId, 2136)
            testScheduler.advanceUntilIdle()
            assertEquals(2.136, estimator.baselineCircumferenceMeters, 1e-6)

            // Branch 2: resetTirePressureBaseline with circumferenceMm == null (silent else-branch)
            currentBaseline.set(2.148)
            deviceRepo.resetTirePressureBaseline(deviceId, null)
            testScheduler.advanceUntilIdle()
            verify { mockDbManager.resetBaselineCircumference(deviceId) }
            assertEquals(2.148, estimator.baselineCircumferenceMeters, 1e-6)
        } finally {
            deviceRepo.cancelScopeForTesting()
        }
    }

    // REQ-SEN-034.1 / TST-SEN-034.1
    @Test
    fun testOutOfOrderAsyncIoLoad_staleCompletionIsDiscardedAndDoesNotOverwriteNewerBaseline_tstSen034_1() = runTest(testDispatcher) {
        val deviceId = 414L
        val slowColdStartDeferred = CompletableDeferred<Double>()
        val callIndex = AtomicInteger(0)
        repository.configureItpmsForTesting(
            loader = {
                when (callIndex.incrementAndGet()) {
                    1 -> slowColdStartDeferred.await() // Older cold-start load stalls on IO
                    else -> 2.145 // Newer explicit refresh completes immediately
                }
            },
            ioDispatcher = testDispatcher
        )

        val wheelDevice = createWheelDevice(deviceId, 2.145) { 0.0 }
        repository.processItpmsTelemetryTick(
            wheelDevices = listOf(wheelDevice),
            location = LatLng(48.0, 11.0),
            accuracyMeters = 3.0,
            bearingDeg = 0.0,
            speedMps = 8.0,
            altitudeMeters = 500.0,
            yawRateRadPerSec = 0.0,
            timestampMs = 1_000L
        )
        // Advance until cold-start IO load #1 is actively suspended on slowColdStartDeferred
        runCurrent()
        val estimator = repository.perDeviceItpmsEstimators[deviceId]!!
        assertEquals(1, callIndex.get())
        assertEquals(0.0, estimator.baselineCircumferenceMeters, 1e-6)

        // Newer explicit refresh completes FIRST with fresh baseline 2.145 m
        DeviceDataRepository.notifyBaselineCircumferenceChanged(deviceId)
        runCurrent()
        assertEquals(2, callIndex.get())
        assertEquals(2.145, estimator.baselineCircumferenceMeters, 1e-6)

        // Stale cold-start IO load #1 finally resolves later with obsolete 2.090 m -> MUST be discarded!
        slowColdStartDeferred.complete(2.090)
        testScheduler.advanceUntilIdle()
        assertEquals(
            "Stale out-of-order IO completion must NOT overwrite newer baseline",
            2.145,
            estimator.baselineCircumferenceMeters,
            1e-6
        )
    }

    // REQ-SEN-034.1 / TST-SEN-034.1
    @Test
    fun testDeviceDataRepositoryUpdateDevice_emitsBaselineChangedAndUpdatesLiveEstimator_tstSen034_1() = runTest(testDispatcher) {
        val deviceId = 515L
        val mockDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        val mockSqlDb = mockk<android.database.sqlite.SQLiteDatabase>(relaxed = true)
        every { mockDbManager.database } returns mockSqlDb
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDbManager

        val mockEqDb = mockk<EquipmentDbHelper>(relaxed = true)
        mockkStatic(EquipmentDbHelper::class)
        every { EquipmentDbHelper.getInstance(any()) } returns mockEqDb

        val mockDiscovery = mockk<EquipmentAndSportTypeDiscoveryManager>(relaxed = true)
        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns mockDiscovery

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } just Runs
        every { anyConstructed<ContentValues>().size() } returns 1

        val currentBaseline = AtomicReference(2.110)
        repository.configureItpmsForTesting(
            loader = { currentBaseline.get() },
            ioDispatcher = testDispatcher
        )
        val wheelDevice = createWheelDevice(deviceId, 2.110) { 0.0 }
        repository.processItpmsTelemetryTick(
            wheelDevices = listOf(wheelDevice),
            location = LatLng(48.0, 11.0),
            accuracyMeters = 3.0,
            bearingDeg = 0.0,
            speedMps = 8.0,
            altitudeMeters = 500.0,
            yawRateRadPerSec = 0.0,
            timestampMs = 1_000L
        )
        testScheduler.advanceUntilIdle()
        val estimator = repository.perDeviceItpmsEstimators[deviceId]!!
        assertEquals(2.110, estimator.baselineCircumferenceMeters, 1e-6)

        val original = DeviceUiData(
            id = deviceId,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.BIKE_SPEED,
            lastSeen = null,
            manufacturer = "Garmin",
            deviceName = "Rear Wheel",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 90,
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = 2110,
            calibrationFactor = null,
            powerFeatures = null,
            baselineCircumference = 2.110,
            isConnected = true,
            mainValue = null,
            allValues = null
        )

        val deviceRepo = DeviceDataRepository(mockContext, testDispatcher, testDispatcher)
        try {
            deviceRepo.seedDevicesForTesting(listOf(original))
            // Only baselineCircumference changes (wheelCircumference stays 2110) -> must still notify BANALServiceRepository
            currentBaseline.set(2.144)
            deviceRepo.updateDevice(original.copy(baselineCircumference = 2.144))
            testScheduler.advanceUntilIdle()

            verify {
                mockSqlDb.update(
                    DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                    any(),
                    "${DevicesDatabaseManager.DevicesDbHelper.C_ID} = ?",
                    arrayOf(deviceId.toString())
                )
            }
            assertEquals(2.144, estimator.baselineCircumferenceMeters, 1e-6)
        } finally {
            deviceRepo.cancelScopeForTesting()
        }
    }
}
