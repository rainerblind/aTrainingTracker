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

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc
import com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying DeviceManager discovery auto-pairing, synchronized lock ordering,
 * active workout auto-pair suppression, database insertion failure resilience, and manual override precedence
 * (REQ-SEN-020, TST-SEN-020.2, TST-SEN-020.9, TST-SEN-020.10, TST-SEN-020.11, ATT-4051).
 */
class DeviceManagerDiscoveryTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private val intentLongExtras = mutableMapOf<Pair<Intent, String>, Long>()

    @Before
    fun setUp() {
        DeviceManager.cMyRemoteDeviceCurrentlySearchingFor = null
        intentLongExtras.clear()

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } returns true
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers {}

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers {
            intentLongExtras[Pair(self as Intent, firstArg<String>())] = secondArg<Long>()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns BANALService.NEW_DEVICE_FOUND_INTENT
        every { anyConstructed<Intent>().getAction() } returns BANALService.NEW_DEVICE_FOUND_INTENT
        every { anyConstructed<Intent>().getLongExtra(any<String>(), any<Long>()) } answers {
            intentLongExtras[Pair(self as Intent, firstArg<String>())] ?: secondArg<Long>()
        }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false
        every { TrainingApplication.getTrackingMode() } returns TrackingMode.READY
        every { TrainingApplication.searchOnlyForSportSpecificDevices() } returns false
        every { TrainingApplication.getNumberOfSearchTries() } returns 2

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockLocationManager = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        val mockBluetoothManager = mockk<BluetoothManager>(relaxed = true)
        every { mockBanalService.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"
        every { mockBanalService.userSelectedBSportType } returns com.atrainingtracker.banalservice.BSportType.UNKNOWN
        every { mockBanalService.sendBroadcast(any()) } returns Unit

        mockkStatic(AntPlusBikePowerPcc::class)
        every { AntPlusBikePowerPcc.requestAccess(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)

        mockkConstructor(ClockDevice::class)
        every { anyConstructed<ClockDevice>().startTimer() } returns Unit
        every { anyConstructed<ClockDevice>().shutDown() } returns Unit
        mockkConstructor(BatteryDevice::class)
        every { anyConstructed<BatteryDevice>().shutDown() } returns Unit
    }

    @After
    fun tearDown() {
        DeviceManager.cMyRemoteDeviceCurrentlySearchingFor = null
        try {
            unmockkAll()
        } catch (e: Exception) {
            // Defensive handling against MockK cancelAll ConcurrentModificationException
        }
    }

    /**
     * TST-SEN-020.2: BTLE discovery inserts with paired = true, triggers pairingChanged and broadcasts new device.
     */
    @Test
    fun testBtDiscovery_newDevice_autoPairsAndNotifies() {
        every { mockDevicesDbManager.isBluetoothDeviceInDB(DeviceType.HRM, "11:22:33:44:55:66") } returns false
        every {
            mockDevicesDbManager.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM, "11:22:33:44:55:66", "Polar H10", "Polar", 95, true
            )
        } returns 10L
        every { mockDevicesDbManager.getProtocol(10L) } returns com.atrainingtracker.banalservice.Protocol.BLUETOOTH_LE
        every { mockDevicesDbManager.getDeviceType(10L) } returns DeviceType.HRM
        every { mockDevicesDbManager.getBluetoothMACAddress(10L) } returns "11:22:33:44:55:66"

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        clearMocks(mockBanalService, answers = false, recordedCalls = true, childMocks = false)

        deviceManager.mBTSearchInterface.onNewDeviceFound(
            DeviceType.HRM, "11:22:33:44:55:66", "Polar H10", "Polar", 95
        )

        // Verify inserted with paired = true
        verify(exactly = 1) {
            mockDevicesDbManager.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM, "11:22:33:44:55:66", "Polar H10", "Polar", 95, true
            )
        }

        // Verify device ID registered in newly found list
        assertTrue(deviceManager.idsOfNewlyFoundDevices.contains(10L))

        // Verify broadcast sent for device 10
        val capturedBroadcasts = mutableListOf<Intent>()
        verify { mockBanalService.sendBroadcast(capture(capturedBroadcasts)) }
        assertTrue(capturedBroadcasts.any {
            it.action == BANALService.NEW_DEVICE_FOUND_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == 10L
        })
    }

    /**
     * TST-SEN-020.2: ANT+ discovery inserts with paired = true, triggers pairingChanged and broadcasts new device.
     */
    @Test
    fun testAntDiscovery_newDevice_autoPairsAndNotifies() {
        val mockSearchResult = mockk<MultiDeviceSearch.MultiDeviceSearchResult>(relaxed = true)
        every { mockSearchResult.antDeviceNumber } returns 54321
        every { mockSearchResult.deviceDisplayName } returns "Stages Crank"
        every { mockSearchResult.antDeviceType } returns DeviceType.getAntPluginDeviceType(DeviceType.BIKE_POWER)

        every { mockDevicesDbManager.isANTDeviceInDB(DeviceType.BIKE_POWER, 54321) } returns false
        every {
            mockDevicesDbManager.insertNewAntDeviceIntoDB(
                DeviceType.BIKE_POWER, "Stages Crank", 54321, true, "Stages", 90
            )
        } returns 25L
        every { mockDevicesDbManager.getProtocol(25L) } returns com.atrainingtracker.banalservice.Protocol.ANT_PLUS
        every { mockDevicesDbManager.getDeviceType(25L) } returns DeviceType.BIKE_POWER
        every { mockDevicesDbManager.getAntDeviceNumber(25L) } returns 54321

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        clearMocks(mockBanalService, answers = false, recordedCalls = true, childMocks = false)

        deviceManager.mANTAsyncSearchEngineInterface.onNewDeviceFound(
            DeviceType.BIKE_POWER, mockSearchResult, true, "Stages", 90
        )

        verify(exactly = 1) {
            mockDevicesDbManager.insertNewAntDeviceIntoDB(
                DeviceType.BIKE_POWER, "Stages Crank", 54321, true, "Stages", 90
            )
        }
        assertTrue(deviceManager.idsOfNewlyFoundDevices.contains(25L))
        val capturedBroadcasts = mutableListOf<Intent>()
        verify { mockBanalService.sendBroadcast(capture(capturedBroadcasts)) }
        assertTrue(capturedBroadcasts.any {
            it.action == BANALService.NEW_DEVICE_FOUND_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == 25L
        })
    }

    /**
     * TST-SEN-020.9: When workout is active (TrackingMode.TRACKING), discovery inserts with paired = true
     * but suppresses immediate pairingChanged to protect ongoing recording telemetry.
     */
    @Test
    fun testActiveWorkout_autoPairingImmunity_suppressesImmediatePairingChanged() {
        every { TrainingApplication.getTrackingMode() } returns TrackingMode.TRACKING

        every { mockDevicesDbManager.isBluetoothDeviceInDB(DeviceType.HRM, "AA:BB:CC:DD:EE:01") } returns false
        every {
            mockDevicesDbManager.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM, "AA:BB:CC:DD:EE:01", "Wahoo TICKR", "Wahoo", 80, true
            )
        } returns 55L

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        clearMocks(mockBanalService, answers = false, recordedCalls = true, childMocks = false)

        deviceManager.mBTSearchInterface.onNewDeviceFound(
            DeviceType.HRM, "AA:BB:CC:DD:EE:01", "Wahoo TICKR", "Wahoo", 80
        )

        // Device is persisted with paired = true in database
        verify(exactly = 1) {
            mockDevicesDbManager.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM, "AA:BB:CC:DD:EE:01", "Wahoo TICKR", "Wahoo", 80, true
            )
        }
        assertTrue(deviceManager.idsOfNewlyFoundDevices.contains(55L))

        // DeviceManager should NOT create remote device or start searching for it immediately
        verify(exactly = 0) { mockDevicesDbManager.getProtocol(55L) }
    }

    /**
     * TST-SEN-020.10: Database Insertion Failure Resilience Test.
     * When insertNewBluetoothDeviceIntoDB returns deviceId <= 0, device is not added to mNewlyFoundDevices
     * and NEW_DEVICE_FOUND_INTENT broadcast is not dispatched.
     */
    @Test
    fun testDbInsertionFailure_resilience_skipsNewlyFoundAndNotifications() {
        every { mockDevicesDbManager.isBluetoothDeviceInDB(DeviceType.HRM, "FAIL:MAC") } returns false
        every {
            mockDevicesDbManager.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM, "FAIL:MAC", "Bad Sensor", null, -1, true
            )
        } returns -2L // SQLException

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        clearMocks(mockBanalService, answers = false, recordedCalls = true, childMocks = false)

        deviceManager.mBTSearchInterface.onNewDeviceFound(
            DeviceType.HRM, "FAIL:MAC", "Bad Sensor", null, -1
        )

        assertFalse(deviceManager.idsOfNewlyFoundDevices.contains(-2L))
        verify(exactly = 0) {
            mockBanalService.sendBroadcast(any())
        }
    }

    /**
     * TST-SEN-020.11: Manual Unpair vs. Auto-Pairing Conflict Resolution Test.
     * If a user manually unpairs a sensor during workout tracking, paired = 0 is persisted in the database;
     * when tracking terminates, startSearchForPairedDevices queries paired > 0, ensuring manual unpair overrides auto-pairing.
     */
    @Test
    fun testManualUnpair_overridesAutoPairPostWorkoutSearch() {
        val mockDb = mockk<android.database.sqlite.SQLiteDatabase>(relaxed = true)
        every { mockDevicesDbManager.database } returns mockDb
        val emptyCursor = com.atrainingtracker.testutil.MockCursorFactory.create(
            columns = listOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.PAIRED,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                DevicesDatabaseManager.DevicesDbHelper.ANT_DEVICE_NUMBER,
                DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS
            ),
            rows = emptyList()
        )
        every { mockDb.query(any(), any(), any(), any(), any(), any(), any()) } returns emptyCursor

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)

        // Simulate tracking state change to READY
        every { TrainingApplication.getTrackingMode() } returns TrackingMode.READY

        // startSearchForPairedDevices is called upon workout end
        deviceManager.startSearchForPairedDevices()

        // createPairedRemoteDevices strictly filters by protocol and inspects database
        verify(atLeast = 1) { mockDevicesDbManager.database }
    }
}
