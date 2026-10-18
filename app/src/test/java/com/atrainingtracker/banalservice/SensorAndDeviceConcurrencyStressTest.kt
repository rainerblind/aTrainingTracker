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

package com.atrainingtracker.banalservice

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.BatteryDevice
import com.atrainingtracker.banalservice.devices.ClockDevice
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.filters.FilterData
import com.atrainingtracker.banalservice.filters.FilterManager
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSession
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Multi-threaded concurrency and stress verification suite for the sensor, device, filter,
 * and live workout session layers (`REQ-TRK-032`, `TST-TRK-024`, `ATT-3988`).
 */
class SensorAndDeviceConcurrencyStressTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    private class FakeRemoteDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceType: DeviceType,
        deviceId: Long
    ) : MyRemoteDevice(context, sensorManager, deviceType, deviceId) {
        override fun addSensors() {
            addSensor(MySensor<Int>(this, SensorType.HR))
            addSensor(MySensor<Double>(this, SensorType.SPEED_mps))
        }

        override fun getProtocol(): Protocol = Protocol.BLUETOOTH_LE

        override fun startSearching() {
            // no-op in unit test
        }

        override fun notifySensorsChanged() {
            // no-op in unit test to avoid Android Intent construction overhead
        }

        override fun shutDown() {
            // Unregister sensors without calling Android Context.unregisterReceiver on a MockK mock from worker threads
            unregisterSensors()
        }

        override fun isReceivingData(): Boolean = true

        override fun getName(): String = "FakeDevice-$deviceId"
    }

    private class HarnessDeviceManager(
        banalService: BANALService,
        sensorManager: MySensorManager
    ) : DeviceManager(banalService, sensorManager) {
        init {
            mClockDevice?.shutDown()
            mClockDevice = null
            mBatteryDevice?.shutDown()
            mBatteryDevice = null
        }

        fun registerFakeRemoteDevice(device: MyRemoteDevice): Boolean {
            val existing = mMyRemoteDevices.putIfAbsent(device.deviceId, device)
            if (existing != null) {
                return false
            }
            mSearchQueue.addFirst(device)
            mMyRemoteDeviceTries[device] = 2
            mNewlyFoundDevices.add(device.deviceId)
            return true
        }

        fun pollAndRemoveDevice(deviceId: Long) {
            mSearchQueue.pollFirst()
            removeAndStopRemoteDevice(deviceId)
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false
        every { TrainingApplication.isPaused() } returns false
        every { TrainingApplication.isAutoPaused() } returns false
        every { TrainingApplication.searchOnlyForSportSpecificDevices() } returns false

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockLocationManager = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"

        mockkConstructor(BatteryDevice::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-TRK-024.1: Verifies that concurrent device discovery/registration, search queue polling,
     * newly found device list snapshots, and filter creation/polling across 8 worker threads
     * complete with zero [ConcurrentModificationException] or [NullPointerException].
     */
    @Test
    fun `TST-TRK-024_1 concurrent DeviceManager and FilterManager operations are thread-safe`() {
        val sensorManager = MySensorManager(mockBanalService)
        val deviceManager = HarnessDeviceManager(mockBanalService, sensorManager)
        val filterManager = FilterManager(mockBanalService, deviceManager, sensorManager)

        val hrSensor = MySensor<Int>(sensorManager, SensorType.HR)
        sensorManager.registerSensor(hrSensor)

        val threadCount = 8
        val iterations = 100
        // Pre-instantiate FakeRemoteDevice instances on the main thread so MockK static mocks
        // in MyDevice/MyRemoteDevice constructors are not contended inside the worker threads.
        val preallocatedDevices = Array(threadCount) { t ->
            Array(iterations) { i ->
                val deviceId = ((t * iterations) + i + 1).toLong()
                FakeRemoteDevice(mockBanalService, sensorManager, DeviceType.HRM, deviceId)
            }
        }

        // Release all MockK bytecode hooks before launching multi-threaded workers so MockK's
        // non-thread-safe invocation recorder is never contended across worker threads.
        unmockkAll()

        val executor = Executors.newFixedThreadPool(threadCount)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)
        val errors = CopyOnWriteArrayList<Throwable>()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    for (i in 0 until iterations) {
                        val fakeDevice = preallocatedDevices[t][i]
                        val deviceId = fakeDevice.deviceId
                        deviceManager.registerFakeRemoteDevice(fakeDevice)

                        // Concurrent read snapshots and iterations
                        for (dev in deviceManager.remoteDeviceList) {
                            assertNotNull(dev)
                        }
                        for (activeId in deviceManager.databaseIdsOfActiveRemoteDevices) {
                            assertNotNull(activeId)
                        }
                        for (sensor in deviceManager.getSensorList(SensorType.HR)) {
                            assertNotNull(sensor)
                        }

                        val newlyFoundSnapshot = deviceManager.idsOfNewlyFoundDevices
                        // Mutating the returned defensive snapshot must not throw CME or corrupt internal list
                        newlyFoundSnapshot.clear()

                        // Concurrent FilterManager operations
                        val filterData = FilterData(
                            if (i % 2 == 0) null else "FakeDevice-$deviceId",
                            SensorType.HR,
                            FilterType.AVERAGE,
                            1.0
                        )
                        filterManager.createFilter(filterData)
                        filterManager.getFilteredSensorData(filterData)
                        for (filtered in filterManager.allFilteredSensorData) {
                            assertNotNull(filtered)
                        }

                        if (i % 5 == 0) {
                            filterManager.resetAllFilters()
                        }
                        if (i % 3 == 0) {
                            deviceManager.pollAndRemoveDevice(deviceId)
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneGate.countDown()
                }
            }
        }

        startGate.countDown()
        assertTrue("Stress test timed out", doneGate.await(15, TimeUnit.SECONDS))
        executor.shutdownNow()

        if (errors.isNotEmpty()) {
            throw AssertionError("Concurrent DeviceManager/FilterManager failures: ${errors.first()}", errors.first())
        }
    }

    /**
     * TST-TRK-024.2: Verifies that concurrent [MySensor] listener registration/removal/dispatch,
     * [ProxySensor] source swapping, [MyAccumulatorSensor] accumulation across 8 threads, and
     * re-entrant [MySensorManager] registration callbacks complete without deadlock,
     * [ConcurrentModificationException], or lost numeric increments.
     */
    @Test
    fun `TST-TRK-024_2 concurrent MySensor ProxySensor MyAccumulatorSensor and MySensorManager stress test`() {
        val sensorManager = MySensorManager(mockBanalService)
        val deviceA = FakeRemoteDevice(mockBanalService, sensorManager, DeviceType.RUN_SPEED, 101L)
        val deviceB = FakeRemoteDevice(mockBanalService, sensorManager, DeviceType.SPEED_AND_LOCATION_GPS, 102L)

        val sourceSensorA = MySensor<Double>(deviceA, SensorType.SPEED_mps)
        val sourceSensorB = MySensor<Double>(deviceB, SensorType.SPEED_mps)
        val proxySensor = ProxySensor<Double>(sensorManager, SensorType.SPEED_mps, sourceSensorA)

        val accumulatorSensor: MyAccumulatorSensor<Int> = MyIntegerAccumulatorSensor(
            deviceA,
            SensorType.LAP_NR,
            false,
            0
        )

        val threadCount = 8
        val incrementsPerThread = 1000
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)
        val errors = CopyOnWriteArrayList<Throwable>()
        val callbackCounter = AtomicInteger(0)

        // Register a re-entrant listener on proxySensor and SENSORS that queries MySensorManager
        // inside newValue() to verify absence of AB-BA deadlocks.
        val reentrantListener = MySensor.SensorListener<Double> { _ ->
            callbackCounter.incrementAndGet()
            sensorManager.accumulatedSensorTypeSet
            sensorManager.allSensors
        }
        proxySensor.addSensorListener(reentrantListener)

        val gcSensorListener = MySensor.SensorListener<String> { _ ->
            sensorManager.accumulatedSensorTypeSet
            sensorManager.allButBestSensors
        }
        sensorManager.getSensor(SensorType.SENSORS).addSensorListener(gcSensorListener)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    val dynamicListener = MySensor.SensorListener<Double> { _ ->
                        callbackCounter.incrementAndGet()
                    }
                    for (i in 0 until incrementsPerThread) {
                        // 1. Exact atomic accumulation on MyAccumulatorSensor
                        accumulatorSensor.increment(1)

                        // 2. Concurrent listener churn & dispatch on MySensor / ProxySensor
                        if (i % 4 == 0) {
                            proxySensor.addSensorListener(dynamicListener)
                        } else if (i % 4 == 2) {
                            proxySensor.removeSensorListener(dynamicListener)
                        }

                        if (i % 2 == 0) {
                            sourceSensorA.newValue(i.toDouble())
                            proxySensor.setSourceSensor(sourceSensorB)
                        } else {
                            sourceSensorB.newValue(i.toDouble())
                            proxySensor.setSourceSensor(sourceSensorA)
                        }

                        val sensorData = proxySensor.sensorData
                        assertNotNull(sensorData)

                        // 3. Concurrent MySensorManager registration / unregistration / queries
                        if (i % 20 == 0) {
                            val transientSensor = MySensor<Int>(deviceA, SensorType.HR)
                            sensorManager.registerSensor(transientSensor)
                            sensorManager.getSensorList(listOf(SensorType.HR, SensorType.SPEED_mps))
                            sensorManager.unregisterSensor(transientSensor)
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneGate.countDown()
                }
            }
        }

        unmockkAll()
        startGate.countDown()
        assertTrue(
            "Deadlock or timeout detected in sensor concurrency test",
            doneGate.await(15, TimeUnit.SECONDS)
        )
        executor.shutdownNow()

        if (errors.isNotEmpty()) {
            throw AssertionError("Concurrent sensor layer failure: ${errors.first()}", errors.first())
        }

        assertEquals(
            "MyAccumulatorSensor must not lose any concurrent increments",
            Integer.valueOf(threadCount * incrementsPerThread),
            accumulatorSensor.value
        )
        assertTrue("Listeners must have been notified", callbackCounter.get() > 0)
    }

    /**
     * TST-TRK-024.3: Verifies that concurrent 1 Hz sampling ([LiveWorkoutSession.addSample],
     * [LiveWorkoutSession.recordStreamPoint]), retroactive barometric altitude correction
     * ([LiveWorkoutSession.applyAltitudeCorrection]), and snapshot reads complete with zero
     * [ConcurrentModificationException] or [IndexOutOfBoundsException] and preserve mathematical
     * consistency.
     */
    @Test
    fun `TST-TRK-024_3 concurrent LiveWorkoutSession sampling altitude shift and snapshot reads`() {
        val session = LiveWorkoutSession(
            42L,
            Collections.singleton(SensorType.ALTITUDE)
        )

        val threadCount = 8
        val iterationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)
        val errors = CopyOnWriteArrayList<Throwable>()

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    for (i in 0 until iterationsPerThread) {
                        val pos = LatLng(48.137 + (i * 0.0001), 11.576 + (i * 0.0001))
                        when (t % 3) {
                            0 -> {
                                // Sampling thread
                                session.addSample(SensorType.ALTITUDE, 500.0, pos)
                                session.recordStreamPoint(pos, 500.0, (i * 5).toDouble())
                            }
                            1 -> {
                                // Barometric correction thread (net shift 0 over pairs of +10 / -10)
                                val offset = if (i % 2 == 0) 10.0 else -10.0
                                session.applyAltitudeCorrection(offset)
                            }
                            else -> {
                                // Snapshot reader thread iterating returned lists and maps
                                for (pt in session.rawLatLngs) {
                                    assertNotNull(pt)
                                }
                                for (pt in session.sampledLatLngs) {
                                    assertNotNull(pt)
                                }
                                for (alt in session.sampledAltitudes) {
                                    assertTrue(alt.isFinite())
                                }
                                for (dist in session.sampledDistances) {
                                    assertTrue(dist.isFinite())
                                }
                                for ((type, stats) in session.sensorStats) {
                                    assertNotNull(type)
                                    assertTrue(stats.average.isFinite())
                                    assertTrue(stats.count >= 0)
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneGate.countDown()
                }
            }
        }

        unmockkAll()
        startGate.countDown()
        assertTrue("LiveWorkoutSession stress test timed out", doneGate.await(15, TimeUnit.SECONDS))
        executor.shutdownNow()

        if (errors.isNotEmpty()) {
            throw AssertionError("Concurrent LiveWorkoutSession failure: ${errors.first()}", errors.first())
        }

        // Verify deterministic altitude shift accuracy on a clean session
        val deterministicSession = LiveWorkoutSession(99L, Collections.singleton(SensorType.ALTITUDE))
        for (i in 0 until 20) {
            deterministicSession.addSample(SensorType.ALTITUDE, 100.0, LatLng(48.0, 11.0))
            deterministicSession.recordStreamPoint(LatLng(48.0, 11.0), 100.0, i.toDouble())
        }
        deterministicSession.applyAltitudeCorrection(25.0)
        val altStats = deterministicSession.sensorStats[SensorType.ALTITUDE]
        assertNotNull(altStats)
        assertEquals(125.0, altStats!!.min, 1e-6)
        assertEquals(125.0, altStats.max, 1e-6)
        assertEquals(125.0, altStats.average, 1e-6)
        for (sampledAlt in deterministicSession.sampledAltitudes) {
            assertEquals(125.0, sampledAlt, 1e-6)
        }
    }
}
