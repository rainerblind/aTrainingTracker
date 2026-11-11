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

package com.atrainingtracker.banalservice.sensor

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating HRV SensorTypes (HRV_RMSSD, HRV_DFA_A1, HRV_ARTIFACT_RATE) properties,
 * categories, value types, icons, and device mappings pursuant to REQ-TRK-060.1 and TST-TRK-060.1.
 */
class SensorTypeHrvTest {

    @Test
    fun testHrvRmssdProperties() {
        val rmssd = SensorType.HRV_RMSSD
        assertEquals(SensorCategory.HEART_RATE_AND_ENERGY, rmssd.category)
        assertEquals(SensorValueType.DOUBLE, rmssd.sensorValueType)
        assertEquals(R.string.hrv_rmssd, rmssd.fullNameId)
        assertEquals(R.string.hrv_rmssd_short, rmssd.shortNameId)
        assertEquals(R.string.units_time_ms, rmssd.unitId)
        assertEquals(R.drawable.ic_heart_rate, rmssd.iconResId)
        assertTrue(rmssd.filteringPossible)

        val deviceTypes = DeviceType.getDeviceTypeList(rmssd)
        assertTrue(deviceTypes.contains(DeviceType.HRV_METRICS))
    }

    @Test
    fun testHrvDfaA1Properties() {
        val dfa = SensorType.HRV_DFA_A1
        assertEquals(SensorCategory.HEART_RATE_AND_ENERGY, dfa.category)
        assertEquals(SensorValueType.DOUBLE, dfa.sensorValueType)
        assertEquals(R.string.hrv_dfa_a1, dfa.fullNameId)
        assertEquals(R.string.hrv_dfa_a1_short, dfa.shortNameId)
        assertEquals(R.string.units_none, dfa.unitId)
        assertEquals(R.drawable.ic_heart_rate, dfa.iconResId)
        assertTrue(dfa.filteringPossible)

        val deviceTypes = DeviceType.getDeviceTypeList(dfa)
        assertTrue(deviceTypes.contains(DeviceType.HRV_METRICS))
    }

    @Test
    fun testHrvArtifactRateProperties() {
        val artifactRate = SensorType.HRV_ARTIFACT_RATE
        assertEquals(SensorCategory.HEART_RATE_AND_ENERGY, artifactRate.category)
        assertEquals(SensorSubCategory.HRV_AND_DRIFT, artifactRate.subCategory)
        assertEquals(SensorValueType.DOUBLE, artifactRate.sensorValueType)
        assertEquals(R.string.hrv_artifact_rate, artifactRate.fullNameId)
        assertEquals(R.string.hrv_artifact_rate_short, artifactRate.shortNameId)
        assertEquals(R.string.units_percent, artifactRate.unitId)
        assertEquals(R.drawable.ic_heart_rate, artifactRate.iconResId)
        assertTrue(artifactRate.filteringPossible)

        val deviceTypes = DeviceType.getDeviceTypeList(artifactRate)
        assertTrue(deviceTypes.contains(DeviceType.HRV_METRICS))
    }

    @Test
    fun testHrvSdnnProperties() {
        val sdnn = SensorType.HRV_SDNN
        assertEquals(SensorCategory.HEART_RATE_AND_ENERGY, sdnn.category)
        assertEquals(SensorSubCategory.HRV_AND_DRIFT, sdnn.subCategory)
        assertEquals(SensorValueType.DOUBLE, sdnn.sensorValueType)
        assertEquals(R.string.hrv_sdnn, sdnn.fullNameId)
        assertEquals(R.string.hrv_sdnn_short, sdnn.shortNameId)
        assertEquals(R.string.units_time_ms, sdnn.unitId)
        assertEquals(R.drawable.ic_heart_rate, sdnn.iconResId)
        assertTrue(sdnn.filteringPossible)
    }
}
