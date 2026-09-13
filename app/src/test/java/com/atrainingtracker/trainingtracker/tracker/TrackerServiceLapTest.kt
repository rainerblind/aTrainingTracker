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

package com.atrainingtracker.trainingtracker.tracker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying TrackerService lap creation guards and zero-duration phantom prevention (REQ-TRK-002, TST-TRK-002, ATT-896).
 */
class TrackerServiceLapTest {

    data class SavedLapRecord(
        val lapNr: Long,
        val lapTime: Int,
        val lapDistance: Double,
        val averageSpeed: Double
    )

    private class TestableLapTrackerService : TrackerService() {
        val savedLaps = mutableListOf<SavedLapRecord>()

        override fun saveLap(lapNr: Long, lapTime: Int, lapDistance: Double, averageSpeed: Double) {
            savedLaps.add(SavedLapRecord(lapNr, lapTime, lapDistance, averageSpeed))
        }

        fun invokeCreateNewLap() {
            createNewLap()
        }

        fun invokeLapSummaryReceiver(context: Context, intent: Intent) {
            mLapSummaryReceiver.onReceive(context, intent)
        }
    }

    private lateinit var service: TestableLapTrackerService
    private lateinit var mockBanalService: BANALServiceComm
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        service = TestableLapTrackerService()
        mockBanalService = mockk<BANALServiceComm>(relaxed = true)
        service.mBanalService = mockBanalService
        mockContext = mockk<Context>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCreateNewLap_zeroDurationAndZeroDistance_discardsPhantomLap() {
        every { mockBanalService.getBestSensorData(SensorType.LAP_NR) } returns SensorData(SensorType.LAP_NR, 1, "1", "test")
        every { mockBanalService.getBestSensorData(SensorType.TIME_LAP) } returns SensorData(SensorType.TIME_LAP, 0, "0", "test")
        every { mockBanalService.getBestSensorData(SensorType.DISTANCE_m_LAP) } returns SensorData(SensorType.DISTANCE_m_LAP, 0.0, "0.0", "test")

        service.invokeCreateNewLap()

        assertTrue("Zero-duration and zero-distance lap must be discarded (ATT-896)", service.savedLaps.isEmpty())
    }

    @Test
    fun testCreateNewLap_validDurationAndDistance_savesLapWithCalculatedSpeed() {
        every { mockBanalService.getBestSensorData(SensorType.LAP_NR) } returns SensorData(SensorType.LAP_NR, 1, "1", "test")
        every { mockBanalService.getBestSensorData(SensorType.TIME_LAP) } returns SensorData(SensorType.TIME_LAP, 1200, "1200", "test")
        every { mockBanalService.getBestSensorData(SensorType.DISTANCE_m_LAP) } returns SensorData(SensorType.DISTANCE_m_LAP, 3000.0, "3000.0", "test")

        service.invokeCreateNewLap()

        assertEquals("Exactly 1 lap must be saved", 1, service.savedLaps.size)
        val lap = service.savedLaps[0]
        assertEquals(1L, lap.lapNr)
        assertEquals(1200, lap.lapTime)
        assertEquals(3000.0, lap.lapDistance, 0.001)
        assertEquals(2.5, lap.averageSpeed, 0.001) // 3000m / 1200s = 2.5 m/s
    }

    @Test
    fun testCreateNewLap_zeroTimeWithPositiveDistance_guardsAgainstDivisionByZero() {
        every { mockBanalService.getBestSensorData(SensorType.LAP_NR) } returns SensorData(SensorType.LAP_NR, 2, "2", "test")
        every { mockBanalService.getBestSensorData(SensorType.TIME_LAP) } returns SensorData(SensorType.TIME_LAP, 0, "0", "test")
        every { mockBanalService.getBestSensorData(SensorType.DISTANCE_m_LAP) } returns SensorData(SensorType.DISTANCE_m_LAP, 50.0, "50.0", "test")

        service.invokeCreateNewLap()

        assertEquals("Lap with positive distance must be saved", 1, service.savedLaps.size)
        val lap = service.savedLaps[0]
        assertEquals(2L, lap.lapNr)
        assertEquals(0, lap.lapTime)
        assertEquals(50.0, lap.lapDistance, 0.001)
        assertEquals("Average speed must fallback safely to 0.0 instead of NaN / Infinity", 0.0, lap.averageSpeed, 0.001)
        assertFalse("Average speed must not be NaN", lap.averageSpeed.isNaN())
        assertFalse("Average speed must not be Infinite", lap.averageSpeed.isInfinite())
    }

    @Test
    fun testLapSummaryReceiver_zeroDurationAndZeroDistance_discardsEmptyBroadcast() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.getIntExtra(BANALService.PREV_LAP_NR, any()) } returns 1
        every { mockIntent.getIntExtra(BANALService.PREV_LAP_TIME_S, any()) } returns 0
        every { mockIntent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, any()) } returns 0.0
        every { mockIntent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, any()) } returns 0.0

        service.invokeLapSummaryReceiver(mockContext, mockIntent)

        assertTrue("Zero-duration broadcast summary must be discarded (ATT-896)", service.savedLaps.isEmpty())
    }

    @Test
    fun testLapSummaryReceiver_validSplit_savesCorrectRecord() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.getIntExtra(BANALService.PREV_LAP_NR, any()) } returns 1
        every { mockIntent.getIntExtra(BANALService.PREV_LAP_TIME_S, any()) } returns 600
        every { mockIntent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, any()) } returns 1800.0
        every { mockIntent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, any()) } returns 3.0

        service.invokeLapSummaryReceiver(mockContext, mockIntent)

        assertEquals("Valid broadcast summary must be saved", 1, service.savedLaps.size)
        val lap = service.savedLaps[0]
        assertEquals(1L, lap.lapNr)
        assertEquals(600, lap.lapTime)
        assertEquals(1800.0, lap.lapDistance, 0.001)
        assertEquals(3.0, lap.averageSpeed, 0.001)
    }

    @Test
    fun testLapSummaryReceiver_naNSpeed_recalculatesSafely() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every { mockIntent.getIntExtra(BANALService.PREV_LAP_NR, any()) } returns 2
        every { mockIntent.getIntExtra(BANALService.PREV_LAP_TIME_S, any()) } returns 500
        every { mockIntent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, any()) } returns 2000.0
        every { mockIntent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, any()) } returns Double.NaN

        service.invokeLapSummaryReceiver(mockContext, mockIntent)

        assertEquals("Lap with NaN speed must be safely saved", 1, service.savedLaps.size)
        val lap = service.savedLaps[0]
        assertEquals(4.0, lap.averageSpeed, 0.001) // 2000m / 500s = 4.0 m/s
    }
}
