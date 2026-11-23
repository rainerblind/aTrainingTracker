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
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.EnumSet

/**
 * Unit tests verifying pre-dereference null guard, direct m/s scaling, and standstill null pace
 * with ThresholdSensor decay in [ANTBikeSpeedDevice] pursuant to REQ-CON-058 and TST-CON-058.1.
 */
class ANTBikeSpeedDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockPcc: AntPlusBikeSpeedDistancePcc
    private var capturedSpeedReceiver: CalculatedSpeedReceiver? = null

    private class TestANTBikeSpeedDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        antDeviceNumber: Int
    ) : ANTBikeSpeedDevice(context, sensorManager, deviceId, antDeviceNumber) {

        public override fun subscribeSpecificEvents() {
            super.subscribeSpecificEvents()
        }

        fun setMockPcc(pcc: AntPlusBikeSpeedDistancePcc) {
            bikeSpeedDistancePcc = pcc
        }

        fun getSpeedSensor(): MySensor<Double>? = mSpeedSensor

        fun getPaceSensor(): MySensor<Double>? = mPaceSensor

        override fun notifySensorsChanged() {
            // No-op for unit testing
        }
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
        every { mockDevicesDb.getCalibrationFactor(any()) } returns 2.1
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockPcc = mockk(relaxed = true)

        capturedSpeedReceiver = null
        val speedSlot = slot<CalculatedSpeedReceiver>()
        every { mockPcc.subscribeCalculatedSpeedEvent(capture(speedSlot)) } answers {
            capturedSpeedReceiver = speedSlot.captured
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-CON-058.1: Verify null calculatedSpeed returns before myLog/dereference without NPE,
     * leaving initial sensor values null.
     */
    @Test
    fun testANTBikeSpeed_nullCalculatedSpeed_initialRemainsNullWithoutNpe_tstCon058_1() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedDevice(mockContext, mockSensorManager, 301L, 5001)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        assertNotNull("CalculatedSpeedReceiver must be subscribed", capturedSpeedReceiver)

        capturedSpeedReceiver?.onNewCalculatedSpeed(1000L, EnumSet.noneOf(EventFlag::class.java), null)

        assertNull("Initial speed sensor value must remain null on null callback", device.getSpeedSensor()?.value)
        assertNull("Initial pace sensor value must remain null on null callback", device.getPaceSensor()?.value)
    }

    /**
     * TST-CON-058.1: Verify null calculatedSpeed after valid motion returns without NPE
     * and leaves prior speed and pace sensor values undisturbed.
     */
    @Test
    fun testANTBikeSpeed_nullCalculatedSpeed_afterMotionPreservesValuesWithoutNpe_tstCon058_1() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedDevice(mockContext, mockSensorManager, 302L, 5002)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedSpeedReceiver?.onNewCalculatedSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            BigDecimal("10.0")
        )
        assertEquals(10.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertEquals(0.1, device.getPaceSensor()?.value ?: Double.NaN, 1e-9)

        capturedSpeedReceiver?.onNewCalculatedSpeed(1001L, EnumSet.noneOf(EventFlag::class.java), null)

        assertEquals(10.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertEquals(0.1, device.getPaceSensor()?.value ?: Double.NaN, 1e-9)
    }

    /**
     * TST-CON-058.1: Verify initial BigDecimal.ZERO standstill reports 0.0 m/s speed and null pace (never Infinity).
     */
    @Test
    fun testANTBikeSpeed_initialZeroSpeed_setsZeroSpeedAndNullPace_tstCon058_1() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedDevice(mockContext, mockSensorManager, 303L, 5003)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedSpeedReceiver?.onNewCalculatedSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            BigDecimal.ZERO
        )

        assertEquals(0.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertNull("Standstill pace must remain null rather than PositiveInfinity", device.getPaceSensor()?.value)
    }

    /**
     * TST-CON-058.1: Verify positive speed (10.0 m/s from CalculatedSpeedReceiver) publishes
     * directly in m/s (no /3.6 scaling) and computes finite pace 0.1 s/m.
     */
    @Test
    fun testANTBikeSpeed_positiveSpeed_publishesMetersPerSecondAndFinitePace_tstCon058_1() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedDevice(mockContext, mockSensorManager, 304L, 5004)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedSpeedReceiver?.onNewCalculatedSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            BigDecimal("10.0")
        )

        assertEquals(10.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertEquals(0.1, device.getPaceSensor()?.value ?: Double.NaN, 1e-9)
    }

    /**
     * TST-CON-058.1: Verify 8 consecutive BigDecimal.ZERO standstill ticks (SPEED_THRESHOLD = 8)
     * after positive speed hold prior pace on ticks 1..7 and decay cleanly to null on tick 8.
     */
    @Test
    fun testANTBikeSpeed_zeroSpeedAfterMotion_decaysPaceToNullAtThreshold_tstCon058_1() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedDevice(mockContext, mockSensorManager, 305L, 5005)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedSpeedReceiver?.onNewCalculatedSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            BigDecimal("10.0")
        )
        assertEquals(0.1, device.getPaceSensor()?.value ?: Double.NaN, 1e-9)

        for (tick in 1..7) {
            capturedSpeedReceiver?.onNewCalculatedSpeed(
                1000L + tick,
                EnumSet.noneOf(EventFlag::class.java),
                BigDecimal.ZERO
            )
            assertEquals(0.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
            assertEquals(
                "Pace should hold prior finite value before SPEED_THRESHOLD ($tick/8)",
                0.1,
                device.getPaceSensor()?.value ?: Double.NaN,
                1e-9
            )
        }

        capturedSpeedReceiver?.onNewCalculatedSpeed(
            1008L,
            EnumSet.noneOf(EventFlag::class.java),
            BigDecimal.ZERO
        )

        assertEquals(0.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertNull("Pace sensor must decay cleanly to null at SPEED_THRESHOLD = 8", device.getPaceSensor()?.value)
    }

    /**
     * TST-CON-058.1: Verify negative calculatedSpeed never emits negative pace (feeds null to mPaceSensor).
     */
    @Test
    fun testANTBikeSpeed_negativeSpeed_emitsNullPace_tstCon058_1() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedDevice(mockContext, mockSensorManager, 306L, 5006)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedSpeedReceiver?.onNewCalculatedSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            BigDecimal("-1.0")
        )

        assertEquals(-1.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertNull("Negative speed must not emit negative pace", device.getPaceSensor()?.value)
    }
}
