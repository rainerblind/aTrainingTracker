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
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract and unit tests verifying zero-friction cold start and absence of eager modal popups
 * in [MainActivityWithNavigation] (REQ-PRI-003, TST-PRI-002, ATT-2075).
 */
class MainActivityStartupPermissionContractTest {

    private lateinit var mockLocationManager: LocationManager
    private lateinit var activity: MainActivityWithNavigation

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        mockkStatic(ContextCompat::class)

        mockLocationManager = mockk(relaxed = true)
        activity = mockk(relaxed = true)
        every { activity.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { activity.checkGpsEnabledIfPermitted() } answers { callOriginal() }
        every { activity.getPermissions(any()) } answers { callOriginal() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testMainActivity_inheritsAppCompatActivity() {
        assertTrue(AppCompatActivity::class.java.isAssignableFrom(MainActivityWithNavigation::class.java))
    }

    @Test
    fun testGetPermissionsMethodContract() {
        val method = MainActivityWithNavigation::class.java.getMethod("getPermissions", Boolean::class.javaPrimitiveType)
        assertNotNull("getPermissions(boolean) method must exist for backward compatibility", method)
        assertTrue(Modifier.isPublic(method.modifiers))
    }

    @Test
    fun testGetPermissionsWithPopupFalse_doesNotThrowOrShowDialog() {
        // When getPermissions is called with popup=false, it immediately returns without throwing
        activity.getPermissions(false)
        verify(exactly = 0) {
            ContextCompat.checkSelfPermission(any(), any())
        }
    }

    @Test
    fun testLocationPermissionSafetyMaintained_REQ_STB_008() {
        every { TrainingApplication.trackLocation() } returns true
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        // When location permission is missing, checkGpsEnabledIfPermitted safely bypasses getProvider
        activity.checkGpsEnabledIfPermitted()

        verify(exactly = 0) {
            mockLocationManager.getProvider(any())
        }
    }
}
