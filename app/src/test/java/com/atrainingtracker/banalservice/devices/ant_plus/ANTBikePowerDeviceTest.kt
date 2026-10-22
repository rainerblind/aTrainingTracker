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

package com.atrainingtracker.banalservice.devices.ant_plus

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.IPedalPowerBalanceReceiver
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.EnumSet

/**
 * Unit test verifying automated ANT+ pedal power balance reference indicator normalization
 * pursuant to REQ-CON-022.2, TST-CON-022.2, and REQ-UI-377.1.
 */
class ANTBikePowerDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockPcc: AntPlusBikePowerPcc
    private var capturedReceiver: IPedalPowerBalanceReceiver? = null

    private class TestANTBikePowerDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        antDeviceNumber: Int
    ) : ANTBikePowerDevice(context, sensorManager, deviceId, antDeviceNumber) {
        public override fun subscribeSpecificEvents() {
            super.subscribeSpecificEvents()
        }

        fun setMockPcc(pcc: AntPlusBikePowerPcc) {
            bikePowerPcc = pcc
        }

        override fun notifySensorsChanged() {
            // No-op for unit test to avoid android.content.Intent construction and global broadcast
        }

        fun getPowerBalanceSensor(): MySensor<Int>? = mPowerBalanceSensor
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { mockDevicesDb.getBikePowerSensorFlags(any()) } returns 0
        every { mockDevicesDb.putBikePowerSensorFlags(any(), any()) } just Runs
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb


        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockPcc = mockk(relaxed = true)

        val slot = slot<IPedalPowerBalanceReceiver>()
        every { mockPcc.subscribePedalPowerBalanceEvent(capture(slot)) } answers {
            capturedReceiver = slot.captured
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-CON-022.2: Verify ANT+ rightPedalIndicator == true emits value as Right percentage directly.
     */
    @Test
    fun testANTBikePower_rightPedalIndicatorTrue_emittedDirectly() {
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 201L, 12345)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedReceiver?.onNewPedalPowerBalance(1000L, EnumSet.noneOf(EventFlag::class.java), true, 54)

        assertEquals(54, device.getPowerBalanceSensor()?.value)
    }

    /**
     * TST-CON-022.2: Verify ANT+ rightPedalIndicator == false (Left reference) inverts to Right percentage.
     * e.g. 46% Left -> 54% Right (100 - 46).
     */
    @Test
    fun testANTBikePower_rightPedalIndicatorFalse_invertedToRightPercentage() {
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 202L, 12346)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedReceiver?.onNewPedalPowerBalance(1000L, EnumSet.noneOf(EventFlag::class.java), false, 46)

        assertEquals(54, device.getPowerBalanceSensor()?.value)
    }

    /**
     * TST-CON-022.2: Boundary verification (0% Left -> 100% Right, 100% Left -> 0% Right).
     */
    @Test
    fun testANTBikePower_boundaryValues() {
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 203L, 12347)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        // 0% Left -> 100% Right
        capturedReceiver?.onNewPedalPowerBalance(1000L, EnumSet.noneOf(EventFlag::class.java), false, 0)
        assertEquals(100, device.getPowerBalanceSensor()?.value)

        // 100% Left -> 0% Right
        capturedReceiver?.onNewPedalPowerBalance(1001L, EnumSet.noneOf(EventFlag::class.java), false, 100)
        assertEquals(0, device.getPowerBalanceSensor()?.value)

        // 0% Right -> 0% Right
        capturedReceiver?.onNewPedalPowerBalance(1002L, EnumSet.noneOf(EventFlag::class.java), true, 0)
        assertEquals(0, device.getPowerBalanceSensor()?.value)

        // 100% Right -> 100% Right
        capturedReceiver?.onNewPedalPowerBalance(1003L, EnumSet.noneOf(EventFlag::class.java), true, 100)
        assertEquals(100, device.getPowerBalanceSensor()?.value)
    }

    /**
     * TST-CON-022.2: Verify negative pedalPowerPercentage publishes null.
     */
    @Test
    fun testANTBikePower_negativeValue_publishesNull() {
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 204L, 12348)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        // First initialize sensor with valid reading
        capturedReceiver?.onNewPedalPowerBalance(1000L, EnumSet.noneOf(EventFlag::class.java), true, 50)
        assertEquals(50, device.getPowerBalanceSensor()?.value)

        // Then invalid reading (< 0) -> publishes null
        capturedReceiver?.onNewPedalPowerBalance(1001L, EnumSet.noneOf(EventFlag::class.java), true, -1)
        assertNull(device.getPowerBalanceSensor()?.value)
    }
}
