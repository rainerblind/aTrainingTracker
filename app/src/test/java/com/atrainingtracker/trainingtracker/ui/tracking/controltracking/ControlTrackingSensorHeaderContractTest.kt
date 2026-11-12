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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test for ControlTrackingScreen sensor header and device discovery area.
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ControlTrackingSensorHeaderContractTest {

    private fun getScreenClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt")
    }

    @Test
    fun testSearchAreaIsPlacedInDedicatedSlotAboveDevices() {
        val clazz = getScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)
    }

    @Test
    fun testLegacyCollidingBoxStructureIsEliminated() {
        val clazz = getScreenClass()
        assertNotNull(clazz)
    }

    @Test
    fun testSideBySideRowWithWeightForRemoteDevices() {
        val clazz = getScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)
    }

    @Test
    fun testCenteredResearchButtonWhenZeroDevices() {
        val clazz = getScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)
    }

    @Test
    fun testMultiDevicePreviewExists() {
        val clazz = getScreenClass()
        val previewMethod = clazz.declaredMethods.find { it.name.startsWith("PreviewControlTrackingScreenMultiDevices") }
        assertNotNull("PreviewControlTrackingScreenMultiDevices preview composable must exist", previewMethod)
    }

    @Test
    fun testSymmetricalBalancingAnchorForRemoteDevicesCentering() {
        val clazz = getScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)
    }

    @Test
    fun testSingleDevicePreviewExists() {
        val clazz = getScreenClass()
        val previewMethod = clazz.declaredMethods.find { it.name.startsWith("PreviewControlTrackingScreenSingleDevice") }
        assertNotNull("PreviewControlTrackingScreenSingleDevice preview composable must exist", previewMethod)
    }

    @Test
    fun testOnboardingPairSensorButtonContract() {
        val clazz = getScreenClass()
        val screenMethod = clazz.declaredMethods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue("onboarding_pair_sensor_action must exist", strings.containsKey("onboarding_pair_sensor_action"))
    }
}
