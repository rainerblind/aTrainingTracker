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

import android.app.Notification
import android.content.Intent
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Pure-JVM unit tests verifying that [TrackerService]'s live `mRrBeatListener` excludes optical
 * PPI beats (`isOpticalPpi = true`) from `WorkoutRr.db` persistence while preserving ECG chest-strap
 * beat persistence (`REQ-HRV-051.4` / `TST-HRV-051.4`).
 */
class TrackerServiceRrOpticalIsolationTest {

    private class TestableTrackerService : TrackerService() {
        override fun hasBackgroundLocationPermission(): Boolean = true
        override fun hasLocationPermission(): Boolean = true
        override fun performStopSelf() {}
        override fun performStartForeground(id: Int, notification: Notification?, foregroundServiceType: Int) {}
        override fun sendBroadcast(intent: Intent?) {}
        override fun getPackageName(): String = "com.atrainingtracker"
        override fun performSuperOnDestroy() {}
    }

    private lateinit var trackerService: TestableTrackerService
    private lateinit var mockRepository: WorkoutRrRepository
    private val insertedBatches = CopyOnWriteArrayList<List<WorkoutRrInterval>>()

    @Before
    fun setUp() {
        mockRepository = mockk(relaxed = true)
        insertedBatches.clear()
        every { mockRepository.insertIntervalsBlocking(any()) } answers {
            val list = firstArg<List<WorkoutRrInterval>>()
            insertedBatches.add(ArrayList(list))
            list.size
        }

        trackerService = TestableTrackerService()
        trackerService.mWorkoutRrRepository = mockRepository
        setField(trackerService, "mWorkoutID", 42L)

        val mockAutoPause = mockk<AutoPauseManager>(relaxed = true)
        every { mockAutoPause.currentState } returns AutoPauseState.TRACKING
        setField(trackerService, "mAutoPauseManager", mockAutoPause)
    }

    @After
    fun tearDown() {
        trackerService.onDestroy()
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = TrackerService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getRrBeatListener(): RrBeatListener {
        val field = TrackerService::class.java.getDeclaredField("mRrBeatListener")
        field.isAccessible = true
        return field.get(trackerService) as RrBeatListener
    }

    @Suppress("UNCHECKED_CAST")
    private fun getLiveRrQueue(): ConcurrentLinkedQueue<WorkoutRrInterval> {
        val field = TrackerService::class.java.getDeclaredField("mLiveRrQueue")
        field.isAccessible = true
        return field.get(trackerService) as ConcurrentLinkedQueue<WorkoutRrInterval>
    }

    @Test
    fun testOpticalPpiBeatsExcludedFromWorkoutRrQueue_whileEcgBeatsPersistNormally_TST_HRV_051_4() {
        // REQ-HRV-051.4 / TST-HRV-051.4
        val listener = getRrBeatListener()
        val queue = getLiveRrQueue()

        // 1. Feed 20 optical PPI beats during active TRACKING workout -> 0 rows queued or flushed
        for (i in 0 until 20) {
            listener.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = 1_700_000_000_000L + i * 800L,
                    rrMs = 800.0 + (i % 3) * 10.0,
                    rawCounter = i,
                    protocol = ProtocolType.BLE,
                    gapBeats = 0,
                    artifactFlag = RrBeatEvent.ARTIFACT_NORMAL,
                    isOpticalPpi = true
                )
            )
        }
        assertTrue("Optical PPI beats must never enter WorkoutRr queue", queue.isEmpty())
        assertTrue("Optical PPI beats must never flush to WorkoutRrRepository", insertedBatches.isEmpty())

        // 2. Feed 5 ECG beats (isOpticalPpi = false) -> all 5 queued normally
        for (i in 0 until 5) {
            listener.onRrBeat(
                RrBeatEvent(
                    beatTimestampMs = 1_700_000_020_000L + i * 790L,
                    rrMs = 790.0 + i * 5.0,
                    rawCounter = 100 + i,
                    protocol = ProtocolType.BLE,
                    gapBeats = 0,
                    artifactFlag = RrBeatEvent.ARTIFACT_NORMAL,
                    isOpticalPpi = false
                )
            )
        }
        assertEquals("ECG beats must continue queuing normally into WorkoutRr.db", 5, queue.size)
        val queued = queue.toList()
        assertEquals(42L, queued[0].workoutId)
        assertEquals(790f, queued[0].rrMs, 0.01f)
        assertEquals(810f, queued[4].rrMs, 0.01f)
    }
}
