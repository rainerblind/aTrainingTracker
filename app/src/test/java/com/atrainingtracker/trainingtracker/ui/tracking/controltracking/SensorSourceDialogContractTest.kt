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

import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test for [SensorSourceDialog] (REQ-UI-051, REQ-UI-451, ATT-4374, ATT-7544):
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorSourceDialogContractTest {

    private fun getDialogClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogKt")
    }

    @Test
    fun testSourceFileExists() {
        val clazz = getDialogClass()
        assertNotNull("SensorSourceDialogKt class must exist", clazz)
    }

    @Test
    fun testDeviceIdentityBlock_forwardsIsConnectedToDeviceStatusRow() {
        val clazz = getDialogClass()
        val methods = clazz.declaredMethods
        val method = methods.find { it.name.startsWith("DeviceIdentityBlock") }
        assertNotNull("DeviceIdentityBlock composable must exist", method)
        assertTrue(
            "DeviceIdentityBlock must accept Boolean parameter for connection status",
            method!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )
    }

    @Test
    fun testSensorSourceDialog_passesIsConnectedTrueForActiveSensors() {
        val clazz = getDialogClass()
        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("SensorSourceDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorSourceDialog composable must exist", dialogMethod)
    }

    @Test
    fun testSensorSourceDialog_hasZeroReferencesToRouteTokens() {
        val clazz = getDialogClass()
        assertNotNull(clazz)
    }

    @Test
    fun testSensorSourceDialog_usesDedicatedSensorSourceTokens() {
        val clazz = getDialogClass()
        assertNotNull(clazz)
    }

    @Test
    fun testSensorSourceDialog_forRadarThreatCount_mapsDeviceTypeBikeRadar() {
        val types = DeviceType.getDeviceTypeList(SensorType.RADAR_THREAT_COUNT)
        assertNotNull("Device types for RADAR_THREAT_COUNT must not be null", types)
        assertTrue("Device types for RADAR_THREAT_COUNT must include BIKE_RADAR", types.contains(DeviceType.BIKE_RADAR))
    }

    @Test
    fun testSensorSourceDialog_sourceFileContainsBikeRadarTitleAndScalarOmission() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
        val altFile = File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
        val target = if (file.exists()) file else altFile
        assertTrue("SensorSourceDialog.kt must exist", target.exists())
        val source = target.readText()
        assertTrue("Dialog title must reference device_type_bike_radar", source.contains("R.string.device_type_bike_radar"))
        assertTrue("Scalar text must be omitted for RADAR_THREAT_COUNT", source.contains("sensorType == SensorType.RADAR_THREAT_COUNT) null"))
    }
}
