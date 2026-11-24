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
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.EnumSet

/**
 * Unit test verifying unconditional PCC handle release and leak prevention in ANTBikeSpeedAndCadenceDevice
 * pursuant to REQ-CON-025.1 / TST-CON-025.1-2, and pre-dereference null speed guard & standstill null pace
 * pursuant to REQ-CON-058 / TST-CON-058.2.
 */
class ANTBikeSpeedAndCadenceDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockSpeedPcc: AntPlusBikeSpeedDistancePcc
    private var capturedSpeedReceiver: CalculatedSpeedReceiver? = null

    private class TestANTBikeSpeedAndCadenceDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        antDeviceNumber: Int
    ) : ANTBikeSpeedAndCadenceDevice(context, sensorManager, deviceId, antDeviceNumber) {

        public override fun releaseAccess() {
            super.releaseAccess()
        }

        public override fun subscribeSpecificEvents() {
            super.subscribeSpecificEvents()
        }

        fun setMockSpeedPcc(pcc: AntPlusBikeSpeedDistancePcc) {
            bikeSpeedDistancePcc = pcc
        }

        fun getSpeedSensor(): MySensor<Double>? = mSpeedSensor

        fun getPaceSensor(): MySensor<Double>? = mPaceSensor

        public override fun setPccReleaseHandleForTesting(handle: PccReleaseHandle<*>?) {
            super.setPccReleaseHandleForTesting(handle)
        }

        public override fun getPccReleaseHandle(): PccReleaseHandle<*>? {
            return super.getPccReleaseHandle()
        }

        public override fun setPccReleaseHandle2ForTesting(handle: PccReleaseHandle<*>?) {
            super.setPccReleaseHandle2ForTesting(handle)
        }

        public override fun getPccReleaseHandle2ForTesting(): PccReleaseHandle<*>? {
            return super.getPccReleaseHandle2ForTesting()
        }

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
        mockSpeedPcc = mockk(relaxed = true)

        capturedSpeedReceiver = null
        val speedSlot = slot<CalculatedSpeedReceiver>()
        every { mockSpeedPcc.subscribeCalculatedSpeedEvent(capture(speedSlot)) } answers {
            capturedSpeedReceiver = speedSlot.captured
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-CON-025.1: Verify super.releaseAccess() closes and nulls primary speed handle
     * even when cadence handle (pccReleaseHandle2) was never initialized or failed.
     */
    @Test
    fun testReleaseAccess_whenSecondaryHandleNull_unconditionallyReleasesPrimaryHandle() {
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 101L, 4567)
        val mockPrimaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)

        device.setPccReleaseHandleForTesting(mockPrimaryHandle)
        device.setPccReleaseHandle2ForTesting(null)

        device.releaseAccess()

        verify(exactly = 1) { mockPrimaryHandle.close() }
        assertNull("Primary release handle must be nulled", device.getPccReleaseHandle())
        assertNull("Secondary release handle must be null", device.getPccReleaseHandle2ForTesting())
    }

    /**
     * TST-CON-025.2: Verify both secondary cadence handle and primary speed handle
     * are closed and nulled when both were active.
     */
    @Test
    fun testReleaseAccess_whenBothHandlesActive_releasesBothSafely() {
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 102L, 4568)
        val mockPrimaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)
        val mockSecondaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)

        device.setPccReleaseHandleForTesting(mockPrimaryHandle)
        device.setPccReleaseHandle2ForTesting(mockSecondaryHandle)

        device.releaseAccess()

        verify(exactly = 1) { mockSecondaryHandle.close() }
        verify(exactly = 1) { mockPrimaryHandle.close() }
        assertNull("Secondary handle must be nulled", device.getPccReleaseHandle2ForTesting())
        assertNull("Primary handle must be nulled", device.getPccReleaseHandle())
    }

    /**
     * TST-CON-025.2: Verify that even if secondary handle close throws an exception,
     * primary handle is still guaranteed to be closed and nulled via finally block.
     */
    @Test
    fun testReleaseAccess_whenSecondaryHandleThrows_primaryHandleStillCleanedUp() {
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 103L, 4569)
        val mockPrimaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)
        val mockSecondaryHandle = mockk<PccReleaseHandle<*>>()
        every { mockSecondaryHandle.close() } throws RuntimeException("ANT plugin IPC error")

        device.setPccReleaseHandleForTesting(mockPrimaryHandle)
        device.setPccReleaseHandle2ForTesting(mockSecondaryHandle)

        device.releaseAccess()

        verify(exactly = 1) { mockSecondaryHandle.close() }
        verify(exactly = 1) { mockPrimaryHandle.close() }
        assertNull("Secondary handle must be nulled in finally", device.getPccReleaseHandle2ForTesting())
        assertNull("Primary handle must be nulled", device.getPccReleaseHandle())
    }

    /**
     * TST-CON-058.2: Verify null calculatedSpeed returns without NPE, leaving initial sensor values null.
     */
    @Test
    fun testANTBikeSpeedAndCadence_nullCalculatedSpeed_initialRemainsNullWithoutNpe_tstCon058_2() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 104L, 4570)
        device.setMockSpeedPcc(mockSpeedPcc)
        device.subscribeSpecificEvents()

        assertNotNull("CalculatedSpeedReceiver must be subscribed", capturedSpeedReceiver)

        capturedSpeedReceiver?.onNewCalculatedSpeed(1000L, EnumSet.noneOf(EventFlag::class.java), null)

        assertNull("Initial speed sensor value must remain null on null callback", device.getSpeedSensor()?.value)
        assertNull("Initial pace sensor value must remain null on null callback", device.getPaceSensor()?.value)
    }

    /**
     * TST-CON-058.2: Verify null calculatedSpeed after valid motion returns without NPE
     * and leaves prior speed and pace sensor values undisturbed.
     */
    @Test
    fun testANTBikeSpeedAndCadence_nullCalculatedSpeed_afterMotionPreservesValuesWithoutNpe_tstCon058_2() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 105L, 4571)
        device.setMockSpeedPcc(mockSpeedPcc)
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
     * TST-CON-058.2: Verify initial BigDecimal.ZERO standstill reports 0.0 m/s speed and null pace (never Infinity).
     */
    @Test
    fun testANTBikeSpeedAndCadence_initialZeroSpeed_setsZeroSpeedAndNullPace_tstCon058_2() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 106L, 4572)
        device.setMockSpeedPcc(mockSpeedPcc)
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
     * TST-CON-058.2: Verify positive speed (10.0 m/s) publishes directly in m/s and computes finite pace 0.1 s/m.
     */
    @Test
    fun testANTBikeSpeedAndCadence_positiveSpeed_publishesMetersPerSecondAndFinitePace_tstCon058_2() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 107L, 4573)
        device.setMockSpeedPcc(mockSpeedPcc)
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
     * TST-CON-058.2: Verify 8 consecutive BigDecimal.ZERO standstill ticks (SPEED_THRESHOLD = 8)
     * after positive speed hold prior pace on ticks 1..7 and decay cleanly to null on tick 8.
     */
    @Test
    fun testANTBikeSpeedAndCadence_zeroSpeedAfterMotion_decaysPaceToNullAtThreshold_tstCon058_2() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 108L, 4574)
        device.setMockSpeedPcc(mockSpeedPcc)
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
     * TST-CON-058.2: Verify negative calculatedSpeed never emits negative pace (feeds null to mPaceSensor).
     */
    @Test
    fun testANTBikeSpeedAndCadence_negativeSpeed_emitsNullPace_tstCon058_2() {
        // Verifies REQ-CON-058 / TST-CON-058
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 109L, 4575)
        device.setMockSpeedPcc(mockSpeedPcc)
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
