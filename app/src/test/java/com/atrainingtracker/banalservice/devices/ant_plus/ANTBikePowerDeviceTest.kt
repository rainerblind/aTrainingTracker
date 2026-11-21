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
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.CalculatedWheelSpeedReceiver
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.DataSource
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.IPedalPowerBalanceReceiver
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikePowerPcc.IPedalSmoothnessReceiver
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.EnumSet

/**
 * Unit test verifying automated ANT+ pedal power balance reference indicator normalization
 * pursuant to REQ-CON-022.2, TST-CON-022.2, and REQ-UI-377.1.
 */
class ANTBikePowerDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockPcc: AntPlusBikePowerPcc
    private lateinit var mockDevicesDb: DevicesDatabaseManager
    private var capturedReceiver: IPedalPowerBalanceReceiver? = null
    private var capturedSmoothnessReceiver: IPedalSmoothnessReceiver? = null
    private var capturedWheelSpeedReceiver: CalculatedWheelSpeedReceiver? = null

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

        fun getPedalSmoothnessSensor(): MySensor<Int>? = mPedalSmoothnessSensor

        fun getSpeedSensor(): MySensor<Double>? = mSpeedSensor

        fun getPaceSensor(): MySensor<Double>? = mPaceSensor

        fun getDevicesDatabaseManager(): DevicesDatabaseManager = mDevicesDatabaseManager
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
        mockDevicesDb = mockk(relaxed = true)
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

        val smoothnessSlot = slot<IPedalSmoothnessReceiver>()
        every { mockPcc.subscribePedalSmoothnessEvent(capture(smoothnessSlot)) } answers {
            capturedSmoothnessReceiver = smoothnessSlot.captured
        }

        val wheelSpeedSlot = slot<CalculatedWheelSpeedReceiver>()
        every { mockPcc.subscribeCalculatedWheelSpeedEvent(capture(wheelSpeedSlot)) } answers {
            capturedWheelSpeedReceiver = wheelSpeedSlot.captured
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

    /**
     * TST-CON-025.3: Verify combined pedal smoothness telemetry (separatePedalSmoothnessSupport == false)
     * creates and dispatches to mPedalSmoothnessSensor even though mPedalSmoothnessLeftSensor is null.
     */
    @Test
    fun testANTBikePower_combinedPedalSmoothness_routesToCombinedSensor() {
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 205L, 12349)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        assertNull("Pedal smoothness sensor initially null", device.getPedalSmoothnessSensor())

        // Deliver combined pedal smoothness event (separatePedalSmoothnessSupport = false, leftOrCombined = 68%)
        capturedSmoothnessReceiver?.onNewPedalSmoothness(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            1L,
            false,
            BigDecimal.valueOf(68),
            BigDecimal.valueOf(-1)
        )

        assertNotNull("Combined pedal smoothness sensor must be created", device.getPedalSmoothnessSensor())
        assertEquals(68, device.getPedalSmoothnessSensor()?.value)
    }

    /**
     * TST-CON-025.4: Verify ANTBikePowerDevice does not declare a shadow field for mDevicesDatabaseManager,
     * relying cleanly on inherited field from MyDevice.
     */
    @Test
    fun testANTBikePower_noFieldShadowingForDevicesDatabaseManager() {
        val declaredField = try {
            ANTBikePowerDevice::class.java.getDeclaredField("mDevicesDatabaseManager")
        } catch (e: NoSuchFieldException) {
            null
        }

        assertNull(
            "ANTBikePowerDevice must not declare mDevicesDatabaseManager field (shadowing MyDevice)",
            declaredField
        )

        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 206L, 12350)
        assertNotNull(
            "Inherited mDevicesDatabaseManager from MyDevice must be initialized",
            device.getDevicesDatabaseManager()
        )
    }

    /**
     * TST-STB-026.1: Verify null calculatedWheelSpeed returns before sensor creation,
     * throwing zero NPEs and leaving speed/pace sensors null without DB flag mutation.
     */
    @Test
    fun testANTBikePower_nullCalculatedWheelSpeed_returnsWithoutCreatingSensorsOrMutatingDb() {
        // Verifies REQ-STB-026 / TST-STB-026
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 207L, 12351)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        assertNotNull("CalculatedWheelSpeedReceiver must be subscribed", capturedWheelSpeedReceiver)

        capturedWheelSpeedReceiver?.onNewCalculatedWheelSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            DataSource.WHEEL_TORQUE_DATA,
            null
        )

        assertNull("Speed sensor must not be created on null callback", device.getSpeedSensor())
        assertNull("Pace sensor must not be created on null callback", device.getPaceSensor())
        verify(exactly = 0) { mockDevicesDb.putBikePowerSensorFlags(any(), any()) }
    }

    /**
     * TST-STB-026.2: Verify initial BigDecimal.ZERO standstill reports 0.0 m/s speed and null pace (never Infinity).
     */
    @Test
    fun testANTBikePower_initialZeroWheelSpeed_setsZeroSpeedAndNullPace() {
        // Verifies REQ-STB-026 / TST-STB-026
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 208L, 12352)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedWheelSpeedReceiver?.onNewCalculatedWheelSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            DataSource.COAST_OR_STOP_DETECTED,
            BigDecimal.ZERO
        )

        assertNotNull("Speed sensor must be created on non-null standstill speed", device.getSpeedSensor())
        assertNotNull("Pace sensor must be created on non-null standstill speed", device.getPaceSensor())
        assertEquals(0.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertNull("Standstill pace must remain null rather than PositiveInfinity", device.getPaceSensor()?.value)
    }

    /**
     * TST-STB-026.2: Verify positive wheel speed (36.0 km/h) converts to 10.0 m/s and 0.1 s/m pace.
     */
    @Test
    fun testANTBikePower_positiveWheelSpeed_computesMetersPerSecondAndFinitePace() {
        // Verifies REQ-STB-026 / TST-STB-026
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 209L, 12353)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedWheelSpeedReceiver?.onNewCalculatedWheelSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            DataSource.WHEEL_TORQUE_DATA,
            BigDecimal("36.0")
        )

        assertEquals(10.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertEquals(0.1, device.getPaceSensor()?.value ?: Double.NaN, 1e-9)
    }

    /**
     * TST-STB-026.2: Verify 8 consecutive BigDecimal.ZERO standstill ticks (SPEED_THRESHOLD = 8)
     * after positive speed decay mPaceSensor cleanly back to null (never Infinity).
     */
    @Test
    fun testANTBikePower_zeroWheelSpeedAfterMotion_decaysPaceToNullAtThreshold() {
        // Verifies REQ-STB-026 / TST-STB-026
        val device = TestANTBikePowerDevice(mockContext, mockSensorManager, 210L, 12354)
        device.setMockPcc(mockPcc)
        device.subscribeSpecificEvents()

        capturedWheelSpeedReceiver?.onNewCalculatedWheelSpeed(
            1000L,
            EnumSet.noneOf(EventFlag::class.java),
            DataSource.WHEEL_TORQUE_DATA,
            BigDecimal("36.0")
        )
        assertEquals(0.1, device.getPaceSensor()?.value ?: Double.NaN, 1e-9)

        for (tick in 1..7) {
            capturedWheelSpeedReceiver?.onNewCalculatedWheelSpeed(
                1000L + tick,
                EnumSet.noneOf(EventFlag::class.java),
                DataSource.COAST_OR_STOP_DETECTED,
                BigDecimal.ZERO
            )
            assertEquals(
                "Pace should hold prior finite value before SPEED_THRESHOLD ($tick/8)",
                0.1,
                device.getPaceSensor()?.value ?: Double.NaN,
                1e-9
            )
        }

        capturedWheelSpeedReceiver?.onNewCalculatedWheelSpeed(
            1008L,
            EnumSet.noneOf(EventFlag::class.java),
            DataSource.COAST_OR_STOP_DETECTED,
            BigDecimal.ZERO
        )

        assertEquals(0.0, device.getSpeedSensor()?.value ?: Double.NaN, 1e-9)
        assertNull("Pace sensor must decay cleanly to null at SPEED_THRESHOLD = 8", device.getPaceSensor()?.value)
    }
}
