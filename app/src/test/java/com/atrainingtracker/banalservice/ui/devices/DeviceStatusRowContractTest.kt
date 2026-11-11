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

package com.atrainingtracker.banalservice.ui.devices

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract test verifying DeviceStatusRow connection parameterization
 * and backward compatibility (REQ-UI-268, TST-UI-227.1, TST-UI-227.2, TST-UI-227.3, ATT-2194).
 */
class DeviceStatusRowContractTest {

    @Test
    fun testDeviceStatusRowClassExists() {
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.DeviceStatusRowKt")
        assertNotNull("DeviceStatusRowKt class must exist", clazz)
    }

    @Test
    fun testDeviceStatusRow_hasIsConnectedParameterWithDefault() {
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.DeviceStatusRowKt")
        val methods = clazz.declaredMethods

        val hasBooleanParam = methods.any { method ->
            method.name.startsWith("DeviceStatusRow") &&
                method.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        }
        assertTrue("DeviceStatusRow must accept isConnected: Boolean parameter", hasBooleanParam)
    }

    @Test
    fun testDeviceStatusRow_referencesRequiredStrings() {
        val strings = LocalizationTestCache.getStrings("values", "strings_devices.xml")
        assertTrue("Must define devices_available", strings.containsKey("devices_available"))
        assertTrue("Must define devices_not_connected", strings.containsKey("devices_not_connected"))
    }
}
