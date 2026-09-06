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

package com.atrainingtracker.trainingtracker.ui.navigation

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit test for [MainActivityWithNavigation] Kotlin migration and Java interoperability contracts.
 *
 * Fulfills verification requirement TST-NAV-008 (ATT-657 / ATT-658).
 */
class MainActivityWithNavigationInteropTest {

    @Test
    fun testCompanionObjectConstantsJavaInterop() {
        // Direct field access verifying @JvmField accessibility from Java-like callers
        assertEquals("SELECTED_FRAGMENT_ID", MainActivityWithNavigation.SELECTED_FRAGMENT_ID)
        assertEquals("SELECTED_FRAGMENT", MainActivityWithNavigation.SELECTED_FRAGMENT)
        assertEquals(
            "com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT",
            MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT
        )

        // Reflection check to verify fields are public static on the class bytecode
        val selectedFragmentIdField = MainActivityWithNavigation::class.java.getField("SELECTED_FRAGMENT_ID")
        assertTrue(Modifier.isPublic(selectedFragmentIdField.modifiers))
        assertTrue(Modifier.isStatic(selectedFragmentIdField.modifiers))

        val selectedFragmentField = MainActivityWithNavigation::class.java.getField("SELECTED_FRAGMENT")
        assertTrue(Modifier.isPublic(selectedFragmentField.modifiers))
        assertTrue(Modifier.isStatic(selectedFragmentField.modifiers))

        val extraResumeField = MainActivityWithNavigation::class.java.getField("EXTRA_RESUME_INTERRUPTED_WORKOUT")
        assertTrue(Modifier.isPublic(extraResumeField.modifiers))
        assertTrue(Modifier.isStatic(extraResumeField.modifiers))
    }

    @Test
    fun testSelectedFragmentEnumIntegrity() {
        // Verify enum values used by TrackerService and ExportNotificationManager
        val enumValues = MainActivityWithNavigation.SelectedFragment.values()
        assertEquals(2, enumValues.size)

        assertEquals(
            "START_OR_TRACKING",
            MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name
        )
        assertEquals(
            "WORKOUT_LIST",
            MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name
        )

        // Verify valueOf lookup
        assertEquals(
            MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING,
            MainActivityWithNavigation.SelectedFragment.valueOf("START_OR_TRACKING")
        )
        assertEquals(
            MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST,
            MainActivityWithNavigation.SelectedFragment.valueOf("WORKOUT_LIST")
        )
    }

    @Test
    fun testClassHierarchyAndInterfaceCompliance() {
        val clazz = MainActivityWithNavigation::class.java

        assertTrue(
            "MainActivityWithNavigation must extend AppCompatActivity",
            AppCompatActivity::class.java.isAssignableFrom(clazz)
        )

        assertTrue(
            "MainActivityWithNavigation must implement GetBanalServiceInterface",
            BANALService.GetBanalServiceInterface::class.java.isAssignableFrom(clazz)
        )

        assertTrue(
            "MainActivityWithNavigation must implement OnPreferenceStartScreenCallback",
            PreferenceFragmentCompat.OnPreferenceStartScreenCallback::class.java.isAssignableFrom(clazz)
        )

        assertTrue(
            "MainActivityWithNavigation must implement StartOrResumeInterface",
            StartOrResumeInterface::class.java.isAssignableFrom(clazz)
        )
    }

    @Test
    fun testPublicMethodsPresence() {
        val clazz = MainActivityWithNavigation::class.java

        // startPairing must exist with exact parameter types (Protocol, DeviceType)
        val startPairingMethod = clazz.getMethod(
            "startPairing",
            com.atrainingtracker.banalservice.Protocol::class.java,
            com.atrainingtracker.banalservice.devices.DeviceType::class.java
        )
        assertNotNull("startPairing method must be present", startPairingMethod)
        assertTrue(Modifier.isPublic(startPairingMethod.modifiers))

        // navigateToDrawerItem must exist
        val navigateMethod = clazz.getMethod("navigateToDrawerItem", Int::class.javaPrimitiveType)
        assertNotNull("navigateToDrawerItem method must be present", navigateMethod)
        assertTrue(Modifier.isPublic(navigateMethod.modifiers))
    }
}
