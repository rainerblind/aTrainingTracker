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
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.DataState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit test suite verifying ANT+ Heart Rate parsing, strict beat-count continuity guard (deltaC == 1),
 * duplicate rejection (deltaC == 0), 8-bit rollover (255 -> 0), and multi-beat gap flagging (deltaC >= 2)
 * (REQ-CON-027, TST-CON-027.4, ATT-3540).
 */
class ANTHeartRateDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 100_000L

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class TestANTHeartRateDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        antDeviceNumber: Int
    ) : ANTHeartRateDevice(context, sensorManager, deviceId, antDeviceNumber) {
        override fun notifySensorsChanged() {
            // No-op for unit testing
        }

        public override fun unregisterSensors() {
            super.unregisterSensors()
        }
    }

    private fun createDevice(): ANTHeartRateDevice {
        return TestANTHeartRateDevice(mockContext, mockSensorManager, 201L, 12345)
    }

    /**
     * TST-CON-027.4: Baseline initialization, single-beat continuity (deltaC == 1), and duplicate rejection (deltaC == 0).
     */
    @Test
    fun testSingleBeatContinuityAndDuplicateRejection() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Packet 1: Initial baseline packet. heartBeatCount = 10, eventTime = 1.000s
        device.parseHeartRateData(100_000L, 70, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)
        assertEquals(70, device.heartRateSensor.value)
        assertEquals(0, receivedEvents.size) // Baseline established, no interval yet
        assertNull(device.rrIntervalSensor)

        // Packet 2: Duplicate 4-Hz packet (same count = 10, same time = 1.000s). deltaC = 0 -> Discard
        device.parseHeartRateData(100_250L, 70, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)
        assertEquals(0, receivedEvents.size)

        // Packet 3: Next beat! count = 11, eventTime = 1.800s. deltaC = 1 -> rrMs = 800.0 ms
        device.parseHeartRateData(100_800L, 72, 11L, BigDecimal("1.800"), DataState.LIVE_DATA)
        assertEquals(72, device.heartRateSensor.value)
        assertEquals(1, receivedEvents.size)
        assertEquals(800.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(11, receivedEvents[0].rawCounter)
        assertEquals(ProtocolType.ANT_PLUS, receivedEvents[0].protocol)
        assertEquals(0, receivedEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
        assertTrue("Beat timestamp must be UTC epoch milliseconds", receivedEvents[0].beatTimestampMs >= 1_000_000_000_000L)
        assertNotNull(device.rrIntervalSensor)
        assertEquals(800, device.rrIntervalSensor?.value)

        // Packet 4: Duplicate of beat 11. deltaC = 0 -> Discard
        device.parseHeartRateData(101_050L, 72, 11L, BigDecimal("1.800"), DataState.LIVE_DATA)
        assertEquals(1, receivedEvents.size)
    }

    /**
     * TST-CON-027.4: 8-bit counter rollover (255 -> 0) yields deltaC = 1.
     */
    @Test
    fun testEightBitRolloverContinuity() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Establish baseline at count = 255, eventTime = 10.000s
        device.parseHeartRateData(200_000L, 80, 255L, BigDecimal("10.000"), DataState.LIVE_DATA)

        // Rollover to count = 0, eventTime = 10.750s. (0 - 255) & 0xFF == 1
        device.parseHeartRateData(200_750L, 80, 0L, BigDecimal("10.750"), DataState.LIVE_DATA)

        assertEquals(1, receivedEvents.size)
        assertEquals(750.0, receivedEvents[0].rrMs, 0.01)
        assertEquals(0, receivedEvents[0].rawCounter)
        assertEquals(0, receivedEvents[0].gapBeats)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
        assertEquals(750, device.rrIntervalSensor?.value)
    }

    /**
     * TST-CON-027.4: Multi-beat RF packet loss (deltaC >= 2): do NOT emit summed delta to mRrIntervalSensor;
     * emit RrBeatEvent with gapBeats = deltaC - 1 and ARTIFACT_GAP.
     */
    @Test
    fun testMultiBeatGapFlagging_preventsFalseBradycardiaSpike() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Beat 1: count = 10, eventTime = 1.000s
        device.parseHeartRateData(100_000L, 60, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)

        // Beat 2: count = 11, eventTime = 2.000s (rrMs = 1000.0 ms)
        device.parseHeartRateData(101_000L, 60, 11L, BigDecimal("2.000"), DataState.LIVE_DATA)
        assertEquals(1, receivedEvents.size)
        assertEquals(1000, device.rrIntervalSensor?.value)

        // Dropped RF packet! count jumps from 11 to 13 (deltaC = 2) across 2400 ms (eventTime = 4.400s)
        receivedEvents.clear()
        device.parseHeartRateData(103_400L, 60, 13L, BigDecimal("4.400"), DataState.LIVE_DATA)

        assertEquals(1, receivedEvents.size)
        assertEquals(1, receivedEvents[0].gapBeats) // deltaC - 1 = 1 gap beat
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[0].artifactFlag)
        assertEquals(13, receivedEvents[0].rawCounter)

        // CRITICAL: mRrIntervalSensor must NOT be updated with the combined 2400 ms interval!
        // It must still hold the previous valid value (1000 ms), preventing a false bradycardia spike
        assertEquals(1000, device.rrIntervalSensor?.value)
    }

    /**
     * TST-CON-027.4: Zero detected / sensor disconnected sets HR to null.
     */
    @Test
    fun testZeroDetectedNullsHeartRate() {
        val device = createDevice()
        device.parseHeartRateData(100_000L, 80, 5L, BigDecimal("1.000"), DataState.LIVE_DATA)
        assertEquals(80, device.heartRateSensor.value)

        device.parseHeartRateData(101_000L, 0, 5L, BigDecimal("1.000"), DataState.ZERO_DETECTED)
        assertNull(device.heartRateSensor.value)
    }

    /**
     * TST-TRK-069.1: UTC epoch translation with fallback to System.currentTimeMillis().
     */
    @Test
    fun testEstTimestampUtcEpochConversion() {
        val device = createDevice()
        val now = System.currentTimeMillis()
        val converted = device.convertEstTimestampToUtcEpochMs(100_500L)
        // SystemClock is mocked to 100_000L, so 100_500L gives now + 500ms
        assertTrue("Converted timestamp should be approximately now + 500ms", Math.abs(converted - (now + 500L)) < 100L)

        // Non-positive fallback
        val fallback = device.convertEstTimestampToUtcEpochMs(0L)
        assertTrue("Fallback timestamp should be approximately now", Math.abs(fallback - now) < 50L)

        // Pre-converted epoch timestamp passthrough (>= 1e12 ms)
        val epochTimestamp = 1_700_000_123_456L
        assertEquals(epochTimestamp, device.convertEstTimestampToUtcEpochMs(epochTimestamp))
    }

    /**
     * TST-TRK-069.2: unregisterSensors() resets mRrIntervalSensor to null, allowing clean re-registration on reconnect.
     */
    @Test
    fun testSensorReRegistrationOnReconnect() {
        val device = createDevice()
        // Connect and receive 2 beats
        device.parseHeartRateData(100_000L, 70, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)
        device.parseHeartRateData(100_800L, 72, 11L, BigDecimal("1.800"), DataState.LIVE_DATA)
        assertNotNull("mRrIntervalSensor must be non-null after first beat", device.rrIntervalSensor)

        // Mid-ride disconnect
        device.unregisterSensors()
        assertNull("mRrIntervalSensor must be reset to null in unregisterSensors()", device.rrIntervalSensor)

        // Reconnect: initial baseline and next beat
        device.parseHeartRateData(105_000L, 75, 20L, BigDecimal("5.000"), DataState.LIVE_DATA)
        device.parseHeartRateData(105_850L, 75, 21L, BigDecimal("5.850"), DataState.LIVE_DATA)
        assertNotNull("mRrIntervalSensor must be re-registered upon reconnect", device.rrIntervalSensor)
        assertEquals(850, device.rrIntervalSensor?.value)
    }

    /**
     * TST-TRK-069.1: ZERO_DETECTED resets beat continuity trackers so reconnecting strap starts fresh.
     */
    @Test
    fun testZeroDetectedResetsContinuity() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Initial connection
        device.parseHeartRateData(100_000L, 70, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)
        device.parseHeartRateData(100_800L, 70, 11L, BigDecimal("1.800"), DataState.LIVE_DATA)
        assertEquals(1, receivedEvents.size)

        // Contact lost
        device.parseHeartRateData(101_000L, 0, 11L, BigDecimal("1.800"), DataState.ZERO_DETECTED)
        assertNull(device.heartRateSensor.value)

        // Contact restored with big jump in counter (e.g. 5 minutes later, strap re-worn)
        receivedEvents.clear()
        device.parseHeartRateData(400_000L, 80, 200L, BigDecimal("20.000"), DataState.LIVE_DATA)
        // Must be treated as baseline packet, NOT a massive 189-beat GAP!
        assertEquals(0, receivedEvents.size)

        // Next beat normal
        device.parseHeartRateData(400_800L, 80, 201L, BigDecimal("20.800"), DataState.LIVE_DATA)
        assertEquals(1, receivedEvents.size)
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
        assertEquals(0, receivedEvents[0].gapBeats)
    }

    /**
     * TST-CON-027.6: Clean RR_INTERVAL sensor teardown on disconnect and reconnect continuity for ANT+ (ATT-7530).
     */
    @Test
    fun testUnregisterSensors_cleansUpRrIntervalSensor_andReconnectCreatesNewInstance() {
        val device = createDevice() as TestANTHeartRateDevice
        assertNull("RR sensor must initially be null", device.rrIntervalSensor)

        // Baseline packet + first beat
        device.parseHeartRateData(100_000L, 70, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)
        device.parseHeartRateData(100_800L, 70, 11L, BigDecimal("1.800"), DataState.LIVE_DATA)
        val firstSensor = device.rrIntervalSensor
        assertNotNull("RR sensor should be created after single beat continuity", firstSensor)
        assertEquals(800, firstSensor?.value)

        // Verify present in device sensors before disconnect
        val rrInSensorsBefore = device.sensors.any { it.sensorType == SensorType.RR_INTERVAL }
        assertTrue("RR_INTERVAL must be present in device sensors before disconnect", rrInSensorsBefore)

        // Unregister sensors (simulating disconnect)
        device.unregisterSensors()

        assertNull("mRrIntervalSensor field must be null after unregisterSensors()", device.rrIntervalSensor)
        val rrInSensorsAfter = device.sensors.any { it.sensorType == SensorType.RR_INTERVAL }
        org.junit.Assert.assertFalse("RR_INTERVAL must be removed from device sensors after unregisterSensors()", rrInSensorsAfter)

        // Reconnect: baseline packet + next beat
        device.parseHeartRateData(200_000L, 72, 20L, BigDecimal("10.000"), DataState.LIVE_DATA)
        device.parseHeartRateData(200_750L, 72, 21L, BigDecimal("10.750"), DataState.LIVE_DATA)
        val secondSensor = device.rrIntervalSensor
        assertNotNull("RR sensor should be re-created after reconnect", secondSensor)
        org.junit.Assert.assertNotSame("Reconnected sensor must be a fresh instance", firstSensor, secondSensor)
        assertEquals(750, secondSensor?.value)
    }

    /**
     * TST-TRK-085.1, TST-TRK-085.2: deltaC == 1 monotonic drift clamping (> 2000 ms),
     * physiological [250, 2500] ms gap flagging, and deltaC >= 2 monotonic packet-loss reconstruction.
     */
    @Test
    fun testMonotonicDriftClamping_andPhysiologicalGapFlagging() {
        val device = createDevice()
        val receivedEvents = mutableListOf<RrBeatEvent>()
        device.addRrBeatListener { receivedEvents.add(it) }

        // Baseline at estTimestamp = 100_000L, beat 10, eventTime = 1.000s
        device.parseHeartRateData(100_000L, 75, 10L, BigDecimal("1.000"), DataState.LIVE_DATA)
        // Beat 11 at estTimestamp = 100_800L, eventTime = 1.800s (normal 800 ms)
        device.parseHeartRateData(100_800L, 75, 11L, BigDecimal("1.800"), DataState.LIVE_DATA)
        assertEquals(1, receivedEvents.size)
        val firstBeatTs = receivedEvents[0].beatTimestampMs
        assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
        assertEquals(0, receivedEvents[0].gapBeats)

        // Beat 12 (deltaC == 1): estTimestamp jumps backwards by 4000 ms (> 2000 ms drift) -> must clamp >= firstBeatTs
        device.parseHeartRateData(96_800L, 75, 12L, BigDecimal("2.600"), DataState.LIVE_DATA)
        assertEquals(2, receivedEvents.size)
        val clampedBeatTs = receivedEvents[1].beatTimestampMs
        assertTrue("deltaC == 1 drift re-anchor must stay monotonic >= previous beat ($clampedBeatTs vs $firstBeatTs)", clampedBeatTs >= firstBeatTs)

        // Beat 13 (deltaC == 1): physiological > 2500 ms interval (3200 ms) arriving near wall clock -> ARTIFACT_GAP
        device.parseHeartRateData(104_000L, 60, 13L, BigDecimal("5.800"), DataState.LIVE_DATA)
        assertEquals(3, receivedEvents.size)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[2].artifactFlag)
        assertTrue("Implausible >2500ms interval must set gapBeats > 0", receivedEvents[2].gapBeats > 0)

        // Beat 14 (deltaC == 1): physiological < 250 ms interval (180 ms) -> ARTIFACT_GAP
        device.parseHeartRateData(104_180L, 60, 14L, BigDecimal("5.980"), DataState.LIVE_DATA)
        assertEquals(4, receivedEvents.size)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[3].artifactFlag)

        // Beat 17 (deltaC == 3): multi-beat packet loss where estTimestamp arrives earlier than last beat -> monotonic >= prev
        val preLossTs = receivedEvents[3].beatTimestampMs
        device.parseHeartRateData(90_000L, 60, 17L, BigDecimal("8.380"), DataState.LIVE_DATA)
        assertEquals(5, receivedEvents.size)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[4].artifactFlag)
        assertEquals(2, receivedEvents[4].gapBeats)
        assertTrue("deltaC >= 2 reconstruction must clamp monotonic >= previous beat", receivedEvents[4].beatTimestampMs >= preLossTs)
    }

    @Test
    fun testPerDeviceHrv_antHeartRateRegistersLazilyCalculatesAndClearsOnDisconnectAndReset_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val baseTs = 1_000_000L
        var nowMs = baseTs + 40_000L
        val device = object : ANTHeartRateDevice(mockContext, mockSensorManager, 301L, 12345) {
            override fun notifySensorsChanged() {}
            override fun createPerDeviceHrvController() =
                com.atrainingtracker.banalservice.devices.hrv.PerDeviceHrvController(
                    mContext, this, createHrvHostCallbacks(), { nowMs },
                    startScheduler = false, artifactGateThresholdPercent = 5.0
                )
            fun triggerReset() = onAccumulatorsReset()
            fun triggerDisconnect() = unregisterSensors()
        }

        assertNull(device.getSensor(SensorType.HRV_SDNN))

        // Baseline + 35 beats with alternating 780 / 820 ms
        var eventTimeSec = 1.0
        device.parseHeartRateData(baseTs, 75, 0L, BigDecimal.valueOf(eventTimeSec), DataState.LIVE_DATA)
        for (i in 1..35) {
            val rrSec = if (i % 2 == 0) 0.78 else 0.82
            eventTimeSec = (eventTimeSec + rrSec) % 64.0
            device.parseHeartRateData(baseTs + i * 800L, 75, i.toLong(), BigDecimal.valueOf(eventTimeSec), DataState.LIVE_DATA)
        }

        device.perDeviceHrvController.calculateMetrics()
        assertNotNull("ANT+ HRM must expose per-device HRV_RMSSD", device.getSensor(SensorType.HRV_RMSSD)?.value)
        assertNotNull("ANT+ HRM must expose per-device HRV_SDNN", device.getSensor(SensorType.HRV_SDNN)?.value)
        assertNotNull("ANT+ HRM must expose per-device HRV_ARTIFACT_RATE", device.getSensor(SensorType.HRV_ARTIFACT_RATE)?.value)

        // Accumulator reset clears to null without removing sensors
        val sdnnSensor = device.getSensor(SensorType.HRV_SDNN)!!
        val seen = mutableListOf<Any?>()
        sdnnSensor.addSensorListener { seen.add(it) }
        device.triggerReset()
        assertNull(seen.last())
        assertNotNull(device.getSensor(SensorType.HRV_SDNN))

        // Disconnect removes all 4 HRV sensors
        device.triggerDisconnect()
        assertNull(device.getSensor(SensorType.HRV_DFA_A1))
        assertNull(device.getSensor(SensorType.HRV_RMSSD))
        assertNull(device.getSensor(SensorType.HRV_ARTIFACT_RATE))
        assertNull(device.getSensor(SensorType.HRV_SDNN))
        device.shutDown()
    }
}
