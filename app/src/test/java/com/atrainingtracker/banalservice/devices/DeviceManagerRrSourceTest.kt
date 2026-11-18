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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Verifies single-primary [RrBeatSource] arbitration (`ECG` > `ANT+` > `PPG`),
 * `5000 ms` stale / disconnect fallback, and monotonic forwarded timestamp clamping
 * in [DeviceManager] (`REQ-TRK-085.1`, `TST-TRK-085.1`).
 */
class DeviceManagerRrSourceTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var deviceManager: DeviceManager

    private class FakeRrBeatSource(
        private val onDispatchListenerCapture: Boolean = true
    ) : RrBeatSource {
        private val listeners = CopyOnWriteArrayList<RrBeatListener>()
        override fun addRrBeatListener(listener: RrBeatListener) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
        override fun removeRrBeatListener(listener: RrBeatListener) {
            listeners.remove(listener)
        }
        fun emit(event: RrBeatEvent) {
            listeners.forEach { it.onRrBeat(event) }
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

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false
        every { TrainingApplication.searchOnlyForSportSpecificDevices() } returns false

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        val mockLocationManager = mockk<LocationManager>(relaxed = true)
        val mockAndroidSensorManager = mockk<SensorManager>(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockSensorManager = mockk(relaxed = true)
        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"
        every { mockBanalService.userSelectedBSportType } returns null

        mockkConstructor(ClockDevice::class)
        mockkConstructor(BatteryDevice::class)

        deviceManager = DeviceManager(mockBanalService, mockSensorManager)
    }

    @After
    fun tearDown() {
        deviceManager.shutDown()
        unmockkAll()
    }

    private fun mockSource(sourceClass: Class<out RrBeatSource>, veritySense: Boolean = false): Pair<RrBeatSource, FakeRrBeatSource> {
        val delegate = FakeRrBeatSource()
        val mockSrc = when (sourceClass) {
            BTLEHeartRateDevice::class.java -> mockk<BTLEHeartRateDevice>(relaxed = true)
            ANTHeartRateDevice::class.java -> mockk<ANTHeartRateDevice>(relaxed = true)
            PolarOnlineStreamingBridge::class.java -> mockk<PolarOnlineStreamingBridge>(relaxed = true).also {
                every { it.isVeritySense } returns veritySense
            }
            else -> error("Unsupported class")
        }
        every { mockSrc.addRrBeatListener(any()) } answers {
            delegate.addRrBeatListener(firstArg())
        }
        every { mockSrc.removeRrBeatListener(any()) } answers {
            delegate.removeRrBeatListener(firstArg())
        }
        return mockSrc to delegate
    }

    @Test
    fun testDualHrmSources_arbitratesSinglePrimaryEcgOverVeritySensePpg() {
        val wallClock = AtomicLong(10_000L)
        deviceManager.setRrClockForTesting { wallClock.get() }

        val (h10Ecg, h10Delegate) = mockSource(BTLEHeartRateDevice::class.java)
        val (verityPpg, verityDelegate) = mockSource(PolarOnlineStreamingBridge::class.java, veritySense = true)

        val forwarded = mutableListOf<RrBeatEvent>()
        deviceManager.addRrBeatListener { forwarded.add(it) }
        deviceManager.registerRrBeatSourceForTesting(verityPpg)
        deviceManager.registerRrBeatSourceForTesting(h10Ecg)

        // Verity Sense emits first while H10 hasn't emitted yet -> temporarily active
        verityDelegate.emit(RrBeatEvent(100_000L, 900.0, 1, ProtocolType.BLE))
        assertSame(verityPpg, deviceManager.activePrimaryRrSourceForTesting)

        // H10 ECG emits -> preempts Verity Sense PPG immediately
        wallClock.addAndGet(200L)
        h10Delegate.emit(RrBeatEvent(100_200L, 800.0, 2, ProtocolType.BLE))
        assertSame(h10Ecg, deviceManager.activePrimaryRrSourceForTesting)

        // Simultaneous interleaved Verity Sense beats within 5s must be dropped
        wallClock.addAndGet(300L)
        verityDelegate.emit(RrBeatEvent(100_500L, 910.0, 3, ProtocolType.BLE))
        wallClock.addAndGet(500L)
        h10Delegate.emit(RrBeatEvent(101_000L, 805.0, 4, ProtocolType.BLE))

        assertEquals(listOf(900.0, 800.0, 805.0), forwarded.map { it.rrMs })
    }

    @Test
    fun testStaleAndDisconnectFallback_switchesToAntOrPpgAndEnforcesMonotonicFloor() {
        val wallClock = AtomicLong(20_000L)
        deviceManager.setRrClockForTesting { wallClock.get() }

        val (h10Ecg, h10Delegate) = mockSource(BTLEHeartRateDevice::class.java)
        val (antHr, antDelegate) = mockSource(ANTHeartRateDevice::class.java)
        val (verityPpg, verityDelegate) = mockSource(PolarOnlineStreamingBridge::class.java, veritySense = true)

        val forwarded = mutableListOf<RrBeatEvent>()
        deviceManager.addRrBeatListener { forwarded.add(it) }
        deviceManager.registerRrBeatSourceForTesting(h10Ecg)
        deviceManager.registerRrBeatSourceForTesting(antHr)
        deviceManager.registerRrBeatSourceForTesting(verityPpg)

        // H10 ECG active at 20_000 ms
        h10Delegate.emit(RrBeatEvent(200_000L, 800.0, 1, ProtocolType.BLE))
        assertSame(h10Ecg, deviceManager.activePrimaryRrSourceForTesting)

        // After 5001 ms stale timeout, ANT+ takes over and backward beat timestamp is clamped to >= 200_000L
        wallClock.addAndGet(5_001L)
        antDelegate.emit(RrBeatEvent(199_570L, 810.0, 2, ProtocolType.ANT_PLUS))
        assertSame(antHr, deviceManager.activePrimaryRrSourceForTesting)
        assertTrue(forwarded.last().beatTimestampMs >= 200_000L)

        // On ANT+ disconnect, Verity Sense PPG takes over immediately
        deviceManager.unregisterRrBeatSourceForTesting(antHr)
        wallClock.addAndGet(500L)
        verityDelegate.emit(RrBeatEvent(205_500L, 820.0, 3, ProtocolType.BLE))
        assertSame(verityPpg, deviceManager.activePrimaryRrSourceForTesting)
        assertEquals(3, forwarded.size)
    }
}
