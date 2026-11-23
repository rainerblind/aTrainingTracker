/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.banalservice.devices

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderApi
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Contract tests verifying Google Fused location provider registration idempotency,
 * deduplication across repeated lifecycle checks, clean teardown unregistration, and exception shielding
 * (REQ-TRK-071, TST-TRK-071, ATT-6683).
 */
class SpeedAndLocationDeviceGoogleFusedTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockGoogleApiClient: GoogleApiClient
    private lateinit var mockFusedLocationApi: FusedLocationProviderApi

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper::class)
        every { com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(any()) } returns 200.0f

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Float>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), isNull<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }

        mockkStatic(TextUtils::class)
        every { TextUtils.equals(any(), any()) } answers {
            firstArg<CharSequence?>() == secondArg<CharSequence?>()
        }
        every { TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.isEmpty()
        }

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ActivityCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSpeedAndLocationGoogleFusedDeviceId() } returns 101L

        mockGoogleApiClient = mockk(relaxed = true)
        every { mockGoogleApiClient.isConnected } returns true
        every { mockGoogleApiClient.isConnecting } returns false

        mockkConstructor(GoogleApiClient.Builder::class)
        every { anyConstructed<GoogleApiClient.Builder>().addApi(any()) } answers { self as GoogleApiClient.Builder }
        every { anyConstructed<GoogleApiClient.Builder>().addConnectionCallbacks(any()) } answers { self as GoogleApiClient.Builder }
        every { anyConstructed<GoogleApiClient.Builder>().addOnConnectionFailedListener(any()) } answers { self as GoogleApiClient.Builder }
        every { anyConstructed<GoogleApiClient.Builder>().build() } returns mockGoogleApiClient

        mockFusedLocationApi = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createDevice(): SpeedAndLocationDevice_GoogleFused {
        val device = SpeedAndLocationDevice_GoogleFused(mockContext, mockSensorManager)
        device.mFusedLocationApi = mockFusedLocationApi
        val mockLocationRequest = mockk<LocationRequest>(relaxed = true)
        every { mockLocationRequest.hashCode() } returns 42
        every { mockLocationRequest.equals(any()) } answers { this === firstArg() }
        device.mLocationRequest = mockLocationRequest
        return device
    }

    @Test
    fun testOnConnected_registersLocationUpdatesAndSetsStateFlag() {
        val device = createDevice()
        assertFalse("Initially updates must not be registered", device.isLocationUpdatesRegistered)

        device.onConnected(Bundle())
        assertTrue("Updates must be registered after onConnected()", device.isLocationUpdatesRegistered)

        verify(exactly = 1) {
            mockFusedLocationApi.requestLocationUpdates(
                mockGoogleApiClient,
                any<LocationRequest>(),
                device
            )
        }
    }

    @Test
    fun testCheckOrReRegister_whenAlreadyRegistered_isIdempotentAndPreventsDuplicates() {
        val device = createDevice()
        device.onConnected(Bundle())
        assertTrue(device.isLocationUpdatesRegistered)

        // Simulate 7 repetitive tracking startup calls (e.g. from BANALService.checkOrInitializeLocationDevices)
        for (i in 1..7) {
            device.checkOrReRegisterLocationUpdates()
        }

        // Verify requestLocationUpdates was ONLY called once across all 7 attempts
        verify(exactly = 1) {
            mockFusedLocationApi.requestLocationUpdates(
                mockGoogleApiClient,
                any<LocationRequest>(),
                device
            )
        }
        assertTrue(device.isLocationUpdatesRegistered)
    }

    @Test
    fun testCheckOrReRegister_whenNotRegistered_registersUpdatesAndSetsFlag() {
        // Simulate initial connect without permission
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        val device = createDevice()
        device.onConnected(Bundle())
        assertFalse("Updates must not be registered without permission", device.isLocationUpdatesRegistered)

        verify(exactly = 0) {
            mockFusedLocationApi.requestLocationUpdates(any(), any<LocationRequest>(), any<LocationListener>())
        }

        // Now grant permission dynamically (runtime permission granted)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        device.checkOrReRegisterLocationUpdates()
        assertTrue("Updates must be registered after permission granted and checkOrReRegister", device.isLocationUpdatesRegistered)

        verify(exactly = 1) {
            mockFusedLocationApi.requestLocationUpdates(
                mockGoogleApiClient,
                any<LocationRequest>(),
                device
            )
        }
    }

    @Test
    fun testShutDown_removesLocationUpdatesAndResetsStateFlag() {
        val device = createDevice()
        device.onConnected(Bundle())
        assertTrue(device.isLocationUpdatesRegistered)

        device.shutDown()

        assertFalse("Updates must be unregistered after shutDown()", device.isLocationUpdatesRegistered)
        verify(exactly = 1) {
            mockFusedLocationApi.removeLocationUpdates(mockGoogleApiClient, device)
        }
        verify(atLeast = 1) {
            mockGoogleApiClient.disconnect()
        }
    }

    @Test
    fun testShutDown_whenApiThrowsException_catchesSafelyAndDisconnects() {
        val device = createDevice()
        device.onConnected(Bundle())
        assertTrue(device.isLocationUpdatesRegistered)

        every {
            mockFusedLocationApi.removeLocationUpdates(any(), any<LocationListener>())
        } throws IllegalStateException("Play services binder dead")

        // shutDown must catch exception defensively and not crash
        device.shutDown()

        assertFalse("Updates flag must be reset even if removeLocationUpdates threw", device.isLocationUpdatesRegistered)
        verify(atLeast = 1) {
            mockGoogleApiClient.disconnect()
        }
    }

    @Test
    fun testConnectionSuspendedAndFailed_resetsStateFlag() {
        val device = createDevice()
        device.onConnected(Bundle())
        assertTrue(device.isLocationUpdatesRegistered)

        device.onConnectionSuspended(1)
        assertFalse("State flag must be false after onConnectionSuspended", device.isLocationUpdatesRegistered)

        // Reconnect and test onConnectionFailed
        device.onConnected(Bundle())
        assertTrue(device.isLocationUpdatesRegistered)

        device.onConnectionFailed(ConnectionResult(ConnectionResult.NETWORK_ERROR))
        assertFalse("State flag must be false after onConnectionFailed", device.isLocationUpdatesRegistered)
    }

    // REQ-EXP-047.1 / TST-EXP-047.1: SpeedAndLocationDevice_GoogleFused always tags fixes as GNSS_FUSED
    @Test
    fun testOnLocationChanged_tagsGoogleFusedFixAsGnssFusedEvenWithNullProvider_TST_EXP_047_1() = kotlinx.coroutines.runBlocking {
        val tempFile = java.io.File.createTempFile("google_fused_tag_", ".phone_raw.csv.gz")
        tempFile.delete()
        val recorder = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder(
            targetFile = tempFile,
            metadata = com.atrainingtracker.banalservice.devices.raw.PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel 10", sportId = 1L, sessionStartTimeMs = 1_795_331_657_000L)
        )
        recorder.start()
        com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(recorder)

        val device = createDevice()
        try {
            val fusedLocNullProvider = mockk<android.location.Location>(relaxed = true) {
                every { provider } returns null
                every { latitude } returns 48.137160
                every { longitude } returns 11.576130
                every { hasAltitude() } returns true
                every { altitude } returns 493.4
                every { hasSpeed() } returns true
                every { speed } returns 3.2f
                every { hasBearing() } returns true
                every { bearing } returns 180.0f
                every { hasAccuracy() } returns true
                every { accuracy } returns 5.5f
                every { time } returns 1_795_331_657_000L
                every { elapsedRealtimeNanos } returns 685_756_090_900_000L
            }
            device.onLocationChanged(fusedLocNullProvider)
            // Duplicate callback with identical elapsedRealtimeNanos must be deduplicated
            device.onLocationChanged(fusedLocNullProvider)
        } finally {
            com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder.setActiveRecorderForTest(null)
            recorder.stop()
            device.shutDown()
        }

        try {
            val parsed = com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryReader.readAll(tempFile)
            val gnssRows = parsed.rows.filter { it.channel == com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS }
            val fusedRows = parsed.rows.filter { it.channel == com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel.GNSS_FUSED }
            org.junit.Assert.assertEquals("Google Fused must never write to raw GNSS channel", 0, gnssRows.size)
            org.junit.Assert.assertEquals("Google Fused must emit exactly 1 deduplicated GNSS_FUSED row", 1, fusedRows.size)
            org.junit.Assert.assertEquals(493.4, fusedRows[0].v2, 1e-4)
        } finally {
            tempFile.delete()
        }
    }
}
