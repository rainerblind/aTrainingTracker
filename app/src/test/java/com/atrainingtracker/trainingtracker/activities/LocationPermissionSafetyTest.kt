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

package com.atrainingtracker.trainingtracker.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationProvider
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying permission-gated GPS provider access and SecurityException immunity
 * in [MainActivityWithNavigation] (REQ-STB-008, TST-STB-008, ATT-1243).
 */
class LocationPermissionSafetyTest {

    private lateinit var mockLocationManager: LocationManager
    private lateinit var activity: MainActivityWithNavigation

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(TrainingApplication::class)
        mockkStatic(ContextCompat::class)

        mockLocationManager = mockk(relaxed = true)
        activity = mockk(relaxed = true)
        every { activity.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { activity.checkGpsEnabledIfPermitted() } answers { callOriginal() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testTrackLocationDisabled_bypassesPermissionCheckAndProviderQuery() {
        // Arrange: User has disabled location tracking in preferences
        every { TrainingApplication.trackLocation() } returns false

        // Act
        activity.checkGpsEnabledIfPermitted()

        // Assert: Neither permission nor provider is queried
        verify(exactly = 0) {
            ContextCompat.checkSelfPermission(any(), any())
            mockLocationManager.getProvider(any())
        }
    }

    @Test
    fun testPermissionDenied_bypassesProviderQueryWithoutSecurityException() {
        // Arrange: Location tracking enabled, but runtime permission NOT granted
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        // Act
        activity.checkGpsEnabledIfPermitted()

        // Assert: getProvider is NOT called, eliminating Crashlytics issue 91baac6c7cecfe1953f184a54816f31e
        verify(exactly = 0) {
            mockLocationManager.getProvider(any())
            mockLocationManager.isProviderEnabled(any())
        }
    }

    @Test
    fun testPermissionGranted_gpsEnabled_checksProviderSafelyWithoutDialog() {
        // Arrange: Permission granted, GPS provider exists and is enabled
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider
        every { mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true

        // Act
        activity.checkGpsEnabledIfPermitted()

        // Assert: Provider queried, isProviderEnabled called, but no alert dialog triggered
        verify(exactly = 1) {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
            mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }

    @Test
    fun testPermissionGranted_gpsDisabled_showsAlertSafely() {
        // Arrange: Permission granted, GPS provider exists but is disabled by user
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider
        every { mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns false

        // Act
        activity.checkGpsEnabledIfPermitted()

        // Assert: Provider queried and detected as disabled
        verify(exactly = 1) {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
            mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }

    @Test
    fun testGetProviderThrowsSecurityException_caughtAndLoggedCleanlyWithoutCrashing() {
        // Arrange: Device throws SecurityException on getProvider (OEM custom ROM edge case)
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        every {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
        } throws SecurityException("\"gps\" location provider requires ACCESS_FINE_LOCATION permission")

        // Act: should catch SecurityException and continue without throwing
        activity.checkGpsEnabledIfPermitted()

        // Assert
        verify(exactly = 1) {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
        }
        verify(exactly = 0) {
            mockLocationManager.isProviderEnabled(any())
        }
    }

    @Test
    fun testGetProviderThrowsIllegalArgumentException_caughtAndLoggedCleanlyWithoutCrashing() {
        // Arrange: Device throws IllegalArgumentException on unknown provider
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        every {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
        } throws IllegalArgumentException("provider \"gps\" does not exist")

        // Act: should catch IllegalArgumentException and continue without throwing
        activity.checkGpsEnabledIfPermitted()

        // Assert
        verify(exactly = 1) {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
        }
        verify(exactly = 0) {
            mockLocationManager.isProviderEnabled(any())
        }
    }

    @Test
    fun testNullProvider_handledSafelyWithoutNPE() {
        // Arrange: Device returns null for GPS provider (e.g. tablet without GPS hardware)
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED

        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns null

        // Act
        activity.checkGpsEnabledIfPermitted()

        // Assert: No NPE, isProviderEnabled not called
        verify(exactly = 1) {
            mockLocationManager.getProvider(LocationManager.GPS_PROVIDER)
        }
        verify(exactly = 0) {
            mockLocationManager.isProviderEnabled(any())
        }
    }
}
