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

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

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
}
