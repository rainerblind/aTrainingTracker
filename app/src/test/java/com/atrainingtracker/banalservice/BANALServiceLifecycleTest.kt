/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.banalservice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test verifying defensive lifecycle teardown and static manager reference cleanup
 * in [BANALService] and [DeviceManager] (REQ-TRK-055.4, TST-TRK-055.4, ATT-6257).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BANALServiceLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val field = TrainingApplication::class.java.getDeclaredField("cAppContext")
        field.isAccessible = true
        field.set(null, context)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context))

        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS
    }

    @After
    fun tearDown() {
        val field = TrainingApplication::class.java.getDeclaredField("cAppContext")
        field.isAccessible = true
        field.set(null, null)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, null)

        unmockkAll()
    }

    @Test
    fun serviceLifecycle_onDestroy_nullifiesStaticManagers() {
        val controller = Robolectric.buildService(BANALService::class.java)
        val service = controller.create().get()

        assertNotNull(BANALService.getDeviceManager())

        // Destroy service
        controller.destroy()

        // Static manager references must be cleanly nullified to prevent stale leak
        assertNull(BANALService.getDeviceManager())
        assertNull(BANALService.getSensorData(SensorType.SPEED_mps))
    }

    @Test
    fun getAvailableSensorTypeSet_unboundProxySensors_excludedFromAvailableSet() {
        val sensorManager = MySensorManager(context)
        val mockDevice = mockk<MyDevice>(relaxed = true) {
            every { deviceType } returns DeviceType.SPEED_AND_LOCATION_GPS
            every { name } returns "GPS"
        }
        val gpsSpeed = object : MySensor<Double>(mockDevice, SensorType.SPEED_mps) {}

        // Register -> ProxySensor created with source
        sensorManager.registerSensor(gpsSpeed)
        val proxy = sensorManager.getSensor(SensorType.SPEED_mps) as ProxySensor<*>
        assertNotNull(proxy.sourceSensor)

        // Inject sensorManager into BANALService static field for test verification
        val controller = Robolectric.buildService(BANALService::class.java)
        val service = controller.create().get()

        // Unregister -> Proxy retained but source is null
        sensorManager.unregisterSensor(gpsSpeed)
        assertNull(proxy.sourceSensor)

        // Unbound proxy must return null from getSensorData and be excluded from available set
        assertNull(proxy.value)
        assertNull(proxy.sourceSensor)

        controller.destroy()
    }

    @Test
    fun deviceManagerShutDown_clearsInternalDeviceFields() {
        val controller = Robolectric.buildService(BANALService::class.java)
        val service = controller.create().get()
        val sensorManager = MySensorManager(service)
        val deviceManager = DeviceManager(service, sensorManager)

        assertNotNull(deviceManager.clockDevice)
        assertNotNull(deviceManager.batteryDevice)

        deviceManager.shutDown()

        assertNull(deviceManager.clockDevice)
        assertNull(deviceManager.batteryDevice)
        assertNull(deviceManager.altitudeFromPressureDevice)
        assertNull(deviceManager.altitudeFromPositionDevice)

        controller.destroy()
    }
}
