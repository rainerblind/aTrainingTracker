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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice
import com.atrainingtracker.banalservice.devices.ClockDevice
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.batterysaver.BatterySaverController
import com.atrainingtracker.trainingtracker.batterysaver.BatterySaverStateMachine
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Consolidated robustness and lifecycle resilience unit test suite for the tracking subsystem
 * enforcing REQ-TRK-039 (TST-TRK-039, ATT-4032).
 */
class TrackingRobustnessTest {

    private class TestableBANALService : BANALService() {
        var capturedBroadcastIntent: Intent? = null

        override fun getPackageName(): String = "com.atrainingtracker"

        override fun sendBroadcast(intent: Intent) {
            capturedBroadcastIntent = intent
        }

        public override fun newLap(isPause: Boolean) {
            super.newLap(isPause)
        }
    }

    private class ConcreteTestDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long
    ) : MyDevice(context, sensorManager, DeviceType.HRM) {
        init {
            mDeviceId = deviceId
        }

        override fun addSensors() {
            // No-op for unit test
        }
    }

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockSportTypeDbManager: SportTypeDatabaseManager
    private val intentExtras = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        intentExtras.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenNewLap() } returns false
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.startSearchWhenUserChangesSport() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false
        every { TrainingApplication.isTracking() } returns false
        every { TrainingApplication.isPaused() } returns false

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockSportTypeDbManager = mockk(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDbManager
        every { SportTypeDatabaseManager.getDefaultSportTypeId() } returns 1L
        every { SportTypeDatabaseManager.getSportTypeId(any()) } returns 1L

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers {
            intentExtras[firstArg()] = secondArg<Double>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } answers {
            intentExtras[firstArg()] = secondArg<Int>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers {
            intentExtras[firstArg()] = secondArg<String>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers {
            intentExtras[firstArg()] = secondArg<Boolean>()
            self as Intent
        }
        every { anyConstructed<Intent>().getDoubleExtra(any<String>(), any<Double>()) } answers {
            (intentExtras[firstArg()] as? Double) ?: secondArg()
        }
        every { anyConstructed<Intent>().getIntExtra(any<String>(), any<Int>()) } answers {
            (intentExtras[firstArg()] as? Int) ?: secondArg()
        }
        every { anyConstructed<Intent>().getStringExtra(any<String>()) } answers {
            intentExtras[firstArg()] as? String
        }
        every { anyConstructed<Intent>().getBooleanExtra(any<String>(), any<Boolean>()) } answers {
            (intentExtras[firstArg()] as? Boolean) ?: secondArg()
        }

        val mockHardwareSensorManager = mockk<SensorManager>(relaxed = true)
        val mockPressureSensor = mockk<Sensor>(relaxed = true)
        every { mockHardwareSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressureSensor

        val mockLocationManager = mockk<LocationManager>(relaxed = true)

        mockContext = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockHardwareSensorManager
        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockContext.packageName } returns "com.atrainingtracker"

        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        setBanalStaticField("cSensorManager", null)
        setBanalStaticField("cDeviceManager", null)
        unmockkAll()
    }

    private fun setBanalStaticField(fieldName: String, value: Any?) {
        try {
            val field: Field = BANALService::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // TST-TRK-039.1: BANALService Defensive Numerics & List Boundary Hardening
    // =========================================================================

    @Test
    fun testBanalServiceNewLap_withZeroLapTime_computesZeroSpeedWithoutNaNOrInfinity() {
        val service = TestableBANALService()

        val mockSensors = mockk<MySensorManager>(relaxed = true)
        setBanalStaticField("cSensorManager", mockSensors)

        val lapNrSensor = mockk<MySensor<*>>(relaxed = true)
        every { lapNrSensor.sensorData } returns SensorData(SensorType.LAP_NR, 2, "2", "clock")
        every { mockSensors.getSensor(SensorType.LAP_NR) } returns lapNrSensor

        val lapTimeSensor = mockk<MySensor<*>>(relaxed = true)
        every { lapTimeSensor.sensorData } returns SensorData(SensorType.TIME_LAP, 0, "00:00:00", "clock")
        every { mockSensors.getSensor(SensorType.TIME_LAP) } returns lapTimeSensor

        val lapDistSensor = mockk<MySensor<*>>(relaxed = true)
        every { lapDistSensor.sensorData } returns SensorData(SensorType.DISTANCE_m_LAP, 250.0, "250.0", "gps")
        every { mockSensors.getSensor(SensorType.DISTANCE_m_LAP) } returns lapDistSensor

        service.newLap(false)

        assertNotNull("Broadcast intent must be emitted", service.capturedBroadcastIntent)
        val speed = intentExtras[BANALService.PREV_LAP_SPEED_mps] as? Double
        assertNotNull("PREV_LAP_SPEED_mps must be present in broadcast", speed)
        assertFalse("Lap speed must not be NaN when lapTime_s == 0", speed!!.isNaN())
        assertFalse("Lap speed must not be Infinite when lapTime_s == 0", speed.isInfinite())
        assertEquals("Lap speed must be 0.0 m/s for zero duration lap", 0.0, speed, 0.0001)
    }

    @Test
    fun testBanalServiceNewLap_withPositiveLapTime_computesAccurateLapSpeed() {
        val service = TestableBANALService()

        val mockSensors = mockk<MySensorManager>(relaxed = true)
        setBanalStaticField("cSensorManager", mockSensors)

        val lapNrSensor = mockk<MySensor<*>>(relaxed = true)
        every { lapNrSensor.sensorData } returns SensorData(SensorType.LAP_NR, 1, "1", "clock")
        every { mockSensors.getSensor(SensorType.LAP_NR) } returns lapNrSensor

        val lapTimeSensor = mockk<MySensor<*>>(relaxed = true)
        every { lapTimeSensor.sensorData } returns SensorData(SensorType.TIME_LAP, 20, "00:00:20", "clock")
        every { mockSensors.getSensor(SensorType.TIME_LAP) } returns lapTimeSensor

        val lapDistSensor = mockk<MySensor<*>>(relaxed = true)
        every { lapDistSensor.sensorData } returns SensorData(SensorType.DISTANCE_m_LAP, 100.0, "100.0", "gps")
        every { mockSensors.getSensor(SensorType.DISTANCE_m_LAP) } returns lapDistSensor

        service.newLap(false)

        val speed = intentExtras[BANALService.PREV_LAP_SPEED_mps] as? Double
        assertNotNull(speed)
        assertEquals(5.0, speed!!, 0.0001) // 100m / 20s = 5.0 m/s
    }

    @Test
    fun testBanalServiceGetSportTypeId_withEmptyList_fallsBackToDefaultSportTypeId() {
        val service = TestableBANALService()
        every { mockSportTypeDbManager.getSportTypesIdList(any(), any()) } returns emptyList()

        val sportTypeId = service.getSportTypeId(5.0)

        assertEquals("Empty sport type list must fallback to default sport type ID", 1L, sportTypeId)
    }

    @Test
    fun testBanalServiceGetSportTypeId_withNullList_fallsBackToDefaultSportTypeId() {
        val service = TestableBANALService()
        every { mockSportTypeDbManager.getSportTypesIdList(any(), any()) } returns null

        val sportTypeId = service.getSportTypeId(5.0)

        assertEquals("Null sport type list must fallback to default sport type ID", 1L, sportTypeId)
    }

    @Test
    fun testBanalServiceGetSportTypeId_withNonEmptyList_returnsFirstMatchingSportTypeId() {
        val service = TestableBANALService()
        every { mockSportTypeDbManager.getSportTypesIdList(any(), any()) } returns listOf(42L, 99L)

        val sportTypeId = service.getSportTypeId(5.0)

        assertEquals("Non-empty list must return the first sport type ID", 42L, sportTypeId)
    }

    // =========================================================================
    // TST-TRK-039.2: MyDevice Name Caching & TrackerService Rate Limiting
    // =========================================================================

    @Test
    fun testMyDevice_inMemoryDeviceNameCachingAndInvalidation() {
        every { mockDevicesDbManager.getDeviceName(201L) } returns "Initial HRM Name"

        val device = ConcreteTestDevice(mockContext, mockSensorManager, 201L)

        // First resolution: reads from SQLite database
        val name1 = device.name
        assertEquals("Initial HRM Name", name1)
        verify(exactly = 1) { mockDevicesDbManager.getDeviceName(201L) }

        // Simulate database value change without cache invalidation
        every { mockDevicesDbManager.getDeviceName(201L) } returns "Renamed HRM in DB"

        // Second resolution: returns cached value, skipping SQLite query
        val name2 = device.name
        assertEquals("Initial HRM Name", name2)
        verify(exactly = 1) { mockDevicesDbManager.getDeviceName(201L) }

        // Invalidate cache explicitly
        device.invalidateDeviceNameCache()

        // Third resolution: queries database again and receives updated name
        val name3 = device.name
        assertEquals("Renamed HRM in DB", name3)
        verify(exactly = 2) { mockDevicesDbManager.getDeviceName(201L) }
    }

    @Test
    fun testTrackerServiceRateLimitsErrorLogging() {
        // Initial burst: 1..3
        assertTrue("Error #1 must be logged", TrackerService.shouldLogSampleInsertFailure(1))
        assertTrue("Error #2 must be logged", TrackerService.shouldLogSampleInsertFailure(2))
        assertTrue("Error #3 must be logged", TrackerService.shouldLogSampleInsertFailure(3))

        // Suppressed interval: 4..59
        for (i in 4..59) {
            assertFalse("Error #$i must be suppressed", TrackerService.shouldLogSampleInsertFailure(i))
        }

        // Periodic log: 60
        assertTrue("Error #60 must be logged", TrackerService.shouldLogSampleInsertFailure(60))

        // Suppressed interval: 61..119
        for (i in 61..119) {
            assertFalse("Error #$i must be suppressed", TrackerService.shouldLogSampleInsertFailure(i))
        }

        // Periodic log: 120
        assertTrue("Error #120 must be logged", TrackerService.shouldLogSampleInsertFailure(120))

        // Non-positive counts
        assertFalse("Error #0 must not be logged", TrackerService.shouldLogSampleInsertFailure(0))
        assertFalse("Negative error count must not be logged", TrackerService.shouldLogSampleInsertFailure(-1))
    }

    // =========================================================================
    // TST-TRK-039.3: ClockDevice Rate Limiting & Monotonic Elapsed Time
    // =========================================================================

    @Test
    fun testClockDeviceRateLimitsErrorLogging() {
        assertTrue("Clock error #1 must be logged", ClockDevice.shouldLogClockTimerError(1))
        assertTrue("Clock error #2 must be logged", ClockDevice.shouldLogClockTimerError(2))
        assertTrue("Clock error #3 must be logged", ClockDevice.shouldLogClockTimerError(3))

        for (i in 4..59) {
            assertFalse("Clock error #$i must be suppressed", ClockDevice.shouldLogClockTimerError(i))
        }

        assertTrue("Clock error #60 must be logged", ClockDevice.shouldLogClockTimerError(60))

        for (i in 61..119) {
            assertFalse("Clock error #$i must be suppressed", ClockDevice.shouldLogClockTimerError(i))
        }

        assertTrue("Clock error #120 must be logged", ClockDevice.shouldLogClockTimerError(120))
        assertFalse(ClockDevice.shouldLogClockTimerError(0))
        assertFalse(ClockDevice.shouldLogClockTimerError(-10))
    }

    @Test
    fun testClockDeviceMonotonicTimeAdvancement() {
        val t1 = ClockDevice.getMonotonicTimeMs()
        assertTrue("Monotonic timestamp must be non-negative", t1 >= 0L)

        val device = ClockDevice(mockContext, mockSensorManager, false)
        assertEquals(0L, device.lastTickElapsedRealtimeMs)

        device.runClockTickForTesting()
        val tickTime = device.lastTickElapsedRealtimeMs
        assertTrue("Tick timestamp must be anchored to monotonic time", tickTime > 0L)
        assertTrue("Tick timestamp must be >= initial timestamp", tickTime >= t1)

        device.shutDown()
    }

    // =========================================================================
    // TST-TRK-039.4: Teardown Hygiene, Receiver Excision & Scope Cancellation
    // =========================================================================

    @Test
    fun testAltitudeFromPressureDeviceShutDown_doesNotRegisterReceiver() {
        val device = AltitudeFromPressureDevice(mockContext, mockSensorManager)

        // Construction registers 2 receivers (MyDevice accumulator receiver + GPS provider receiver)
        verify(exactly = 2) { ContextCompat.registerReceiver(any(), any(), any(), any()) }

        device.shutDown()

        // shutDown() must NOT register any receiver; call count remains exactly 2
        verify(exactly = 2) { ContextCompat.registerReceiver(any(), any(), any(), any()) }
        assertFalse("Device must not be calibrated after shutdown", device.isCalibrated)
    }

    @Test
    fun testBatterySaverControllerRelease_cancelsCoroutineScope() {
        val controller = BatterySaverController(
            activity = null,
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            stateMachine = BatterySaverStateMachine(),
            brightnessApplier = {}
        )

        assertTrue("CoroutineScope must be active initially", controller.scope.isActive)
        controller.release()
        assertFalse("CoroutineScope must be cancelled after release", controller.scope.isActive)
    }

    @Test
    fun testDeviceManagerCreatePairedRemoteDevices_closesCursorDeterministically() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        every { mockDevicesDbManager.database } returns mockDb

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToNext() } returns false
        every { mockDb.query(any(), any(), any(), any(), any(), any(), any()) } returns mockCursor

        val mockBanal = mockk<BANALService>(relaxed = true)
        every { mockBanal.getSystemService(Context.LOCATION_SERVICE) } returns mockk<LocationManager>(relaxed = true)
        every { mockBanal.getSystemService(Context.SENSOR_SERVICE) } returns mockk<SensorManager>(relaxed = true)
        every { mockBanal.packageName } returns "com.atrainingtracker"

        val deviceManager = DeviceManager(mockBanal, mockSensorManager)
        deviceManager.createPairedRemoteDevices(Protocol.BLUETOOTH_LE)

        verify(exactly = 1) { mockCursor.close() }
    }
}
