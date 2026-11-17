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

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_GPS
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEBikePowerDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarSource
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Software Integration Verification Test: Hardware HAL to Services (ASPICE SWE.5,
 * INT-SWE-04, IF-SWE-04, REQ-PRO-110.4, TST-INT-012, ATT-8155).
 *
 * Verifies:
 * 1. Bluetooth Low Energy GATT byte stream ingestion (Heart Rate 0x2A37, Cycling Power 0x2A63, Radar 6a4e3204 V1 & V2).
 * 2. Multi-RR interval unpacking and cumulative beat-timestamp reconstruction from raw bytes.
 * 3. Location HAL fix ingestion via SpeedAndLocationDevice_GPS into MySensorManager sensors.
 * 4. Real BTLERadarDevice watchdog timeout debounce (>3.5s) transitioning RadarStateRepository to DISCONNECTED_ERROR.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardwareHalIngestionIntegrationTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    private class TestBTLEHeartRateDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEHeartRateDevice(context, sensorManager, deviceId, address) {
        override fun notifySensorsChanged() {}
        fun registerForTest() = super.registerSensors()
    }

    private class TestBTLEBikePowerDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEBikePowerDevice(context, sensorManager, deviceId, address) {
        override fun addSensors() {
            addPowerSensor()
            addPowerBalanceSensor()
            addCadenceSensor()
            mIsPowerBalanceSupported = true
            mIsCrankRevolutionDataSupported = true
        }

        override fun notifySensorsChanged() {}
        fun registerForTest() = super.registerSensors()

        fun ingestMeasurement(characteristic: BluetoothGattCharacteristic) {
            measurementCharacteristicUpdate(null, characteristic)
        }

        fun getPowerValue(): Int? = mPowerSensor?.value
        fun getPowerBalanceValue(): Double? = mPowerBalanceSensor?.value
        fun getCadenceValue(): Double? = mCadenceSensor?.value
    }

    private class TestBTLERadarDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLERadarDevice(context, sensorManager, deviceId, address) {
        override fun notifySensorsChanged() {}

        fun connectForTest(gatt: BluetoothGatt) {
            configureServices(gatt)
            setStateForTesting(State.CONNECTED_WITH_SERVICE)
        }

        fun setConnectedStateForTest() {
            setStateForTesting(State.CONNECTED_WITH_SERVICE)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        RadarStateRepository.reset()
        RadarLightController.reset()
        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        RadarLightController.reset()
        RadarStateRepository.reset()
        sensorManager.shutDown()
        context.deleteDatabase("Devices.db")
        unmockkAll()
    }

    /**
     * TST-INT-012.1: Verifies raw Bluetooth SIG Heart Rate (0x2A37) GATT packet ingestion
     * and multi-RR interval unpacking into RrBeatEvents and MySensorManager.
     */
    @Test
    fun testBtleHeartRate_gattPacketIngestion_multiRrUnpacking() {
        val hrDevice = TestBTLEHeartRateDevice(context, sensorManager, 101L, "AA:BB:CC:DD:EE:01")
        hrDevice.registerForTest()
        val receivedBeats = mutableListOf<RrBeatEvent>()
        hrDevice.addRrBeatListener { receivedBeats.add(it) }

        // Packet 1: flag = 0x10 (UINT8 HR, RR present), HR = 75 bpm, 1 RR interval (819 / 1024 * 1000 ~= 799.8 ms)
        val packet1 = byteArrayOf(
            0x10.toByte(), 75.toByte(),
            0x33.toByte(), 0x03.toByte()
        )
        hrDevice.parseHeartRateMeasurement(packet1, 100_000L)

        assertEquals(75, hrDevice.heartRateSensor.value)
        assertEquals(75, sensorManager.getSensor(SensorType.HR).value)
        assertEquals(1, receivedBeats.size)
        assertEquals(ProtocolType.BLE, receivedBeats[0].protocol)
        assertEquals(100_000L, receivedBeats[0].beatTimestampMs)

        // Packet 2: Bundled 2 RR intervals (500 ms each: 512 raw = 0x0200)
        receivedBeats.clear()
        val packet2 = byteArrayOf(
            0x10.toByte(), 130.toByte(),
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x02.toByte()
        )
        hrDevice.parseHeartRateMeasurement(packet2, 101_000L)

        assertEquals(130, hrDevice.heartRateSensor.value)
        assertEquals(130, sensorManager.getSensor(SensorType.HR).value)
        assertEquals(2, receivedBeats.size)
        assertEquals(500.0, receivedBeats[0].rrMs, 0.01)
        assertEquals(500.0, receivedBeats[1].rrMs, 0.01)
    }

    /**
     * TST-INT-012.2: Verifies raw Bluetooth SIG Cycling Power (0x2A63) GATT packet ingestion
     * for instantaneous power, pedal power balance, and crank-revolution cadence into MySensorManager.
     */
    @Test
    fun testBtleBikePower_gattPacketIngestion_powerBalanceAndCadence() {
        val powerDevice = TestBTLEBikePowerDevice(context, sensorManager, 102L, "AA:BB:CC:DD:EE:02")
        powerDevice.registerForTest()

        val charUuid = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        val characteristic = BluetoothGattCharacteristic(
            charUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )

        // Flags = 0x0021 (Bit 0: Pedal Power Balance Present, Bit 5: Crank Revolution Data Present)
        // Packet 1: Power = 250 W (0x00FA), Raw Balance = 104 (52.0% Right), Crank Rev = 10, Crank Time = 1024 (1.0s)
        characteristic.setValue(
            byteArrayOf(
                0x21.toByte(), 0x00.toByte(),
                0xFA.toByte(), 0x00.toByte(),
                104.toByte(),
                10.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x04.toByte()
            )
        )
        powerDevice.ingestMeasurement(characteristic)

        assertEquals(250, powerDevice.getPowerValue())
        assertEquals(250, sensorManager.getSensor(SensorType.POWER).value)
        assertEquals(52.0, powerDevice.getPowerBalanceValue() ?: -1.0, 0.01)

        // Packet 2: Power = 280 W (0x0118), Raw Balance = 96 (48.0%), Crank Rev = 13 (+3 revs), Crank Time = 3072 (+2048 = 2.0s) -> 90 rpm
        characteristic.setValue(
            byteArrayOf(
                0x21.toByte(), 0x00.toByte(),
                0x18.toByte(), 0x01.toByte(),
                96.toByte(),
                13.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x0C.toByte()
            )
        )
        powerDevice.ingestMeasurement(characteristic)

        assertEquals(280, powerDevice.getPowerValue())
        assertEquals(280, sensorManager.getSensor(SensorType.POWER).value)
        assertEquals(48.0, powerDevice.getPowerBalanceValue() ?: -1.0, 0.01)
        assertEquals(90.0, powerDevice.getCadenceValue() ?: -1.0, 0.01)
        assertEquals(90.0, (sensorManager.getSensor(SensorType.CADENCE).value as Number).toDouble(), 0.01)
    }

    /**
     * TST-INT-012.3: Verifies raw radar GATT characteristic packet ingestion (V1 and V2)
     * decoding into structured RadarTarget objects and propagation into RadarStateRepository.
     */
    @Test
    fun testBtleRadar_gattPacketIngestion_v1AndV2ThreatClassification() {
        // V1 Threat Packet: header=0x00, id=1, range=45m, byte3=(threat=1 << 4) | (speed=5) = 0x15
        val v1Payload = byteArrayOf(0x00, 0x01.toByte(), 45.toByte(), 0x15.toByte())
        val v1Targets = BTLERadarDevice.parseV1ThreatPacket(v1Payload)
        assertNotNull("V1 radar packet must be parsed into targets", v1Targets)
        assertEquals(1, v1Targets?.size)
        assertEquals(1, v1Targets?.first()?.targetId)
        assertEquals(45.0f, v1Targets?.first()?.rangeMeters ?: 0f, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, v1Targets?.first()?.threatLevel)

        // V2 Multi-target packet: header 0x01 + 2 * 8-byte targets
        val v2Payload = byteArrayOf(
            0x01,
            0x01, 0x76.toByte(), 0x06.toByte(), 0x96.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xFF.toByte(), 0x0A.toByte(),
            0x02, 0x5E.toByte(), 0x01.toByte(), 0x64.toByte(), 0x00.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x35.toByte()
        )
        val v2Targets = BTLERadarDevice.parseV2ThreatPacket(v2Payload)
        assertNotNull("V2 radar packet must be parsed into targets", v2Targets)
        assertEquals(2, v2Targets?.size)
        assertEquals(ThreatLevel.FAST_APPROACHING, v2Targets?.get(0)?.threatLevel)
        assertEquals(VehicleSizeCategory.TRUCK_OR_BUS, v2Targets?.get(0)?.vehicleSizeCategory)
        assertEquals(165.4f, v2Targets?.get(0)?.rangeMeters ?: 0f, 0.01f)
        assertEquals(ThreatLevel.APPROACHING, v2Targets?.get(1)?.threatLevel)

        // Feed parsed V2 targets into real BTLERadarDevice -> RadarStateRepository
        val radarDevice = TestBTLERadarDevice(context, sensorManager, 103L, "DC:2C:26:01:02:03")
        radarDevice.setConnectedStateForTest()
        radarDevice.processTargetsForTesting(v2Targets)
        assertEquals(2, RadarStateRepository.getState().targets.size)

        // Guard against empty / malformed payloads
        assertNull("Empty V1 bytes must safely return null", BTLERadarDevice.parseV1ThreatPacket(byteArrayOf()))
        assertNull("Empty V2 bytes must safely return null", BTLERadarDevice.parseV2ThreatPacket(byteArrayOf()))
    }

    /**
     * TST-INT-012.4: Verifies Location HAL ingestion via SpeedAndLocationDevice_GPS.onNewLocation()
     * populating LATITUDE, LONGITUDE, SPEED_mps, and DISTANCE_m sensors in MySensorManager.
     */
    @Test
    fun testLocationHal_onNewLocation_updatesSpeedAndLocationSensors() {
        val appField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appField.isAccessible = true
        appField.set(null, context)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        val locationDevice = SpeedAndLocationDevice_GPS(context, sensorManager)

        val loc1 = Location("gps").apply {
            latitude = 48.1370
            longitude = 11.5750
            altitude = 520.0
            accuracy = 5.0f
            speed = 8.0f
            time = 1_000_000L
        }
        val loc2 = Location("gps").apply {
            latitude = 48.1372
            longitude = 11.5750
            altitude = 521.0
            accuracy = 5.0f
            speed = 11.1f
            time = 1_002_000L
        }

        locationDevice.onNewLocation(loc1)
        locationDevice.onNewLocation(loc2)

        val lat = sensorManager.getSensor(SensorType.LATITUDE)?.value as? Double
        val lon = sensorManager.getSensor(SensorType.LONGITUDE)?.value as? Double
        val spd = (sensorManager.getSensor(SensorType.SPEED_mps)?.value as? Number)?.toDouble()
        val dist = (sensorManager.getSensor(SensorType.DISTANCE_m)?.value as? Number)?.toDouble()

        assertNotNull("LATITUDE sensor must be populated", lat)
        assertNotNull("LONGITUDE sensor must be populated", lon)
        assertNotNull("SPEED_mps sensor must be populated", spd)
        assertNotNull("DISTANCE_m sensor must be populated", dist)
        assertEquals(48.1372, lat!!, 0.0001)
        assertEquals(11.5750, lon!!, 0.0001)
        assertTrue("SPEED_mps must reflect motion (> 0)", spd!! > 0.0)
        assertTrue("DISTANCE_m must accumulate between fixes (> 0)", dist!! > 0.0)
    }

    /**
     * TST-INT-012.5: Verifies real BTLERadarDevice watchdog timeout debounce (WATCHDOG_TIMEOUT_MS == 3500ms)
     * transitions RadarStateRepository to DISCONNECTED_ERROR and clears isReceivingData when packets cease.
     */
    @Test
    fun testSensorDropout_realWatchdogTimeoutDebounce() {
        assertEquals(3_500L, BTLERadarDevice.WATCHDOG_TIMEOUT_MS)

        val radarDevice = TestBTLERadarDevice(context, sensorManager, 104L, "DC:2C:26:01:02:04")
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockRadarService = mockk<BluetoothGattService>(relaxed = true)
        every { mockGatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) } returns mockRadarService

        radarDevice.connectForTest(mockGatt)
        assertTrue("Connected BLE radar must report isReceivingData == true", radarDevice.isReceivingData)

        // Execute real production watchdog timeout runnable
        radarDevice.watchdogRunnableForTesting.run()

        assertFalse("isReceivingData must return false after watchdog timeout", radarDevice.isReceivingData)
        assertEquals(
            "RadarStateRepository must transition to DISCONNECTED_ERROR on watchdog timeout",
            RadarConnectionState.DISCONNECTED_ERROR,
            RadarStateRepository.getState().connectionState
        )
        assertEquals(RadarSource.BLE, RadarStateRepository.getState().source)
    }
}
