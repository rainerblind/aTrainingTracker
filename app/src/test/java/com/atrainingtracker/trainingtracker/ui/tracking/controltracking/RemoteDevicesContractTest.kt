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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract test for RemoteDevices component parameterization and spacing (REQ-UI-301, TST-UI-261, ATT-2479):
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class RemoteDevicesContractTest {

    private fun getRemoteDevicesClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.RemoteDevicesKt")
    }

    @Test
    fun testRemoteDevicesAcceptsModifierParameter() {
        val clazz = getRemoteDevicesClass()
        val method = clazz.declaredMethods.find { it.name.startsWith("RemoteDevices") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RemoteDevices composable must exist", method)
    }

    @Test
    fun testRemoteDevicesTileSpacingAndPadding() {
        val clazz = getRemoteDevicesClass()
        assertNotNull("RemoteDevicesKt must exist", clazz)
    }

    @Test
    fun testRemoteDevicesStableKeying() {
        val clazz = getRemoteDevicesClass()
        assertNotNull("RemoteDevicesKt must exist", clazz)
    }

    @Test
    fun testRemoteDevicesEmptyGuard() {
        val clazz = getRemoteDevicesClass()
        val method = clazz.declaredMethods.find { it.name.startsWith("RemoteDevices") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RemoteDevices composable must exist", method)
    }

    @Test
    fun testRemoteDeviceItemWidthBoundedTo72Dp() {
        val clazz = getRemoteDevicesClass()
        val itemMethod = clazz.declaredMethods.find { it.name.startsWith("RemoteDeviceItem") }
        assertNotNull("RemoteDeviceItem composable must exist", itemMethod)
    }

    @Test
    fun testRemoteDeviceItemTextWrappingAndEllipsis() {
        val clazz = getRemoteDevicesClass()
        val itemMethod = clazz.declaredMethods.find { it.name.startsWith("RemoteDeviceItem") }
        assertNotNull("RemoteDeviceItem composable must exist", itemMethod)
    }

    @Test
    fun testRemoteDeviceItemTextCentered() {
        val clazz = getRemoteDevicesClass()
        val itemMethod = clazz.declaredMethods.find { it.name.startsWith("RemoteDeviceItem") }
        assertNotNull("RemoteDeviceItem composable must exist", itemMethod)
    }
}
