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

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.getIconId
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
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

/**
 * Behavioral tests for internal barometric altitude sensor registration, self-healing DB recovery,
 * pairing toggle control, and device icon mapping (REQ-DAT-006, REQ-CON-010, REQ-UI-119).
 */
// Linked test specs: TST-BUG-008, TST-BUG-009, TST-BUG-010, TST-BUG-011
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PressureSensorRegistrationAndIconTest {

    private lateinit var context: Context
    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockPressureSensor: Sensor
    private lateinit var mockLocationManager: LocationManager
    private val capturedReceivers = mutableListOf<Pair<BroadcastReceiver, IntentFilter>>()

    @Before
    fun setUp() {
        capturedReceivers.clear()
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )

        DeviceDataRepository.resetForTesting()
        DevicesDatabaseManager.resetForTesting()
        context.deleteDatabase(DevicesDatabaseManager.DevicesDbHelper.DB_NAME)

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns true

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.registerReceiver(any(), any(), any(), any())
        } answers {
            val receiver = secondArg<BroadcastReceiver>()
            val filter = thirdArg<IntentFilter>()
            capturedReceivers.add(receiver to filter)
            null
        }

        mockPressureSensor = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressureSensor

        mockLocationManager = mockk(relaxed = true)
        every { mockLocationManager.allProviders } returns emptyList()

        mockSensorManager = mockk(relaxed = true)

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.applicationContext } returns context
        every { mockBanalService.packageName } returns context.packageName
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getString(R.string.devices_altitude_from_pressure) } returns "Barometer"
        every { mockBanalService.getString(R.string.devices_altitude_from_position) } returns "GPS Altitude"
    }

    @After
    fun tearDown() {
        DeviceDataRepository.resetForTesting()
        DevicesDatabaseManager.resetForTesting()
        context.deleteDatabase(DevicesDatabaseManager.DevicesDbHelper.DB_NAME)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        unmockkAll()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    /**
     * TST-BUG-008 & TST-BUG-011 (REQ-DAT-006): Deleting the ALTITUDE_FROM_PRESSURE row from
     * Devices.db self-heals on DeviceManager initialization when hardware pressure sensor exists,
     * registering the device eagerly as paired without requiring manual search.
     */
    @Test
    fun testPressureSensor_deletedDbRowSelfHealsAndInstantiatesOnStartup_TST_BUG_008_TST_BUG_011() {
        val devicesDb = DevicesDatabaseManager.getInstance(context)
        val initialId = devicesDb.altitudeFromPressureDeviceId
        if (initialId > 0L) {
            devicesDb.deleteDevice(initialId)
        }
        assertEquals(
            "Precondition: ALTITUDE_FROM_PRESSURE must be absent from Devices table",
            -1L,
            devicesDb.altitudeFromPressureDeviceId
        )

        val repo = DeviceDataRepository.getInstance(context)
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager, repo)

        val healedId = devicesDb.altitudeFromPressureDeviceId
        assertTrue("ALTITUDE_FROM_PRESSURE row must self-heal into Devices table", healedId > 0L)
        assertTrue("Self-healed pressure sensor must default to paired", repo.isPaired(healedId))
        assertNotNull(
            "AltitudeFromPressureDevice must instantiate eagerly after self-healing",
            deviceManager.altitudeFromPressureDevice
        )
        verify(atLeast = 1) {
            mockAndroidSensorManager.registerListener(
                deviceManager.altitudeFromPressureDevice,
                mockPressureSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    /**
     * TST-BUG-009 (REQ-CON-010): Deactivating the paired pressure sensor shuts down the active
     * AltitudeFromPressureDevice and unregisters hardware listener; reactivating creates it again.
     */
    @Test
    fun testPressureSensor_pairingToggleStopsAndRestartsSensorUpdates_TST_BUG_009() {
        val devicesDb = DevicesDatabaseManager.getInstance(context)
        val repo = DeviceDataRepository.getInstance(context)
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager, repo)

        val deviceId = devicesDb.altitudeFromPressureDeviceId
        assertTrue(deviceId > 0L)
        val initialDevice = deviceManager.altitudeFromPressureDevice
        assertNotNull("AltitudeFromPressureDevice must start active when paired", initialDevice)

        val pairingReceiver = capturedReceivers.firstOrNull { (_, filter) ->
            filter.hasAction(BANALService.PAIRING_CHANGED)
        }?.first
        assertNotNull("DeviceManager must register mPairingChangedReceiver", pairingReceiver)

        // Deactivate sensor
        pairingReceiver!!.onReceive(
            mockBanalService,
            Intent(BANALService.PAIRING_CHANGED)
                .putExtra(BANALService.DEVICE_ID, deviceId)
                .putExtra(BANALService.PAIRED, false)
        )
        assertNull(
            "AltitudeFromPressureDevice must shut down and clear when unpaired",
            deviceManager.altitudeFromPressureDevice
        )
        verify(exactly = 1) {
            mockAndroidSensorManager.unregisterListener(initialDevice)
        }

        // Reactivate sensor
        pairingReceiver.onReceive(
            mockBanalService,
            Intent(BANALService.PAIRING_CHANGED)
                .putExtra(BANALService.DEVICE_ID, deviceId)
                .putExtra(BANALService.PAIRED, true)
        )
        val restartedDevice = deviceManager.altitudeFromPressureDevice
        assertNotNull(
            "AltitudeFromPressureDevice must recreate when re-paired",
            restartedDevice
        )
        verify(atLeast = 1) {
            mockAndroidSensorManager.registerListener(
                restartedDevice,
                mockPressureSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    /**
     * TST-BUG-010 (REQ-UI-119): Verifies specialized altitude iconography for smartphone internal
     * pressure sensor instead of generic phone icon.
     */
    @Test
    fun testPressureSensor_usesAltitudeIconResource_TST_BUG_010() {
        assertEquals(
            R.drawable.ic_altitude,
            getIconId(DeviceType.ALTITUDE_FROM_PRESSURE, Protocol.SMARTPHONE)
        )
    }
}
